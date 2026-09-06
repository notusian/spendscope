import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Alert, Card, errorMessage } from '../components/ui'
import { useState } from 'react'

const FEATURES = [
  {
    title: 'Import in seconds',
    body: 'Upload a CSV export from your bank. Rows are validated, merchants detected and duplicates flagged before anything is saved.',
  },
  {
    title: 'Automatic categories',
    body: 'Known merchants are filed automatically. Anything unrecognised stays uncategorised so you can correct it, not guess at it.',
  },
  {
    title: 'Analytics you configure',
    body: 'Build the dashboard you want from metrics, periods, comparisons and chart types. Your layout is saved to your account.',
  },
  {
    title: 'Budgets and subscriptions',
    body: 'Set monthly limits per category, and see the recurring payments hiding in your statement with what they cost per year.',
  },
]

export default function LandingPage() {
  const { user, startDemo, isAuthenticating } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<unknown>(null)

  const handleDemo = async () => {
    setError(null)
    try {
      await startDemo()
      navigate('/dashboard')
    } catch (demoError) {
      setError(demoError)
    }
  }

  return (
    <div className="landing">
      <header className="landing__nav">
        <span className="sidebar__brand">
          <span className="sidebar__mark" aria-hidden="true" />
          SpendScope
        </span>
        <div className="row">
          {user ? (
            <button type="button" className="button" onClick={() => navigate('/dashboard')}>
              Open dashboard
            </button>
          ) : (
            <>
              <button
                type="button"
                className="button button--secondary"
                onClick={() => navigate('/login')}
              >
                Log in
              </button>
              <button type="button" className="button" onClick={() => navigate('/register')}>
                Sign up
              </button>
            </>
          )}
        </div>
      </header>

      <section className="landing__hero">
        <h1>See where your money actually goes</h1>
        <p>
          Import your transactions, let SpendScope categorise them, and get spending analytics,
          budgets, subscription detection and unusual-spending alerts — all in one place.
        </p>
        {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
        <div className="landing__actions">
          <button type="button" className="button" onClick={handleDemo} disabled={isAuthenticating}>
            {isAuthenticating ? 'Preparing demo…' : 'Try the demo'}
          </button>
          <button
            type="button"
            className="button button--secondary"
            onClick={() => navigate('/register')}
          >
            Create an account
          </button>
        </div>
        <p className="subtle" style={{ marginTop: 'var(--space-4)' }}>
          The demo creates a temporary account filled with synthetic data. No sign-up, no real
          financial information.
        </p>
      </section>

      <section className="landing__features">
        {FEATURES.map((feature) => (
          <Card key={feature.title}>
            <h3 style={{ marginBottom: 'var(--space-2)' }}>{feature.title}</h3>
            <p className="muted" style={{ margin: 0, fontSize: '0.9rem' }}>
              {feature.body}
            </p>
          </Card>
        ))}
      </section>
    </div>
  )
}
