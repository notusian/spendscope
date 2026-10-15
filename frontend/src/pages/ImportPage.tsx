import { type FormEvent, useRef, useState } from 'react'
import { useAccounts, useImportCsv, useImportJobs } from '../hooks/queries'
import { Alert, Badge, Card, EmptyState, ErrorState, Field, LoadingState, errorMessage } from '../components/ui'
import { formatDateTime } from '../lib/format'
import type { ImportSummary } from '../api/types'

/** Mirrors spendscope.import.max-file-size; the server enforces the real limit. */
const MAX_FILE_SIZE_MB = 5

export default function ImportPage() {
  const [page, setPage] = useState(0)
  const { data: accounts } = useAccounts()
  const { data: jobs, isLoading, error, refetch } = useImportJobs(page)
  const importCsv = useImportCsv()
  const fileInputRef = useRef<HTMLInputElement>(null)

  const [accountId, setAccountId] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [summary, setSummary] = useState<ImportSummary | null>(null)
  const [uploadError, setUploadError] = useState<unknown>(null)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setUploadError(null)
    setSummary(null)

    if (!file || !accountId) {
      setUploadError(new Error('Choose an account and a CSV file.'))
      return
    }
    if (file.size === 0 || file.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
      setUploadError(new Error(`Choose a non-empty CSV file no larger than ${MAX_FILE_SIZE_MB} MB.`))
      return
    }

    try {
      const result = await importCsv.mutateAsync({ accountId: Number(accountId), file })
      setSummary(result)
      setPage(0)
      setFile(null)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    } catch (importError) {
      setUploadError(importError)
    }
  }

  return (
    <>
      <header className="page-header">
        <div className="page-header__text">
          <h1>Import transactions</h1>
        </div>
      </header>

      <div className="stack">
        <Card title="Upload a CSV">
          {uploadError ? <Alert variant="error">{errorMessage(uploadError)}</Alert> : null}

          <form onSubmit={handleSubmit}>
            <div className="filter-bar">
              <Field label="Import into" htmlFor="import-account">
                <select
                  id="import-account"
                  className="select"
                  required
                  disabled={importCsv.isPending}
                  value={accountId}
                  onChange={(event) => setAccountId(event.target.value)}
                >
                  <option value="">Select an account</option>
                  {accounts?.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name}
                    </option>
                  ))}
                </select>
              </Field>

              <Field label="CSV file" htmlFor="import-file">
                <input
                  id="import-file"
                  ref={fileInputRef}
                  className="input"
                  type="file"
                  accept=".csv,text/csv,text/plain"
                  required
                  disabled={importCsv.isPending}
                  onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                />
              </Field>

              <button type="submit" className="button" disabled={importCsv.isPending}>
                {importCsv.isPending ? 'Importing…' : 'Import'}
              </button>
            </div>
          </form>

          <details style={{ marginTop: 'var(--space-3)' }}>
            <summary className="subtle" style={{ cursor: 'pointer' }}>
              Expected format
            </summary>
            <pre
              style={{
                background: 'var(--surface-sunken)',
                padding: 'var(--space-3)',
                borderRadius: 'var(--radius-sm)',
                overflowX: 'auto',
                fontSize: '0.85rem',
              }}
            >
{`date,description,amount
2026-08-01,STARBUCKS,-7.20
2026-08-02,GRAB,-14.50
2026-08-03,SPOTIFY,-12.98`}
            </pre>
            <p className="subtle">
              Negative amounts record spending, and positive amounts record income. Dates may use
              YYYY-MM-DD or DD/MM/YYYY. Files can contain up to {MAX_FILE_SIZE_MB} MB and 10,000 rows;
              valid rows are imported while duplicates and invalid rows are skipped.
            </p>
          </details>
        </Card>

        {summary ? <ImportSummaryCard summary={summary} /> : null}

        <Card title="Past imports">
          {isLoading ? <LoadingState /> : null}
          {error ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

          {jobs && jobs.content.length === 0 ? (
            <EmptyState title="No imports yet" />
          ) : null}

          {jobs && jobs.totalPages > 1 ? (
            <div className="row row--between" style={{ marginTop: 'var(--space-3)' }}>
              <span className="subtle">
                Page {jobs.page + 1} of {jobs.totalPages}
              </span>
              <div className="row">
                <button
                  type="button"
                  className="button button--secondary button--small"
                  disabled={page === 0}
                  onClick={() => setPage((current) => current - 1)}
                >
                  Previous
                </button>
                <button
                  type="button"
                  className="button button--secondary button--small"
                  disabled={!jobs.hasNext}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Next
                </button>
              </div>
            </div>
          ) : null}

          {jobs && jobs.content.length > 0 ? (
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">File</th>
                    <th scope="col">Status</th>
                    <th scope="col" className="numeric">Processed</th>
                    <th scope="col" className="numeric">Imported</th>
                    <th scope="col" className="numeric">Rejected</th>
                    <th scope="col" className="numeric">Duplicates</th>
                    <th scope="col">When</th>
                  </tr>
                </thead>
                <tbody>
                  {jobs.content.map((job) => (
                    <tr key={job.id}>
                      <td>{job.originalFilename}</td>
                      <td>
                        <Badge variant={job.status === 'COMPLETED' ? 'positive' : job.status === 'FAILED' ? 'negative' : 'default'}>
                          {job.status.toLowerCase()}
                        </Badge>
                      </td>
                      <td className="numeric">{job.rowsProcessed}</td>
                      <td className="numeric">{job.rowsImported}</td>
                      <td className="numeric">{job.rowsRejected}</td>
                      <td className="numeric">{job.duplicatesDetected}</td>
                      <td className="muted">{formatDateTime(job.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </Card>
      </div>
    </>
  )
}

function ImportSummaryCard({ summary }: { summary: ImportSummary }) {
  const hasProblems = summary.rowsRejected > 0 || summary.duplicatesDetected > 0

  return (
    <Card title="Import result">
      <Alert variant={summary.rowsImported > 0 ? 'success' : hasProblems ? 'warning' : 'info'}>
        The import added {summary.rowsImported} of {summary.rowsProcessed} rows.
        {summary.duplicatesDetected > 0
          ? ` ${summary.duplicatesDetected} were already in your history and were skipped.`
          : ''}
        {summary.rowsRejected > 0 ? ` ${summary.rowsRejected} failed validation.` : ''}
      </Alert>

      {summary.errors.length > 0 ? (
        <>
          <h3 style={{ marginBottom: 'var(--space-2)' }}>Rows that could not be imported</h3>
          <ul className="ranked-list">
            {summary.errors.map((rowError) => (
              <li key={`${rowError.line}-${rowError.message}`}>
                <span className="ranked-list__label">Line {rowError.line}</span>
                <span className="muted">{rowError.message}</span>
              </li>
            ))}
          </ul>
          {summary.errorsTruncated ? (
            <p className="subtle">More rows failed than are listed here.</p>
          ) : null}
        </>
      ) : null}
    </Card>
  )
}
