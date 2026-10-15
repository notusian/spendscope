import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Alert, errorMessage } from '../components/ui'
import Brand from '../components/Brand'
import ThemeSelect from '../components/ThemeSelect'

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
        <Brand />
        <div className="row">
          <ThemeSelect />
          <Link className="button button--secondary" to={user ? '/dashboard' : '/login'}>
            {user ? 'Open dashboard' : 'Log in'}
          </Link>
        </div>
      </header>

      <main>
        <section className="landing__hero">
          <div className="landing__intro">
            <h1>Understand where your money goes.</h1>
            <p>
              Turn your transaction history into a clear view of your spending,
              so you can plan your budget and keep recurring costs in sight.
            </p>
            {error ? <Alert variant="error">{errorMessage(error)}</Alert> : null}
            <div className="landing__actions">
              {user ? (
                <Link className="button" to="/dashboard">Open dashboard <span aria-hidden="true">↗</span></Link>
              ) : (
                <>
                  <Link className="button" to="/register">Create an account <span aria-hidden="true">↗</span></Link>
                  <button type="button" className="button button--secondary" onClick={handleDemo} disabled={isAuthenticating}>
                    {isAuthenticating ? 'Preparing demo…' : 'Try the demo'}
                  </button>
                </>
              )}
            </div>
            {!user && <p className="landing__note">The demo uses sample transactions in a temporary account.</p>}
          </div>

          <div className="spending-preview" aria-label="Illustrative monthly spending overview">
            <div className="spending-preview__heading">
              <span>Monthly spending</span>
              <span className="spending-preview__badge">Illustration</span>
            </div>
            <div className="spending-preview__total">S$2,840<span>.00</span></div>
            <svg className="spending-preview__chart" viewBox="0 0 420 156" fill="none" role="img" aria-label="Example spending trend across four weeks">
              <path d="M0 26H420M0 78H420M0 130H420" stroke="currentColor" strokeOpacity=".15" />
              <path d="M0 130L30 112L60 116L90 75L120 90L150 62L180 83L210 32L240 49L270 18L300 58L330 39L360 70L390 50L420 61V156H0Z" fill="currentColor" fillOpacity=".06" />
              <path d="M0 130L30 112L60 116L90 75L120 90L150 62L180 83L210 32L240 49L270 18L300 58L330 39L360 70L390 50L420 61" stroke="currentColor" strokeWidth="2.5" strokeLinejoin="round" />
            </svg>
            <div className="spending-preview__weeks" aria-hidden="true"><span>Week 1</span><span>Week 2</span><span>Week 3</span><span>Week 4</span></div>
            <dl className="spending-preview__categories">
              <div><dt><i />Housing</dt><dd>S$1,600</dd></div>
              <div><dt><i />Food & groceries</dt><dd>S$740</dd></div>
              <div><dt><i />Other spending</dt><dd>S$500</dd></div>
            </dl>
          </div>
        </section>

        <section className="landing__workflow" aria-label="How SpendScope works">
          <article><span className="landing__step" aria-hidden="true">01</span><h2>Import your history</h2><p>Upload transactions in the supported CSV format, with a report of duplicate and rejected rows.</p></article>
          <article><span className="landing__step" aria-hidden="true">02</span><h2>Make sense of spending</h2><p>Review automatic categories and compare spending across merchants and time periods.</p></article>
          <article><span className="landing__step" aria-hidden="true">03</span><h2>Plan with context</h2><p>Set category budgets and review recurring payments alongside changes in your spending.</p></article>
        </section>
      </main>
      <footer className="landing__footer"><span>SpendScope</span><span>By Farren Widjaja</span></footer>
    </div>
  )
}
