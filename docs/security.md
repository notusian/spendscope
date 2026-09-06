# Security

This document describes what SpendScope actually does, and — in the last
section — what it does not. The limitations are stated because a security
document that only lists strengths is not a security document.

## Threat model

SpendScope holds a user's transaction history: where they shop, when, and for
how much. That is sensitive personal data even though it contains no card
numbers and moves no money. The threats that matter most, in order:

1. **One user reading another user's data.** The highest-impact and most likely
   failure for an application shaped like this.
2. **Session theft**, via XSS or a leaked cookie.
3. **Cross-site request forgery** — inherent to cookie authentication.
4. **Malicious uploads**, since users upload files by design.
5. **Credential attacks** against the login endpoint.

## Authentication

**Session cookies, not JWTs.** The only client is a browser. A server-side
session can be revoked the instant a user logs out; a stateless JWT cannot be,
without building the revocation list that made it stateless in the first place.
Storing a JWT in `localStorage` would also make it readable by any injected
script, which is strictly worse than an `HttpOnly` cookie.

**Password hashing** uses Spring Security's `DelegatingPasswordEncoder`, which
stores an algorithm prefix with the hash:

```
{bcrypt}$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa
```

That prefix is what makes it possible to move to a stronger algorithm later and
re-hash on next login, rather than being locked into today's choice forever.

Passwords are 10–72 characters. The upper bound is not arbitrary: BCrypt
silently ignores everything past 72 bytes, so accepting a 100-character password
would give the user a false sense of strength.

**Account enumeration is prevented.** A wrong password and an unknown email
produce the same status, the same error code and the same message
(`"Invalid email or password"`). `AuthenticationIT` asserts the two responses
are byte-identical.

**Login is throttled** per `email + client IP`, defaulting to 10 failures per 15
minutes, after which the endpoint returns `429`. Keying on both is deliberate:
keying on email alone would let anyone lock any account out; keying on IP alone
would let one address spray many accounts cheaply.

## Session security

| Cookie | Flags | Why |
|---|---|---|
| `JSESSIONID` | `HttpOnly`, `SameSite=Lax` (dev) / `None` (prod split-origin), `Secure` in production | JavaScript must never be able to read the session id |
| `XSRF-TOKEN` | readable by JS, `Secure` in production | The SPA has to read it to echo it back in a header |

`SESSION_COOKIE_SECURE` is `false` in local development and `true` in
production. This is not laziness: over plain HTTP a `Secure` cookie is never
sent back, so login would silently fail on `localhost`.

**Session fixation** is defeated by rotating the session id on login, via
Spring Security's `ChangeSessionIdAuthenticationStrategy`. An attacker who
planted a session id in the victim's browser does not learn the new one.

Sessions expire after 30 minutes of inactivity and are invalidated explicitly on
logout.

## CSRF

Cookie authentication means the browser attaches credentials automatically —
which is exactly what CSRF exploits. Protection is therefore **on**, not
disabled to make development easier.

The mechanism is the standard SPA double-submit:

1. The server sets a random token in a JS-readable `XSRF-TOKEN` cookie.
2. The SPA reads it and echoes it in an `X-XSRF-TOKEN` header on every
   state-changing request.
3. The server compares the two.

A cross-site page can cause the browser to *send* the cookie, but the same-origin
policy stops it from *reading* the cookie, so it cannot produce the header.

Two configuration details that are easy to get wrong and are handled explicitly:

- `CsrfTokenRequestAttributeHandler` with `setCsrfRequestAttributeName(null)`.
  The default BREACH mitigation is designed for server-rendered forms and
  breaks the SPA flow.
- A `CsrfCookieFilter` that forces the token to materialise. Spring resolves the
  token lazily; without this the cookie the browser needs would never be sent.

Safe methods (`GET`, `HEAD`, `OPTIONS`) do not require a token.

## CORS

```
allowed-origins: ${FRONTEND_ORIGIN}    # exact origins, comma separated
allow-credentials: true
allowed-methods: GET, POST, PUT, PATCH, DELETE, OPTIONS
allowed-headers: Content-Type, Accept, X-XSRF-TOKEN, X-Requested-With
```

**Never a wildcard.** `Access-Control-Allow-Origin: *` is illegal together with
`Allow-Credentials: true`, and allowing any origin would let a hostile page
drive the API using the victim's session cookie.

**Development and production differ, deliberately:**

- **Development:** the Vite dev server proxies `/api` to the backend, so requests
  are same-origin and CORS is not exercised at all. `http://localhost:5173` is
  still listed so running the frontend without the proxy also works.
- **Production, single origin (recommended):** nginx serves the bundle and
  proxies `/api`. Again same-origin; CORS is a no-op.
- **Production, split origin:** static frontend on one host, API on another.
  `FRONTEND_ORIGIN` must be the exact frontend origin, and the session cookie
  needs `SameSite=None; Secure` — which is why the prod profile sets it.

`WebSecurityIT` asserts that a request from `https://evil.example.com` is
rejected and that the response never contains a wildcard origin.

## Authorisation and data isolation

This is the part that matters most, so it is enforced structurally rather than
by convention.

Ids are sequential integers, so guessing another user's transaction id is
trivial. The defence is that the authenticated user id is part of every query:

```java
transactionRepository.findByIdAndUserId(transactionId, authenticatedUserId)
```

- The user id comes from the **session**, never from a request parameter.
- Every service method takes `userId` as its first argument.
- Aggregate and analytics queries are scoped too — a leaking totals endpoint is
  as bad as a leaking detail endpoint.
- A row that does not exist and a row owned by someone else both return **404**.
  Returning 403 for the latter would confirm which ids are real.

Protected this way: accounts, transactions, categories, budgets, dashboard
widgets, imports, analytics, recurring detection, anomalies and the audit trail.

`CrossUserAccessIT` covers the matrix explicitly, including the less obvious
vectors: Bob attaching a transaction to Alice's account, Bob importing a CSV
into Alice's account, Bob using Alice's private category, and Bob reordering
Alice's widgets.

## Input validation

Everything is validated server-side. Frontend validation exists only to give
faster feedback and is never trusted.

- Jakarta Bean Validation on every request body and query-parameter object.
- Currency codes checked against the JDK's ISO-4217 table, not a
  `[A-Z]{3}` regex that would accept `ZZZ`.
- Pagination clamped: page size is capped at 200, and `?size=100000` is a
  validation error rather than a way to make the server materialise every row.
- Sort fields are an **allowlist**. Passing an arbitrary property through to
  Spring Data would leak entity field names through error messages.
- Dates sanity-checked: nothing before 1970 or more than a year in the future,
  which is almost always a typo in the year.
- Inverted date ranges are rejected rather than silently returning nothing.

**Mass assignment** is impossible by construction: request DTOs are records with
only the fields a client may set. There is no `id`, `userId` or `createdAt`
field to overpost into, and entities are never bound directly to request bodies.

**SQL injection**: all dynamic filtering goes through the JPA Criteria API and
all native queries use bound parameters. `date_trunc`'s unit is bound as a
parameter and can only originate from an enum, never from a client. LIKE
wildcards in a search term are escaped, so a search for `50%` does not match
everything.

## File upload security

Users upload financial statements, so this is a real attack surface.

| Control | Implementation |
|---|---|
| Size limit | 5 MB, enforced at both the servlet layer and in the service |
| Row limit | 10,000, enforced during parsing |
| Content validation | Strict UTF-8 decode plus a NUL-byte scan |
| Parsing | Apache Commons CSV — not `String.split(",")` |
| Filename | Sanitised: path components stripped, control characters removed, length capped |
| Storage | **The file is never written to disk** |

**The filename is not trusted.** A file called `statement.csv` containing a JPEG
is rejected; a file called `statement.txt` containing valid CSV is accepted. The
*content* decides.

**No filesystem path is ever built from user input.** The upload is processed
from memory. `sanitiseFilename` exists only because the name is stored and later
rendered in the UI — `../../../etc/passwd` becomes `passwd`.

**Raw statements are not retained.** Only the parsed transactions and a small
row-count summary survive an import. A directory of everyone's bank statements
would be a high-value target that no feature depends on, so it does not exist.
`CsvImportIT` asserts there is no column anywhere capable of holding file
content.

**CSV injection** (a description beginning `=`, `+`, `@`) is stored as-is and
rendered as text by React, never as a formula. It would matter if SpendScope
exported CSV for Excel; it does not. If export is ever added, values starting
with those characters must be prefixed.

## Error handling

One shape for every error, from one `@RestControllerAdvice`:

```json
{
  "timestamp": "2026-09-06T12:00:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "The request contains invalid fields",
  "path": "/api/transactions",
  "fieldErrors": { "amount": "Amount is required" }
}
```

Clients never see a stack trace, an exception class name, a SQL fragment or a
constraint name. Anything unrecognised is logged at ERROR with full detail
server-side and returned as a bare 500. `WebSecurityIT` asserts that error
bodies contain none of `com.spendscope`, `org.springframework`, `org.hibernate`,
`Exception`, `at java.`, `SELECT` or `jdbc`.

Spring's own error page is locked down too:
`server.error.include-message=never`, `include-stacktrace=never`.

## Security headers

Set on every response:

| Header | Value |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `same-origin` |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'` |

The CSP is that restrictive because the API only ever returns JSON — it needs no
script, style or frame sources of its own. HTTP Basic is disabled so browsers
are never offered a native auth prompt the SPA cannot control.

## Audit logging

Recorded: registration, login success, login failure, login blocked, logout,
password change, account create/update/archive/delete, category create/delete,
transaction create/update/delete, CSV import and rejection, budget changes,
dashboard changes, demo session creation.

Users see **their own** events on the Security page. This is not an
administrative view of the system log.

Metadata is **filtered before it is stored.** A denylist drops any key containing
`password`, `secret`, `token`, `cookie`, `session`, `authorization`,
`credential`, `hash`, `csrf` or `apikey`, matched case-insensitively as a
substring, and values are truncated to 200 characters. `AuditMetadataTest` pins
this down. Writing an audit row can never fail a business operation: it runs in
its own transaction and a failure is logged, not propagated.

**Application logs** record method, path, status and duration, plus a short
correlation id. They deliberately do **not** log headers, cookies, request
bodies or query strings, any of which can carry credentials or financial data.

## Secrets management

Nothing secret is committed. All configuration comes from environment variables
(`.env.example` documents them); `.env` is gitignored. There are no default
production credentials — the defaults in `application.yml` are local-development
values, and the prod profile requires the environment to supply real ones.

Actuator exposes only `/actuator/health`, with `show-details: never`.
`/actuator/env`, `/beans`, `/configprops` and `/heapdump` would each leak
configuration and are not exposed; `WebSecurityIT` asserts they return 4xx.
OpenAPI and Swagger UI are disabled entirely in production.

## Rate limiting

| Endpoint | Limit |
|---|---|
| `POST /api/auth/login` | 10 failures per email+IP per 15 minutes |
| `POST /api/demo/session` | 5 per IP per hour |

The demo endpoint is limited because it is the only place an anonymous caller
can create rows — each call writes three accounts and several hundred
transactions.

Both limiters are **in-process**, which is stated plainly in the limitations
below.

## Dependency management

Dependabot is configured for Maven, npm and GitHub Actions, with related updates
grouped so a week of patch bumps is one pull request. CodeQL runs on every push
and pull request for both Java and TypeScript.

## Known limitations

Stated rather than hidden. None of these is a defect in what was built; they are
the boundaries of what was built.

1. **Rate limiting is per-process.** With more than one backend instance behind
   a load balancer, each keeps its own counters, so the effective limit is
   multiplied by the instance count. A shared store or an edge rate limiter is
   the real answer at that scale.

2. **Sessions are in-memory.** A restart logs everyone out, and horizontal
   scaling needs sticky sessions. Spring Session with JDBC would fix both and is
   a small change.

3. **No email verification or password reset.** There is no mail infrastructure,
   so a forgotten password is unrecoverable. Anyone can register with an address
   they do not own.

4. **No multi-factor authentication.**

5. **`X-Forwarded-For` is ignored** when identifying a client for rate limiting.
   The header is attacker-controlled unless every hop is a trusted proxy that
   overwrites it. Behind a load balancer, set
   `server.forward-headers-strategy=framework` (the prod profile does) so the
   container populates the remote address from the trusted header.

6. **No account lockout or breach notification.** Repeated failures are throttled
   and audited, but the user is not emailed and the account is not locked.

7. **Data is not encrypted at rest by the application.** This is delegated to the
   database and its host — standard for managed PostgreSQL, but worth knowing it
   is not application-level encryption.

8. **No GDPR-style data export or deletion endpoint.** The cascade behaviour
   makes deletion a one-line operation, but no user-facing endpoint exposes it.

9. **The audit trail is append-only by convention, not by permission.** The
   application never updates or deletes audit rows, but the database user it
   connects as could.

10. **Analytics sum across currencies without conversion.** A user whose
    accounts are all in one currency — the overwhelmingly common case — gets
    correct numbers. A genuinely multi-currency user would need FX rates.
