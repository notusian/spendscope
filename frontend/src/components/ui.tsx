import { cloneElement, isValidElement, type ReactNode } from 'react'
import { ApiError } from '../api/client'

/**
 * Small presentational building blocks shared across pages.
 *
 * Kept in one file because each is a handful of lines; splitting them into a
 * file each would add navigation cost without adding clarity.
 */

export function Card({
  title,
  actions,
  children,
  className = '',
}: {
  title?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <header className="card__header">
          {title ? <h3 className="card__title">{title}</h3> : <span />}
          {actions ? <div className="card__actions">{actions}</div> : null}
        </header>
      )}
      {children}
    </section>
  )
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="row" role="status" aria-live="polite">
      <div className="spinner" />
      <span className="visually-hidden">{label}</span>
    </div>
  )
}

/** Loading placeholder with enough height that the layout does not jump. */
export function LoadingState({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="state" role="status" aria-live="polite">
      <div className="row" style={{ justifyContent: 'center' }}>
        <div className="spinner" />
        <span>{label}</span>
      </div>
    </div>
  )
}

/**
 * Every failed request renders through here, so error text is consistent and
 * always comes from the backend's message rather than a raw exception.
 */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="state" role="alert">
      <div className="state__title">Something went wrong</div>
      <p>{errorMessage(error)}</p>
      {onRetry ? (
        <button type="button" className="button button--secondary button--small" onClick={onRetry}>
          Try again
        </button>
      ) : null}
    </div>
  )
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string
  description?: string
  action?: ReactNode
}) {
  return (
    <div className="state">
      <div className="state__title">{title}</div>
      {description ? <p>{description}</p> : null}
      {action}
    </div>
  )
}

export function Alert({
  variant = 'info',
  children,
}: {
  variant?: 'info' | 'error' | 'success' | 'warning'
  children: ReactNode
}) {
  return (
    <div className={`alert alert--${variant}`} role={variant === 'error' ? 'alert' : 'status'}>
      {children}
    </div>
  )
}

export function Field({
  label,
  htmlFor,
  error,
  children,
}: {
  label: string
  htmlFor: string
  error?: string
  children: ReactNode
}) {
  return (
    <div className="field">
      <label className="field__label" htmlFor={htmlFor}>
        {label}
      </label>
      {isValidElement<{ 'aria-describedby'?: string; 'aria-invalid'?: boolean }>(children)
        ? cloneElement(children, {
            'aria-describedby': [children.props['aria-describedby'], error ? `${htmlFor}-error` : undefined].filter(Boolean).join(' ') || undefined,
            'aria-invalid': error ? true : children.props['aria-invalid'],
          })
        : children}
      {error ? (
        <span id={`${htmlFor}-error`} className="field__error" role="alert">
          {error}
        </span>
      ) : null}
    </div>
  )
}

export function Badge({
  children,
  variant = 'default',
}: {
  children: ReactNode
  variant?: 'default' | 'positive' | 'negative' | 'warning' | 'accent'
}) {
  const suffix = variant === 'default' ? '' : ` badge--${variant}`
  return <span className={`badge${suffix}`}>{children}</span>
}

/**
 * A budget/limit bar.
 *
 * The fill is capped at 100% width so an overspend does not overflow its
 * container, but the colour changes so the overspend is still obvious.
 */
export function ProgressBar({ percentage }: { percentage: number | null }) {
  const value = percentage !== null && Number.isFinite(percentage) ? percentage : 0
  const width = Math.min(Math.max(value, 0), 100)
  const modifier = value > 100 ? ' progress__fill--over' : value >= 80 ? ' progress__fill--near' : ''
  return (
    <div
      className="progress"
      role="progressbar"
      aria-label="Budget used"
      aria-valuenow={Math.round(width)}
      aria-valuetext={percentage === null ? 'No budget usage available' : `${Math.round(value)}% of budget used`}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <div className={`progress__fill${modifier}`} style={{ width: `${width}%` }} />
    </div>
  )
}

/** Turns any thrown value into a message safe to show a user. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'An unexpected error occurred.'
}

/** Pulls a field-level validation message out of an ApiError, if present. */
export function fieldErrorOf(error: unknown, field: string): string | undefined {
  return error instanceof ApiError ? error.fieldError(field) : undefined
}
