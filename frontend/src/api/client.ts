import type { ApiErrorBody, ErrorCode } from './types'

/**
 * The single place the browser talks to the API.
 *
 * Responsibilities:
 *  - send the session cookie (`credentials: 'include'`)
 *  - attach the CSRF header on state-changing requests
 *  - turn a non-2xx response into a typed `ApiError`
 *  - notify the app when the session has expired
 *
 * Everything else in the app calls the typed helpers in `endpoints.ts`, never
 * `fetch` directly, so these guarantees hold everywhere.
 */

const API_BASE = '/api'

/** Thrown for every non-2xx response. Carries the backend's error contract. */
export class ApiError extends Error {
  readonly status: number
  readonly code: ErrorCode
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: ErrorCode, message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors ?? {}
  }

  /** True when the user needs to log in again. */
  get isUnauthenticated(): boolean {
    return this.status === 401
  }

  /** A message for a specific form field, if the backend supplied one. */
  fieldError(field: string): string | undefined {
    return this.fieldErrors[field]
  }
}

/**
 * Listeners notified when the API reports the session has expired.
 *
 * A plain callback list rather than a store: exactly one thing subscribes (the
 * auth provider), and it needs to react from outside React's render cycle.
 */
type SessionExpiredListener = () => void
const sessionExpiredListeners = new Set<SessionExpiredListener>()

export function onSessionExpired(listener: SessionExpiredListener): () => void {
  sessionExpiredListeners.add(listener)
  return () => sessionExpiredListeners.delete(listener)
}

/**
 * Reads the CSRF token Spring Security writes into a JS-readable cookie.
 *
 * The session cookie itself is HttpOnly and unreadable here, which is the
 * point: a script that could read the session id would make XSS catastrophic
 * rather than merely bad.
 */
function readCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match?.[1] ? decodeURIComponent(match[1]) : null
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

interface RequestOptions {
  method?: string
  body?: unknown
  /** Sent as multipart/form-data; the browser sets the boundary itself. */
  formData?: FormData
  signal?: AbortSignal
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }

  if (!SAFE_METHODS.has(method)) {
    const csrfToken = readCsrfToken()
    if (csrfToken) {
      headers['X-XSRF-TOKEN'] = csrfToken
    }
  }

  let body: BodyInit | undefined
  if (options.formData) {
    // Deliberately no Content-Type: setting it manually would omit the
    // multipart boundary and the server could not parse the upload.
    body = options.formData
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body,
    // Without this the browser would not send the session cookie at all.
    credentials: 'include',
    signal: options.signal,
  })

  if (response.status === 204 || response.status === 205) {
    return undefined as T
  }

  if (!response.ok) {
    throw await toApiError(response)
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    return undefined as T
  }
  return (await response.json()) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: Partial<ApiErrorBody> = {}
  try {
    body = (await response.json()) as Partial<ApiErrorBody>
  } catch {
    // A proxy or gateway can return a non-JSON error page; fall through to the
    // generic message below rather than throwing a parse error over the top of
    // the real failure.
  }

  const error = new ApiError(
    response.status,
    body.code ?? 'INTERNAL_ERROR',
    body.message ?? defaultMessageFor(response.status),
    body.fieldErrors,
  )

  if (error.isUnauthenticated) {
    sessionExpiredListeners.forEach((listener) => listener())
  }
  return error
}

function defaultMessageFor(status: number): string {
  if (status === 401) return 'Your session has expired. Please log in again.'
  if (status === 403) return 'You do not have access to that.'
  if (status === 404) return 'That was not found.'
  if (status >= 500) return 'Something went wrong on our side. Please try again.'
  return 'The request could not be completed.'
}

/** Builds a query string, dropping undefined/null/empty values. */
export function queryString(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  })
  const rendered = search.toString()
  return rendered ? `?${rendered}` : ''
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) => request<T>(path, { signal }),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, formData: FormData) => request<T>(path, { method: 'POST', formData }),
}
