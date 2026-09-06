import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

/**
 * The authenticated shell: a sidebar on desktop, a bottom bar on mobile.
 *
 * Both navigations render from one array, so a new page cannot appear in one
 * and be forgotten in the other.
 */

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', short: 'Home' },
  { to: '/transactions', label: 'Transactions', short: 'Activity' },
  { to: '/accounts', label: 'Accounts', short: 'Accounts' },
  { to: '/budgets', label: 'Budgets', short: 'Budgets' },
  { to: '/import', label: 'Import', short: 'Import' },
  { to: '/security', label: 'Security', short: 'Security' },
  { to: '/settings', label: 'Settings', short: 'Settings' },
]

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <>
      <div className="mobile-topbar">
        <span className="sidebar__brand">
          <span className="sidebar__mark" aria-hidden="true" />
          SpendScope
        </span>
        <button type="button" className="button button--ghost button--small" onClick={handleLogout}>
          Log out
        </button>
      </div>

      <div className="app-shell">
        <aside className="sidebar">
          <div className="sidebar__brand">
            <span className="sidebar__mark" aria-hidden="true" />
            SpendScope
          </div>

          <nav className="sidebar__nav" aria-label="Main navigation">
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `sidebar__link${isActive ? ' sidebar__link--active' : ''}`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="sidebar__footer">
            {user?.demo ? <span className="badge badge--accent">Demo account</span> : null}
            <span>{user?.email}</span>
            <button type="button" className="button button--secondary button--small" onClick={handleLogout}>
              Log out
            </button>
          </div>
        </aside>

        <main className="main">
          <Outlet />
        </main>
      </div>

      <nav className="mobile-nav" aria-label="Main navigation">
        {NAV_ITEMS.slice(0, 5).map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className={({ isActive }) => `mobile-nav__link${isActive ? ' mobile-nav__link--active' : ''}`}
          >
            {item.short}
          </NavLink>
        ))}
      </nav>
    </>
  )
}
