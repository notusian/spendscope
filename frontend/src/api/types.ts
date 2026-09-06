/**
 * TypeScript mirrors of the backend DTOs.
 *
 * Hand written rather than generated: the API surface is small, and a hand
 * written type is a second pair of eyes on the contract. When a backend record
 * changes, the compiler points at every component that read the old field.
 *
 * Money arrives as a JSON number produced from a Java BigDecimal. It is
 * formatted, never used for arithmetic in the browser - see lib/format.ts.
 */

export interface ApiErrorBody {
  timestamp: string
  status: number
  code: ErrorCode
  message: string
  path: string
  fieldErrors?: Record<string, string>
}

export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'BAD_REQUEST'
  | 'UNAUTHENTICATED'
  | 'ACCESS_DENIED'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'PAYLOAD_TOO_LARGE'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'IMPORT_FAILED'
  | 'RATE_LIMITED'
  | 'INTERNAL_ERROR'

export interface User {
  id: number
  email: string
  demo: boolean
  createdAt: string | null
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export type AccountType = 'CHECKING' | 'SAVINGS' | 'CREDIT_CARD' | 'CASH' | 'INVESTMENT' | 'OTHER'

export interface Account {
  id: number
  name: string
  type: AccountType
  currency: string
  archived: boolean
  createdAt: string
}

export interface AccountInput {
  name: string
  type: AccountType
  currency: string
}

export type CategoryType = 'EXPENSE' | 'INCOME'

export interface Category {
  id: number
  name: string
  type: CategoryType
  builtIn: boolean
}

export interface Transaction {
  id: number
  accountId: number
  accountName: string | null
  categoryId: number | null
  categoryName: string | null
  date: string
  description: string
  merchant: string | null
  amount: number
  currency: string
  createdAt: string
}

export interface TransactionInput {
  accountId: number
  categoryId: number | null
  date: string
  description: string
  amount: number
  currency?: string | null
  merchant?: string | null
}

export interface TransactionFilters {
  from?: string
  to?: string
  categoryId?: number
  accountId?: number
  search?: string
  page?: number
  size?: number
  sort?: 'date' | 'amount' | 'description' | 'createdAt'
  direction?: 'asc' | 'desc'
}

export type PeriodType =
  | 'THIS_MONTH'
  | 'LAST_MONTH'
  | 'LAST_30_DAYS'
  | 'LAST_90_DAYS'
  | 'THIS_YEAR'
  | 'CUSTOM'

export type GroupBy = 'CATEGORY' | 'MERCHANT' | 'ACCOUNT' | 'DAY' | 'WEEK' | 'MONTH'

export type Metric =
  | 'TOTAL_SPENDING'
  | 'AVERAGE_DAILY_SPENDING'
  | 'TRANSACTION_COUNT'
  | 'CATEGORY_SPENDING'
  | 'ACCOUNT_SPENDING'
  | 'TOP_CATEGORY'
  | 'TOP_MERCHANT'

export type Comparison = 'NONE' | 'PREVIOUS_PERIOD'

export interface ResolvedPeriod {
  start: string
  end: string
  days: number
}

export interface AnalyticsPoint {
  key: string
  label: string
  value: number
  transactionCount: number
  /** null when no comparison was requested. */
  previousValue: number | null
  /** null when a percentage would be undefined (previous total of zero). */
  percentageChange: number | null
  share: number | null
}

export interface AnalyticsResult {
  metric: Metric
  groupBy: GroupBy | null
  period: ResolvedPeriod
  comparisonPeriod: ResolvedPeriod | null
  total: number
  previousTotal: number | null
  percentageChange: number | null
  currency: string
  points: AnalyticsPoint[]
}

export interface TopEntry {
  key: string
  label: string
  value: number
}

export interface AnalyticsSummary {
  period: ResolvedPeriod
  comparisonPeriod: ResolvedPeriod | null
  totalSpending: number
  previousTotalSpending: number | null
  spendingChangePercentage: number | null
  averageDailySpending: number
  totalIncome: number
  netFlow: number
  transactionCount: number
  topCategory: TopEntry | null
  topMerchant: TopEntry | null
  currency: string
}

export interface AnalyticsQuery {
  metric: Metric
  period: { type: PeriodType; start?: string | null; end?: string | null }
  groupBy?: GroupBy | null
  comparison?: Comparison
  limit?: number
}

export type WidgetType =
  | 'TOTAL_SPENDING'
  | 'AVERAGE_DAILY_SPENDING'
  | 'TRANSACTION_COUNT'
  | 'CATEGORY_SPENDING'
  | 'ACCOUNT_SPENDING'
  | 'TOP_CATEGORY'
  | 'TOP_MERCHANT'
  | 'SPENDING_TREND'
  | 'TOP_MERCHANTS'
  | 'BUDGET_PROGRESS'
  | 'RECURRING_EXPENSES'
  | 'SPENDING_ANOMALIES'
  | 'RECENT_TRANSACTIONS'

export type ChartType = 'NUMBER' | 'CHANGE' | 'LINE' | 'BAR' | 'PIE' | 'DONUT' | 'LIST' | 'PROGRESS'

export type WidgetSize = 'SMALL' | 'MEDIUM' | 'LARGE' | 'FULL'

export interface WidgetConfiguration {
  limit?: number
  title?: string
  categoryId?: number
  showLegend?: boolean
  month?: string
}

export interface Widget {
  id: number
  metricType: WidgetType
  metric: Metric | null
  groupBy: GroupBy | null
  period: PeriodType
  comparisonPeriod: Comparison | null
  chartType: ChartType
  position: number
  size: WidgetSize
  configuration: WidgetConfiguration
}

export interface WidgetInput {
  metricType: WidgetType
  groupBy?: GroupBy | null
  period: PeriodType
  comparisonPeriod?: Comparison | null
  chartType: ChartType
  position?: number | null
  size: WidgetSize
  configuration?: WidgetConfiguration
}

export interface BudgetProgress {
  id: number
  categoryId: number
  categoryName: string | null
  month: string
  amount: number
  spent: number
  remaining: number
  percentageUsed: number | null
  exceeded: boolean
  currency: string
}

export interface BudgetInput {
  categoryId: number
  month: string
  amount: number
}

export interface ImportSummary {
  importJobId: number
  rowsProcessed: number
  rowsImported: number
  rowsRejected: number
  duplicatesDetected: number
  errors: { line: number; message: string }[]
  errorsTruncated: boolean
}

export interface ImportJob {
  id: number
  accountId: number
  originalFilename: string
  status: 'PENDING' | 'COMPLETED' | 'FAILED'
  rowsProcessed: number
  rowsImported: number
  rowsRejected: number
  duplicatesDetected: number
  createdAt: string
  completedAt: string | null
}

export type RecurringCadence = 'WEEKLY' | 'FORTNIGHTLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY'
export type RecurringConfidence = 'LOW' | 'MEDIUM' | 'HIGH'

export interface RecurringExpense {
  merchant: string
  categoryId: number | null
  categoryName: string | null
  cadence: RecurringCadence
  typicalAmount: number
  monthlyCost: number
  annualCost: number
  occurrences: number
  firstSeen: string
  lastSeen: string
  nextExpectedDate: string
  confidence: RecurringConfidence
  explanation: string
  currency: string
}

export interface SpendingAnomaly {
  categoryId: number | null
  categoryName: string
  currentAmount: number
  baselineAverage: number
  baselineStandardDeviation: number
  baselineMonths: number
  changeAmount: number
  changePercentage: number
  comparedUpToDay: number
  severity: 'NOTABLE' | 'HIGH'
  explanation: string
}

export interface AuditEvent {
  id: number
  eventType: string
  occurredAt: string
  metadata: Record<string, unknown>
}
