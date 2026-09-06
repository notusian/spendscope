#!/usr/bin/env bash
#
# Measures the operations that matter on a realistic dataset.
#
# Prerequisites: the backend running on :8080 against a PostgreSQL that this
# script is allowed to populate. It creates its own user, so it does not
# disturb existing data.
#
#   ./scripts/run-backend.sh &
#   ./scripts/measure-performance.sh
#
# Every number printed is measured, not estimated.
set -euo pipefail

BASE="${BASE:-http://localhost:8080/api}"
JAR="$(mktemp -t spendscope-perf-cookies)"
CSV="$(dirname "$0")/../sample-data/performance-transactions.csv"
EMAIL="perf-$(date +%s)@example.com"
PASSWORD="correct-horse-battery"

csrf() { grep -i XSRF-TOKEN "$JAR" | awk '{print $7}' | tail -1; }

# Wall-clock milliseconds for one HTTP call.
time_call() {
  local start end
  start=$(python3 -c 'import time; print(int(time.time()*1000))')
  "$@" > /dev/null
  end=$(python3 -c 'import time; print(int(time.time()*1000))')
  echo $((end - start))
}

# Median of N repetitions, to damp JIT warm-up and scheduler noise.
median_of() {
  local runs=$1; shift
  local times=()
  for _ in $(seq 1 "$runs"); do
    times+=("$(time_call "$@")")
  done
  printf '%s\n' "${times[@]}" | sort -n | awk '{a[NR]=$1} END {print (NR%2==1) ? a[(NR+1)/2] : int((a[NR/2]+a[NR/2+1])/2)}'
}

if [[ ! -f "$CSV" ]]; then
  echo "Missing $CSV. Run: python3 scripts/generate_sample_data.py --performance" >&2
  exit 1
fi

echo "SpendScope performance measurement"
echo "=================================="
echo "dataset: $(($(wc -l < "$CSV") - 1)) rows"
echo

curl -s -c "$JAR" -b "$JAR" -o /dev/null "$BASE/auth/csrf"
curl -s -c "$JAR" -b "$JAR" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(csrf)" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" -o /dev/null "$BASE/auth/register"
curl -s -c "$JAR" -b "$JAR" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(csrf)" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" -o /dev/null "$BASE/auth/login"
ACCOUNT_ID=$(curl -s -c "$JAR" -b "$JAR" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(csrf)" \
  -d '{"name":"Perf Account","type":"CHECKING","currency":"SGD"}' "$BASE/accounts" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')

# ---------------------------------------------------------------- import
IMPORT_MS=$(time_call curl -s -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $(csrf)" \
  -F "file=@$CSV" "$BASE/imports/csv?accountId=$ACCOUNT_ID")
printf 'CSV import (10,000 rows)           %6s ms\n' "$IMPORT_MS"

# Re-import the same file: every row is a duplicate, so this measures the
# fingerprint lookup path rather than the insert path.
DEDUPE_MS=$(time_call curl -s -c "$JAR" -b "$JAR" -H "X-XSRF-TOKEN: $(csrf)" \
  -F "file=@$CSV" "$BASE/imports/csv?accountId=$ACCOUNT_ID")
printf 'CSV re-import (all duplicates)     %6s ms\n' "$DEDUPE_MS"
echo

# ------------------------------------------------------------ read paths
printf 'Transaction page 1 (25 rows)       %6s ms\n' \
  "$(median_of 5 curl -s -c "$JAR" -b "$JAR" "$BASE/transactions?page=0&size=25")"
printf 'Transaction page 200 (deep paging)  %5s ms\n' \
  "$(median_of 5 curl -s -c "$JAR" -b "$JAR" "$BASE/transactions?page=200&size=25")"
printf 'Transaction search (merchant)      %6s ms\n' \
  "$(median_of 5 curl -s -c "$JAR" -b "$JAR" "$BASE/transactions?search=fairprice&size=25")"
printf 'Transaction filter (date+category)  %5s ms\n' \
  "$(median_of 5 curl -s -c "$JAR" -b "$JAR" "$BASE/transactions?from=2025-01-01&to=2025-12-31&size=25")"
echo

printf 'Dashboard summary (this month)     %6s ms\n' \
  "$(median_of 5 curl -s -c "$JAR" -b "$JAR" "$BASE/analytics/summary?period=THIS_MONTH")"
printf 'Dashboard summary (this year)      %6s ms\n' \
  "$(median_of 5 curl -s -c "$JAR" -b "$JAR" "$BASE/analytics/summary?period=THIS_YEAR")"

QUERY() {
  curl -s -c "$JAR" -b "$JAR" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(csrf)" \
    -d "$1" "$BASE/analytics/query"
}
printf 'Category aggregation (1 year)      %6s ms\n' \
  "$(median_of 5 QUERY '{"metric":"CATEGORY_SPENDING","period":{"type":"THIS_YEAR"}}')"
printf 'Merchant aggregation (1 year)      %6s ms\n' \
  "$(median_of 5 QUERY '{"metric":"TOTAL_SPENDING","period":{"type":"THIS_YEAR"},"groupBy":"MERCHANT"}')"
printf 'Monthly aggregation (3 years)      %6s ms\n' \
  "$(median_of 5 QUERY '{"metric":"TOTAL_SPENDING","period":{"type":"CUSTOM","start":"2023-01-01","end":"2026-12-31"},"groupBy":"MONTH"}')"
printf 'Daily aggregation (90 days)        %6s ms\n' \
  "$(median_of 5 QUERY '{"metric":"TOTAL_SPENDING","period":{"type":"LAST_90_DAYS"},"groupBy":"DAY"}')"
echo

printf 'Recurring detection (12 months)    %6s ms\n' \
  "$(median_of 5 curl -s -c "$JAR" -b "$JAR" "$BASE/recurring")"
printf 'Anomaly detection (7 months)       %6s ms\n' \
  "$(median_of 5 curl -s -c "$JAR" -b "$JAR" "$BASE/insights/anomalies")"
printf 'Budget progress                    %6s ms\n' \
  "$(median_of 5 curl -s -c "$JAR" -b "$JAR" "$BASE/budgets")"

rm -f "$JAR"
