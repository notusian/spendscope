import { type FormEvent, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Alert, Field, errorMessage, fieldErrorOf } from '../components/ui'
import Brand from '../components/Brand'
import ThemeSelect from '../components/ThemeSelect'

export default function LoginPage() {
  const { user, login, startDemo, isAuthenticating } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const from = (location.state as { from?: string } | null)?.from
  const destination = from?.startsWith('/') && !from.startsWith('//') ? from : '/dashboard'

  // Already logged in: go where they were headed, or to the dashboard.
  if (user) {
    return <Navigate to={destination} replace />
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      await login(email, password)
      navigate(destination, { replace: true })
    } catch (loginError) {
      setError(loginError)
    }
  }

  const handleDemo = async () => {
    setError(null)
    try {
      await startDemo()
      navigate(destination, { replace: true })
    } catch (demoError) {
      setError(demoError)
    }
  }

  return (
    <div className="auth-page">
      <header className="auth-topbar"><Brand /><ThemeSelect /></header>
      <div className="auth-card">
        <h1 className="auth-card__title">Welcome back</h1>
        <p className="auth-card__subtitle">Log in to your SpendScope account.</p>

        {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}

        <form onSubmit={handleSubmit} noValidate>
          <Field label="Email" htmlFor="email" error={fieldErrorOf(error, 'email')}>
            <input
              id="email"
              className="input"
              type="email"
              autoComplete="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </Field>

          <Field label="Password" htmlFor="password" error={fieldErrorOf(error, 'password')}>
            <input
              id="password"
              className="input"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </Field>

          <button type="submit" className="button" style={{ width: '100%' }} disabled={isAuthenticating}>
            {isAuthenticating ? 'Logging in…' : 'Log in'}
          </button>
        </form>

        <div className="divider">or</div>

        <button
          type="button"
          className="button button--secondary"
          style={{ width: '100%' }}
          onClick={handleDemo}
          disabled={isAuthenticating}
        >
          Try the demo
        </button>

        <p className="subtle" style={{ marginTop: 'var(--space-4)', textAlign: 'center' }}>
          <Link to="/register">Create a SpendScope account</Link>
        </p>
      </div>
    </div>
  )
}
