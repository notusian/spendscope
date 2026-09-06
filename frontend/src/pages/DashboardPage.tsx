import { useState } from 'react'
import {
  useCreateWidget,
  useDeleteWidget,
  useResetDashboard,
  useWidgets,
} from '../hooks/queries'
import WidgetCard from '../components/widgets/WidgetCard'
import { Alert, Card, EmptyState, ErrorState, Field, LoadingState, errorMessage } from '../components/ui'
import type { ChartType, GroupBy, PeriodType, Widget, WidgetInput, WidgetSize, WidgetType } from '../api/types'

/**
 * The customisable dashboard.
 *
 * Layout is persisted per user, so it survives logout and a different device.
 * Reordering is by position field rather than drag-and-drop: the spec calls
 * drag-and-drop optional, and move-up/move-down is keyboard accessible for
 * free while a drag handle is not.
 */

const WIDGET_TYPE_OPTIONS: { value: WidgetType; label: string; groupBy: GroupBy | null; chart: ChartType }[] = [
  { value: 'TOTAL_SPENDING', label: 'Total spending', groupBy: null, chart: 'NUMBER' },
  { value: 'TOTAL_SPENDING', label: 'Spending change', groupBy: null, chart: 'CHANGE' },
  { value: 'AVERAGE_DAILY_SPENDING', label: 'Average daily spending', groupBy: null, chart: 'NUMBER' },
  { value: 'TRANSACTION_COUNT', label: 'Transaction count', groupBy: null, chart: 'NUMBER' },
  { value: 'SPENDING_TREND', label: 'Spending over time', groupBy: 'DAY', chart: 'LINE' },
  { value: 'CATEGORY_SPENDING', label: 'Category breakdown', groupBy: 'CATEGORY', chart: 'DONUT' },
  { value: 'TOP_MERCHANTS', label: 'Top merchants', groupBy: 'MERCHANT', chart: 'LIST' },
  { value: 'ACCOUNT_SPENDING', label: 'Spending by account', groupBy: 'ACCOUNT', chart: 'BAR' },
  { value: 'BUDGET_PROGRESS', label: 'Budget progress', groupBy: null, chart: 'PROGRESS' },
  { value: 'RECURRING_EXPENSES', label: 'Recurring expenses', groupBy: null, chart: 'LIST' },
  { value: 'SPENDING_ANOMALIES', label: 'Unusual spending', groupBy: null, chart: 'LIST' },
  { value: 'RECENT_TRANSACTIONS', label: 'Recent transactions', groupBy: null, chart: 'LIST' },
]

const PERIOD_OPTIONS: { value: PeriodType; label: string }[] = [
  { value: 'THIS_MONTH', label: 'This month' },
  { value: 'LAST_MONTH', label: 'Last month' },
  { value: 'LAST_30_DAYS', label: 'Last 30 days' },
  { value: 'LAST_90_DAYS', label: 'Last 90 days' },
  { value: 'THIS_YEAR', label: 'This year' },
]

const SIZE_OPTIONS: WidgetSize[] = ['SMALL', 'MEDIUM', 'LARGE', 'FULL']

export default function DashboardPage() {
  const { data: widgets, isLoading, error, refetch } = useWidgets()
  const createWidget = useCreateWidget()
  const deleteWidget = useDeleteWidget()
  const resetDashboard = useResetDashboard()

  const [isCustomising, setIsCustomising] = useState(false)
  const [selectedOption, setSelectedOption] = useState(0)
  const [period, setPeriod] = useState<PeriodType>('THIS_MONTH')
  const [size, setSize] = useState<WidgetSize>('MEDIUM')
  const [formError, setFormError] = useState<unknown>(null)

  const handleAdd = async () => {
    const option = WIDGET_TYPE_OPTIONS[selectedOption]
    if (!option) return

    const input: WidgetInput = {
      metricType: option.value,
      groupBy: option.groupBy,
      period,
      // A CHANGE card is meaningless without something to compare against, so
      // the comparison is forced on rather than left to the user to discover.
      comparisonPeriod: option.chart === 'CHANGE' ? 'PREVIOUS_PERIOD' : 'NONE',
      chartType: option.chart,
      size,
      configuration: { title: option.label, ...(option.chart === 'LIST' ? { limit: 6 } : {}) },
    }

    setFormError(null)
    try {
      await createWidget.mutateAsync(input)
    } catch (addError) {
      setFormError(addError)
    }
  }

  const handleRemove = (widget: Widget) => {
    deleteWidget.mutate(widget.id)
  }

  return (
    <>
      <header className="page-header">
        <div className="page-header__text">
          <h1>Dashboard</h1>
          <p>Your spending at a glance. Add, remove and resize cards to suit you.</p>
        </div>
        <div className="row">
          <button
            type="button"
            className="button button--secondary"
            onClick={() => setIsCustomising((current) => !current)}
          >
            {isCustomising ? 'Done' : 'Customise'}
          </button>
        </div>
      </header>

      {isCustomising ? (
        <Card title="Add a widget" className="stack" >
          {formError ? <Alert variant="error">{errorMessage(formError)}</Alert> : null}
          <div className="filter-bar">
            <Field label="Widget" htmlFor="widget-type">
              <select
                id="widget-type"
                className="select"
                value={selectedOption}
                onChange={(event) => setSelectedOption(Number(event.target.value))}
              >
                {WIDGET_TYPE_OPTIONS.map((option, index) => (
                  <option key={`${option.value}-${option.chart}`} value={index}>
                    {option.label}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Period" htmlFor="widget-period">
              <select
                id="widget-period"
                className="select"
                value={period}
                onChange={(event) => setPeriod(event.target.value as PeriodType)}
              >
                {PERIOD_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Size" htmlFor="widget-size">
              <select
                id="widget-size"
                className="select"
                value={size}
                onChange={(event) => setSize(event.target.value as WidgetSize)}
              >
                {SIZE_OPTIONS.map((option) => (
                  <option key={option} value={option}>
                    {option.charAt(0) + option.slice(1).toLowerCase()}
                  </option>
                ))}
              </select>
            </Field>

            <button type="button" className="button" onClick={handleAdd} disabled={createWidget.isPending}>
              {createWidget.isPending ? 'Adding…' : 'Add widget'}
            </button>

            <button
              type="button"
              className="button button--secondary"
              onClick={() => resetDashboard.mutate()}
              disabled={resetDashboard.isPending}
            >
              Reset to defaults
            </button>
          </div>
        </Card>
      ) : null}

      <div style={{ marginTop: 'var(--space-4)' }}>
        {isLoading ? <LoadingState /> : null}
        {error ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

        {widgets && widgets.length === 0 ? (
          <EmptyState
            title="Your dashboard is empty"
            description="Add a widget, or restore the default layout."
            action={
              <button type="button" className="button" onClick={() => resetDashboard.mutate()}>
                Restore defaults
              </button>
            }
          />
        ) : null}

        {widgets && widgets.length > 0 ? (
          <div className="widget-grid">
            {widgets.map((widget) => (
              <WidgetCard
                key={widget.id}
                widget={widget}
                onRemove={isCustomising ? handleRemove : undefined}
              />
            ))}
          </div>
        ) : null}
      </div>
    </>
  )
}
