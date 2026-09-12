import { useEffect, useRef, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { Avatar } from '../components/Photo'
import { useAuth } from '../auth/AuthContext'
import { authApi } from '../api/endpoints'
import { BRAND } from '../brand'

/**
 * The application shell: a navy rail on the left, a white topbar, and the page.
 *
 * <p>Every nav entry names the screen key the server sends back in the login
 * response, so a role only ever sees the items it may open. The route guard repeats
 * the check, but the server is what decides.</p>
 */
const NAV_ITEMS = [
  { screen: 'HOME', to: '/', label: 'Dashboard', icon: '\u{1F4CA}' },
  { screen: 'SEWADAR', to: '/sewadars', label: 'Sewadar', icon: '\u{1F464}' },
  { screen: 'ATTENDANCE', to: '/attendance', label: 'Attendance', icon: '\u{1F4C5}' },
  { screen: 'BADGES', to: '/badges', label: 'Badge Detail', icon: '\u{1F3AB}' },
  { screen: 'REPORT', to: '/reports', label: 'Report', icon: '\u{1F4C8}' },
  { screen: 'REQUEST', to: '/requests', label: 'Request', icon: '\u21c4' },
  { screen: 'USERS', to: '/users', label: 'User Account', icon: '\u{1F511}' },
  { screen: 'SETUP', to: '/setup', label: 'Setup', icon: '\u2699' },
  { screen: 'CONTACT', to: '/contact', label: 'Contact', icon: '\u2709' },
]

/** Route names, kept for the document title and for reference. */
const TITLES = {
  '/': 'Dashboard',
  '/badges': 'Badge Detail',
  '/sewadars': 'Sewadar',
  '/attendance': 'Attendance',
  '/reports': 'Report',
  '/requests': 'Request',
  '/setup': 'Setup',
  '/users': 'User Account',
  '/contact': 'Contact',
  '/profile': 'My Profile',
}

export default function AppLayout() {
  const { user, logout, can } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [pending, setPending] = useState(0)
  const menuRef = useRef(null)

  // Close the drawer and the user menu whenever the route changes.
  useEffect(() => {
    setSidebarOpen(false)
    setMenuOpen(false)
  }, [location.pathname])

  // While the drawer is open the page behind it must not scroll - on a phone a
  // stray swipe otherwise scrolls the list underneath the menu.
  useEffect(() => {
    document.body.classList.toggle('nav-open', sidebarOpen)
    return () => document.body.classList.remove('nav-open')
  }, [sidebarOpen])

  // A click anywhere else dismisses the user menu.
  useEffect(() => {
    if (!menuOpen) return undefined
    const onDown = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [menuOpen])

  // The bell badge is the real pending zone change count for this role.
  useEffect(() => {
    authApi
      .dashboard()
      .then((d) => setPending(d.pendingRequests || 0))
      .catch(() => setPending(0))
  }, [location.pathname])

  const visibleItems = NAV_ITEMS.filter((item) => can(item.screen))

  const onLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  // Search jumps to the sewadar register with the term applied.
  const onSearch = (event) => {
    event.preventDefault()
    const term = search.trim()
    if (!term) return
    navigate(can('SEWADAR') ? `/sewadars?query=${encodeURIComponent(term)}` : '/attendance')
    setSearch('')
  }

  return (
    <div className="shell">
      {/* Tapping beside the drawer closes it, which is what every phone user expects.
          A button rather than a div so it is reachable by keyboard and screen reader. */}
      {sidebarOpen && (
        <button
          type="button"
          className="sidebar-backdrop"
          aria-label="Close navigation"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <aside className={sidebarOpen ? 'sidebar open' : 'sidebar'}>
        <div className="sidebar-brand">
          <span className="brand-mark">{BRAND.mark}</span>
          <span className="brand-text">
            <strong>{BRAND.name}</strong>
            <span>{BRAND.tagline}</span>
          </span>
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
          <strong>{BRAND.name}</strong>
          <span>{BRAND.version}</span>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button
            type="button"
            className="icon-btn"
            onClick={() => setSidebarOpen((open) => !open)}
            aria-label="Toggle navigation"
          >
            &#9776;
          </button>

          <form className="topbar-search" onSubmit={onSearch}>
            <span className="search-icon">&#8981;</span>
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search sewadars..."
              aria-label="Search"
            />
          </form>

          <div className="topbar-right">
            <button
              type="button"
              className="icon-btn bell"
              onClick={() => navigate('/requests')}
              aria-label={`${pending} pending requests`}
              title={`${pending} pending zone change request${pending === 1 ? '' : 's'}`}
            >
              &#128276;
              {pending > 0 && <span className="badge-dot">{pending}</span>}
            </button>

            <div className="user-menu" ref={menuRef}>
              <button
                type="button"
                className="user-btn"
                onClick={() => setMenuOpen((open) => !open)}
                aria-expanded={menuOpen}
              >
                {/*
                  The signed-in account's own photo. Avatar falls back to initials
                  when there is none, which is what this used to do unconditionally -
                  it never showed the photo, because it never asked for one.
                */}
                <Avatar
                  kind="users"
                  id={user?.userId}
                  stamp={user?.photoUpdatedAt}
                  name={user?.fullName || user?.username}
                  size={36}
                />
                {/* Name over role, the way the design shows it. */}
                <span className="user-name">
                  <strong>{user?.fullName}</strong>
                  <span>{user?.roleDisplayName}</span>
                </span>
                <span className="caret">&#9662;</span>
              </button>
              {menuOpen && (
                <div className="user-drop">
                  <div className="user-drop-head">
                    <strong>{user?.fullName}</strong>
                    <span>{user?.roleDisplayName}</span>
                    <span className="muted">
                      {user?.zoneNames?.length ? user.zoneNames.join(', ') : 'All zones'}
                    </span>
                  </div>
                  <button type="button" onClick={() => navigate('/profile')}>
                    My Profile
                  </button>
                  <button type="button" onClick={onLogout}>
                    Sign out
                  </button>
                </div>
              )}
            </div>
          </div>
        </header>

        <main className="content">
          {/* No heading here: every screen prints its own page-head, with a
              subtitle and its own actions, which says more than a route name. */}
          <Outlet />
        </main>

        {/*
          On a phone the primary screens move to a bar along the bottom, where a
          thumb reaches them; the drawer keeps everything else. Hidden above 640px,
          where the navy rail already does this job. "More" opens the drawer rather
          than being a sixth destination.
        */}
        <nav className="tabbar" aria-label="Primary">
          {visibleItems.slice(0, 4).map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'}>
              <span className="tabbar-icon" aria-hidden="true">
                {item.icon}
              </span>
              {item.label}
            </NavLink>
          ))}
          <a
            href="#menu"
            onClick={(event) => {
              event.preventDefault()
              setSidebarOpen(true)
            }}
          >
            <span className="tabbar-icon" aria-hidden="true">
              &#9776;
            </span>
            More
          </a>
        </nav>

        <footer className="appfoot">
          <span>
            &copy; {new Date().getFullYear()} {BRAND.name}. {BRAND.tagline}
          </span>
          <span className="appfoot-links">
            <NavLink to="/badges">Badge Detail</NavLink>
            <NavLink to="/contact">Support</NavLink>
            <NavLink to="/about">About</NavLink>
          </span>
        </footer>
      </div>
    </div>
  )
}
