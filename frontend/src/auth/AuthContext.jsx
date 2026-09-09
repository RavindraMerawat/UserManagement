import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi } from '../api/endpoints'
import { tokenStore } from '../api/client'

const AuthContext = createContext(null)

/** Which roles may reach each screen. Mirrors AuthService.menuFor on the server. */
const EVERYONE = [
  'ADMIN',
  'OFFICE_ADMIN',
  'COORDINATOR',
  'ZONE_INCHARGE',
  'SUPERVISOR',
  'OFFICE_USER',
  'SEWADAR',
]

/** Roles whose data reach is limited to the zones on their account. */
export const ZONE_SCOPED_ROLES = ['COORDINATOR', 'ZONE_INCHARGE', 'SUPERVISOR']

export const SCREEN_ROLES = {
  HOME: EVERYONE,
  ABOUT: EVERYONE,
  SEWADAR: EVERYONE.filter((role) => role !== 'SEWADAR'),
  ATTENDANCE: EVERYONE,
  REPORT: EVERYONE,
  REQUEST: EVERYONE,
  ZONES: ['ADMIN', 'OFFICE_ADMIN'],
  USERS: ['ADMIN'],
  CONTACT: EVERYONE,
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => tokenStore.getUser())
  const [loading, setLoading] = useState(() => Boolean(tokenStore.get()))

  // On a page refresh the token is in storage but the profile may be stale.
  useEffect(() => {
    if (!tokenStore.get()) {
      setLoading(false)
      return
    }
    let cancelled = false
    authApi
      .me()
      .then((profile) => {
        if (cancelled) return
        setUser(profile)
        tokenStore.setUser(profile)
      })
      .catch(() => {
        if (!cancelled) {
          tokenStore.clear()
          setUser(null)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (username, password) => {
    const result = await authApi.login({ username, password })
    tokenStore.set(result.token)
    tokenStore.setUser(result)
    setUser(result)
    return result
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    setUser(null)
  }, [])

  const refresh = useCallback(async () => {
    const profile = await authApi.me()
    setUser(profile)
    tokenStore.setUser(profile)
    return profile
  }, [])

  const value = useMemo(() => {
    const role = user?.role
    return {
      user,
      role,
      loading,
      login,
      logout,
      refresh,
      isAuthenticated: Boolean(user),
      /** Screens this role may open, from the server menu with a client fallback. */
      menu: user?.menu || [],
      can: (screen) => (SCREEN_ROLES[screen] || []).includes(role),
      /** Add, edit and delete on sewadar master data. */
      canManageSewadars: ['ADMIN', 'OFFICE_ADMIN'].includes(role),
      /** Mark and update attendance. */
      canMarkAttendance: ['ADMIN', 'OFFICE_ADMIN', 'COORDINATOR', 'ZONE_INCHARGE', 'SUPERVISOR'].includes(
        role,
      ),
      /** Approve or reject a zone change. */
      canReviewRequests: ['ADMIN', 'OFFICE_ADMIN'].includes(role),
      /** A sewadar login only ever sees its own data. */
      isSewadar: role === 'SEWADAR',
      isGlobalScope: ['ADMIN', 'OFFICE_ADMIN', 'OFFICE_USER'].includes(role),
      isZoneScoped: ZONE_SCOPED_ROLES.includes(role),
    }
  }, [user, loading, login, logout, refresh])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
