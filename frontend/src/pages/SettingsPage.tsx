import { type FormEvent, useState } from 'react'
import { useCategories, useCreateCategory, useDeleteCategory, useRecurringExpenses } from '../hooks/queries'
import { useAuth } from '../hooks/useAuth'
import { authApi } from '../api/endpoints'
import {
  Alert,
  Badge,
  Card,
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  errorMessage,
  fieldErrorOf,
} from '../components/ui'
import { formatCurrency, formatDate } from '../lib/format'
import ThemeSelect from '../components/ThemeSelect'

export default function SettingsPage() {
  const { user } = useAuth()

  return (
    <>
      <header className="page-header">
        <div className="page-header__text">
          <h1>Settings</h1>
        </div>
      </header>

      <div className="stack">
        <Card title="Appearance"><ThemeSelect /></Card>
        <Card title="Account">
          <div className="row row--between">
            <div>
              <div style={{ fontWeight: 550 }}>{user?.email}</div>
              <span className="subtle">Member since {formatDate(user?.createdAt ?? null)}</span>
            </div>
            {user?.demo ? <Badge variant="accent">Demo account</Badge> : null}
          </div>
          {user?.demo ? (
            <Alert variant="info">
              This is a temporary demo account filled with synthetic data. It is deleted
              automatically, so do not add anything you want to keep.
            </Alert>
          ) : null}
        </Card>

        {user?.demo ? null : <ChangePasswordCard />}
        <CategoriesCard />
        <RecurringCard />
      </div>
    </>
  )
}

function ChangePasswordCard() {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [success, setSuccess] = useState(false)
  const [isPending, setIsPending] = useState(false)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSuccess(false)
    setIsPending(true)
    try {
      await authApi.changePassword(currentPassword, newPassword)
      setSuccess(true)
      setCurrentPassword('')
      setNewPassword('')
    } catch (submitError) {
      setError(submitError)
    } finally {
      setIsPending(false)
    }
  }

  return (
    <Card title="Change password">
      {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
      {success ? <Alert variant="success">Your password has been changed.</Alert> : null}

      <form onSubmit={handleSubmit} noValidate>
        <div className="filter-bar">
          <Field
            label="Current password"
            htmlFor="current-password"
            error={fieldErrorOf(error, 'currentPassword')}
          >
            <input
              id="current-password"
              className="input"
              type="password"
              autoComplete="current-password"
              required
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
            />
          </Field>

          <Field label="New password" htmlFor="new-password" error={fieldErrorOf(error, 'newPassword')}>
            <input
              id="new-password"
              className="input"
              type="password"
              autoComplete="new-password"
              required
              minLength={10}
              value={newPassword}
              onChange={(event) => setNewPassword(event.target.value)}
            />
          </Field>

          <button type="submit" className="button" disabled={isPending}>
            {isPending ? 'Saving…' : 'Change password'}
          </button>
        </div>
      </form>
    </Card>
  )
}

function CategoriesCard() {
  const { data: categories, isLoading, error, refetch } = useCategories()
  const createCategory = useCreateCategory()
  const deleteCategory = useDeleteCategory()

  const [name, setName] = useState('')
  const [formError, setFormError] = useState<unknown>(null)

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(null)
    try {
      await createCategory.mutateAsync({ name, type: 'EXPENSE' })
      setName('')
    } catch (submitError) {
      setFormError(submitError)
    }
  }

  return (
    <Card title="Categories">
      {formError ? <Alert variant="error">{errorMessage(formError)}</Alert> : null}
      {deleteCategory.error ? <Alert variant="error">{errorMessage(deleteCategory.error)}</Alert> : null}

      <form onSubmit={handleCreate}>
        <div className="filter-bar">
          <Field label="New category" htmlFor="category-name" error={fieldErrorOf(formError, 'name')}>
            <input
              id="category-name"
              className="input"
              required
              placeholder="e.g. Pets"
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
          </Field>
          <button type="submit" className="button" disabled={createCategory.isPending}>
            Add category
          </button>
        </div>
      </form>

      {isLoading ? <LoadingState /> : null}
      {error ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

      {categories ? (
        <ul className="ranked-list">
          {categories.map((category) => (
            <li key={category.id}>
              <span className="ranked-list__label">
                {category.name}{' '}
                {category.builtIn ? <Badge>built-in</Badge> : null}
              </span>
              {!category.builtIn && (
                <button
                  type="button"
                  className="button button--ghost button--small"
                  disabled={deleteCategory.isPending}
                  aria-label={`Delete ${category.name}`}
                  onClick={() => deleteCategory.mutate(category.id)}
                >
                  Delete
                </button>
              )}
            </li>
          ))}
        </ul>
      ) : null}

      <p className="subtle" style={{ marginTop: 'var(--space-3)' }}>
        Deleting a custom category keeps its transactions and removes their category assignment.
      </p>
    </Card>
  )
}

function RecurringCard() {
  const { data, isLoading, error, refetch } = useRecurringExpenses()

  return (
    <Card title="Detected subscriptions">
      {isLoading ? <LoadingState /> : null}
      {error ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

      {data && data.length === 0 ? (
        <EmptyState
          title="Nothing detected yet"
          description="Three or more similar payments to the same merchant are needed before a pattern is called recurring."
        />
      ) : null}

      {data && data.length > 0 ? (
        <div className="table-wrapper">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Merchant</th>
                <th scope="col">Cadence</th>
                <th scope="col" className="numeric">Typical</th>
                <th scope="col" className="numeric">Per month</th>
                <th scope="col" className="numeric">Per year</th>
                <th scope="col">Next expected</th>
                <th scope="col">Confidence</th>
              </tr>
            </thead>
            <tbody>
              {data.map((expense) => (
                <tr key={`${expense.merchant}-${expense.currency}`}>
                  <td><details><summary>{expense.merchant}</summary><p className="subtle">{expense.explanation}</p></details></td>
                  <td className="muted">{expense.cadence.toLowerCase()}</td>
                  <td className="numeric">{formatCurrency(expense.typicalAmount, expense.currency)}</td>
                  <td className="numeric">{formatCurrency(expense.monthlyCost, expense.currency)}</td>
                  <td className="numeric">{formatCurrency(expense.annualCost, expense.currency)}</td>
                  <td className="muted">{formatDate(expense.nextExpectedDate)}</td>
                  <td>
                    <Badge variant={expense.confidence === 'HIGH' ? 'positive' : 'default'}>
                      {expense.confidence.toLowerCase()}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}

      <p className="subtle" style={{ marginTop: 'var(--space-3)' }}>
        These estimates come from repeated payments in your history. Select a merchant to see the reasoning.
      </p>
    </Card>
  )
}
