import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { Alert, errorMessage } from './ui'
import Brand from './Brand'
import ThemeSelect from './ThemeSelect'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/transactions', label: 'Transactions' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/budgets', label: 'Budgets' },
  { to: '/import', label: 'Import' },
  { to: '/security', label: 'Security' },
  { to: '/settings', label: 'Settings' },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [logoutError, setLogoutError] = useState<unknown>(null)
  const [isLoggingOut, setIsLoggingOut] = useState(false)

  const handleLogout = async () => {
    setLogoutError(null)
    setIsLoggingOut(true)
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch (error) {
      setLogoutError(error)
    } finally {
      setIsLoggingOut(false)
    }
  }

  const logoutButton = (
    <button type="button" className="button button--secondary button--small" onClick={handleLogout} disabled={isLoggingOut}>
      {isLoggingOut ? 'Logging out…' : 'Log out'}
    </button>
  )

  return (
    <>
      <a href="#main-content" className="skip-link">Skip to content</a>
      <div className="mobile-topbar">
        <Brand to="/dashboard" />
        <div className="row"><ThemeSelect />{logoutButton}</div>
      </div>

      <div className="app-shell">
        <aside className="sidebar">
          <Brand to="/dashboard" />
          <nav className="sidebar__nav" aria-label="Main navigation">
            {NAV_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} className={({ isActive }) => `sidebar__link${isActive ? ' sidebar__link--active' : ''}`}>
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="sidebar__footer">
            <ThemeSelect />
            {user?.demo ? <span className="badge badge--accent">Demo account</span> : null}
            {!user?.demo && <span>{user?.email}</span>}
            {logoutButton}
          </div>
        </aside>

        <main className="main" id="main-content" tabIndex={-1}>
          {logoutError ? <Alert variant="error">We could not log you out. {errorMessage(logoutError)}</Alert> : null}
          <Outlet />
        </main>
      </div>

      <nav className="mobile-nav" aria-label="Main navigation">
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} className={({ isActive }) => `mobile-nav__link${isActive ? ' mobile-nav__link--active' : ''}`}>
            {item.label}
          </NavLink>
        ))}
      </nav>
    </>
  )
}
