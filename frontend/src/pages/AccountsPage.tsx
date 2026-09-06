import { type FormEvent, useState } from 'react'
import {
  useAccounts,
  useArchiveAccount,
  useCreateAccount,
  useDeleteAccount,
} from '../hooks/queries'
import { Alert, Badge, Card, EmptyState, ErrorState, Field, LoadingState, errorMessage, fieldErrorOf } from '../components/ui'
import { formatDate, humaniseEnum } from '../lib/format'
import type { AccountInput, AccountType } from '../api/types'

const ACCOUNT_TYPES: AccountType[] = ['CHECKING', 'SAVINGS', 'CREDIT_CARD', 'CASH', 'INVESTMENT', 'OTHER']

export default function AccountsPage() {
  const [includeArchived, setIncludeArchived] = useState(false)
  const { data: accounts, isLoading, error, refetch } = useAccounts(includeArchived)
  const createAccount = useCreateAccount()
  const archiveAccount = useArchiveAccount()
  const deleteAccount = useDeleteAccount()

  const [form, setForm] = useState<AccountInput>({ name: '', type: 'CHECKING', currency: 'SGD' })
  const [formError, setFormError] = useState<unknown>(null)
  const [actionError, setActionError] = useState<unknown>(null)

  const handleCreate = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(null)
    try {
      await createAccount.mutateAsync(form)
      setForm({ name: '', type: 'CHECKING', currency: 'SGD' })
    } catch (submitError) {
      setFormError(submitError)
    }
  }

  const handleDelete = async (id: number) => {
    setActionError(null)
    try {
      await deleteAccount.mutateAsync(id)
    } catch (deleteError) {
      // The backend refuses to delete an account that still has transactions,
      // and says so. Surfacing that message is more useful than a generic
      // failure toast.
      setActionError(deleteError)
    }
  }

  return (
    <>
      <header className="page-header">
        <div className="page-header__text">
          <h1>Accounts</h1>
          <p>The bank accounts, cards and wallets your transactions belong to.</p>
        </div>
        <label className="row" style={{ fontSize: '0.9rem' }}>
          <input
            type="checkbox"
            checked={includeArchived}
            onChange={(event) => setIncludeArchived(event.target.checked)}
          />
          Show archived
        </label>
      </header>

      <div className="stack">
        <Card title="Add an account">
          {formError ? <Alert variant="error">{errorMessage(formError)}</Alert> : null}
          <form onSubmit={handleCreate}>
            <div className="filter-bar">
              <Field label="Name" htmlFor="account-name" error={fieldErrorOf(formError, 'name')}>
                <input
                  id="account-name"
                  className="input"
                  required
                  placeholder="e.g. DBS Debit"
                  value={form.name}
                  onChange={(event) => setForm({ ...form, name: event.target.value })}
                />
              </Field>

              <Field label="Type" htmlFor="account-type" error={fieldErrorOf(formError, 'type')}>
                <select
                  id="account-type"
                  className="select"
                  value={form.type}
                  onChange={(event) => setForm({ ...form, type: event.target.value as AccountType })}
                >
                  {ACCOUNT_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {humaniseEnum(type)}
                    </option>
                  ))}
                </select>
              </Field>

              <Field label="Currency" htmlFor="account-currency" error={fieldErrorOf(formError, 'currency')}>
                <input
                  id="account-currency"
                  className="input"
                  required
                  maxLength={3}
                  style={{ textTransform: 'uppercase' }}
                  value={form.currency}
                  onChange={(event) => setForm({ ...form, currency: event.target.value.toUpperCase() })}
                />
              </Field>

              <button type="submit" className="button" disabled={createAccount.isPending}>
                {createAccount.isPending ? 'Creating…' : 'Create account'}
              </button>
            </div>
          </form>
        </Card>

        <Card title="Your accounts">
          {actionError ? <Alert variant="error">{errorMessage(actionError)}</Alert> : null}
          {isLoading ? <LoadingState /> : null}
          {error ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

          {accounts && accounts.length === 0 ? (
            <EmptyState title="No accounts yet" description="Create one above, then import a CSV." />
          ) : null}

          {accounts && accounts.length > 0 ? (
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Name</th>
                    <th scope="col">Type</th>
                    <th scope="col">Currency</th>
                    <th scope="col">Created</th>
                    <th scope="col"><span className="visually-hidden">Actions</span></th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((account) => (
                    <tr key={account.id}>
                      <td>
                        {account.name}{' '}
                        {account.archived ? <Badge>Archived</Badge> : null}
                      </td>
                      <td className="muted">{humaniseEnum(account.type)}</td>
                      <td className="muted">{account.currency}</td>
                      <td className="muted">{formatDate(account.createdAt)}</td>
                      <td>
                        <div className="row" style={{ gap: 'var(--space-1)', flexWrap: 'nowrap' }}>
                          <button
                            type="button"
                            className="button button--ghost button--small"
                            onClick={() =>
                              archiveAccount.mutate({ id: account.id, archived: !account.archived })
                            }
                          >
                            {account.archived ? 'Restore' : 'Archive'}
                          </button>
                          <button
                            type="button"
                            className="button button--ghost button--small"
                            onClick={() => handleDelete(account.id)}
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
          ) : null}

          <p className="subtle" style={{ marginTop: 'var(--space-3)' }}>
            Accounts with transaction history can be archived but not deleted, so your analytics stay
            intact.
          </p>
        </Card>
      </div>
    </>
  )
}
