#!/usr/bin/env python3
"""
Generates the synthetic CSV files under sample-data/.

Everything here is invented. No real financial data is used, and the merchant
names are public brands attached to made-up amounts.

The data is shaped to exercise the whole product rather than to be uniform
noise:

  * exact-amount monthly subscriptions      -> recurring detection has a target
  * a holiday two months before the end     -> period comparison shows a swing
  * an expensive final month for Shopping   -> the anomaly detector has a signal
  * a steady weekly grocery rhythm          -> trend charts look like real life

Usage:
    python3 scripts/generate_sample_data.py                 # regenerate samples
    python3 scripts/generate_sample_data.py --performance   # also write 10k rows
"""

from __future__ import annotations

import argparse
import csv
import random
from calendar import monthrange
from datetime import date, timedelta
from pathlib import Path

# A fixed seed keeps the committed files stable: regenerating produces the same
# rows, so a diff only appears when this script actually changes.
SEED = 20260905
END_DATE = date(2026, 9, 5)
MONTHS_OF_HISTORY = 9

REPO_ROOT = Path(__file__).resolve().parent.parent
SAMPLE_DIR = REPO_ROOT / "sample-data"


def month_starts(end: date, months: int) -> list[date]:
    """First day of each month in the window, oldest first."""
    starts = []
    year, month = end.year, end.month
    for _ in range(months):
        starts.append(date(year, month, 1))
        month -= 1
        if month == 0:
            year, month = year - 1, 12
    return list(reversed(starts))


def day_in_month(day: int, month_start: date, end: date) -> date | None:
    """
    Resolves a day-of-month to a real date, or None if it has not happened yet.

    Returning None rather than clamping to `end` matters: clamping would pile
    every remaining transaction of the current month onto today, making the
    partial month look like a spending explosion in every period comparison.
    Skipping instead gives a realistic partial month.
    """
    last_day = monthrange(month_start.year, month_start.month)[1]
    candidate = month_start.replace(day=min(day, last_day))
    return None if candidate > end else candidate


def money(rng: random.Random, centre: float, spread: float) -> float:
    return round(centre + (rng.random() - 0.5) * 2 * spread, 2)


def build_rows(end: date, months: int, rng: random.Random) -> list[tuple[date, str, float]]:
    rows: list[tuple[date, str, float]] = []
    starts = month_starts(end, months)
    holiday_month = starts[-3]
    final_month = starts[-1]

    def add(day: int, month_start: date, description: str, amount: float) -> None:
        resolved = day_in_month(day, month_start, end)
        if resolved is not None:
            rows.append((resolved, description, round(amount, 2)))

    for month_start in starts:
        # --- income -------------------------------------------------------
        add(25, month_start, "SALARY CREDIT ACME PTE LTD", 4200.00)

        # --- subscriptions: exact amounts, stable days --------------------
        add(3, month_start, "SPOTIFY P34A9B21", -12.98)
        add(15, month_start, "NETFLIX.COM SINGAPORE", -19.98)
        add(8, month_start, "APPLE.COM/BILL ITUNES", -4.98)
        add(20, month_start, "ADOBE CREATIVE CLOUD", -28.98)

        # --- utilities ----------------------------------------------------
        add(10, month_start, "SP SERVICES LTD", money(rng, -95, 45))
        add(12, month_start, "SINGTEL MOBILE POSTPAID", money(rng, -42, 8))

        # --- groceries: weekly big shop plus top-ups ----------------------
        for day in (4, 11, 18, 25):
            add(day, month_start, f"NTUC FAIRPRICE #{100 + rng.randrange(80)}", money(rng, -78, 30))
        add(14, month_start, "COLD STORAGE HOLLAND V", money(rng, -36, 18))
        add(22, month_start, "SHENG SIONG SUPERMARKET", money(rng, -24, 12))

        # --- eating out ---------------------------------------------------
        eating_out = [
            "STARBUCKS COFFEE #4821", "MCDONALDS RESTAURANT", "GRABFOOD DELIVERY",
            "YA KUN KAYA TOAST", "TOAST BOX ORCHARD", "KFC SINGAPORE", "DELIVEROO SG",
        ]
        for _ in range(12 + rng.randrange(8)):
            add(1 + rng.randrange(28), month_start, rng.choice(eating_out), money(rng, -14, 11))

        # --- transport ----------------------------------------------------
        for _ in range(10 + rng.randrange(10)):
            description = (
                f"GRAB *TRIP {1000 + rng.randrange(8000)}" if rng.random() < 0.5 else "SIMPLYGO TRANSIT"
            )
            add(1 + rng.randrange(28), month_start, description, money(rng, -11, 9))

        # --- shopping -----------------------------------------------------
        shopping = [
            "UNIQLO SINGAPORE", "SHOPEE SG PAYMENT", "AMAZON SG MARKETPLACE",
            "LAZADA SINGAPORE", "MUJI PLAZA SING", "DECATHLON SG",
        ]
        for _ in range(2 + rng.randrange(3)):
            add(2 + rng.randrange(26), month_start, rng.choice(shopping), money(rng, -58, 40))

        # --- entertainment ------------------------------------------------
        add(6 + rng.randrange(20), month_start, "GOLDEN VILLAGE CINEMAS", money(rng, -27, 10))
        if rng.random() < 0.5:
            add(6 + rng.randrange(20), month_start, "STEAMGAMES.COM", money(rng, -32, 20))

        # --- healthcare, occasionally --------------------------------------
        if rng.random() < 0.4:
            add(5 + rng.randrange(20), month_start, "GUARDIAN HEALTH & BEAUTY", money(rng, -34, 18))

        # --- the holiday ---------------------------------------------------
        if month_start == holiday_month:
            add(8, month_start, "SINGAPORE AIRLINES TICKET", -742.40)
            add(9, month_start, "AGODA HOTEL BOOKING", -586.00)
            add(12, month_start, "AIRBNB * HMQ4T2", -318.50)
            add(13, month_start, "BOOKING.COM RESERVATION", -204.00)

        # --- the unusual month ---------------------------------------------
        if month_start == final_month:
            add(2, month_start, "AMAZON SG MARKETPLACE LAPTOP STAND SET", -689.00)

    rows.sort(key=lambda row: (row[0], row[1]))
    return rows


def write_csv(path: Path, rows: list[tuple[date, str, float]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["date", "description", "amount"])
        for row_date, description, amount in rows:
            writer.writerow([row_date.isoformat(), description, f"{amount:.2f}"])
    print(f"wrote {len(rows):>6} rows -> {path.relative_to(REPO_ROOT)}")


def write_performance_csv(path: Path, rng: random.Random, target_rows: int = 10_000) -> None:
    """A large file for the performance measurements in docs/performance.md."""
    merchants = [
        "NTUC FAIRPRICE", "COLD STORAGE", "SHENG SIONG", "STARBUCKS COFFEE",
        "MCDONALDS RESTAURANT", "GRABFOOD DELIVERY", "GRAB *TRIP", "SIMPLYGO TRANSIT",
        "SHOPEE SG PAYMENT", "LAZADA SINGAPORE", "AMAZON SG MARKETPLACE", "UNIQLO SINGAPORE",
        "GOLDEN VILLAGE CINEMAS", "SP SERVICES LTD", "SINGTEL MOBILE POSTPAID",
        "WATSONS PERSONAL CARE", "AGODA HOTEL BOOKING", "COURSERA SUBSCRIPTION",
    ]
    rows: list[tuple[date, str, float]] = []
    start = END_DATE - timedelta(days=365 * 3)
    # A unique reference per row keeps fingerprints distinct, so the import
    # measures insert throughput rather than duplicate rejection.
    for index in range(target_rows):
        row_date = start + timedelta(days=rng.randrange(365 * 3))
        merchant = rng.choice(merchants)
        rows.append((row_date, f"{merchant} REF{index:06d}", -round(rng.uniform(3, 250), 2)))
    rows.sort(key=lambda row: row[0])
    write_csv(path, rows)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--performance", action="store_true",
                        help="also generate the 10,000 row performance file")
    args = parser.parse_args()

    rng = random.Random(SEED)
    rows = build_rows(END_DATE, MONTHS_OF_HISTORY, rng)
    write_csv(SAMPLE_DIR / "sample-transactions.csv", rows)

    # A deliberate overlap with the main file, so importing it after the main
    # file demonstrates duplicate detection reporting rather than inserting.
    overlap = [row for row in rows if row[0] >= END_DATE - timedelta(days=30)]
    write_csv(SAMPLE_DIR / "sample-transactions-overlap.csv", overlap)

    # A file with deliberately broken rows, for demonstrating per-row rejection.
    malformed = SAMPLE_DIR / "sample-transactions-with-errors.csv"
    malformed.write_text(
        "date,description,amount\n"
        "2026-08-01,STARBUCKS COFFEE #4821,-7.20\n"
        "not-a-date,BROKEN ROW ONE,-10.00\n"
        "2026-08-03,BROKEN ROW TWO,not-a-number\n"
        "2026-08-04,,-15.00\n"
        "2026-08-05,VALID ROW AFTER ERRORS,-22.40\n"
        "2026-08-06,ZERO AMOUNT ROW,0.00\n",
        encoding="utf-8",
    )
    print(f"wrote      6 rows -> {malformed.relative_to(REPO_ROOT)}")

    if args.performance:
        write_performance_csv(SAMPLE_DIR / "performance-transactions.csv", rng)


if __name__ == "__main__":
    main()
