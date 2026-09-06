# Engineering decisions

Each entry states the decision, the context it was made in, what else was
considered, why this option won, and what it costs. The tradeoffs are real —
none of these choices is free.

---

## 1. Java + Spring Boot for the backend

**Decision.** Java 21 with Spring Boot 3.5.

**Context.** A CRUD-plus-analytics application handling money, needing
authentication, authorisation, database migrations, file processing and a REST
API.

**Alternatives.** Node/Express or NestJS; Python/FastAPI or Django; Go.

**Reason.** Everything this project needs is a first-class, well-trodden part of
the Spring ecosystem: Spring Security for auth and CSRF, Spring Data JPA,
Flyway, Bean Validation, Actuator. Java's static typing and `BigDecimal` matter
for a financial domain — `BigDecimal` is a language-level answer to a problem
that in JavaScript needs a third-party library and discipline. And the mature
testing story (JUnit, Spring Boot Test, Testcontainers) is why this project has
321 backend tests rather than a handful.

**Tradeoffs.** More ceremony than FastAPI for the same endpoint. A heavier
runtime than Go. Slower startup than either — about 2.5 seconds, which is
irrelevant for a long-running service and mildly annoying in development.

---

## 2. PostgreSQL

**Decision.** PostgreSQL 17 for development, testing and production.

**Alternatives.** MySQL; SQLite; H2 for tests with PostgreSQL in production.

**Reason.** `NUMERIC` for exact money arithmetic. `date_trunc` for the time
bucketing the entire analytics layer rests on. JSONB for the small amount of
genuinely schema-less widget configuration. Partial unique indexes, which is
what makes "built-in categories are globally unique, custom ones are unique per
user" expressible as a constraint rather than as application code.

**Tradeoffs.** The application is now tied to PostgreSQL — the native aggregate
queries would need rewriting for another database. That is an accepted, explicit
trade: pretending to be portable would have meant giving up `date_trunc` and
JSONB, and portability was never a requirement.

---

## 3. The same database engine in tests as in production

**Decision.** Integration tests run against real PostgreSQL via Testcontainers.

**Alternatives.** H2 in PostgreSQL-compatibility mode; an embedded database;
mocking the repository layer.

**Reason.** Half of what these tests exist to verify does not exist in H2:
`date_trunc`, JSONB columns, partial unique indexes, `EXTRACT(DAY FROM ...)`.
A suite that passes on H2 and fails in production is worse than no suite,
because it produces false confidence.

**Tradeoffs.** Integration tests need a Docker daemon and take about 25 seconds
rather than 3. Because Docker is not available everywhere, `AbstractIntegrationTest`
also accepts `SPENDSCOPE_TEST_DB_URL` to point at an existing PostgreSQL — the
tests are identical either way, only the source of the connection differs.

---

## 4. React + TypeScript + Vite

**Decision.** React 18, TypeScript in strict mode, Vite, React Router, TanStack
Query, Recharts.

**Alternatives.** Next.js; Vue or Svelte; server-rendered Thymeleaf.

**Reason.** The application is a logged-in dashboard: no SEO requirement, no
public content to server-render, so Next.js would add a Node server and a
rendering model for no benefit. TypeScript in strict mode catches a wrong field
name against the API types at compile time rather than as `undefined` in a
chart. TanStack Query removes the entire category of bugs where server data is
copied into component state and goes stale.

**Tradeoffs.** Recharts is 420 kB of the bundle — the single largest dependency.
It is split into its own chunk so it caches independently, but a hand-rolled SVG
chart layer would be far smaller. Not worth building for this project.

---

## 5. Plain CSS with custom properties

**Decision.** One `global.css` of design tokens plus plain class names.

**Alternatives.** Tailwind; CSS Modules; styled-components; a component library.

**Reason.** The visual language is small and consistent. One file of named
tokens (`--surface`, `--accent`, `--chart-1`) is easy to read a year later, and
light/dark mode is a single `prefers-color-scheme` block redefining the tokens.
Tailwind would put the design system in the markup; a component library would
bring opinions and weight the project does not need.

**Tradeoffs.** No scoping, so class names must be disciplined. No compile-time
purging of unused CSS — though at 12 kB before gzip that is not a real cost.

---

## 6. Session authentication, not JWT

**Decision.** Server-side sessions with an `HttpOnly` cookie.

**Alternatives.** JWT in `localStorage`; JWT in a cookie; OAuth via a third party.

**Reason.** The only client is a browser. A session can be revoked instantly on
logout; a stateless JWT cannot be, unless you build the revocation list that
made it stateless in the first place. A JWT in `localStorage` is readable by any
injected script, which turns an XSS into full account takeover; an `HttpOnly`
cookie is not readable at all. Spring Security's session handling — fixation
protection, expiry, invalidation — is mature and already there.

**Tradeoffs.** Sessions are server state, so a restart logs everyone out and
horizontal scaling needs sticky sessions or Spring Session. Cookies require CSRF
protection, which is real complexity — see decision 7. Both are accepted, and
both are listed in the security limitations.

---

## 7. CSRF protection on, with a JS-readable token cookie

**Decision.** Spring Security CSRF with `CookieCsrfTokenRepository.withHttpOnlyFalse()`
and an explicit `CsrfCookieFilter`.

**Alternatives.** Disable CSRF (common, and wrong); rely solely on `SameSite`;
switch to header-based auth to avoid the problem.

**Reason.** Cookie authentication means the browser attaches credentials
automatically, which is precisely what CSRF exploits. Disabling the protection
because it is inconvenient in development is how the vulnerability ships.
`SameSite=Lax` helps but is not sufficient alone: it is browser-dependent, and a
split-origin production deployment needs `SameSite=None`, which removes the
protection entirely.

**Tradeoffs.** Two non-obvious pieces of configuration are required —
`setCsrfRequestAttributeName(null)` to disable a BREACH mitigation designed for
server-rendered forms, and a filter to force the lazily-resolved token to
materialise. Both are commented at the point of use, because they are exactly
the kind of thing that looks like noise and gets deleted.

---

## 8. A modular monolith

**Decision.** One deployable Spring Boot application, organised by domain.

**Alternatives.** Microservices; a layered monolith (`controllers/`, `services/`,
`repositories/` at the top level).

**Reason.** Microservices would add network calls, partial-failure handling and
deployment coordination in exchange for nothing at this size. Organising by
domain rather than by layer means a feature lives in one package — everything
about transactions is in `transaction/` — instead of being smeared across three.
Domains talk through services, never through each other's repositories, so the
seams for a future extraction already exist.

**Tradeoffs.** No independent scaling or deployment of parts. Nothing *forces*
the module boundaries — discipline does. A tool like ArchUnit could enforce
"no repository is used outside its own package"; that has not been added.

---

## 9. Flyway for schema migrations

**Decision.** Explicit, versioned SQL migrations. `ddl-auto: validate` in every
environment.

**Alternatives.** Hibernate `ddl-auto: update`; Liquibase.

**Reason.** `ddl-auto: update` means a code change can silently and irreversibly
alter a production schema, and it cannot express data migrations, partial
indexes or check constraints at all. Hand-written SQL means the schema is
reviewable in a pull request. `validate` turns a mismatch into a startup failure
rather than a runtime corruption — and it earned its place during this build by
catching a `CHAR(3)` column that the entity mapped as `varchar(3)`.

Liquibase's XML abstraction buys database portability that decision 2 already
gave up.

**Tradeoffs.** Every schema change is two edits (migration plus entity). A
migration that has shipped can never be edited, only superseded.

---

## 10. BigDecimal and NUMERIC for money

**Decision.** `BigDecimal` in Java, `NUMERIC(19,4)` in PostgreSQL. Never
`float`, `double` or `real`.

**Alternatives.** Storing cents as a `long`; `double`.

**Reason.** Binary floating point cannot represent `0.10` exactly. `0.1 + 0.2`
is famously not `0.3`, and in a system that sums thousands of amounts the drift
is not theoretical. `TransactionApiIT` asserts that two transactions of `-0.10`
and `-0.20` sum to exactly `-0.30`.

Long-cents was the serious alternative and is genuinely faster, but it pushes
scaling into every call site and makes currencies with other minor units awkward.

Four decimal places rather than two because some currencies and some FX figures
need them, and because rounding once at display time beats rounding repeatedly
during aggregation.

**Tradeoffs.** `BigDecimal` is more verbose and slower than a primitive.
Irrelevant at this scale, and correctness wins over speed for money.

---

## 11. Signed amounts rather than a direction column

**Decision.** One signed `amount`. Negative is money out.

**Alternatives.** A positive amount plus a `DEBIT`/`CREDIT` column.

**Reason.** A period total is a plain `SUM`, and a refund automatically cancels
the purchase it reverses. A separate direction column makes every aggregate a
`CASE` expression and creates a state — direction `CREDIT` with a negative
amount — that means nothing.

**Tradeoffs.** "Spending" needs defining, and it is defined as `amount < 0`,
reported positive. Income is excluded from spending metrics rather than
cancelling against them, because a salary would otherwise make a month look like
it had negative spending.

---

## 12. Raw CSV files are never retained

**Decision.** Uploads are parsed in memory. Only the resulting transactions and
a small counter summary are stored.

**Alternatives.** Store the original for re-processing or support; store it
temporarily; store it encrypted.

**Reason.** A directory of everyone's bank statements is a high-value target,
and no feature depends on it. Not storing it removes an entire class of risk
(path traversal, disk exhaustion, backup exposure, retention obligations) rather
than mitigating it. Processing from memory also means no filesystem path is ever
built from user input.

**Tradeoffs.** A re-parse after a categorisation-rule improvement is impossible —
the user must re-upload. Debugging a user's import problem means asking for the
file rather than looking it up. Both are acceptable; the risk removed is larger
than the convenience lost.

---

## 13. Rule-based categorisation before machine learning

**Decision.** Ordered keyword rules in `application.yml`, matched against the
normalised description.

**Alternatives.** A trained classifier; an LLM call per transaction; nothing at
all.

**Reason.** A new user has no labelled training data, so a learned model has
nothing to learn from on day one — the exact moment first impressions are
formed. Rules are inspectable ("why was this Food?" has an answer), instantly
correct for the merchants people actually repeat, and extensible by editing
config rather than retraining. An LLM per row would add latency, cost and
non-determinism to something a substring match solves.

The user can always override, and those overrides are precisely the labelled
data that would make a learned model possible later.

**Tradeoffs.** Rules do not generalise: an unknown merchant stays uncategorised.
That is the honest behaviour — guessing a category we have no evidence for would
put wrong numbers in the user's analytics. Ordering is significant and easy to
get wrong (`GRABFOOD` must precede `GRAB`), so ordering is documented in the
config and asserted in tests.

**Note.** This decision produced a real bug during development. The normaliser
strips noise tokens including `COM`, so a rule keyword `APPLE COM BILL` could
never match — dead config, silently. `ShippedCategorizationRulesTest` now
validates the real shipped rules against realistic descriptions and asserts no
keyword contains a stripped token.

---

## 14. Deterministic recurring-expense detection

**Decision.** Median-interval analysis over amount-clustered payments, with an
explicit confidence grade.

**Alternatives.** Exact date-and-amount matching; a time-series or clustering
model.

**Reason.** Real subscriptions do not bill on an exact 30-day cycle — months
differ in length, dates slip over weekends, cards retry. Exact matching finds
almost nothing in real data. The median is used rather than the mean so a single
missed month does not drag a monthly subscription into "quarterly", and amounts
are clustered around the median so a one-off in-app purchase does not distort a
steady series.

Determinism matters because this makes a claim about the user's money. The same
input always produces the same output, and every field traces to an observation
the UI can show: "6 payments of about 12.98 monthly since January, 100% of them
25–35 days apart."

**Tradeoffs.** Thresholds (3 occurrences, 60% interval consistency, 20% amount
tolerance) are judgement calls, not learned. They will miss a genuinely
irregular subscription and could flag a coincidentally regular habit. The
confidence grade exists so the UI can present weak evidence as weak.

---

## 15. Explainable anomaly detection, not ML

**Decision.** A category is unusual when it clears three tests simultaneously:
at least 40% above its own baseline, more than 1.5 standard deviations above it,
and at least 30 in absolute money terms.

**Alternatives.** Isolation forests or another unsupervised model; a single
percentage threshold.

**Reason.** All three tests are needed, and each one exists because of a specific
false positive. Relative alone flags a 3% wobble on a large category. Statistical
alone flags naturally spiky categories like travel every month. Absolute alone
misses a real jump in a small category. Together they are still describable in
one sentence in the UI.

Comparison is **month-to-date against the same slice of previous months** — on
the 5th, five days against five days. Comparing five days against six whole
months would flag everything on the 5th of every month.

**Tradeoffs.** Simple statistics miss genuine seasonality: December is unusual
every year and will be flagged every year. Detection needs three months of
history before it says anything.

**Language matters.** Findings are described as "higher than usual", never as
suspicious or fraudulent. SpendScope has no idea whether an unusual month is a
fraud, a holiday or a new sofa, and the vocabulary reflects that. A test asserts
the explanation contains neither "fraud" nor "suspicious".

---

## 16. Analytics aggregate in the database

**Decision.** All summing and grouping happens in PostgreSQL. Time bucketing
uses native `date_trunc` queries.

**Alternatives.** Fetch rows and aggregate in Java; pre-computed summary tables.

**Reason.** Loading a year of transactions into the JVM to sum them works at a
thousand rows and falls over at a hundred thousand. Measured: a three-year
monthly aggregation over 10,000 rows takes 52 ms end to end, index-driven with
no sequential scan.

Summary tables were rejected because they must be invalidated on every
transaction insert, edit, delete, import and re-categorisation — and the first
path that forgets leaves the user staring at a number that is quietly wrong.

**Tradeoffs.** Native SQL for the time buckets, which ties the code to
PostgreSQL and returns `Object[]` rows needing explicit mapping. The mapping is
verbose but keeps column order visible at the mapping site rather than relying
on alias-matching magic.

---

## 17. A generalised analytics request instead of an endpoint per widget

**Decision.** One `POST /api/analytics/query` taking metric, period, groupBy and
comparison, plus a `GET /api/analytics/summary` convenience endpoint.

**Alternatives.** An endpoint per widget type; GraphQL; a query DSL.

**Reason.** A widget is a saved analytics request, so adding a widget type needs
no backend change at all — that is the whole reason the dashboard can be
user-configurable. Four fields cover every widget in the product. A query
language would need parsing, validation and its own injection story for no
user-visible gain; GraphQL would add a schema layer and N+1 risk to an API with
about twenty endpoints.

`/summary` exists alongside it because a dashboard opens with the same six
numbers every time, and six round trips is six chances for them to disagree.

**Tradeoffs.** `POST` for a read, because the request does not fit cleanly in a
query string. That costs HTTP caching, which this data would not benefit from
anyway.

---

## 18. Budget spending is derived, never stored

**Decision.** The `budgets` table holds only the limit. Amount spent,
percentage, remaining and exceeded status are computed from transactions on
every read.

**Alternatives.** A `spent` column updated on every transaction change.

**Reason.** A stored total must be updated by every path that touches a
transaction — insert, edit, delete, import, re-categorisation, category
deletion. The first path that forgets leaves the budget quietly wrong, and
nothing detects it. Derived state cannot drift from its source.

`AnalyticsIT` asserts exactly this: create a budget, then create a transaction,
and the budget's `spent` moves without any update call to the budget.

**Tradeoffs.** One aggregate query per budget per read. Measured at 29 ms for a
full budget page, so not a problem at this size.

---

## 19. Accounts are archived, not deleted

**Decision.** `DELETE /api/accounts/{id}` succeeds only for an account with no
transactions. Otherwise it returns 409 telling the user to archive.

**Alternatives.** Cascade the delete; soft-delete everything.

**Reason.** Deleting an account with history would cascade into every
transaction ever recorded against it, silently destroying the analytics the user
came for. Refusing with an explanation is better than a destructive action
behind a button labelled "Delete".

**Tradeoffs.** Two concepts (archive and delete) instead of one. Archived
accounts accumulate. Worth it — this is a data-loss trade, and the user's
history is the product.

---

## 20. Built-in categories are shared, not copied per user

**Decision.** Twelve categories with `user_id IS NULL`, readable by everyone,
editable by no one.

**Alternatives.** Copy the twelve into every new user's account.

**Reason.** The categorisation rules refer to categories by name, so a shared
set keeps that mapping stable. Adding a thirteenth built-in later is a migration,
not a backfill across every user.

**Tradeoffs.** A user cannot rename or delete a built-in — a real limitation,
since someone who never travels is stuck with a Travel category. They can add
their own. Ownership also becomes a two-part predicate (`user_id IS NULL OR
user_id = :userId`) rather than a simple equality, which is why it lives in one
named repository query instead of being re-derived at each call site.

---

## 21. Sequential integer ids

**Decision.** `BIGINT GENERATED BY DEFAULT AS IDENTITY`.

**Alternatives.** UUIDv4 or UUIDv7.

**Reason.** Smaller, faster to index, and readable in logs and URLs. Crucially,
guessable ids make the authorisation model *honest*: nothing is protected by
obscurity, so the ownership scoping in every query is the only thing standing
between users — and it is tested as such.

**Tradeoffs.** Ids leak volume (a competitor could estimate row counts) and are
enumerable. Enumeration is mitigated by returning 404 rather than 403 for
another user's row. If this ever mattered, UUIDv7 would be the swap.

---

## 22. `open-in-view` disabled

**Decision.** `spring.jpa.open-in-view: false`.

**Reason.** The Spring Boot default keeps a Hibernate session open through view
rendering, which makes lazy-loading N+1 problems invisible in development and
expensive in production. Disabling it makes a missing fetch fail loudly at the
service boundary instead.

**Tradeoffs.** Every association must be loaded deliberately. That is the point.
It is why `TransactionService` loads account and category names once per page
rather than twice per row.
