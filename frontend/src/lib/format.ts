/**
 * Display formatting.
 *
 * All money arithmetic happens on the server in BigDecimal. These helpers only
 * turn already-computed numbers into strings, which is the one thing floating
 * point is safe for.
 */

const DEFAULT_LOCALE = 'en-SG'

export function formatCurrency(value: number | null | undefined, currency = 'SGD'): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—'
  }
  return new Intl.NumberFormat(DEFAULT_LOCALE, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value)
}

/** Compact form for chart axes: $1.2k rather than $1,234.56. */
export function formatCompactCurrency(value: number, currency = 'SGD'): string {
  return new Intl.NumberFormat(DEFAULT_LOCALE, {
    style: 'currency',
    currency,
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(value)
}

/**
 * A percentage change, with its sign.
 *
 * `null` means the backend could not compute one - no previous data, or a
 * previous total of zero. It is shown as "no comparison" rather than as 0%,
 * which would falsely claim spending was flat.
 */
export function formatPercentageChange(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—'
  }
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)}%`
}

export function formatPercentage(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—'
  }
  return `${value.toFixed(1)}%`
}

export function formatDate(isoDate: string | null | undefined): string {
  if (!isoDate) return '—'
  const date = new Date(isoDate)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(DEFAULT_LOCALE, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(date)
}

export function formatDateTime(isoInstant: string | null | undefined): string {
  if (!isoInstant) return '—'
  const date = new Date(isoInstant)
  if (Number.isNaN(date.getTime())) return '—'
  return new Intl.DateTimeFormat(DEFAULT_LOCALE, {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

/** "2026-09" -> "September 2026". */
export function formatMonth(yearMonth: string): string {
  const [year, month] = yearMonth.split('-')
  if (!year || !month) return yearMonth
  const date = new Date(Number(year), Number(month) - 1, 1)
  return new Intl.DateTimeFormat(DEFAULT_LOCALE, { month: 'long', year: 'numeric' }).format(date)
}

/** Today as YYYY-MM-DD in the browser's own timezone, for date inputs. */
export function todayIsoDate(): string {
  const now = new Date()
  const offsetMinutes = now.getTimezoneOffset()
  return new Date(now.getTime() - offsetMinutes * 60_000).toISOString().slice(0, 10)
}

/** Current month as YYYY-MM. */
export function currentYearMonth(): string {
  return todayIsoDate().slice(0, 7)
}

/**
 * Human label for an audit event type.
 *
 * `LOGIN_SUCCESS` -> `Login success`. A lookup table would go stale every time
 * the backend adds an event; this degrades gracefully instead.
 */
export function humaniseEventType(eventType: string): string {
  const lower = eventType.toLowerCase().replace(/_/g, ' ')
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}

/** Sentence-cases an enum value: `CREDIT_CARD` -> `Credit card`. */
export function humaniseEnum(value: string): string {
  return humaniseEventType(value)
}
