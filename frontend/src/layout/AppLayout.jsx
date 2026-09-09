import { useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/**
 * Left-hand navigation. Every entry names the screen key the server sends back in
 * the login response, so a role only ever sees the items it may open.
 */
const NAV_ITEMS = [
  { screen: 'HOME', to: '/', label: 'Home', icon: '⌂' },
  { screen: 'ABOUT', to: '/about', label: 'About', icon: 'ℹ' },
  { screen: 'SEWADAR', to: '/sewadars', label: 'Sewadar', icon: '\u{1F464}' },
  { screen: 'ATTENDANCE', to: '/attendance', label: 'Attendance', icon: '✓' },
  { screen: 'REPORT', to: '/reports', label: 'Report', icon: '\u{1F4C8}' },
  { screen: 'REQUEST', to: '/requests', label: 'Request', icon: '⇄' },
  { screen: 'ZONES', to: '/zones', label: 'Zones', icon: '◉' },
  { screen: 'USERS', to: '/users', label: 'User Accounts', icon: '\u{1F511}' },
  { screen: 'CONTACT', to: '/contact', label: 'Contact', icon: '✉' },
]

const TITLES = {
  '/': 'Home',
  '/about': 'About',
  '/sewadars': 'Sewadar',
  '/attendance': 'Attendance',
  '/reports': 'Report',
  '/requests': 'Request',
  '/zones': 'Zones',
  '/users': 'User Accounts',
  '/contact': 'Contact',
  '/profile': 'My Profile',
}

export default function AppLayout() {
  const { user, logout, can } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [sidebarOpen, setSidebarOpen] = useState(false)

  // Close the drawer whenever the route changes on a narrow screen.
  useEffect(() => {
    setSidebarOpen(false)
  }, [location.pathname])

  const visibleItems = NAV_ITEMS.filter((item) => can(item.screen))
  const initials = (user?.fullName || user?.username || '?')
    .split(' ')
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase()

  const onLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="shell">
      <aside className={sidebarOpen ? 'sidebar open' : 'sidebar'}>
        <div className="sidebar-brand">
          <strong>Sewadar Management</strong>
          <span>Attendance &amp; Sewa Records</span>
        </div>
        <nav className="sidebar-nav">
          {visibleItems.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'}>
              <span className="nav-icon">{item.icon}</span>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          {user?.roleDisplayName}
          <br />
          {user?.zoneNames?.length > 0 ? user.zoneNames.join(', ') : 'All zones'}
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <button
              type="button"
              className="menu-toggle"
              onClick={() => setSidebarOpen((open) => !open)}
              aria-label="Toggle navigation"
            >
              &#9776;
            </button>
            <h2>{TITLES[location.pathname] || 'Sewadar Management'}</h2>
          </div>
          <div className="topbar-user">
            <div style={{ textAlign: 'right', lineHeight: 1.3 }}>
              <div style={{ fontWeight: 600 }}>{user?.fullName}</div>
              <div className="muted" style={{ fontSize: 11.5 }}>
                {user?.roleDisplayName}
              </div>
            </div>
            <div className="avatar" title={user?.username}>
              {initials}
            </div>
            <button type="button" className="btn ghost small" onClick={() => navigate('/profile')}>
              Profile
            </button>
            <button type="button" className="btn ghost small" onClick={onLogout}>
              Sign out
            </button>
          </div>
        </header>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
