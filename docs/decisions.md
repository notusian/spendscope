# Engineering decisions

These decisions describe the current implementation and its costs. They are
intended to make the code reviewable, including the places where a different
scale or product requirement would change the design.

## Java and Spring Boot

Java 21 provides explicit types and `BigDecimal`, while Spring Boot integrates
HTTP validation, transactions, persistence, and session security. This reduces
the amount of application code needed to connect those concerns. The cost is a
larger runtime and more framework configuration than a small HTTP service would
need. A reader still needs to understand proxy-based transactions and the
security filter chain rather than treating annotations as guarantees.

## One backend organised by domain

A single deployable backend keeps imports, transactions, and budgets within one
database transaction model. Domain packages keep related code together without
adding inter-service protocols. Package boundaries are conventions, however,
and services sometimes read another domain's repository. Independent deployment
would require stronger boundaries and a plan for shared data.

## PostgreSQL and migration-controlled schemas

PostgreSQL supports exact decimal storage, `date_trunc`, JSONB, and partial
unique indexes. These features fit analytics queries and shared category
constraints directly. The native SQL ties the application to PostgreSQL and
would need attention during a database migration.

Flyway records explicit schema changes, while `ddl-auto: validate` checks entity
mappings. This makes SQL changes reviewable and avoids automatic schema updates
at application startup. Hibernate validation does not prove that every business
constraint or migration is correct; integration tests must still exercise them.

## Integration tests against PostgreSQL

Integration tests use Testcontainers or an explicitly supplied test database.
This exercises the same database engine as the application, including native
queries and migration behaviour. It costs more setup and runtime than a mocked
repository test. Unit tests remain appropriate for parsing, periods, and
statistical calculations that do not depend on the database.

## Decimal money and signed amounts

Amounts use Java `BigDecimal` and PostgreSQL `NUMERIC(19,4)`. Decimal values such
as `0.10` therefore retain their intended representation through storage and
aggregation. Four decimal places define the supported precision; values still
need explicit range checks and a rounding policy.

Signed amounts keep outgoing and incoming records in one column. Spending
queries select negative amounts and report their magnitude, while net flow
includes both signs. A positive refund therefore increases inflow without
reducing spending. Refund linking or net-spending analysis would require an
additional product rule. Integer minor units would also provide exact arithmetic,
but would require currency-aware scaling throughout the application.

## Session authentication and CSRF

The browser uses an `HttpOnly` session cookie, and logout invalidates the server
session. This is a straightforward fit for a browser application without a
separate mobile or third-party API. Sessions consume server state and currently
disappear when the process restarts.

Because browsers attach cookies automatically, state-changing requests also
require a CSRF header copied from a readable token cookie. `HttpOnly` reduces
session-token exposure but does not prevent an injected script from making
requests in the user's browser. Cookie flags, CSRF validation, and safe rendering
address different threats.

The current frontend assumes one origin for its assets and API. CORS is
configured on the backend, but it does not solve cross-host cookie access for
the frontend's CSRF implementation.

## React, TypeScript, and TanStack Query

The interface is an authenticated dashboard with forms and charts, so a static
SPA can serve it without an application server for frontend rendering.
TypeScript checks the expected API contract at compile time. TanStack Query
provides caching, loading states, and invalidation after mutations.

Neither tool proves that runtime API responses have the expected shape or that
an invalidation list includes every dependency. Browser and integration tests
are still needed. Recharts provides the chart components at the cost of a
substantial client dependency; the build separates chart code into its own
chunk.

## CSS custom properties

Shared colour, spacing, and surface variables keep the interface consistent and
allow light and dark themes to reuse the same component styles. Plain CSS
avoids another styling dependency. Its global scope also makes naming and
cascade order important, especially when adding responsive overrides.

## CSV retention and duplicate detection

The application stores parsed transactions and import metadata rather than the
original statement. That limits retained copies of financial data, but it also
means a later parser change cannot reprocess the original without another
upload. Multipart handling can still use temporary storage at the web-server
layer.

Imports derive a fingerprint from ownership, account, date, normalised
description, amount, and currency. Bulk lookups and an in-file set reduce the
number of database queries. Without a bank transaction ID, the result is a
heuristic: identical legitimate purchases may be treated as duplicates. The
index is non-unique to permit manual duplicates, so simultaneous imports can
also admit duplicate rows.

## Ordered merchant rules

Normalised descriptions are matched against ordered keywords in configuration.
Rules provide an inspectable starting point without external services or labelled
training data. Their scope is limited to known patterns, and broad substrings can
match the wrong merchant. More specific rules such as `GRABFOOD` must precede
`GRAB`; tests also check that configured keywords survive normalisation.

User corrections are useful evidence for a future classifier, but storing a
correction does not by itself provide training, evaluation, or a safe model
update process.

## Statistical insights

Recurring detection groups outgoing payments by merchant, keeps amounts near
the median, and examines median intervals. It requires at least three qualifying
payments and 60% of gaps within a cadence band. Tolerance helps with variable
billing dates, but regular shopping habits can resemble subscriptions, and a
single merchant can sell several unrelated subscriptions.

Anomaly detection requires spending to exceed its historical mean by at least
40%, at least 30 currency units, and more than 1.5 population standard
deviations. The relative threshold rejects small proportional changes, the
absolute threshold rejects small monetary changes, and the deviation threshold
accounts for variability. These are heuristics rather than a calibrated
statistical significance test. Seasonal changes can still be flagged.

Both detectors expose supporting observations. Their outputs describe patterns
in recorded transactions; they do not establish fraud or confirm future bills.

## Database aggregation and configurable widgets

Database aggregates return totals and buckets without transferring the source
rows to Java. This reduces response work for dashboard reads, while preserving
native SQL that needs explicit result mapping. Recurring detection remains an
exception because its interval analysis uses individual payment dates.

A general analytics request supports the existing combinations of metric,
period, grouping, and comparison. Most widgets save those choices, while budgets
and insights use dedicated endpoints. Reconfiguration reuses existing code;
adding a new metric or specialised widget can still require backend changes.

## Derived budgets and preserved history

Budget rows store the category, month, and limit. Spending is queried from
transactions, avoiding a second stored total that every import, edit, or deletion
would need to update. The cost is an aggregate query for each budget when read.
A larger budget list may justify a grouped query after measurement.

Accounts with transaction history cannot be deleted through the API. Archiving
preserves the history but adds a second account state for the interface to
handle. Built-in categories are shared and immutable, while users can maintain
custom categories. This avoids copying the same categorisation targets for each
user, at the cost of preventing personal edits to the built-in set.

## Explicit loading and sequential IDs

Disabling Open Session in View keeps database work within service transactions.
Related account and category labels are fetched deliberately rather than loaded
once per displayed row. This requires care when adding response fields but
makes query costs easier to inspect.

Sequential database IDs are compact and readable. They reveal ordering and may
be guessed, so access checks depend on ownership rather than secrecy of an ID.
Changing to UUIDs would not remove that requirement.
