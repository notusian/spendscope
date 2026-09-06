# Performance

Every number in this document was measured on a real run. Nothing here is
estimated, extrapolated or aspirational. Where a measurement is unflattering or
ambiguous, it is reported as measured and the caveat is stated.

## How to reproduce

```bash
# 1. Generate the 10,000 row dataset (gitignored; regenerate on demand)
python3 scripts/generate_sample_data.py --performance

# 2. Start PostgreSQL and the backend
docker compose up -d db          # or a local PostgreSQL on :5432
./scripts/run-backend.sh &

# 3. Measure
./scripts/measure-performance.sh
```

The script registers its own user, imports the dataset into a fresh account and
times each operation over HTTP, so the figures include JSON serialisation,
Spring's filter chain and the database round trip — not just query time.
Read operations are reported as the **median of five calls**, which damps JIT
warm-up and scheduler noise. Import figures are single runs, because importing
the same file twice is a different code path by design.

## Environment

| | |
|---|---|
| Machine | Apple M4 Pro, 12 cores, 24 GB RAM |
| OS | macOS 26.5 |
| JDK | Temurin 21.0.12.1 |
| PostgreSQL | 17.11 (Homebrew), local, default configuration |
| Backend | Spring Boot 3.5.16, `local` profile, run via `spring-boot:run` |
| Client | `curl` over loopback |

This is a developer laptop with the database on the same machine. A deployed
setup pays real network latency to a managed database, which will dominate
these numbers for the small queries. Treat the figures as a measure of the
application's own work, not as a latency SLA.

## Dataset

| | |
|---|---|
| Rows imported | 10,000 |
| Time span | 3 years |
| Distinct merchants | 18 |
| Total rows in the table at measurement time | 11,583 (across 8 users) |

The table holding several users' data matters: it means the user-scoped
indexes are doing real filtering work rather than scanning a table that happens
to contain only one user's rows.

## Results

### Import

| Operation | Measured |
|---|---|
| CSV import, 10,000 rows | **1,193 ms** |
| CSV re-import, all 10,000 rows duplicates | **406 ms** |

About 8,400 rows/second end to end, including parsing, normalisation, merchant
matching, categorisation, fingerprinting, duplicate lookup and insertion.

The re-import being three times *faster* is the expected shape: no rows are
inserted, so the cost is parsing plus the batched fingerprint lookups. It also
confirms duplicate detection is not doing a query per row — 10,000 individual
`SELECT`s would be slower than the insert path, not faster.

### Transaction list

| Operation | Measured |
|---|---|
| Page 1, 25 rows | **35 ms** |
| Page 200, 25 rows (deep paging) | **39 ms** |
| Merchant/description search | **47 ms** |
| Date range filter | **26 ms** |

Deep paging costs essentially the same as the first page, because
`idx_transactions_user_date` supplies the ordering and PostgreSQL walks the
index rather than sorting the whole result set.

### Analytics

| Operation | Measured |
|---|---|
| Dashboard summary, this month | **30 ms** |
| Dashboard summary, this year | **39 ms** |
| Category aggregation, 1 year | **35 ms** |
| Merchant aggregation, 1 year | **34 ms** |
| Monthly aggregation, 3 years | **52 ms** |
| Daily aggregation, 90 days | **38 ms** |

The summary endpoint runs six aggregate queries and still lands at 30 ms, which
is the argument for having it be one endpoint rather than six round trips.

### Insights

| Operation | Measured |
|---|---|
| Recurring expense detection, 12 months | **42 ms** |
| Anomaly detection, 7 months | **31 ms** |
| Budget progress | **29 ms** |

Recurring detection pulls a year of outgoing rows into the JVM and runs the
grouping there. At 10,000 rows that is comfortably fast; see *Known limits*
below for where it stops being so.

## Query plans

Measured with `EXPLAIN ANALYZE` against the same dataset, after `ANALYZE
transactions`.

**Transaction list** — uses the composite index for both filter and ordering:

```
Limit  (cost=1.34..4.60 rows=25) (actual time=0.031..0.033 rows=25)
  ->  Incremental Sort  (Sort Key: date DESC, id DESC; Presorted Key: date)
        ->  Index Scan using idx_transactions_user_date on transactions
```

The `Presorted Key: date` line is the one that matters: the index already
provides the order, so only the `id` tiebreaker needs sorting, and only within
one date group.

**Category aggregation** — index-driven, no sequential scan:

```
HashAggregate  (actual time=0.449..0.451 rows=9)
  Group Key: category_id
  ->  Bitmap Heap Scan on transactions t  (Heap Blocks: exact=64)
        ->  Bitmap Index Scan on idx_transactions_user_date  (rows=2273)
```

**Duplicate detection** — the fingerprint index resolves in microseconds:

```
Index Scan using idx_transactions_user_fingerprint on transactions
  Index Cond: ((user_id = 9) AND (fingerprint = 'abc123'))
Execution Time: 0.046 ms
```

No query in the measured set falls back to a sequential scan on
`transactions`.

## What was deliberately not optimised

No performance problem showed up that justified a fix, so nothing was tuned
speculatively. Specifically, none of the following were added, because at this
data size they would be complexity without measurable benefit:

- a caching layer,
- materialised views or summary tables for analytics,
- a read replica,
- cursor-based (keyset) pagination.

The one N+1 that *was* fixed — loading account and category names once per page
rather than twice per row — was fixed because it is a correctness-of-design
issue that gets worse linearly, not because a measurement demanded it.

## Known limits

These are the points where the current design would need work. None of them is
reached by the measured dataset; they are stated so the ceiling is known rather
than discovered later.

1. **Recurring detection loads a year of rows into memory.** At ~10,000 rows
   per year it is fine. A user with 100,000 transactions a year would make this
   the slowest endpoint in the application. The fix is to push the grouping
   into SQL, or to cap the merchants considered.

2. **Offset pagination degrades at very deep offsets.** Page 200 was flat here,
   but offset paging is `O(offset)` in principle. Keyset pagination is the
   answer if anyone ever pages that far, and nobody does.

3. **The import runs in one transaction.** 10,000 rows in ~1.2 s is a short
   transaction. A 500,000 row file would hold locks and memory for a long time
   and should be chunked or moved to a background job.

4. **Analytics are computed on every request.** There is no caching. At 30-50 ms
   that is the right trade — cached analytics that lag a just-imported statement
   would be a worse product — but a high-traffic deployment would want a short
   TTL on the summary endpoint.

5. **Measured on loopback with a warm database.** Cold-cache and
   network-attached-database numbers will be higher. The relative shape of the
   results should hold; the absolute values will not.
