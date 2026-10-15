import { Link } from 'react-router-dom'

export default function Brand({ to = '/' }: { to?: string }) {
  return (
    <Link className="sidebar__brand" to={to} aria-label="SpendScope home">
      <svg className="sidebar__mark" viewBox="0 0 32 32" fill="none" aria-hidden="true">
        <path d="M3 24V14h6v10H3Zm10 0V8h6v16h-6Zm10 0V2h6v22h-6Z" fill="currentColor" />
        <path d="M3 30h26" stroke="currentColor" strokeWidth="2" />
      </svg>
      SpendScope
    </Link>
  )
}
