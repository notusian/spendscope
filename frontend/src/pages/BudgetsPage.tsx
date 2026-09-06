import { type FormEvent, useState } from 'react'
import { useBudgets, useCategories, useCreateBudget, useDeleteBudget } from '../hooks/queries'
import {
  Alert,
  Badge,
  Card,
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  ProgressBar,
  errorMessage,
  fieldErrorOf,
} from '../components/ui'
import { currentYearMonth, formatCurrency, formatMonth, formatPercentage } from '../lib/format'

export default function BudgetsPage() {
  const [month, setMonth] = useState(currentYearMonth())
  const { data: budgets, isLoading, error, refetch } = useBudgets(month)
  const { data: categories } = useCategories()
  const createBudget = useCreateBudget()
  const deleteBudget = useDeleteBudget()

  const [categoryId, setCategoryId] = useState('')
  const [amount, setAmount] = useState('')
  const [formError, setFormError] = useState<unknown>(null)

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(null)
    try {
      await createBudget.mutateAsync({
        categoryId: Number(categoryId),
        month,
        amount: Number(amount),
      })
      setCategoryId('')
      setAmount('')
    } catch (submitError) {
      setFormError(submitError)
    }
  }

  const expenseCategories = categories?.filter((category) => category.type === 'EXPENSE') ?? []

  return (
    <>
      <header className="page-header">
        <div className="page-header__text">
          <h1>Budgets</h1>
          <p>Monthly limits per category. Spending is always calculated from your transactions.</p>
        </div>
        <Field label="Month" htmlFor="budget-month">
          <input
            id="budget-month"
            className="input"
            type="month"
            value={month}
            onChange={(event) => setMonth(event.target.value)}
          />
        </Field>
      </header>

      <div className="stack">
        <Card title={`Add a budget for ${formatMonth(month)}`}>
          {formError ? <Alert variant="error">{errorMessage(formError)}</Alert> : null}
          <form onSubmit={handleCreate}>
            <div className="filter-bar">
              <Field label="Category" htmlFor="budget-category" error={fieldErrorOf(formError, 'categoryId')}>
                <select
                  id="budget-category"
                  className="select"
                  required
                  value={categoryId}
                  onChange={(event) => setCategoryId(event.target.value)}
                >
                  <option value="">Select a category</option>
                  {expenseCategories.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
              </Field>

              <Field label="Monthly limit" htmlFor="budget-amount" error={fieldErrorOf(formError, 'amount')}>
                <input
                  id="budget-amount"
                  className="input"
                  type="number"
                  step="0.01"
                  min="0.01"
                  required
                  placeholder="500.00"
                  value={amount}
                  onChange={(event) => setAmount(event.target.value)}
                />
              </Field>

              <button type="submit" className="button" disabled={createBudget.isPending}>
                {createBudget.isPending ? 'Saving…' : 'Set budget'}
              </button>
            </div>
          </form>
        </Card>

        {isLoading ? <LoadingState /> : null}
        {error ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

        {budgets && budgets.length === 0 ? (
          <EmptyState
            title={`No budgets for ${formatMonth(month)}`}
            description="Add one above to start tracking a category against a limit."
          />
        ) : null}

        {budgets && budgets.length > 0 ? (
          <div className="widget-grid">
            {budgets.map((budget) => (
              <div key={budget.id} className="widget--MEDIUM">
                <Card
                  title={budget.categoryName ?? 'Category'}
                  actions={
                    <button
                      type="button"
                      className="button button--ghost button--small"
                      onClick={() => deleteBudget.mutate(budget.id)}
                      aria-label={`Delete ${budget.categoryName} budget`}
                    >
                      ✕
                    </button>
                  }
                >
                  <div className="row row--between" style={{ marginBottom: 'var(--space-2)' }}>
                    <span className="metric-value" style={{ fontSize: '1.4rem' }}>
                      {formatCurrency(budget.spent, budget.currency)}
                    </span>
                    <span className="muted">of {formatCurrency(budget.amount, budget.currency)}</span>
                  </div>

                  <ProgressBar percentage={budget.percentageUsed} />

                  <div className="row row--between" style={{ marginTop: 'var(--space-2)' }}>
                    <span className="subtle">{formatPercentage(budget.percentageUsed)} used</span>
                    {budget.exceeded ? (
                      <Badge variant="negative">
                        Over by {formatCurrency(Math.abs(budget.remaining), budget.currency)}
                      </Badge>
                    ) : (
                      <Badge variant="positive">
                        {formatCurrency(budget.remaining, budget.currency)} left
                      </Badge>
                    )}
                  </div>
                </Card>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </>
  )
}
