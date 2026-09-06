import {
  useAnalyticsQuery,
  useAnomalies,
  useBudgets,
  useRecurringExpenses,
  useTransactions,
} from '../../hooks/queries'
import { Badge, Card, EmptyState, ErrorState, LoadingState, ProgressBar } from '../ui'
import {
  RankedList,
  SpendingBarChart,
  SpendingLineChart,
  SpendingPieChart,
} from './WidgetCharts'
import { formatCurrency, formatDate, formatPercentage, formatPercentageChange } from '../../lib/format'
import type { AnalyticsQuery, Widget } from '../../api/types'

/**
 * Renders one dashboard widget.
 *
 * A widget is a saved analytics request. Most types turn straight into a
 * `POST /api/analytics/query` built from the widget's own configuration, which
 * is why adding a widget type needs no new backend endpoint. The four
 * non-analytics types (budgets, recurring, anomalies, recent activity) call
 * their own endpoints instead.
 */
export default function WidgetCard({
  widget,
  onRemove,
}: {
  widget: Widget
  onRemove?: (widget: Widget) => void
}) {
  const title = widget.configuration.title ?? defaultTitleFor(widget)

  const actions = onRemove ? (
    <button
      type="button"
      className="button button--ghost button--small"
      onClick={() => onRemove(widget)}
      aria-label={`Remove ${title} widget`}
      title="Remove widget"
    >
      ✕
    </button>
  ) : null

  return (
    <div className={`widget--${widget.size}`}>
      <Card title={title} actions={actions}>
        <WidgetBody widget={widget} />
      </Card>
    </div>
  )
}

function WidgetBody({ widget }: { widget: Widget }) {
  switch (widget.metricType) {
    case 'BUDGET_PROGRESS':
      return <BudgetProgressWidget />
    case 'RECURRING_EXPENSES':
      return <RecurringWidget limit={widget.configuration.limit ?? 5} />
    case 'SPENDING_ANOMALIES':
      return <AnomaliesWidget />
    case 'RECENT_TRANSACTIONS':
      return <RecentTransactionsWidget limit={widget.configuration.limit ?? 6} />
    default:
      return <AnalyticsWidget widget={widget} />
  }
}

// ------------------------------------------------------------- analytics

function AnalyticsWidget({ widget }: { widget: Widget }) {
  const request = toAnalyticsQuery(widget)
  const { data, isLoading, error, refetch } = useAnalyticsQuery(request, {
    enabled: request !== null,
  })

  if (request === null) {
    return <p className="muted">This widget is not configured correctly.</p>
  }
  if (isLoading) return <LoadingState />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!data) return null

  const { currency, points } = data

  switch (widget.chartType) {
    case 'NUMBER':
      return (
        <div>
          <div className="metric-value">{formatMetricValue(widget, data.total, currency)}</div>
          <div className="metric-caption">
            {data.percentageChange !== null ? (
              <ChangeBadge change={data.percentageChange} />
            ) : (
              <span>{formatPeriodCaption(data.period.start, data.period.end)}</span>
            )}
          </div>
        </div>
      )

    case 'CHANGE':
      return (
        <div>
          <div className="metric-value">{formatPercentageChange(data.percentageChange)}</div>
          <div className="metric-caption">
            {data.previousTotal === null
              ? 'No comparison available'
              : `vs ${formatCurrency(data.previousTotal, currency)} previously`}
          </div>
        </div>
      )

    case 'LINE':
      return points.length === 0 ? (
        <EmptyState title="No spending in this period" />
      ) : (
        <SpendingLineChart points={points} currency={currency} />
      )

    case 'BAR':
      return points.length === 0 ? (
        <EmptyState title="No spending in this period" />
      ) : (
        <SpendingBarChart points={points} currency={currency} />
      )

    case 'PIE':
    case 'DONUT':
      return points.length === 0 ? (
        <EmptyState title="No spending in this period" />
      ) : (
        <SpendingPieChart
          points={points}
          currency={currency}
          donut={widget.chartType === 'DONUT'}
          showLegend={widget.configuration.showLegend ?? true}
        />
      )

    case 'LIST':
      return points.length === 0 ? (
        <EmptyState title="Nothing to show yet" />
      ) : (
        <RankedList points={points} currency={currency} />
      )

    default:
      return <p className="muted">Unsupported chart type.</p>
  }
}

/**
 * Builds the analytics request for a widget.
 *
 * Returns null for a widget whose type has no metric - those are handled by the
 * dedicated components above and should never reach here.
 */
function toAnalyticsQuery(widget: Widget): AnalyticsQuery | null {
  if (!widget.metric) {
    return null
  }
  return {
    metric: widget.metric,
    period: { type: widget.period },
    groupBy: widget.groupBy,
    comparison: widget.comparisonPeriod ?? 'NONE',
    limit: widget.configuration.limit,
  }
}

function formatMetricValue(widget: Widget, total: number, currency: string): string {
  if (widget.metric === 'TRANSACTION_COUNT') {
    return String(Math.round(total))
  }
  return formatCurrency(total, currency)
}

function ChangeBadge({ change }: { change: number }) {
  // Spending going up is not good news, so an increase is styled as negative.
  const variant = change > 0 ? 'negative' : change < 0 ? 'positive' : 'default'
  const arrow = change > 0 ? '↑' : change < 0 ? '↓' : '→'
  return (
    <Badge variant={variant}>
      {arrow} {formatPercentageChange(change)} vs previous
    </Badge>
  )
}

function formatPeriodCaption(start: string, end: string): string {
  return `${formatDate(start)} – ${formatDate(end)}`
}

// --------------------------------------------------------------- budgets

function BudgetProgressWidget() {
  const { data, isLoading, error, refetch } = useBudgets()

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!data || data.length === 0) {
    return <EmptyState title="No budgets yet" description="Set monthly limits on the Budgets page." />
  }

  return (
    <div className="stack stack--tight">
      {data.map((budget) => (
        <div key={budget.id}>
          <div className="row row--between" style={{ fontSize: '0.88rem' }}>
            <span>{budget.categoryName}</span>
            <span className="numeric muted">
              {formatCurrency(budget.spent, budget.currency)} /{' '}
              {formatCurrency(budget.amount, budget.currency)}
            </span>
          </div>
          <ProgressBar percentage={budget.percentageUsed} />
          <div className="subtle">
            {formatPercentage(budget.percentageUsed)}
            {budget.exceeded
              ? ` · over by ${formatCurrency(Math.abs(budget.remaining), budget.currency)}`
              : ` · ${formatCurrency(budget.remaining, budget.currency)} left`}
          </div>
        </div>
      ))}
    </div>
  )
}

// ------------------------------------------------------------- recurring

function RecurringWidget({ limit }: { limit: number }) {
  const { data, isLoading, error, refetch } = useRecurringExpenses()

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!data || data.length === 0) {
    return (
      <EmptyState
        title="No recurring payments detected"
        description="At least three similar payments to the same merchant are needed."
      />
    )
  }

  return (
    <ul className="ranked-list">
      {data.slice(0, limit).map((expense) => (
        <li key={expense.merchant}>
          <span className="ranked-list__label">
            {expense.merchant}{' '}
            <Badge variant={expense.confidence === 'HIGH' ? 'positive' : 'default'}>
              {expense.cadence.toLowerCase()}
            </Badge>
          </span>
          <span className="numeric">
            {formatCurrency(expense.monthlyCost, expense.currency)}
            <span className="muted">/mo</span>
          </span>
        </li>
      ))}
    </ul>
  )
}

// ------------------------------------------------------------- anomalies

function AnomaliesWidget() {
  const { data, isLoading, error, refetch } = useAnomalies()

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!data || data.length === 0) {
    return <EmptyState title="Nothing unusual this month" description="Spending is in line with your normal range." />
  }

  return (
    <div className="stack stack--tight">
      {data.map((anomaly) => (
        <div key={anomaly.categoryName}>
          <div className="row row--between">
            <strong>{anomaly.categoryName}</strong>
            <Badge variant={anomaly.severity === 'HIGH' ? 'negative' : 'warning'}>
              {formatPercentageChange(anomaly.changePercentage)}
            </Badge>
          </div>
          <p className="subtle" style={{ margin: 0 }}>
            {anomaly.explanation}
          </p>
        </div>
      ))}
    </div>
  )
}

// ---------------------------------------------------- recent transactions

function RecentTransactionsWidget({ limit }: { limit: number }) {
  const { data, isLoading, error, refetch } = useTransactions({ page: 0, size: limit, sort: 'date' })

  if (isLoading) return <LoadingState />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (!data || data.content.length === 0) {
    return <EmptyState title="No transactions yet" />
  }

  return (
    <ul className="ranked-list">
      {data.content.map((transaction) => (
        <li key={transaction.id}>
          <span className="ranked-list__label">
            {transaction.merchant ?? transaction.description}
            <span className="subtle"> · {formatDate(transaction.date)}</span>
          </span>
          <span className={`numeric ${transaction.amount > 0 ? 'amount--in' : 'amount--out'}`}>
            {formatCurrency(transaction.amount, transaction.currency)}
          </span>
        </li>
      ))}
    </ul>
  )
}

function defaultTitleFor(widget: Widget): string {
  const words = widget.metricType.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}
