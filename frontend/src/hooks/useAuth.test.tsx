import { useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { authApi } from '../api/endpoints'
import { AuthProvider, useAuth } from './useAuth'

const user = { id: 1, email: 'student@example.com', demo: false, createdAt: '2026-10-01' }

function SessionControls() {
  const auth = useAuth()
  const [error, setError] = useState('')
  return <>
    <p>{auth.isLoading ? 'Loading session' : auth.user?.email ?? 'Signed out'}</p>
    {error ? <p role="alert">{error}</p> : null}
    <button onClick={() => void auth.login(user.email, 'password').catch((cause: Error) => setError(cause.message))}>Sign in</button>
    <button onClick={() => void auth.logout().catch((cause: Error) => setError(cause.message))}>Sign out</button>
  </>
}

function renderAuth() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  client.setQueryData(['transactions'], ['cached transaction'])
  render(<QueryClientProvider client={client}><AuthProvider><SessionControls /></AuthProvider></QueryClientProvider>)
  return { client, browser: userEvent.setup() }
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.spyOn(authApi, 'primeCsrf').mockResolvedValue(undefined)
  vi.spyOn(authApi, 'me').mockRejectedValue(new ApiError(401, 'UNAUTHENTICATED', 'Signed out'))
  vi.spyOn(authApi, 'login').mockResolvedValue(user)
  vi.spyOn(authApi, 'logout').mockResolvedValue(undefined)
})

describe('session lifecycle', () => {
  it('cancels a pending session lookup after login succeeds', async () => {
    let lookupSignal: AbortSignal | undefined
    vi.mocked(authApi.me).mockImplementation((signal) => new Promise((_resolve, reject) => {
      lookupSignal = signal
      signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')), { once: true })
    }))
    const { browser } = renderAuth()
    await browser.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText(user.email)).toBeInTheDocument()
    expect(lookupSignal?.aborted).toBe(true)
  })

  it('waits for the CSRF cookie before submitting credentials and drops earlier cached data', async () => {
    const { browser, client } = renderAuth()
    await screen.findByText('Signed out')
    let finishPriming!: () => void
    vi.mocked(authApi.primeCsrf).mockImplementationOnce(() => new Promise<void>((resolve) => { finishPriming = resolve }))
    await browser.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(authApi.login).not.toHaveBeenCalled()
    finishPriming()
    expect(await screen.findByText(user.email)).toBeInTheDocument()
    expect(client.getQueryData(['transactions'])).toBeUndefined()
  })

  it('keeps the session visible when the server rejects logout', async () => {
    vi.mocked(authApi.me).mockResolvedValue(user)
    vi.mocked(authApi.logout).mockRejectedValue(new Error('Logout failed. Please try again.'))
    const { browser, client } = renderAuth()
    await screen.findByText(user.email)
    await browser.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Logout failed. Please try again.')
    expect(screen.getByText(user.email)).toBeInTheDocument()
    expect(client.getQueryData(['transactions'])).toEqual(['cached transaction'])
  })

  it('clears private query data after a successful logout', async () => {
    vi.mocked(authApi.me).mockResolvedValue(user)
    const { browser, client } = renderAuth()
    await screen.findByText(user.email)
    await browser.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(await screen.findByText('Signed out')).toBeInTheDocument()
    expect(client.getQueryData(['transactions'])).toBeUndefined()
    await waitFor(() => expect(authApi.primeCsrf).toHaveBeenCalledTimes(2))
  })
})
