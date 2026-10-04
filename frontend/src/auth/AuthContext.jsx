import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { authApi, clearPhotoCache } from '../api/endpoints'
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

/**
 * Kept only so a screen can still be named in one place. The server decides who
 * sees what - see `menu` below - and this table is no longer consulted for it.
 */
export const SCREEN_ROLES = {
  HOME: EVERYONE,
  BADGES: EVERYONE,
  SEWADAR: EVERYONE.filter((role) => role !== 'SEWADAR'),
  ATTENDANCE: EVERYONE,
  REPORT: EVERYONE,
  REQUEST: EVERYONE,
  // Setup and User Account belong to the office pair, here and on the server.
  SETUP: ['ADMIN', 'OFFICE_ADMIN'],
  ZONES: ['ADMIN', 'OFFICE_ADMIN'],
  // Account administration is Admin and Office Admin. The server decides; this
  // only keeps the menu and the route guard in step with it.
  USERS: ['ADMIN', 'OFFICE_ADMIN'],
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
    // Whoever was signed in here before, their pictures go with them.
    clearPhotoCache()
    tokenStore.set(result.token)
    tokenStore.setUser(result)
    setUser(result)
    return result
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    clearPhotoCache()
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
      /*
       * Everything below is the server's answer, not a second opinion.
       *
       * Permissions are decided by designation now, and the matrix lives in
       * Capabilities.java. Keeping a copy of it here in JavaScript would mean two
       * sets of rules that can disagree - and the one the browser holds is the one
       * that cannot be trusted anyway. The menu and these flags come down with the
       * login; the API enforces the same rules again on every call.
       */
      menu: user?.menu || [],
      can: (screen) => (user?.menu || []).includes(screen),
      /** The designation the rules were read from, for display. */
      designation: user?.designation || null,
      /** Add, edit and delete on sewadar master data. */
      canManageSewadars: Boolean(user?.canManageSewadars),
      /** Issue a badge or record that one was collected. */
      canManageBadges: Boolean(user?.canManageBadges),
      /** Record or correct a construction sewa count. Office work. */
      canManageConstruction: Boolean(user?.canManageConstruction),
      /** Mark and update attendance. */
      canMarkAttendance: Boolean(user?.canMarkAttendance),
      /** Open All Attendance Record, and correct an entry on it. */
      canManageAttendanceRecords: Boolean(user?.canManageAttendanceRecords),
      /** Open the Monthly Report. The one-sewadar hours report is not this. */
      canViewMonthlyReport: Boolean(user?.canViewMonthlyReport),
      /** Zone Attendance and Manage Past Attendance, beside Mark Attendance. */
      canUseFullAttendance: Boolean(user?.canUseFullAttendance),
      /**
       * Whose register this account reads, or null for both. The server narrows the
       * data either way; the screens use this to stop offering the other one.
       *
       * Admin is the exception and reads both, exactly as `CurrentUserService.scope()`
       * has it - the gender on an Admin account is who they are, not what they may
       * see. Without the same exception here an Admin who filled that field in would
       * lose half their cards while the numbers behind them stayed whole.
       */
      accountGender: role === 'ADMIN' ? null : user?.gender || null,
      /** Raise a zone change request. */
      canCreateZoneRequest: Boolean(user?.canCreateZoneRequest),
      /** Approve or reject a zone change. Admin only. */
      canReviewRequests: Boolean(user?.canReviewRequests),
      /** Administer login accounts and the Setup lists. */
      canAdminister: Boolean(user?.canAdminister),
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
