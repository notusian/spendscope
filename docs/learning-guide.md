# Learning guide

Written for someone who knows Java but is new to production Spring Boot. It
follows one request from the browser to the database and back, then covers each
subsystem in the order you would need to build it.

Read it with the code open. Every section names the files it describes.

---

## 1. How the application starts

`SpendScopeApplication.java` is thirty lines and does one thing:

```java
@SpringBootApplication
@EnableConfigurationProperties({ SecurityProperties.class, ... })
@EnableScheduling
public class SpendScopeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpendScopeApplication.class, args);
    }
}
```

`@SpringBootApplication` is three annotations in one:

- `@ComponentScan` — scan this package and everything below it for classes
  annotated `@Component`, `@Service`, `@Repository`, `@RestController`,
  `@Configuration`, and register one instance of each in the **application
  context**.
- `@EnableAutoConfiguration` — look at what is on the classpath and configure it.
  `spring-boot-starter-web` is present, so start an embedded Tomcat. A
  PostgreSQL driver and a `spring.datasource.url` are present, so build a
  connection pool. Flyway is present, so run migrations.
- `@Configuration` — this class may itself define beans.

The startup sequence, in order:

1. **Read configuration.** `application.yml`, then `application-{profile}.yml`,
   then environment variables. Later sources win, which is how
   `DATABASE_PASSWORD` in the environment overrides the local default without
   editing a file.
2. **Create the DataSource.** A HikariCP pool, not a single connection.
3. **Run Flyway migrations** against that pool.
4. **Build the JPA EntityManagerFactory.** With `ddl-auto: validate`, Hibernate
   compares every `@Entity` against the real schema and **refuses to start** on a
   mismatch. This is not a formality — during this build it caught a `CHAR(3)`
   column that the entity mapped as `varchar(3)`.
5. **Instantiate beans**, injecting dependencies through constructors.
6. **Build the security filter chain** from `SecurityConfig`.
7. **Start Tomcat** on port 8080.

> **Constructor injection, not `@Autowired` fields.** Every class here takes its
> dependencies as constructor parameters and stores them in `final` fields. The
> object cannot exist in a half-built state, the dependencies are visible in the
> signature, and the class can be instantiated in a plain unit test with `new`.

---

## 2. How a browser request reaches Spring Boot

```
Browser
  → nginx (production) or the Vite dev server (development)
    → Tomcat
      → Servlet filter chain
        → DispatcherServlet
          → Your @RestController method
```

In development, Vite serves the React app on `:5173` and **proxies** `/api` to
`:8080`. That proxy is not a convenience — it makes the browser see one origin,
so cookies and CSRF behave exactly as they do in production. Without it you
would be fighting cross-origin cookie rules on your laptop only.

The **filter chain** runs before any controller. Ours, in order:

1. `RequestLoggingFilter` — starts a timer, puts a correlation id in the logging
   context.
2. `CorsFilter` — for a cross-origin request, decides whether to allow it.
3. `CsrfFilter` — for a state-changing request, checks the token.
4. `CsrfCookieFilter` — ours; forces the CSRF token to materialise so the cookie
   is actually written.
5. `SecurityContextHolderFilter` — loads the session's `SecurityContext`.
6. `AuthorizationFilter` — applies the rules in `SecurityConfig`.

A filter can end the request. An unauthenticated call to `/api/transactions`
never reaches a controller; `AuthorizationFilter` rejects it and
`RestAuthenticationEntryPoint` writes a JSON 401.

Then `DispatcherServlet` matches the URL to a controller method, converts the
JSON body into a record, runs validation, and calls your method.

---

## 3. How authentication works

Look at `AuthService.login`.

```java
Authentication authentication = authenticationManager.authenticate(
        UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
```

`AuthenticationManager` delegates to `DaoAuthenticationProvider`, which:

1. Calls `AppUserDetailsService.loadUserByUsername(email)` — one `SELECT`.
2. Compares the submitted password against the stored hash using
   `PasswordEncoder.matches`.
3. Returns an authenticated `Authentication`, or throws `BadCredentialsException`.

You never compare passwords yourself, and you never see a plain password outside
this call.

**Why the stored hash starts with `{bcrypt}`.** `DelegatingPasswordEncoder`
stores the algorithm as a prefix:

```
{bcrypt}$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa
```

That prefix is how you move to a stronger algorithm in five years without
invalidating every existing password.

**Then the session is established** — and the order matters:

```java
sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(authentication);
SecurityContextHolder.setContext(context);
securityContextRepository.saveContext(context, request, response);
```

`onAuthentication` rotates the session id **before** the context is saved. If an
attacker planted a known session id in the victim's browser, that id is now dead
and they never learn the new one. This is session-fixation protection.

> A subtle trap worth knowing: calling `request.changeSessionId()` directly
> throws when no session exists yet — which is the normal case for a first login
> from a fresh browser. Spring's strategy correctly does nothing in that case.
> That exact bug appeared during this build.

---

## 4. How Spring Security determines the current user

On the next request, `SecurityContextHolderFilter` reads the `SecurityContext`
back out of the session and puts it in a `ThreadLocal`. The controller reads the
principal from it:

```java
@GetMapping
public List<AccountResponse> list(@AuthenticationPrincipal AppUserDetails principal) {
    return accountService.list(principal.getUserId(), false);
}
```

`AppUserDetails` is our `UserDetails` implementation, and the important thing
about it is that it carries the **database user id**:

```java
public class AppUserDetails implements UserDetails {
    private final Long userId;   // ← this is the point
    private final String email;
    ...
}
```

`getUserId()` returns an id that came from the session, which came from a
successful password check. A client cannot influence it. That is what makes the
next section work.

Note there are **no roles**. `getAuthorities()` returns an empty list, because
authorisation here is about *whose data this is*, not *what kind of user you are*.

---

## 5. How authorisation prevents cross-user access

This is the most important section in this guide.

Ids are sequential integers. If Alice's transaction is `#123`, Bob can guess
`#123`. The naive implementation leaks it:

```java
// WRONG. The row is loaded before anyone checks who owns it.
Transaction transaction = transactionRepository.findById(id).orElseThrow();
return toResponse(transaction);
```

Even adding a check afterwards is fragile — one method that forgets it is a
breach. The fix is to make the ownership part of the **query**:

```java
// RIGHT. Another user's row is never loaded into memory at all.
transactionRepository.findByIdAndUserId(transactionId, userId)
        .orElseThrow(() -> NotFoundException.of("Transaction", transactionId));
```

Spring Data derives the SQL from the method name:
`WHERE id = ? AND user_id = ?`.

Three conventions make this hold across a whole codebase:

1. **Every service method takes `userId` as its first parameter.** An unscoped
   call then looks wrong at the call site, rather than being wrong by omission.
2. **The user id always comes from the principal**, never from a path variable
   or request body.
3. **404, not 403.** A row that does not exist and a row you do not own give the
   same answer. Distinguishing them would let someone enumerate valid ids.

This applies to aggregates too. `sumSpending(userId, from, to)` is user-scoped:
a totals endpoint that leaks is as bad as a detail endpoint that leaks.

`CrossUserAccessIT` asserts the whole matrix, including the vectors that are
easy to forget — Bob attaching a transaction to Alice's account, Bob importing a
CSV into it, Bob using Alice's private category.

---

## 6. How controllers call services

Controllers are deliberately boring:

```java
@PostMapping
public ResponseEntity<AccountResponse> create(
        @AuthenticationPrincipal AppUserDetails principal,
        @Valid @RequestBody AccountRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(accountService.create(principal.getUserId(), request));
}
```

Extract the principal, delegate, map to a status code. No business logic, no
queries, no `if`.

`@Valid` triggers Bean Validation on the record before your method runs. A
failure throws `MethodArgumentNotValidException`, which
`GlobalExceptionHandler` turns into a 400 with per-field messages. You never
write validation `if` statements for things annotations can express.

**Why the request is a record, not the entity.** `AccountRequest` has exactly
three components: `name`, `type`, `currency`. There is no `id`, no `userId`, no
`createdAt`. A client cannot overpost fields it should not control, because
those fields **do not exist on the type**. That is mass-assignment protection by
construction rather than by a denylist.

---

## 7. How services interact with repositories

The service owns the business rules and the transaction boundary:

```java
@Transactional
public AccountResponse create(Long userId, AccountRequest request) {
    String name = request.name().trim();
    if (accountRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
        throw new ConflictException("An account named '" + name + "' already exists");
    }
    Account account = new Account(userId, name, request.type(), normaliseCurrency(request.currency()));
    Account saved = accountRepository.save(account);
    auditService.record(userId, AuditEventType.ACCOUNT_CREATED, Map.of("accountId", saved.getId()));
    return AccountResponse.from(saved);
}
```

**What `@Transactional` actually does.** Spring wraps the bean in a proxy. On
entry it opens a database transaction; on normal return it commits; on a runtime
exception it rolls back. So a CSV import either writes all its accepted rows and
its job record, or neither.

Two consequences that surprise people:

- **Self-invocation does not work.** Calling `this.someTransactionalMethod()`
  from inside the same class bypasses the proxy, so no transaction starts.
- **`@Transactional(readOnly = true)`** on queries lets Hibernate skip dirty
  checking and hints the driver. Use it for reads.

**Dirty checking.** Inside a transaction, entities loaded from the database are
*managed*. Changing a field is enough:

```java
@Transactional
public AccountResponse archive(Long userId, Long accountId, boolean archived) {
    Account account = require(userId, accountId);
    account.setArchived(archived);      // no save() call needed
    return AccountResponse.from(account);
}
```

At commit, Hibernate compares the entity to its loaded state and issues an
`UPDATE` for what changed. This is elegant and occasionally baffling — an
accidental setter inside a transaction writes to the database.

---

## 8. How JPA interacts with PostgreSQL

An `@Entity` is a class mapped to a table:

```java
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
}
```

`GenerationType.IDENTITY` means the database assigns the id.

**Why `userId` is a plain `Long` and not `@ManyToOne User`.** Nothing in this
domain navigates from a transaction to a user object. A raw id keeps every
ownership-scoped query a single-table lookup with no join, and avoids
lazy-loading surprises entirely.

Repositories are interfaces; Spring Data writes the implementation:

```java
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdAndUserId(Long id, Long userId);   // derived from the name
    long countByUserIdAndAccountId(Long userId, Long accountId);
}
```

For anything the method-name DSL cannot express, write the query:

```java
@Query("""
        SELECT COALESCE(SUM(-t.amount), 0) FROM Transaction t
        WHERE t.userId = :userId AND t.date BETWEEN :from AND :to AND t.amount < 0
        """)
BigDecimal sumSpending(@Param("userId") Long userId, ...);
```

That is **JPQL** — it queries entities, not tables. For PostgreSQL-specific SQL
like `date_trunc`, drop to `nativeQuery = true`.

**The N+1 problem**, and how it is avoided here. Mapping 25 transactions to
responses needs each one's account name and category name. The naive version
issues 50 extra queries. `TransactionService.loadLabels` loads both maps **once
per request**:

```java
private LabelLookup loadLabels(Long userId) {
    Map<Long, String> accountNames = ...;   // one query
    Map<Long, String> categoryNames = ...;  // one query
    return new LabelLookup(accountNames, categoryNames);
}
```

Two queries instead of fifty-one. The same idea drives
`CategorizationSession`, which loads the category table once for an entire
10,000-row import.

Related: `spring.jpa.open-in-view: false`. Spring Boot's default keeps a
Hibernate session open through response rendering, which makes N+1 problems
invisible in development. Turning it off makes them fail loudly instead.

---

## 9. How Flyway creates the schema

`src/main/resources/db/migration/` holds numbered SQL files:

```
V1__create_users.sql
V2__create_accounts.sql
...
V8__create_audit_events.sql
```

On startup Flyway checks its own `flyway_schema_history` table, applies anything
not yet applied, in order, and records a checksum.

**Rules that come with it:**

- An applied migration is **immutable**. Editing it changes its checksum and
  Flyway refuses to start. Fix a mistake with a new `V9__`.
- `ddl-auto` is `validate`, never `update`. Hibernate checks the schema; it
  never changes it. A production `update` can silently and irreversibly alter a
  live database.
- Data migrations belong here too. `V3` seeds the twelve built-in categories.

Read `V4__create_transactions.sql` for the interesting parts: `NUMERIC(19,4)`
for money, a `CHECK` constraint forbidding a zero amount, and four indexes that
all lead with `user_id` because no query ever crosses users.

---

## 10. How CSV imports work

`CsvImportService.importCsv` is the pipeline:

```
ownership check → read bytes → validate content → parse → validate rows
    → normalise → detect merchant → categorise → fingerprint
    → detect duplicates → batch insert → summary
```

**The ownership check is first**, before the file is even read. No work happens
on a file destined for someone else's account.

**Content validation, not filename validation** (`CsvTransactionParser.decodeAsText`):
strict UTF-8 decoding plus a NUL-byte scan. A file called `statement.csv`
containing a JPEG is rejected; a file called `statement.txt` containing valid CSV
is accepted. The extension is a hint, never evidence.

**A real CSV parser.** `String.split(",")` breaks on the first quoted field
containing a comma — `"ACME, INC. PAYMENT"` becomes two columns and the row is
silently wrong. Apache Commons CSV handles quoting, escaping and embedded
newlines.

**One bad row is one bad row:**

```java
try {
    rows.add(toRow(record));
} catch (CsvRowException ex) {
    errors.add(new RowError(record.getRecordNumber() + 1, ex.getMessage()));
}
```

Line 3 being unparseable must not cost the user the other 9,999 valid rows.

**Bounded everywhere**: 5 MB, 10,000 rows, 50 reported errors. All configurable,
all enforced.

---

## 11. How duplicate detection works

Bank statements have no stable transaction id, so identity is derived from the
fields that do not change between exports (`TransactionFingerprint`):

```java
String payload = String.join("|",
        userId, accountId, date, normalisedDescription,
        amount.setScale(4, HALF_UP).toPlainString(), currency);
return sha256Hex(payload);
```

Each part earns its place:

- `userId` and `accountId` — so two users' identical coffee purchases never
  collide, and the same purchase on two cards stays two rows.
- The **normalised** description — so cosmetic differences between exports do not
  produce different fingerprints.
- The amount **scaled first** — so `7.2` and `7.2000` agree.
- The `|` separator — without it, `("AB","C")` and `("A","BC")` would hash the
  same.

SHA-256 is used for a well-distributed fixed-width value, not for secrecy.

Detection checks **two** sets — what is already stored, and what has already been
accepted from *this file*:

```java
boolean alreadyExists = existingFingerprints.contains(row.fingerprint());
boolean repeatedInFile = !seenInThisFile.add(row.fingerprint());
```

Without the second, a file containing the same row twice would insert it twice.

**The column is indexed but not unique.** Buying the same coffee twice on one day
is legitimate. Duplicates are *reported*, never dropped or overwritten — the
existing row is the user's data and an import has no authority over it.

---

## 12. How categorisation works

Three small pieces.

**`DescriptionNormalizer`** turns bank noise into a comparable string:

```
"STARBUCKS #4821 SINGAPORE SG"  →  "STARBUCKS SINGAPORE"
"POS  starbucks   coffee  4821" →  "STARBUCKS COFFEE"
```

It strips accents, upper-cases, removes punctuation, embedded dates, long digit
runs and card-processing noise (`POS`, `VISA`, `REF`, `PTE`, `LTD`).

**`MerchantRule`** — configuration, not code:

```yaml
- merchant: GrabFood
  category: Food
  keywords: [GRABFOOD, GRAB FOOD]
- merchant: Grab
  category: Transport
  keywords: [GRAB]
```

Order is significant: first match wins, so the specific rule must precede the
general one. Without that ordering every food delivery would be filed as
transport.

**`MerchantResolver`** matches, and falls back to a guessed merchant name with
**no category** when nothing matches. Guessing a category we have no evidence for
would put wrong numbers in the user's analytics; uncategorised is the honest
state, and the user can correct it.

> **A real bug worth learning from.** The normaliser strips `COM`. A rule
> keyword `APPLE COM BILL` therefore normalises to something that can never
> appear, so the rule silently never fired. Nothing failed; the config was just
> dead. `ShippedCategorizationRulesTest` now loads the **real** `application.yml`
> and asserts (a) no keyword contains a stripped token, and (b) realistic
> descriptions resolve to the expected merchant and category. This is the
> difference between testing a fixture and testing what ships.

---

## 13. How analytics are calculated

Every request is `metric + period + groupBy + comparison`.

**`PeriodResolver`** turns a `PeriodType` into concrete dates. It takes a
`Clock` as a constructor dependency rather than calling `LocalDate.now()`, so
tests can pin "today" to a fixed date and assert exact boundaries.

The interesting part is what "the previous period" means. The obvious answer is
wrong: on 5 September, "this month" covers five days, and comparing it against
the whole of August would report a ~90% drop on the 5th of every month, forever.

- **Calendar periods** compare against the *same slice* of the preceding
  calendar period: 1–5 September against 1–5 August.
- **Rolling windows** compare against the equally long window before them.

**Aggregation happens in PostgreSQL**, never in Java. Loading a year of rows to
sum them works at a thousand rows and fails at a hundred thousand.

**Percentage change never returns `Infinity` or `NaN`:**

```java
if (previous.signum() == 0) {
    return null;   // growth from zero is undefined
}
```

`null` means "no comparison available" and the UI renders an em dash. Returning
`0%` would falsely claim spending was flat; `Infinity` is not even valid JSON.

**Time buckets are dense.** A week with no spending comes back as a zero, not a
gap, so a line chart cannot silently connect across missing periods.

> Another real bug: the result limit (default 10, for "top N merchants") was
> also being applied to time series, silently truncating a 30-day chart to its
> first 10 days. `AnalyticsIT.returnsDenseTimeBuckets` caught it, and the limit
> now skips time groupings.

---

## 14. How dashboard configuration works

A widget is a **saved analytics request**:

| Column | Meaning |
|---|---|
| `metric_type` | which widget this is |
| `group_by`, `period`, `comparison_period` | the analytics request |
| `chart_type`, `size`, `position` | presentation |
| `configuration` | JSONB, for widget-specific extras |

The frontend reads a widget, builds the analytics request from it, and calls the
generic endpoint. **Adding a widget type needs no backend change** — that is
what makes the dashboard configurable at all.

**Why the split between columns and JSONB.** The knobs every widget shares are
real columns so the database can validate them with `CHECK` constraints. Only
the handful of genuinely varying extras (a result limit, a title) live in JSONB,
which would otherwise need a mostly-null column per widget type.

JSONB is not a free-for-all: `DashboardService.sanitiseConfiguration`
allowlists the keys, rejects nested structures and caps sizes. A client is
writing to your database; treat the payload accordingly.

---

## 15. How recurring expenses are detected

`RecurringExpenseDetector` is pure logic — no Spring, no database, so it can be
tested with a handful of in-memory payments.

Per merchant:

1. Take the **median** amount and keep payments close to it. This drops a one-off
   4.99 in-app purchase from an otherwise steady 12.98 subscription.
2. Require at least **3** payments. Two is a coincidence.
3. Compute the gaps between consecutive payments and take the **median gap**.
   Median, not mean, so one missed month does not drag a monthly subscription
   into "quarterly".
4. Map that gap onto a **cadence band** — monthly is 25–35 days, not exactly 30.
   Real subscriptions slip over weekends and short months.
5. Measure what fraction of gaps fall inside the band. That is the confidence
   input, along with the count and the amount stability.

Everything is deterministic, and every output field traces to an observation the
UI can show: *"6 payments of about 12.98 monthly since January, 100% of them
25–35 days apart."* When you make a claim about someone's money, they should be
able to check your reasoning.

---

## 16. How anomaly detection works

A category is flagged only when it clears **all three** tests:

| Test | Threshold | Prevents |
|---|---|---|
| Relative | ≥ 40% above baseline | Flagging a 3% wobble |
| Statistical | > 1.5 standard deviations | Flagging naturally spiky categories like travel |
| Absolute | ≥ 30 in money | "Coffee went from 5 to 20 (+300%!)" |

Each threshold exists because of a specific false positive, and all three are
needed — `SpendingAnomalyDetectorTest` has a case for each.

**The comparison is month-to-date against the same slice of previous months.**
On the 6th, six days against six days. That is why the SQL carries
`EXTRACT(DAY FROM t.date) <= :maxDayOfMonth` — comparing six days against six
whole months would flag everything, every month.

A month with no spending in a category counts as a **real zero**, not a missing
value. Dropping it would inflate the average and hide the anomaly.

**The language is deliberate.** Findings say "higher than usual", never
"suspicious" or "fraudulent". SpendScope has no idea whether an unusual month is
a fraud, a holiday or a new sofa. A test asserts the explanation contains
neither word.

---

## 17. How React communicates with Spring Boot

One file — `api/client.ts` — is the only place `fetch` is called. It guarantees
four things everywhere:

```ts
const response = await fetch(`/api${path}`, {
  method, headers, body,
  credentials: 'include',   // ← without this the session cookie is not sent
})
```

1. **`credentials: 'include'`** — otherwise the browser omits the cookie and
   every request is a 401.
2. **The CSRF header on unsafe methods** — read from the `XSRF-TOKEN` cookie,
   echoed as `X-XSRF-TOKEN`.
3. **A typed `ApiError`** for every non-2xx, carrying the backend's `code`,
   `message` and `fieldErrors`.
4. **A session-expiry notification**, so the app can clear its cache and send the
   user to the login page.

The session cookie is `HttpOnly` and therefore invisible to this code. That is
the point: a script that could read the session id would turn any XSS into full
account takeover.

The CSRF token cookie *is* readable, because the SPA has to echo it back. That
is safe because the same-origin policy stops a hostile page from reading it.

---

## 18. How TanStack Query manages server state

The rule: **server data lives in TanStack Query and nowhere else.** No component
copies API data into `useState`. That duplication is what makes UIs go stale —
two sources of truth, one of which nobody remembers to update.

A read:

```ts
export function useTransactions(filters: TransactionFilters) {
  return useQuery({
    queryKey: queryKeys.transactions(filters),
    queryFn: () => transactionsApi.list(filters),
    placeholderData: (previous) => previous,   // no empty flash when paging
  })
}
```

The **query key** is the cache identity. Because `filters` is part of it,
changing a filter is automatically a different cache entry.

A write invalidates what it affects:

```ts
export function useCreateTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input) => transactionsApi.create(input),
    onSuccess: () => invalidateTransactionDependents(queryClient),
  })
}
```

Editing one transaction moves analytics totals, budget progress, recurring
detection and anomalies. Those dependencies are listed **once**, in
`TRANSACTION_DEPENDENT_KEYS`, so no future mutation can forget one of them.

One configuration detail worth copying: don't retry 4xx.

```ts
retry: (failureCount, error) => {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < 2
}
```

Retrying an expired session three times just delays the redirect.

Auth follows the same principle. There is no `isLoggedIn` boolean; the source of
truth is `GET /api/auth/me`. A client-side flag would let the UI believe someone
is logged in after their session expired.

---

## 19. How Docker packages the application

`backend/Dockerfile` is a **multi-stage build**:

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
COPY pom.xml .
RUN mvn -B dependency:go-offline      # cached layer
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
COPY --from=build /build/target/*.jar app.jar
```

Two things to take from it:

- **Dependencies are resolved in their own layer**, before the source is copied.
  Changing a Java file does not re-download the Maven repository.
- **The runtime stage contains only a JRE and the jar** — no Maven, no source, no
  build cache. Smaller image, smaller attack surface.

It also runs as an **unprivileged user**. A container process running as root
that gets compromised has a much shorter path to the host.

`frontend/Dockerfile` builds the bundle with Node and serves it with nginx. The
production image contains no Node runtime, because a static bundle does not need
one.

`docker-compose.yml` wires the three together, with the backend waiting on the
database's `healthcheck` so it never starts against a database that is still
initialising.

---

## 20. How CI tests the project

`.github/workflows/ci.yml` runs five jobs.

`backend` and `frontend` run in parallel; `e2e` and `docker` wait for both.

```
backend:  compile → unit tests → integration tests → package
frontend: install → typecheck → lint → unit tests → build
e2e:      start PostgreSQL → start backend → run Playwright
docker:   build both images → validate compose
codeql:   static analysis for Java and TypeScript
```

**Unit and integration tests are separated by naming**, not by tags: Surefire
runs `*Test.java`, Failsafe runs `*IT.java`. So `mvn test` is fast and needs
nothing, while `mvn verify` needs a Docker daemon for Testcontainers.

Testcontainers starts a real PostgreSQL for the integration tests. That is why
the tests can rely on `date_trunc`, JSONB and partial unique indexes — none of
which exist in H2.

---

## 21. How production deployment works

```
Browser ──HTTPS──> static host (React bundle)
        ──HTTPS──> container host (Spring Boot) ──TLS──> managed PostgreSQL
```

Activated by `SPRING_PROFILES_ACTIVE=prod`, which changes:

| Setting | Production value | Why |
|---|---|---|
| `server.servlet.session.cookie.secure` | `true` | HTTPS only |
| `...cookie.same-site` | `none` | Required for a split-origin SPA |
| `server.forward-headers-strategy` | `framework` | Trust the load balancer's `X-Forwarded-*` |
| `springdoc.api-docs.enabled` | `false` | No API docs in production |
| `logging.level.root` | `WARN` | Less noise |

Everything environment-specific is an environment variable: `DATABASE_URL`,
`DATABASE_USERNAME`, `DATABASE_PASSWORD`, `FRONTEND_ORIGIN`,
`SESSION_COOKIE_SECURE`. No secret is committed; `.env` is gitignored.

**On startup in production:** Flyway applies any pending migrations, then
Hibernate validates the schema. A mismatch is a startup failure — the
application refuses to run against a schema it does not understand, rather than
corrupting data at runtime.

`/actuator/health` is the readiness probe. `show-details: never`, because a
health endpoint should say whether the service is up, not what it is built from.

---

## Suggested order for rebuilding this

1. `SpendScopeApplication`, `application.yml`, `V1__create_users.sql`. Get it to
   start and connect.
2. `User`, `UserRepository`, `UserService`. Understand entity → repository →
   service.
3. `SecurityConfig`, `AppUserDetails`, `AuthService`. This is the hardest part;
   take your time.
4. `Account` end to end — entity, repository, service, controller, DTOs. Now you
   have the pattern.
5. `CrossUserAccessIT`. Write the security tests **before** adding more domains,
   so the ownership habit is established early.
6. `Transaction`, plus `TransactionSpecifications` for dynamic filtering.
7. `DescriptionNormalizer` and `TransactionFingerprint` — pure logic, easy to
   test, and the foundation of everything that follows.
8. The CSV pipeline.
9. `PeriodResolver`, then `AnalyticsService`.
10. The frontend: `client.ts` first, then auth, then one page.
11. Budgets, recurring, anomalies — each is small once the analytics layer exists.
12. Docker and CI last.
