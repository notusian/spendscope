# Performance

`scripts/measure-performance.sh` exercises the API against generated data. It
is a development benchmark, not a load test: serial requests to a local server
do not establish concurrent-user capacity or deployment latency.

## Run the benchmark

Use a development database that can retain benchmark records. The script creates
a new user and account, so it leaves data behind after a run.

From the repository root, generate the dataset and start PostgreSQL:

```bash
python3 scripts/generate_sample_data.py --performance
docker compose up -d db
```

Start the backend in a separate terminal:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Once the health endpoint responds, run the benchmark:

```bash
./scripts/measure-performance.sh
```

The default API address is `http://localhost:8080/api`; set `BASE` to use another
instance. Record the commit, run date, Java and PostgreSQL versions, machine,
dataset dates, and complete script output alongside any results. Running against
a different instance also changes network and database latency.

The script uses curl's transfer duration, fails on HTTP error responses, and
removes temporary cookie and response files when it exits. It verifies that the
fresh import accepted every row and that the second import reports every row as
a duplicate. It also creates a grocery budget in the dataset's final month
before measuring budget progress.

Filtered reads and custom analytics ranges derive their dates from the CSV
rather than fixed calendar literals. The recurring and anomaly endpoints still
use the server clock, so their current windows can contain less data as the
fixed sample ages. Their output should not be interpreted as evidence that
those detectors found a pattern.

The import and duplicate-import paths are separate measurements. Repeating an
import on the same account does not measure another fresh insert, because the
fingerprint lookup now finds the previous records. Reads use the median of five
requests, which is useful for a quick comparison but too small a sample for
reliable tail latency. Explicit warm-up and repeated independent runs are needed
for stronger conclusions.

## Recorded development run

The [15 October 2026 run](performance-runs/2026-10-15.txt) records the output
from one execution of the corrected harness on macOS arm64, Homebrew OpenJDK
21.0.12.1, and PostgreSQL 17.11. The backend used the local profile and an
existing development database.

| Operation | Observed time |
|---|---:|
| Import 10,000 rows, with counts verified | 1,404.71 ms |
| Re-import the same file, with duplicate counts verified | 427.81 ms |
| First transaction page, median of five requests | 12.57 ms |
| Category aggregation, median of five requests | 23.74 ms |
| One populated budget, median of five requests | 10.72 ms |

The dataset covers 6 September 2023 through 4 September 2026. Recurring and
anomaly endpoints use the server's current date, so their windows do not match
the dataset's final month. Their timings do not demonstrate the cost of fully
populated current-period detection. Database state, warm-up, and the small
sample size also limit comparisons with another machine or deployment.

Earlier undocumented timings have been removed because their harness included
external clock-process overhead and did not verify HTTP or import results.

## Inspect query plans separately

Use `EXPLAIN (ANALYZE, BUFFERS)` with representative parameters and retain the
complete plan. For example, substitute the benchmark user's ID in this read:

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, date, amount
FROM transactions
WHERE user_id = 1
ORDER BY date DESC, id DESC
LIMIT 25;
```

Compare the actual rows, estimates, buffer activity, and sort work. The index
on `(user_id, date)` can support ownership filtering and date ordering, while
an additional sort may handle the ID tie-breaker. Whether PostgreSQL chooses
that index depends on table statistics, selectivity, and data size.

A sequential scan is not automatically a performance defect. It may be cheaper
when a query needs much of a small table. Conversely, seeing an index in one
plan does not prove that all date ranges or users will have similar costs.
HTTP timing alone cannot establish the number of SQL statements executed.

## Scaling questions to measure

- Recurring detection loads outgoing payments from the lookback window into
  memory. Measure its allocation and runtime as payment count and merchant
  variety increase; a date bound is not a row-count bound.
- Offset pagination still skips earlier rows. Similar timings for two pages in
  a small dataset do not establish constant cost at large offsets. Keyset
  pagination is an option if measured navigation patterns justify it.
- Imports run in one transaction and currently reject files over 10,000 rows.
  Supporting larger files would require measuring memory, transaction duration,
  and failure recovery before changing the limit.
- Dashboard aggregates are computed for each request, and budgets issue a
  spending query per budget. Measure realistic widget and budget counts before
  deciding whether grouped queries, caching, or summary tables are appropriate.
- Public deployment measurements should include concurrent requests, database
  network latency, cold and warm runs, and latency percentiles. Single-user
  loopback results cannot substitute for those tests.
