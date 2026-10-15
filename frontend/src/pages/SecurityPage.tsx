import { useState } from 'react'
import { useSecurityEvents } from '../hooks/queries'
import { Badge, Card, EmptyState, ErrorState, LoadingState } from '../components/ui'
import { formatDateTime, humaniseEventType } from '../lib/format'

/** Event types worth drawing attention to when scanning the list. */
const NOTABLE_EVENTS = new Set(['LOGIN_FAILURE', 'LOGIN_BLOCKED', 'PASSWORD_CHANGED'])

export default function SecurityPage() {
  const [page, setPage] = useState(0)
  const { data, isLoading, error, refetch } = useSecurityEvents(page)

  return (
    <>
      <header className="page-header">
        <div className="page-header__text">
          <h1>Security activity</h1>
        </div>
      </header>

      <Card>
        {isLoading ? <LoadingState /> : null}
        {error ? <ErrorState error={error} onRetry={() => void refetch()} /> : null}

        {data && data.content.length === 0 ? <EmptyState title="No activity recorded yet" /> : null}

        {data && data.content.length > 0 ? (
          <>
            <div className="table-wrapper">
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Event</th>
                    <th scope="col">When</th>
                    <th scope="col">Details</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((event) => (
                    <tr key={event.id}>
                      <td>
                        {NOTABLE_EVENTS.has(event.eventType) ? (
                          <Badge variant="warning">{humaniseEventType(event.eventType)}</Badge>
                        ) : (
                          humaniseEventType(event.eventType)
                        )}
                      </td>
                      <td className="muted">{formatDateTime(event.occurredAt)}</td>
                      <td className="muted">
                        {Object.keys(event.metadata).length === 0
                          ? '—'
                          : Object.entries(event.metadata)
                              .map(([key, value]) => `${key}: ${String(value)}`)
                              .join(', ')}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="row row--between" style={{ marginTop: 'var(--space-3)' }}>
              <span className="subtle">
                {data.totalElements} events · page {data.page + 1} of {Math.max(data.totalPages, 1)}
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
                  disabled={!data.hasNext}
                  onClick={() => setPage((current) => current + 1)}
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
