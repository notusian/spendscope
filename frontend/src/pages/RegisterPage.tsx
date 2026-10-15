import { type FormEvent, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Alert, Field, errorMessage, fieldErrorOf } from '../components/ui'
import Brand from '../components/Brand'
import ThemeSelect from '../components/ThemeSelect'

/** Mirrors the backend rule, which is the one that actually enforces it. */
const MIN_PASSWORD_LENGTH = 10

export default function RegisterPage() {
  const { user, register, isAuthenticating } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)

  if (user) {
    return <Navigate to="/dashboard" replace />
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    try {
      await register(email, password)
      navigate('/dashboard', { replace: true })
    } catch (registerError) {
      setError(registerError)
    }
  }

  const passwordTooShort = password.length > 0 && password.length < MIN_PASSWORD_LENGTH

  return (
    <div className="auth-page">
      <header className="auth-topbar"><Brand /><ThemeSelect /></header>
      <div className="auth-card">
        <h1 className="auth-card__title">Create your account</h1>
        <p className="auth-card__subtitle">Use a password with at least ten characters.</p>

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

          <Field
            label="Password"
            htmlFor="password"
            error={
              passwordTooShort
                ? `Use at least ${MIN_PASSWORD_LENGTH} characters`
                : fieldErrorOf(error, 'password')
            }
          >
            <input
              id="password"
              className={`input${passwordTooShort ? ' input--invalid' : ''}`}
              type="password"
              autoComplete="new-password"
              required
              minLength={MIN_PASSWORD_LENGTH}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </Field>

          <button
            type="submit"
            className="button"
            style={{ width: '100%' }}
            disabled={isAuthenticating || passwordTooShort}
          >
            {isAuthenticating ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="subtle" style={{ marginTop: 'var(--space-4)', textAlign: 'center' }}>
          <Link to="/login">Log in to an existing account</Link>
        </p>
      </div>
    </div>
  )
}
