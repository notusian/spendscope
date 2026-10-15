# Security

SpendScope stores sensitive transaction histories. The main threats considered
here are access to another user's records, compromised sessions, forged browser
requests, malicious uploads, and repeated credential attempts. This document
records implemented controls and their limits; it is not a security audit.

## Authentication and sessions

Passwords use Spring Security's delegating password encoder with BCrypt. The
stored algorithm prefix permits future encoder changes, but the current login
flow does not implement automatic password-hash migration. New passwords must
satisfy the length rules and fit within BCrypt's 72-byte UTF-8 input limit;
character count alone is insufficient for non-ASCII text.

A failed login returns the same public error for an unknown email and an
incorrect password. Registration still reports an existing email as a conflict,
so the application does not prevent account enumeration across all endpoints.

Successful authentication rotates an existing session ID before saving the
security context. Sessions expire after 30 minutes of inactivity, and logout
invalidates the current session. Password changes require the current password,
but do not revoke other sessions that are already logged in.

| Cookie | Purpose and configuration |
|---|---|
| `JSESSIONID` | The session identifier is `HttpOnly`; the local profile uses `SameSite=Lax`, and the production profile sets `Secure` and `SameSite=None`. |
| `XSRF-TOKEN` | JavaScript reads this token and sends it in `X-XSRF-TOKEN`; the cookie repository derives its secure setting from the request. |

An `HttpOnly` cookie prevents scripts from reading its value, but an injected
script can still issue requests from the user's browser. Cookie protection does
not replace prevention of cross-site scripting.

## CSRF and browser origins

State-changing requests, including registration, login, and demo creation,
require a CSRF token. The browser first obtains the token cookie from
`GET /api/auth/csrf`, then echoes its value in a request header. Spring Security
checks the supplied value; `CsrfCookieFilter` ensures the deferred token is
available to the SPA. Safe methods do not require the header.

The backend allows credentialed CORS requests only from configured origins.
The supplied frontend nevertheless uses a relative `/api` URL and reads the
CSRF cookie on its own origin, so the working deployment is a single origin
with a reverse proxy. A separate API hostname needs frontend and token-delivery
changes. Different origins do not always mean different sites, and
`SameSite=None` is not a general requirement for all cross-origin deployments.

In production, forwarded headers are enabled. The trusted proxy must overwrite
client-supplied forwarding headers and restrict direct access to the backend.
Otherwise an attacker may influence the request scheme or client address used
for cookies and throttling.

## Ownership checks

Controllers obtain the user ID from the authenticated principal. Services use
that ID in resource lookups and aggregate queries, including checks on linked
accounts and categories. A missing private resource and one owned by another
user return 404. Built-in categories are shared read-only records.

`CrossUserAccessIT` covers direct reads and writes as well as associations,
imports, widget ordering, and aggregate data. These tests are important because
extending `JpaRepository` still exposes unscoped methods; the framework does not
automatically add an ownership predicate to an arbitrary new query.

## Validation and uploads

Request records expose client-editable fields separately from entity IDs and
ownership. Bean Validation and service checks enforce required values, supported
currencies, date ranges, and page sizes. Sort fields are allowlisted. Criteria
queries and native-query parameters keep user input separate from SQL syntax,
and transaction searches escape wildcard characters.

| Upload control | Implementation |
|---|---|
| File size | The default file limit is 5 MB, with a 6 MB multipart request ceiling. |
| Row count | Parsing accepts at most 10,000 data rows. |
| Encoding | The parser requires UTF-8 and rejects NUL bytes. |
| Structure | Apache Commons CSV parses quoted fields and validates required headers. |
| Row feedback | Invalid data rows are counted, with at most 50 detailed errors returned. |
| Filename | Directory components and control characters are removed before the label is stored. |

The service reads content into memory and does not retain an original statement
in the database or application storage. The servlet container may spool multipart
content to temporary files before the service receives it; deployment storage
and cleanup must account for that. The sanitised filename is a display label,
not a path used to open a file.

Descriptions are rendered as text in React. Formula-like content would need a
separate escaping policy if spreadsheet export were added.

## Error responses, headers, and logs

Application failures use a structured response containing a code, message,
status, path, and optional field errors. Unexpected exceptions are logged on
the server and returned without stack traces or database details. Security
filters have matching error handlers, and Spring's default error configuration
also suppresses implementation details. A proxy can still return its own error
format, which the frontend handles with a fallback message.

The API sets `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, a
same-origin referrer policy, and a restrictive content security policy. These
headers apply to API responses; they are not a replacement for the frontend
host's document policy. The restrictive API CSP can also interfere with the
locally enabled Swagger UI, which serves scripts and styles.

Audit events record authentication activity and changes to application data.
Users can read their own events. Metadata keys associated with credentials are
filtered, values are capped, and audit write failures are logged without being
propagated to the main operation. A key denylist does not detect a secret placed
under an unrelated key, so callers must still choose safe metadata.

Request logs contain method, path, status, duration, and a correlation identifier;
the request-logging filter does not log bodies, cookies, or query strings. Log
access and retention remain operational responsibilities.

## Rate limiting and deployment configuration

Login failures are limited to 10 per email-and-IP pair in 15 minutes. Demo
creation is limited to five requests per IP per hour. These controls are stored
in process. Pair-based login throttling does not stop an attacker rotating IPs
or spraying many emails, and ordinary registration has no equivalent limiter.

The production profile enables secure session cookies and disables OpenAPI.
It still inherits the base database defaults unless deployment variables override
them. Set deployment-specific credentials, protect the database network, and
configure TLS explicitly; selecting `prod` does not perform those tasks.
`.env` is ignored by Git, but that does not establish that a repository's entire
history is free of secrets.

Health endpoints expose status without component details. Other Actuator
endpoints are not exposed. Dependabot and CodeQL are configured in the repository;
the presence of a workflow does not establish that its latest run passed.

## Remaining limitations

- Sessions and throttling state are local to one process. Durable shared sessions
  and shared rate limits are separate requirements for multiple backend instances.
- Email ownership verification, password recovery, multi-factor authentication,
  account lockout, and breach notification are absent.
- Password changes leave other active sessions valid, and registration reveals
  whether an email is already used.
- Database encryption at rest, backups, retention, and access control depend on
  the deployment. The application does not encrypt individual financial fields.
- Users cannot export all data or delete their account through a dedicated API.
- Audit records are append-only by application convention; the database role can
  modify them, and events are not an immutable compliance record.
- Upload limits bound an individual request, but concurrent uploads and anonymous
  registration still require capacity controls for a public deployment.
