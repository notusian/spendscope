# Code walkthrough

This guide connects the main implementation paths to the concepts they use.
It assumes basic Java and HTTP knowledge. The accompanying tests are useful
examples to run and modify; reading an explanation alone does not establish
that the implementation behaves as intended.

## 1. Startup, dependency injection, and schema ownership

Start with `SpendScopeApplication`, `application.yml`, and the Flyway migrations.
Spring scans the application packages and constructs components through their
constructors. Constructor injection makes a service's dependencies visible and
lets unit tests supply a fixed clock or a test double.

Flyway applies ordered SQL migrations before Hibernate validates the entity
mappings. These responsibilities differ: a migration changes the schema, while
validation checks whether the mappings can use it. Neither replaces tests for
business behaviour. A migration that has already been applied should be followed
by a new migration rather than edited in place.

## 2. Trace a request through its boundaries

Follow `POST /api/accounts` through `SecurityConfig`, `AccountController`,
`AccountService`, and `AccountRepository`. The security filters establish the
principal and check CSRF. The controller validates the request body, and the
service checks the business rules before writing through the repository.

The HTTP request contains an account name, type, and currency; ownership comes
from the principal. Keeping the request record separate from the entity prevents
the API contract from accidentally exposing an editable `userId` or password
hash. A response record similarly defines which fields leave the service.

The backend returns JSON errors through `GlobalExceptionHandler`, with separate
handlers for failures that occur in the security filters before a controller
runs. This is why controller advice alone does not cover authentication errors.

## 3. Separate authentication from authorisation

`AuthService.login` checks the login limiter, delegates password verification to
Spring Security, rotates an existing session ID, and saves the authenticated
security context in the session. The next request supplies the session cookie,
which reconnects it to that principal.

Authentication does not grant access to every record. Compare these lookups:

```java
repository.findById(transactionId);
repository.findByIdAndUserId(transactionId, authenticatedUserId);
```

The second includes ownership in the database predicate. The same check is
needed for IDs supplied as relationships, such as an account attached to a new
transaction. `CrossUserAccessIT` demonstrates why testing only the detail
endpoint would leave important paths uncovered.

Passwords are encoded, not encrypted for later recovery. BCrypt's limit is in
UTF-8 bytes, so a string containing non-ASCII characters can exceed the limit
before reaching 72 characters. Length validation must check the representation
that the encoder actually consumes.

## 4. Understand cookies and CSRF independently

The session cookie is `HttpOnly`, while the CSRF token cookie is readable by
JavaScript. `api/client.ts` copies the latter into `X-XSRF-TOKEN` for writes.
A hostile site cannot normally read the victim origin's token, even if the
browser would attach cookies to a forged request.

`HttpOnly` protects the cookie value from direct script access; it does not
prevent an injected same-origin script from acting as the user. CSRF protection
also does not prevent cross-site scripting. These controls address different
ways a browser request could be misused.

The client explicitly sets `credentials: 'include'`. Fetch already sends cookies
by default for same-origin requests; `include` also permits credentialed
cross-origin requests when browser and server policies allow them. The current
application still expects the API on the same origin because its URL is relative
and its CSRF token is read from `document.cookie`.

## 5. Locate transaction boundaries

Spring applies `@Transactional` through a proxy around a managed service. A
successful operation commits its changes; an unchecked failure normally causes
a rollback. Calling an annotated method directly from another method on the
same instance does not cross that proxy and therefore does not create a new
transaction boundary by itself.

`CsvImportService.importCsv` treats the accepted transaction rows and import
summary as one operation. A malformed data row can be counted as a row-level
rejection while other rows succeed, whereas a structural CSV failure aborts the
request. An import job saved before an exception is still rolled back with that
transaction; the placement of `save` does not make it durable immediately.

Audit writing uses a separate transaction so audit failures do not abort the
main operation. That separation also means audit records are not a perfect
atomic history of business commits. Transaction propagation must be assessed
against the intended failure behaviour.

## 6. Follow decimal values from input to storage

`Money` defines the common decimal scale, and transaction columns use
`NUMERIC(19,4)`. Constructing a `BigDecimal` from decimal text preserves that
value; constructing it from an already rounded binary floating-point result
cannot recover precision that was lost earlier.

The CSV parser accepts textual amounts, rounds to the supported scale, and
checks the stored result. Checking only before rounding would allow a tiny
non-zero input to become zero, or a boundary amount to round beyond the
permitted range. Tests should cover those boundaries as well as normal amounts.

Read `TransactionRepository` before interpreting a dashboard value. Spending is
`SUM(-amount)` where `amount < 0`; income sums positive values. A refund recorded
as positive therefore affects income and net flow, not spending. Exact arithmetic
cannot resolve an unclear definition of the metric being calculated.

## 7. Follow a CSV row through normalisation and deduplication

`CsvTransactionParser` handles encoding, required columns, quoted fields, and
per-row validation. Strict date parsing matters because accepting an impossible
date by adjusting it into another month changes the financial record.

`DescriptionNormalizer` removes known reference noise. `CategorizationService`
then applies the configured rules in order. A specific merchant such as
`GRABFOOD` needs to precede `GRAB`, and a configured keyword must remain possible
after normalisation. `ShippedCategorizationRulesTest` checks the actual
configuration rather than only a hand-built test rule.

`TransactionFingerprint` hashes the user, account, date, normalised description,
amount, and currency. The import service looks up those fingerprints in chunks
and uses a set for duplicates within the file. This reduces queries, but the
fingerprint does not prove real-world identity: two identical purchases can be
legitimate, and concurrent imports can race without a uniqueness constraint.

## 8. Define comparable periods before calculating percentages

`PeriodResolver` uses an injected `Clock`, allowing tests to select a precise
date. A month-to-date result compares with the same part of the previous month,
while a completed month compares with the previous complete month. Year
comparisons need calendar-aware handling of leap years. Rolling windows compare
with an immediately preceding window of the same length.

`Percentages.change` returns `null` when the earlier value is zero. Returning
zero would claim no change, while division by zero cannot produce a finite
percentage. The UI can show that a comparison is unavailable without inventing
a number.

Database time buckets are made dense in `AnalyticsService`: missing days become
zero-valued points. This differs from dropping missing observations, which would
let a chart visually connect across quiet periods. The top-N limit applies to
category and merchant rankings, not to truncating a time series.

## 9. Distinguish configuration from extension

Inspect `DashboardWidget`, `WidgetType`, and the frontend widget renderer.
Analytics-backed widgets save existing metric and grouping choices. Other types
call dedicated endpoints for budgets, recent transactions, or insights.

Saving configuration lets users choose among supported behaviours. It does not
make the program understand arbitrary new metrics. Adding one may require a
backend enum, query implementation, validation rules, API types, and a renderer.
This distinction keeps the extensibility claim consistent with the code.

## 10. Evaluate the statistical assumptions

`RecurringExpenseDetector` groups payments by merchant, filters amounts around
the median, and checks intervals between qualifying payments. The median is less
sensitive to an isolated gap than the mean, but it is not immune to a short or
mixed series. A confidence grade describes consistency under these rules; it is
not a measured probability that a subscription exists.

`SpendingAnomalyDetector` compares current category spending with historical
amounts from the same part of earlier months. It requires a minimum relative
increase, a minimum absolute increase, and an increase beyond a multiple of the
population standard deviation. Zero-spending months remain part of the baseline.

The thresholds are heuristic. They do not model seasonality, and the short
baseline does not justify interpreting the result as a formal significance test.
Useful tests demonstrate both a pattern that should be reported and similar
patterns that should not satisfy the rules.

## 11. Keep frontend state consistent after writes

The API client handles cookies and errors in one place. TanStack Query keys
include filters so that different transaction searches have distinct cache
entries. Form state remains local while edits are in progress; saved server
responses remain query data.

A transaction mutation can change totals, budgets, recurring detection, and
anomalies. The invalidation helpers list these dependencies so related views
refresh after a write. A central list reduces repetition, but a new feature
still needs its dependency added deliberately. A useful browser test edits a
transaction and observes the dependent result rather than asserting only that
a save button was clicked.

## 12. Verify the layers with different kinds of evidence

Unit tests are suited to pure rules such as date resolution and amount parsing.
PostgreSQL integration tests exercise migrations, native queries, transaction
behaviour, and ownership through the API. Frontend tests check component and
state behaviour, while Playwright follows complete browser workflows.

The Maven build separates `*Test` unit tests from `*IT` integration tests.
`mvn test` runs the former; `mvn verify` also runs the latter using Testcontainers
or the configured test database. GitHub Actions combines those checks with a
frontend build, image builds, and static analysis. A test count alone says little
about whether an important boundary has been covered.

Docker's build stages package source into a backend JAR or a static frontend
bundle, leaving build tools out of the runtime images. Packaging is separate
from deploying securely: HTTPS, trusted proxy headers, credentials, backups,
and operational monitoring still require a concrete deployment configuration.
