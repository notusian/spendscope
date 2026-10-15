# API reference

The base path is `/api`. Requests and responses use `application/json` unless
stated otherwise. Examples assume the backend runs at `http://localhost:8080`.

An interactive version is available at `/swagger-ui.html` when the application
runs with `OPENAPI_ENABLED=true` (the default outside production).

## Authentication

Authentication uses a server-side session identified by a browser cookie.

1. `GET /api/auth/csrf` — obtain the `XSRF-TOKEN` cookie.
2. Call `POST /api/auth/login` with the CSRF header to receive the `JSESSIONID` cookie.
3. Send both cookies on subsequent requests. For every state-changing request
   (`POST`, `PUT`, `PATCH`, `DELETE`), also echo the CSRF token in the
   `X-XSRF-TOKEN` header.

```bash
# The pattern used throughout this document
curl -c jar -b jar http://localhost:8080/api/auth/csrf
TOKEN=$(grep XSRF-TOKEN jar | awk '{print $7}')
curl -c jar -b jar -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' ...
```

Protected endpoints return `401` without a valid session. State-changing
requests with a missing or invalid CSRF token return `403`, including anonymous
registration, login, and demo creation. CSRF validation can run before
authentication, so a request missing both may receive `403` first.

## Errors

Application error responses use the following structure; a reverse proxy may
return a different format for failures it handles itself.

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

`fieldErrors` is present only for validation failures.

| `code` | Status | Meaning |
|---|---|---|
| `VALIDATION_ERROR` | 400 | One or more fields failed validation |
| `BAD_REQUEST` | 400 | Semantically invalid request |
| `UNAUTHENTICATED` | 401 | No valid session |
| `ACCESS_DENIED` | 403 | Rejected, including CSRF failure |
| `NOT_FOUND` | 404 | Does not exist, **or is not yours** |
| `CONFLICT` | 409 | Collides with existing state |
| `PAYLOAD_TOO_LARGE` | 413 | Upload exceeds the size limit |
| `IMPORT_FAILED` | 400 | The file could not be processed |
| `RATE_LIMITED` | 429 | Too many attempts |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

> A resource owned by another user returns `404`, never `403`. Distinguishing
> the two would let a caller enumerate valid ids.

## Pagination

Paged endpoints return:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 25,
  "totalElements": 396,
  "totalPages": 16,
  "hasNext": true
}
```

Transaction pages accept at most 200 rows. Import history and security activity
accept at most 100 rows. Values outside an endpoint's limits return a validation
error.

---

# Endpoints

## Auth

### `POST /api/auth/register`

This endpoint permits anonymous requests and creates a user with a starter
dashboard. It does not establish a login session.

```json
{ "email": "alice@example.com", "password": "correct-horse-battery" }
```

Passwords must be 10–72 characters and at most 72 UTF-8 bytes. A successful
registration returns `201` with the user:

```json
{ "id": 1, "email": "alice@example.com", "demo": false, "createdAt": "2026-09-06T12:00:00Z" }
```

Invalid email or password input returns `400`; an email already registered
returns `409`.

### `POST /api/auth/login`

This endpoint permits anonymous requests and establishes a session.

```json
{ "email": "alice@example.com", "password": "correct-horse-battery" }
```

A successful login returns `200` with the user. An incorrect password or unknown
email returns the same public `401` error, while repeated failures return `429`.

### `POST /api/auth/logout`

This endpoint invalidates the current session and returns `204`.

### `GET /api/auth/me`

This endpoint returns the authenticated user, or `401` when there is no valid
session.

### `GET /api/auth/csrf`

This endpoint permits anonymous requests and returns `204` with the
`XSRF-TOKEN` cookie set.

### `POST /api/auth/password`

```json
{ "currentPassword": "...", "newPassword": "..." }
```

A successful change returns `204`. An incorrect current password returns `401`.
The new password must satisfy the registration length limits. Other existing
login sessions are not revoked by this endpoint.

---

## Demo

### `POST /api/demo/session`

This endpoint permits anonymous requests and creates a disposable account with
approximately nine months of synthetic data. It logs the caller in and returns
`201` with `"demo": true`.

The endpoint permits five requests per IP per hour, then returns `429`.

---

## Accounts

### `GET /api/accounts`

| Parameter | Type | Default |
|---|---|---|
| `includeArchived` | boolean | `false` |

```json
[ { "id": 1, "name": "DBS Debit", "type": "CHECKING", "currency": "SGD",
    "archived": false, "createdAt": "2026-09-06T12:00:00Z" } ]
```

### `GET /api/accounts/{id}`
### `POST /api/accounts`
### `PUT /api/accounts/{id}`

```json
{ "name": "DBS Debit", "type": "CHECKING", "currency": "SGD" }
```

`type` ∈ `CHECKING`, `SAVINGS`, `CREDIT_CARD`, `CASH`, `INVESTMENT`, `OTHER`.
`currency` must be a real ISO-4217 code. `201` on create, `200` on update.
`409` if the name is already used by another of your accounts.

### `POST /api/accounts/{id}/archive` · `POST /api/accounts/{id}/unarchive`

`200` with the updated account.

### `DELETE /api/accounts/{id}`

Deletion returns `204` for an account without transactions. An account with
history returns `409`:

```json
{ "code": "CONFLICT",
  "message": "This account has 396 transactions. Archive it instead of deleting it." }
```

---

## Categories

### `GET /api/categories`

This endpoint returns built-in categories and the current user's custom categories.

```json
[ { "id": 1, "name": "Food", "type": "EXPENSE", "builtIn": true },
  { "id": 13, "name": "Pets", "type": "EXPENSE", "builtIn": false } ]
```

### `POST /api/categories` · `PUT /api/categories/{id}` · `DELETE /api/categories/{id}`

```json
{ "name": "Pets", "type": "EXPENSE" }
```

Only custom categories can be modified or deleted; built-ins return `404`.
Deleting a category leaves its transactions in place, uncategorised.

---

## Transactions

### `GET /api/transactions`

| Parameter | Type | Notes |
|---|---|---|
| `from`, `to` | date | `YYYY-MM-DD`, inclusive |
| `categoryId`, `accountId` | number | |
| `search` | string | Matches description and merchant, max 100 chars |
| `page` | number | Default `0` |
| `size` | number | Default `25`, max `200` |
| `sort` | string | `date`, `amount`, `description`, `createdAt` |
| `direction` | string | `asc` or `desc` (default) |

```
GET /api/transactions?page=0&size=25&from=2026-01-01&to=2026-09-01&categoryId=5&accountId=2
```

```json
{ "content": [ {
    "id": 42, "accountId": 1, "accountName": "DBS Debit",
    "categoryId": 1, "categoryName": "Food",
    "date": "2026-08-01", "description": "STARBUCKS COFFEE #4821",
    "merchant": "Starbucks", "amount": -7.2000, "currency": "SGD",
    "createdAt": "2026-09-06T12:00:00Z" } ],
  "page": 0, "size": 25, "totalElements": 396, "totalPages": 16, "hasNext": true }
```

An inverted date range or unsupported sort field returns `400`.

### `GET /api/transactions/{id}`
### `POST /api/transactions` · `PUT /api/transactions/{id}`

```json
{ "accountId": 1, "categoryId": null, "date": "2026-08-01",
  "description": "STARBUCKS COFFEE #4821", "amount": -7.20,
  "currency": "SGD", "merchant": null }
```

- `amount` is signed and must be non-zero: negative values are outgoings, and
  positive values are inflows.
- `categoryId` `null` means "detect automatically" on create.
- `currency` and `merchant` are optional; they default to the account currency
  and the detected merchant.

Creation returns `201`, while an update returns `200`; both include the transaction.

### `PATCH /api/transactions/{id}/category`

```json
{ "categoryId": 3 }
```

A null category ID clears the category. The endpoint returns `200` with the
updated transaction.

### `DELETE /api/transactions/{id}`

Successful deletion returns `204`.

---

## Imports

### `POST /api/imports/csv`

This endpoint accepts `multipart/form-data`.

| Part / parameter | Notes |
|---|---|
| `accountId` (query) | Must be one of your accounts |
| `file` (part) | Max 5 MB, max 10,000 rows |

Expected format:

```csv
date,description,amount
2026-08-01,STARBUCKS,-7.20
2026-08-02,GRAB,-14.50
2026-08-03,SPOTIFY,-12.98
```

Dates may be `YYYY-MM-DD`, `DD/MM/YYYY`, `DD-MM-YYYY` or `YYYY/MM/DD`.
`MM/DD/YYYY` is deliberately **not** supported — it is indistinguishable from
`DD/MM/YYYY` for the first twelve days of a month. Amounts accept `-7.20`,
`(7.20)`, `$1,234.56` and `+50`.

```bash
curl -c jar -b jar -H "X-XSRF-TOKEN: $TOKEN" \
  -F "file=@sample-data/sample-transactions.csv" \
  "http://localhost:8080/api/imports/csv?accountId=1"
```

`200`:

```json
{ "importJobId": 1, "rowsProcessed": 150, "rowsImported": 143,
  "rowsRejected": 4, "duplicatesDetected": 3,
  "errors": [ { "line": 12, "message": "Amount 'abc' is not a number" } ],
  "errorsTruncated": false }
```

`rowsProcessed = rowsImported + rowsRejected + duplicatesDetected`. Duplicates
are counted separately because they are not errors — the row was understood, it
just matches an existing fingerprint. Existing transactions are not overwritten
or deleted. Identical legitimate purchases can share a fingerprint, so a skipped
row should be reviewed when the statement contains repeated purchases.

Invalid file structure or missing required columns return `400`. An oversized
upload returns `413`, and an inaccessible account returns `404`.

### `GET /api/imports`

This endpoint returns a paginated history of completed imports. It accepts
`page` (default 0) and `size` (default 20, maximum 100).

---

## Analytics

### `GET /api/analytics/summary`

| Parameter | Notes |
|---|---|
| `period` | `THIS_MONTH` (default), `LAST_MONTH`, `LAST_30_DAYS`, `LAST_90_DAYS`, `THIS_YEAR`, `CUSTOM` |
| `start`, `end` | Required when `period=CUSTOM` |

```json
{ "period": { "start": "2026-09-01", "end": "2026-09-06", "days": 6 },
  "comparisonPeriod": { "start": "2026-08-01", "end": "2026-08-06", "days": 6 },
  "totalSpending": 835.2400, "previousTotalSpending": 169.8100,
  "spendingChangePercentage": 391.9, "averageDailySpending": 139.2067,
  "totalIncome": 0, "netFlow": -835.2400, "transactionCount": 7,
  "topCategory": { "key": "4", "label": "Shopping", "value": 689.0000 },
  "topMerchant": { "key": "Amazon", "label": "Amazon", "value": 689.0000 },
  "currency": "SGD" }
```

`spendingChangePercentage` is null when the previous period had no spending,
because percentage growth from zero is undefined. Spending sums the magnitudes
of negative transactions; positive refunds are included in income instead.
Totals do not perform currency conversion across accounts.

### `POST /api/analytics/query`

```json
{ "metric": "TOTAL_SPENDING",
  "period": { "type": "CUSTOM", "start": "2026-08-01", "end": "2026-08-31" },
  "groupBy": "CATEGORY",
  "comparison": "PREVIOUS_PERIOD",
  "limit": 10 }
```

| Field | Values |
|---|---|
| `metric` | `TOTAL_SPENDING`, `AVERAGE_DAILY_SPENDING`, `TRANSACTION_COUNT`, `CATEGORY_SPENDING`, `ACCOUNT_SPENDING`, `TOP_CATEGORY`, `TOP_MERCHANT` |
| `groupBy` | `CATEGORY`, `MERCHANT`, `ACCOUNT`, `DAY`, `WEEK`, `MONTH` |
| `comparison` | `NONE` (default), `PREVIOUS_PERIOD` |
| `limit` | 1–100, default 10. Ignored for time groupings, which always return every bucket |

```json
{ "metric": "TOTAL_SPENDING", "groupBy": "CATEGORY",
  "period": { "start": "2026-08-01", "end": "2026-08-31", "days": 31 },
  "comparisonPeriod": { "start": "2026-07-01", "end": "2026-07-31", "days": 31 },
  "total": 1274.7900, "previousTotal": 2952.1700, "percentageChange": -56.8,
  "currency": "SGD",
  "points": [ { "key": "2", "label": "Groceries", "value": 430.5900,
                "transactionCount": 6, "previousValue": 412.1000,
                "percentageChange": 4.5, "share": 33.8 } ] }
```

The `TRANSACTION_COUNT` and `AVERAGE_DAILY_SPENDING` metrics return scalar
values and reject `groupBy`. Metrics with an implied grouping, such as
`CATEGORY_SPENDING`, use that grouping even if another is requested.

Time groupings return dense buckets, including zero-valued points for periods
without spending. Previous-period time buckets are paired by their position
within each period; an unmatched bucket has a null `previousValue`. Category,
merchant, and account comparisons instead match the group identity. Calendar periods compare with the corresponding previous
calendar period; custom and rolling ranges compare with the preceding range of
equal length.

---

## Dashboard

### `GET /api/dashboard/widgets`

This endpoint returns the current user's widgets in display order.

```json
[ { "id": 1, "metricType": "TOTAL_SPENDING", "metric": "TOTAL_SPENDING",
    "groupBy": null, "period": "THIS_MONTH", "comparisonPeriod": "PREVIOUS_PERIOD",
    "chartType": "NUMBER", "position": 0, "size": "SMALL",
    "configuration": { "title": "Total Spending" } } ]
```

`metric` is `null` for widget types backed by their own endpoint
(`BUDGET_PROGRESS`, `RECURRING_EXPENSES`, `SPENDING_ANOMALIES`,
`RECENT_TRANSACTIONS`), which tells the client which API to call.

### `POST /api/dashboard/widgets` · `PUT /api/dashboard/widgets/{id}`

```json
{ "metricType": "SPENDING_TREND", "groupBy": "DAY", "period": "LAST_30_DAYS",
  "comparisonPeriod": "NONE", "chartType": "LINE", "position": 4,
  "size": "FULL", "configuration": { "title": "Spending Over Time", "limit": 8 } }
```

`chartType` ∈ `NUMBER`, `CHANGE`, `LINE`, `BAR`, `PIE`, `DONUT`, `LIST`,
`PROGRESS`. `size` ∈ `SMALL`, `MEDIUM`, `LARGE`, `FULL`. Max 24 widgets.

`configuration` accepts only `limit`, `title`, `categoryId`, `showLegend` and
`month`; other keys are dropped.

### `DELETE /api/dashboard/widgets/{id}`
### `PUT /api/dashboard/widgets/order`

```json
{ "widgetIds": [3, 1, 2, 4] }
```

The request must list each of the current user's widgets exactly once; otherwise
it returns `400`.

### `POST /api/dashboard/widgets/reset`

This endpoint replaces the current layout with the default widgets.

---

## Budgets

### `GET /api/budgets`

| Parameter | Notes |
|---|---|
| `month` | `YYYY-MM`. Defaults to the current month |

```json
[ { "id": 1, "categoryId": 1, "categoryName": "Food", "month": "2026-09",
    "amount": 500.0000, "spent": 420.0000, "remaining": 80.0000,
    "percentageUsed": 84.0, "exceeded": false, "currency": "SGD" } ]
```

`spent` is always computed from transactions. `remaining` goes negative when a
budget is exceeded rather than clamping at zero.

### `POST /api/budgets` · `PUT /api/budgets/{id}` · `DELETE /api/budgets/{id}`

```json
{ "categoryId": 1, "month": "2026-09", "amount": 500.00 }
```

Only the amount is editable on an existing budget, although the request still
uses the same required fields. Creating a second budget for the same category
and month returns `409`.

---

## Insights

### `GET /api/recurring`

```json
[ { "merchant": "Spotify", "categoryId": 6, "categoryName": "Subscriptions",
    "cadence": "MONTHLY", "typicalAmount": 12.98, "monthlyCost": 12.98,
    "annualCost": 155.76, "occurrences": 9,
    "firstSeen": "2026-01-03", "lastSeen": "2026-09-03",
    "nextExpectedDate": "2026-10-03", "confidence": "HIGH",
    "explanation": "9 payments of about 12.98 monthly since 2026-01-03, 100% of them 25-35 days apart",
    "currency": "SGD" } ]
```

`cadence` ∈ `WEEKLY`, `FORTNIGHTLY`, `MONTHLY`, `QUARTERLY`, `YEARLY`.
`confidence` ∈ `LOW`, `MEDIUM`, `HIGH`. The endpoint examines the preceding
12 months, so the detector's minimum of three payments prevents annual patterns
from being established through this endpoint. A cadence enum describes supported
algorithm inputs, not a guarantee that the API's lookback supplies enough history.

### `GET /api/insights/anomalies`

```json
[ { "categoryId": 4, "categoryName": "Shopping",
    "currentAmount": 689.00, "baselineAverage": 60.13,
    "baselineStandardDeviation": 52.81, "baselineMonths": 6,
    "changeAmount": 628.87, "changePercentage": 1045.8,
    "comparedUpToDay": 6, "severity": "HIGH",
    "explanation": "Shopping spending is 1045.8% higher than usual: 689.00 so far this month against an average of 60.13 over the first 6 days of the previous 6 months." } ]
```

`comparedUpToDay` is the day of month both sides are measured to — a like-for-like
comparison. Findings describe spending patterns; they are never presented as
fraud.

---

## Security activity

### `GET /api/security/events`

This endpoint returns a paginated list of the current user's audit events. It
accepts `page` (default 0) and `size` (default 25, maximum 100).

```json
{ "content": [ { "id": 1, "eventType": "LOGIN_SUCCESS",
                 "occurredAt": "2026-09-06T12:00:00Z", "metadata": {} } ],
  "page": 0, "size": 25, "totalElements": 12, "totalPages": 1, "hasNext": false }
```

Credential-related metadata keys are filtered before storage. The filtering
policy is described in [security.md](security.md).

---

## Operations

### `GET /actuator/health`

This endpoint permits anonymous requests.

```json
{ "status": "UP", "groups": ["liveness", "readiness"] }
```

No component detail is exposed. `/actuator/env`, `/beans`, `/configprops` and
`/heapdump` are not exposed at all.
