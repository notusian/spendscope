#!/usr/bin/env bash
# Measures serial API requests using the generated performance dataset.
# Run against a development database: each run leaves a benchmark user and data.
set -euo pipefail

BASE="${BASE:-http://localhost:8080/api}"
CSV="$(dirname "$0")/../sample-data/performance-transactions.csv"
if [[ ! -f "$CSV" ]]; then
  echo "Missing $CSV. Run: python3 scripts/generate_sample_data.py --performance" >&2
  exit 1
fi

JAR="$(mktemp -t spendscope-perf-cookies)"
RESPONSE=""
trap 'rm -f "$JAR" "${RESPONSE:-}"' EXIT
RESPONSE="$(mktemp -t spendscope-perf-response)"
EMAIL="perf-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')@example.com"
PASSWORD="correct-horse-battery"

# Use the dataset's actual dates rather than calendar literals that become stale.
DATASET=$(python3 - "$CSV" <<'PY'
import csv
import sys
from datetime import date, timedelta

with open(sys.argv[1], newline='', encoding='utf-8') as source:
    dates = [date.fromisoformat(row['date']) for row in csv.DictReader(source)]
if not dates:
    raise SystemExit('The performance dataset has no data rows.')
first, last = min(dates), max(dates)
print(len(dates), first, last, last - timedelta(days=364),
      last - timedelta(days=89), last.replace(day=1), last.strftime('%Y-%m'))
PY
)
read -r ROWS FIRST LAST YEAR_START NINETY_DAY_START MONTH_START BUDGET_MONTH <<< "$DATASET"

csrf() {
  awk '$6 == "XSRF-TOKEN" { token = $7 } END { print token }' "$JAR"
}

request() {
  curl --fail --silent --show-error --connect-timeout 10 --max-time 120 \
    -c "$JAR" -b "$JAR" "$@"
}

# curl measures the transfer itself, excluding Python and shell process startup.
time_call() {
  local seconds
  seconds=$(request -o "$RESPONSE" -w '%{time_total}' "$@") || return
  awk -v seconds="$seconds" 'BEGIN { printf "%.2f\n", seconds * 1000 }'
}

median_of() {
  local runs=$1 elapsed index
  shift
  local times=()
  for ((index = 0; index < runs; index++)); do
    elapsed=$(time_call "$@") || return
    times+=("$elapsed")
  done
  printf '%s\n' "${times[@]}" | sort -n | awk '
    { values[NR] = $1 }
    END { printf "%.2f\n", NR % 2 ? values[(NR + 1) / 2] : (values[NR / 2] + values[NR / 2 + 1]) / 2 }
  '
}

measure_read() {
  local label=$1 elapsed
  shift
  # Keep this assignment separate so a failed request cannot be hidden by printf.
  elapsed=$(median_of 5 "$@") || return
  printf '%-46s %9s ms\n' "$label" "$elapsed"
}

verify_import() {
  python3 - "$RESPONSE" "$ROWS" "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding='utf-8') as source:
    result = json.load(source)
rows, mode = int(sys.argv[2]), sys.argv[3]
expected = {
    'rowsProcessed': rows,
    'rowsImported': rows if mode == 'fresh' else 0,
    'rowsRejected': 0,
    'duplicatesDetected': 0 if mode == 'fresh' else rows,
}
for field, value in expected.items():
    if result.get(field) != value:
        raise SystemExit(f'Import verification failed: {field} was {result.get(field)!r}; expected {value}.')
PY
}

printf 'SpendScope API benchmark\nDataset: %s rows, %s through %s\n' "$ROWS" "$FIRST" "$LAST"
printf 'API: %s\nRead timings are medians of five serial requests.\n\n' "$BASE"

request -o /dev/null "$BASE/auth/csrf"
request -o /dev/null -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" "$BASE/auth/register"
request -o /dev/null -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" "$BASE/auth/login"
ACCOUNT_ID=$(request -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" \
  -d '{"name":"Performance Account","type":"CHECKING","currency":"SGD"}' "$BASE/accounts" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')

IMPORT_MS=$(time_call -H "X-XSRF-TOKEN: $(csrf)" \
  -F "file=@$CSV" "$BASE/imports/csv?accountId=$ACCOUNT_ID")
verify_import fresh
printf '%-46s %9s ms\n' "CSV import ($ROWS rows verified)" "$IMPORT_MS"

DEDUPE_MS=$(time_call -H "X-XSRF-TOKEN: $(csrf)" \
  -F "file=@$CSV" "$BASE/imports/csv?accountId=$ACCOUNT_ID")
verify_import duplicate
printf '%-46s %9s ms\n' 'CSV re-import (all duplicates verified)' "$DEDUPE_MS"

measure_read 'First transaction page (25 rows)' "$BASE/transactions?page=0&size=25"
measure_read 'Transaction page at offset 5,000' "$BASE/transactions?page=200&size=25"
measure_read 'Transaction search (fairprice)' "$BASE/transactions?search=fairprice&size=25"
measure_read 'Transaction filter (dataset final 365 days)' \
  "$BASE/transactions?from=$YEAR_START&to=$LAST&size=25"
measure_read 'Summary (dataset final partial month)' \
  "$BASE/analytics/summary?period=CUSTOM&start=$MONTH_START&end=$LAST"
measure_read 'Summary (dataset final 365 days)' \
  "$BASE/analytics/summary?period=CUSTOM&start=$YEAR_START&end=$LAST"

measure_read 'Category aggregation (dataset final 365 days)' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" \
  -d "{\"metric\":\"CATEGORY_SPENDING\",\"period\":{\"type\":\"CUSTOM\",\"start\":\"$YEAR_START\",\"end\":\"$LAST\"}}" \
  "$BASE/analytics/query"
measure_read 'Merchant aggregation (dataset final 365 days)' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" \
  -d "{\"metric\":\"TOTAL_SPENDING\",\"period\":{\"type\":\"CUSTOM\",\"start\":\"$YEAR_START\",\"end\":\"$LAST\"},\"groupBy\":\"MERCHANT\"}" \
  "$BASE/analytics/query"
measure_read 'Monthly aggregation (entire dataset)' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" \
  -d "{\"metric\":\"TOTAL_SPENDING\",\"period\":{\"type\":\"CUSTOM\",\"start\":\"$FIRST\",\"end\":\"$LAST\"},\"groupBy\":\"MONTH\"}" \
  "$BASE/analytics/query"
measure_read 'Daily aggregation (dataset final 90 days)' \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" \
  -d "{\"metric\":\"TOTAL_SPENDING\",\"period\":{\"type\":\"CUSTOM\",\"start\":\"$NINETY_DAY_START\",\"end\":\"$LAST\"},\"groupBy\":\"DAY\"}" \
  "$BASE/analytics/query"

# Populate a budget in a month that contains data before measuring its aggregate.
CATEGORY_ID=$(request "$BASE/categories" | python3 -c \
  'import json,sys; print(next(c["id"] for c in json.load(sys.stdin) if c["name"] == "Groceries"))')
request -o /dev/null -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" \
  -d "{\"categoryId\":$CATEGORY_ID,\"month\":\"$BUDGET_MONTH\",\"amount\":500}" "$BASE/budgets"
measure_read "Budget progress (one budget, $BUDGET_MONTH)" "$BASE/budgets?month=$BUDGET_MONTH"
measure_read 'Recurring detection (current 12-month window)' "$BASE/recurring"
measure_read 'Anomalies (current month and baseline)' "$BASE/insights/anomalies"

printf '\nBenchmark records remain under %s.\n' "$EMAIL"
printf 'Insight endpoints use the server clock; their windows may extend beyond the dataset.\n'
