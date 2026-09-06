import { type FormEvent, useState } from 'react'
import {
  useAccounts,
  useCategories,
  useCreateTransaction,
  useDeleteTransaction,
  useRecategoriseTransaction,
  useTransactions,
  useUpdateTransaction,
} from '../hooks/queries'
import {
  Alert,
  Card,
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  errorMessage,
  fieldErrorOf,
} from '../components/ui'
import { formatCurrency, formatDate, todayIsoDate } from '../lib/format'
import type { Transaction, TransactionFilters, TransactionInput } from '../api/types'

const PAGE_SIZE = 25

const EMPTY_FORM: TransactionInput = {
  accountId: 0,
  categoryId: null,
  date: todayIsoDate(),
  description: '',
  amount: 0,
}

export default function TransactionsPage() {
  const [filters, setFilters] = useState<TransactionFilters>({
    page: 0,
    size: PAGE_SIZE,
    sort: 'date',
    direction: 'desc',
  })
  const [editing, setEditing] = useState<Transaction | null>(null)
  const [isFormOpen, setIsFormOpen] = useState(false)

  const { data: accounts } = useAccounts()
  const { data: categories } = useCategories()
  const { data, isLoading, error, refetch } = useTransactions(filters)
  const recategorise = useRecategoriseTransaction()
  const deleteTransaction = useDeleteTransaction()

  const updateFilter = (patch: Partial<TransactionFilters>) => {
    // Any filter change resets to the first page: staying on page 7 of a
    // narrower result set shows an empty table and looks like a bug.
    setFilters((current) => ({ ...current, ...patch, page: 0 }))
  }

  const openCreate = () => {
    setEditing(null)
    setIsFormOpen(true)
  }

  const openEdit = (transaction: Transaction) => {
    setEditing(transaction)
    setIsFormOpen(true)
  }

  return (
    <>
      <header className="page-header">
        <div className="page-header__text">
          <h1>Transactions</h1>
          <p>Search, filter and correct your spending history.</p>
        </div>
        <button type="button" className="button" onClick={openCreate}>
          Add transaction
        </button>
      </header>

      {isFormOpen ? (
        <TransactionForm
          transaction={editing}
          onClose={() => setIsFormOpen(false)}
        />
      ) : null}

      <Card>
        <div className="filter-bar">
          <Field label="Search" htmlFor="filter-search">
            <input
              id="filter-search"
              className="input"
              type="search"
              placeholder="Merchant or description"
              defaultValue={filters.search ?? ''}
              onChange={(event) => updateFilter({ search: event.target.value || undefined })}
            />
          </Field>

          <Field label="From" htmlFor="filter-from">
            <input
              id="filter-from"
              className="input"
              type="date"
              value={filters.from ?? ''}
              onChange={(event) => updateFilter({ from: event.target.value || undefined })}
            />
          </Field>

          <Field label="To" htmlFor="filter-to">
            <input
              id="filter-to"
              className="input"
              type="date"
              value={filters.to ?? ''}
              onChange={(event) => updateFilter({ to: event.target.value || undefined })}
            />
          </Field>

          <Field label="Account" htmlFor="filter-account">
            <select
              id="filter-account"
              className="select"
              value={filters.accountId ?? ''}
              onChange={(event) =>
                updateFilter({ accountId: event.target.value ? Number(event.target.value) : undefined })
              }
            >
              <option value="">All accounts</option>
              {accounts?.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Category" htmlFor="filter-category">
            <select
              id="filter-category"
              className="select"
              value={filters.categoryId ?? ''}
              onChange={(event) =>
                updateFilter({ categoryId: event.target.value ? Number(event.target.value) : undefined })
              }
            >
              <option value="">All categories</option>
              {categories?.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </Field>

          <button
            type="button"
            className="button button--secondary"
            onClick={() => setFilters({ page: 0, size: PAGE_SIZE, sort: 'date', direction: 'desc' })}
          >
            Clear
          </button>
        </div>

        {isLoading ? <LoadingState /> : null}
        {error ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

        {data && data.content.length === 0 ? (
          <EmptyState
            title="No transactions found"
            description="Try widening your filters, or import a CSV to get started."
          />
        ) : null}

        {data && data.content.length > 0 ? (
          <>
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Date</th>
                    <th scope="col">Description</th>
                    <th scope="col">Merchant</th>
                    <th scope="col">Category</th>
                    <th scope="col">Account</th>
                    <th scope="col" className="numeric">Amount</th>
                    <th scope="col"><span className="visually-hidden">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((transaction) => (
                    <tr key={transaction.id}>
                      <td>{formatDate(transaction.date)}</td>
                      <td>{transaction.description}</td>
                      <td className="muted">{transaction.merchant ?? '—'}</td>
                      <td>
                        <label className="visually-hidden" htmlFor={`category-${transaction.id}`}>
                          Category for {transaction.description}
                        </label>
                        <select
                          id={`category-${transaction.id}`}
                          className="select"
                          style={{ minWidth: 140 }}
                          value={transaction.categoryId ?? ''}
                          onChange={(event) =>
                            recategorise.mutate({
                              id: transaction.id,
                              categoryId: event.target.value ? Number(event.target.value) : null,
                            })
                          }
                        >
                          <option value="">Uncategorised</option>
                          {categories?.map((category) => (
                            <option key={category.id} value={category.id}>
                              {category.name}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="muted">{transaction.accountName ?? '—'}</td>
                      <td className={`numeric ${transaction.amount > 0 ? 'amount--in' : 'amount--out'}`}>
                        {formatCurrency(transaction.amount, transaction.currency)}
                      </td>
                      <td>
                        <div className="row" style={{ gap: 'var(--space-1)', flexWrap: 'nowrap' }}>
                          <button
                            type="button"
                            className="button button--ghost button--small"
                            onClick={() => openEdit(transaction)}
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            className="button button--ghost button--small"
                            onClick={() => deleteTransaction.mutate(transaction.id)}
                            aria-label={`Delete ${transaction.description}`}
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="row row--between" style={{ marginTop: 'var(--space-3)' }}>
              <span className="subtle">
                Showing {data.content.length} of {data.totalElements} · page {data.page + 1} of{' '}
                {Math.max(data.totalPages, 1)}
              </span>
              <div className="row">
                <button
                  type="button"
                  className="button button--secondary button--small"
                  disabled={data.page === 0}
                  onClick={() => setFilters((current) => ({ ...current, page: (current.page ?? 0) - 1 }))}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className="button button--secondary button--small"
                  disabled={!data.hasNext}
                  onClick={() => setFilters((current) => ({ ...current, page: (current.page ?? 0) + 1 }))}
                >
                  Next
                </button>
              </div>
            </div>
          </>
        ) : null}
      </Card>
    </>
  )
}

function TransactionForm({
  transaction,
  onClose,
}: {
  transaction: Transaction | null
  onClose: () => void
}) {
  const { data: accounts } = useAccounts()
  const { data: categories } = useCategories()
  const createTransaction = useCreateTransaction()
  const updateTransaction = useUpdateTransaction()

  const [form, setForm] = useState<TransactionInput>(() =>
    transaction
      ? {
          accountId: transaction.accountId,
          categoryId: transaction.categoryId,
          date: transaction.date,
          description: transaction.description,
          amount: transaction.amount,
          merchant: transaction.merchant,
        }
      : { ...EMPTY_FORM, accountId: accounts?.[0]?.id ?? 0 },
  )
  const [error, setError] = useState<unknown>(null)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      if (transaction) {
        await updateTransaction.mutateAsync({ id: transaction.id, input: form })
      } else {
        await createTransaction.mutateAsync(form)
      }
      onClose()
    } catch (submitError) {
      setError(submitError)
    }
  }

  const isPending = createTransaction.isPending || updateTransaction.isPending

  return (
    <Card title={transaction ? 'Edit transaction' : 'New transaction'} className="stack">
      {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
      <form onSubmit={handleSubmit} noValidate>
        <div className="filter-bar">
          <Field label="Date" htmlFor="form-date" error={fieldErrorOf(error, 'date')}>
            <input
              id="form-date"
              className="input"
              type="date"
              required
              value={form.date}
              onChange={(event) => setForm({ ...form, date: event.target.value })}
            />
          </Field>

          <Field label="Description" htmlFor="form-description" error={fieldErrorOf(error, 'description')}>
            <input
              id="form-description"
              className="input"
              required
              placeholder="e.g. STARBUCKS"
              value={form.description}
              onChange={(event) => setForm({ ...form, description: event.target.value })}
            />
          </Field>

          <Field label="Amount" htmlFor="form-amount" error={fieldErrorOf(error, 'amount')}>
            <input
              id="form-amount"
              className="input"
              type="number"
              step="0.01"
              required
              placeholder="-12.98"
              value={form.amount === 0 ? '' : form.amount}
              onChange={(event) => setForm({ ...form, amount: Number(event.target.value) })}
            />
          </Field>

          <Field label="Account" htmlFor="form-account" error={fieldErrorOf(error, 'accountId')}>
            <select
              id="form-account"
              className="select"
              required
              value={form.accountId || ''}
              onChange={(event) => setForm({ ...form, accountId: Number(event.target.value) })}
            >
              <option value="">Select an account</option>
              {accounts?.map((account) => (
                <option key={account.id} value={account.id}>
                  {account.name}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Category" htmlFor="form-category">
            <select
              id="form-category"
              className="select"
              value={form.categoryId ?? ''}
              onChange={(event) =>
                setForm({ ...form, categoryId: event.target.value ? Number(event.target.value) : null })
              }
            >
              <option value="">Detect automatically</option>
              {categories?.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </Field>
        </div>

        <p className="subtle">Use a negative amount for spending and a positive amount for income.</p>

        <div className="row">
          <button type="submit" className="button" disabled={isPending}>
            {isPending ? 'Saving…' : transaction ? 'Save changes' : 'Add transaction'}
          </button>
          <button type="button" className="button button--secondary" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Card>
  )
}
