import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, onSessionExpired } from '../api/client'
import { authApi, demoApi } from '../api/endpoints'
import type { User } from '../api/types'

/**
 * Authentication state, derived from the server rather than stored locally.
 *
 * There is no "isLoggedIn" boolean in client state. The source of truth is
 * `GET /api/auth/me`: if the session cookie is valid the server says who you
 * are, and if it is not it returns 401. Keeping a separate client-side flag
 * would let the UI believe a user is logged in after their session expired.
 */

interface AuthContextValue {
  user: User | null
  isLoading: boolean
  login: (email: string, password: string) => Promise<User>
  register: (email: string, password: string) => Promise<User>
  startDemo: () => Promise<User>
  logout: () => Promise<void>
  isAuthenticating: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

const CURRENT_USER_KEY = ['auth', 'me'] as const

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()

  const { data: user, isLoading } = useQuery({
    queryKey: CURRENT_USER_KEY,
    queryFn: async () => {
      try {
        return await authApi.me()
      } catch (error) {
        // A 401 here is the normal "not logged in" answer, not a failure.
        if (error instanceof ApiError && error.isUnauthenticated) {
          return null
        }
        throw error
      }
    },
    staleTime: 60_000,
  })

  // Make sure a CSRF cookie exists before any state-changing request. Without
  // it the very first login POST would be rejected by CSRF validation.
  useEffect(() => {
    authApi.primeCsrf().catch(() => {
      // Not fatal: the token is also set by any other GET response.
    })
  }, [])

  /**
   * When any request reports the session has gone, drop every cached query.
   * Otherwise the previous user's data would still be on screen behind the
   * login prompt.
   */
  useEffect(() => {
    return onSessionExpired(() => {
      queryClient.setQueryData(CURRENT_USER_KEY, null)
      queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
    })
  }, [queryClient])

  const loginMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      authApi.login(email, password),
    onSuccess: (loggedInUser) => {
      queryClient.setQueryData(CURRENT_USER_KEY, loggedInUser)
      queryClient.invalidateQueries()
    },
  })

  const registerMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) =>
      authApi.register(email, password).then(() => authApi.login(email, password)),
    onSuccess: (loggedInUser) => {
      queryClient.setQueryData(CURRENT_USER_KEY, loggedInUser)
      queryClient.invalidateQueries()
    },
  })

  const demoMutation = useMutation({
    mutationFn: () => demoApi.createSession(),
    onSuccess: (demoUser) => {
      queryClient.setQueryData(CURRENT_USER_KEY, demoUser)
      queryClient.invalidateQueries()
    },
  })

  const logoutMutation = useMutation({
    mutationFn: () => authApi.logout(),
    onSettled: async () => {
      queryClient.setQueryData(CURRENT_USER_KEY, null)
      queryClient.clear()
      // The session cookie is gone; fetch a fresh CSRF token for the next login.
      await authApi.primeCsrf().catch(() => undefined)
    },
  })

  const login = useCallback(
    (email: string, password: string) => loginMutation.mutateAsync({ email, password }),
    [loginMutation],
  )
  const register = useCallback(
    (email: string, password: string) => registerMutation.mutateAsync({ email, password }),
    [registerMutation],
  )
  const startDemo = useCallback(() => demoMutation.mutateAsync(), [demoMutation])
  const logout = useCallback(async () => {
    await logoutMutation.mutateAsync()
  }, [logoutMutation])

  const value = useMemo<AuthContextValue>(
    () => ({
      user: user ?? null,
      isLoading,
      login,
      register,
      startDemo,
      logout,
      isAuthenticating:
        loginMutation.isPending || registerMutation.isPending || demoMutation.isPending,
    }),
    [
      user,
      isLoading,
      login,
      register,
      startDemo,
      logout,
      loginMutation.isPending,
      registerMutation.isPending,
      demoMutation.isPending,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
