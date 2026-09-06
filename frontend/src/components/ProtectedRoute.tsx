import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from '../hooks/useAuth'
import { LoadingState } from './ui'

/**
 * Keeps unauthenticated visitors off the app pages.
 *
 * This is a user-experience convenience and nothing more. Anyone can edit the
 * JavaScript that runs in their own browser, so the real access control is the
 * ownership-scoped queries on the server - this component only saves an
 * already-logged-out user from watching a screen full of 401s.
 */
export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return <LoadingState label="Checking your session…" />
  }

  if (!user) {
    // Remember where they were headed so login can send them back there.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return <>{children}</>
}
