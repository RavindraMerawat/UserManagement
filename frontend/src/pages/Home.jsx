import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { BRAND } from '../brand'
import Spinner from '../components/Spinner'
import Alert from '../components/Alert'

/** Tuesday, 9 September 2026 */
function longDate(iso) {
  const d = iso ? new Date(`${iso}T00:00:00`) : new Date()
  return d.toLocaleDateString('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

/** "Good Morning" / "Good Afternoon" / "Good Evening", from the clock. */
function greeting() {
  const hour = new Date().getHours()
  if (hour < 12) return 'Good Morning'
  if (hour < 17) return 'Good Afternoon'
  return 'Good Evening'
}

/** Thu, 11 Sep 2026 - the form the date chip uses. */
function shortDate(iso) {
  const d = iso ? new Date(`${iso}T00:00:00`) : new Date()
  return d.toLocaleDateString('en-GB', {
    weekday: 'short',
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
}

/** Turns an ISO instant into "2 hours ago". */
function timeAgo(iso) {
  if (!iso) return ''
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const mins = Math.max(0, Math.round((Date.now() - then) / 60000))
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} minute${mins === 1 ? '' : 's'} ago`
  const hours = Math.round(mins / 60)
  if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`
  const days = Math.round(hours / 24)
  return `${days} day${days === 1 ? '' : 's'} ago`
}

/**
 * Movement against the period before, from two real measurements.
 *
 * Returns null when there is nothing honest to say - a previous value of zero has
 * no percentage, and an unchanged figure has no trend. The tile then shows its
 * plain sub-label instead of a number nobody can act on.
 */
function trend(now, before, period) {
  if (before == null || before === 0 || now === before) return null
  const pct = Math.round(((now - before) / before) * 100)
  if (pct === 0) return null
  return {
    dir: pct > 0 ? 'up' : 'down',
    text: `${pct > 0 ? '+' : ''}${pct}%`,
    period,
  }
}

/** A bar chart of present and absent days, one column pair per month. */
function AttendanceChart({ points }) {
  const hasData = points?.some((p) => p.present > 0 || p.absent > 0)
  if (!hasData) {
    return <div className="chart-empty">No attendance recorded this year yet</div>
  }

  // Round the top of the scale up to something readable so the gridlines land on
  // whole numbers rather than on whatever the tallest bar happens to be.
  const peak = Math.max(...points.map((p) => Math.max(p.present, p.absent)), 1)
  const step = Math.max(1, Math.ceil(peak / 4 / 10) * 10 || Math.ceil(peak / 4))
  const top = step * 4

  return (
    <div className="chart">
      <div className="chart-axis" aria-hidden="true">
        {[4, 3, 2, 1, 0].map((n) => (
          <span key={n}>{n * step}</span>
        ))}
      </div>
      <div className="chart-plot">
        <div className="chart-grid" aria-hidden="true">
          {[0, 1, 2, 3, 4].map((n) => (
            <span key={n} />
          ))}
        </div>
        {points.map((p) => (
          <div className="chart-month" key={p.month} tabIndex={0}>
            <span className="chart-tip">
              {p.label}: {p.present} present &middot; {p.absent} absent
            </span>
            <span
              className="chart-bar present"
              style={{ height: `${(p.present / top) * 100}%` }}
            />
            <span
              className="chart-bar absent"
              style={{ height: `${(p.absent / top) * 100}%` }}
            />
            <span className="chart-month-label">{p.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function Home() {
  const auth = useAuth()
  const { user, isSewadar, canMarkAttendance, canManageSewadars, canReviewRequests, can } = auth
  const canManageUsers = can('USERS')

  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    authApi
      .dashboard()
      .then(setData)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  if (loading) return <Spinner label="Loading your dashboard" />
  if (error) return <Alert kind="error">{error}</Alert>
  if (!data) return null

  // `to` turns a tile into a link to the people it counted; the server runs the
  // same query for the list that it ran for the number. `trend` is computed from
  // two real measurements and is simply absent when there is nothing to compare.
  const stats = isSewadar
    ? [
        { key: 'p', label: 'My Present Days', value: data.myMonthPresentDays, hint: 'This month', icon: '\u{1F464}', tone: 'blue' },
        { key: 'h', label: 'My Sewa Hours', value: data.myMonthHours, hint: 'This month', icon: '\u{1F553}', tone: 'green' },
        { key: 't', label: 'Marked Today', value: data.presentToday > 0 ? 'Present' : '—', hint: longDate(data.today), icon: '✓', tone: 'amber' },
        { key: 'r', label: 'My Requests', value: data.pendingRequests, hint: 'Awaiting review', icon: '⇄', tone: 'red' },
      ]
    : [
        {
          key: 's',
          label: 'Total Sewadar',
          value: data.totalSewadars,
          hint: data.scopeLabel,
          icon: '\u{1F465}',
          tone: 'blue',
          to: '/sewadar-list?metric=total',
          trend: trend(data.totalSewadars, data.totalSewadarsLastMonth, 'from last month'),
        },
        {
          key: 'p',
          label: 'Present Today',
          value: data.presentToday,
          hint: longDate(data.today),
          icon: '✓',
          tone: 'green',
          to: '/sewadar-list?metric=present',
          trend: trend(data.presentToday, data.presentYesterday, 'from yesterday'),
        },
        {
          key: 'q',
          label: 'Pending Requests',
          value: data.pendingRequests,
          hint: 'Awaiting review',
          icon: '\u{1F4C4}',
          tone: 'amber',
          to: '/requests',
          trend: trend(data.pendingRequests, data.pendingRequestsLastWeek, 'from last week'),
        },
        // The design's fourth tile is Active Users, which only the roles that
        // administer accounts may be shown. Every other role gets Absent Today in
        // its place - the same tile shape, a figure they are allowed to see.
        canManageUsers
          ? {
              key: 'u',
              label: 'Active Users',
              value: data.activeUsers,
              hint: 'Enabled accounts',
              icon: '\u{1F465}',
              tone: 'violet',
              to: '/users',
              trend: trend(data.activeUsers, data.activeUsersLastMonth, 'from last month'),
            }
          : {
              key: 'a',
              label: 'Absent Today',
              value: data.absentToday,
              hint: 'Today',
              icon: '⚠',
              tone: 'red',
              to: '/sewadar-list?metric=absent',
              trend: trend(data.absentToday, data.absentYesterday, 'from yesterday'),
            },
      ]

  // Only actions this role can actually complete.
  const actions = [
    canManageSewadars && { to: '/sewadars', label: 'Add Sewadar', icon: '\u{1F464}', tone: 'blue' },
    canMarkAttendance && { to: '/attendance', label: 'Mark Attendance', icon: '✓', tone: 'green' },
    { to: '/reports', label: 'Monthly Report', icon: '\u{1F4C8}', tone: 'violet' },
    { to: '/requests', label: canReviewRequests ? 'Review Requests' : 'Zone Change', icon: '⇄', tone: 'amber' },
    { to: '/contact', label: 'Contact Office', icon: '✉', tone: 'red' },
  ].filter(Boolean)

  const statusRows = Object.entries(data.monthStatusBreakdown || {})
  const sewaRows = Object.entries(data.monthSewaTypeBreakdown || {})
  const maxSewa = Math.max(...sewaRows.map(([, v]) => v), 1)

  return (
    <div className="home">
      <div className="home-head">
        <div>
          <h1 className="home-name">
            {greeting()}, {data.greetingName.split(' ')[0]}! <span aria-hidden="true">&#128075;</span>
          </h1>
          <p className="home-sub">Service before self, always.</p>
        </div>
        <div className="home-head-right">
          <span className="home-date">
            <span aria-hidden="true">&#128197;</span>
            {shortDate(data.today)}
          </span>
        </div>
      </div>

      {user?.mustChangePassword && (
        <Alert kind="warn">
          Your password is still the one that was set for you.{' '}
          <Link to="/profile">Change it now</Link>.
        </Alert>
      )}

      {/* ---------- stat tiles ---------- */}
      <div className="tile-grid">
        {stats.map((s) => {
          const body = (
            <>
              <span className={`tile-icon ${s.tone}`}>{s.icon}</span>
              <div className="tile-body">
                <span className="tile-label">{s.label}</span>
                <span className="tile-value">{s.value}</span>
                {s.trend ? (
                  <span className={`tile-trend ${s.trend.dir}`}>
                    {/* The value and the period are each one unbreakable unit, so a
                        narrow tile wraps between them rather than through them. */}
                    <b>
                      {s.trend.dir === 'up' ? '▲' : '▼'} {s.trend.text}
                    </b>
                    <span>{s.trend.period}</span>
                  </span>
                ) : (
                  <span className="tile-hint">{s.hint}</span>
                )}
              </div>
              {s.to && (
                <span className="tile-go" aria-hidden="true">
                  &rsaquo;
                </span>
              )}
            </>
          )
          // A tile that leads somewhere is a real link: it can be opened in a new
          // tab and reached by keyboard. One that does not stays a plain div.
          return s.to ? (
            <Link className="tile tile-link" key={s.key} to={s.to}>
              {body}
            </Link>
          ) : (
            <div className="tile" key={s.key}>
              {body}
            </div>
          )
        })}
      </div>

      {/* ---------- chart and activity ---------- */}
      <div className="home-cols">
        <section className="panel">
          <header className="panel-head">
            <h2>Attendance Overview</h2>
            {/* Two series, so a legend is always present - identity is never
                carried by colour alone. */}
            <div className="chart-legend">
              <span className="chart-key present">
                <i />
                Present
              </span>
              <span className="chart-key absent">
                <i />
                Absent
              </span>
            </div>
          </header>
          <AttendanceChart points={data.monthlyAttendance} />
          <p className="hint" style={{ marginTop: 22 }}>
            Attendance days recorded this year, by month, within your zones.
          </p>
        </section>

        <section className="panel">
          <header className="panel-head">
            <h2>Recent Activities</h2>
            <Link to="/attendance">View All</Link>
          </header>
          <ul className="feed">
            {data.recentAttendance.length === 0 ? (
              <li className="feed-empty">No attendance marked yet</li>
            ) : (
              data.recentAttendance.map((row) => (
                <li className="feed-row" key={row.id}>
                  <span
                    className={`feed-icon ${row.status === 'PRESENT' ? 'green' : row.status === 'ABSENT' ? 'red' : 'amber'}`}
                  >
                    {row.status === 'PRESENT' ? '✓' : row.status === 'ABSENT' ? '✕' : '•'}
                  </span>
                  <div className="feed-body">
                    <span className="feed-title">
                      {isSewadar ? row.sewaTypeLabel : row.sewadarName}
                    </span>
                    <span className="feed-note">
                      {row.statusLabel} &middot; {row.zoneName} &middot; {row.sewaTypeLabel}
                    </span>
                  </div>
                  <span className="feed-time">{timeAgo(row.updatedAt)}</span>
                </li>
              ))
            )}
          </ul>
        </section>
      </div>

      {/* ---------- quick actions ---------- */}
      <section className="panel">
        <header className="panel-head">
          <h2>Quick Actions</h2>
        </header>
        <div className="action-grid">
          {actions.map((a) => (
            <Link className={`action ${a.tone}`} to={a.to} key={a.to + a.label}>
              <span className="action-icon">{a.icon}</span>
              <span className="action-label">{a.label}</span>
            </Link>
          ))}
        </div>
      </section>

      {/* ---------- this month, and the closing note ---------- */}
      <div className="home-cols">
        <section className="panel">
          <header className="panel-head">
            <h2>This Month</h2>
            <Link to="/reports">View All</Link>
          </header>

          {statusRows.length === 0 && sewaRows.length === 0 ? (
            <p className="feed-empty">No attendance recorded this month</p>
          ) : (
            <>
              <div className="chip-row">
                {statusRows.map(([label, count]) => (
                  <span className={`chip ${label.toLowerCase().replace(/ /g, '_')}`} key={label}>
                    {label}: <strong>{count}</strong>
                  </span>
                ))}
              </div>
              <ul className="meter-list" style={{ marginTop: 16 }}>
                {sewaRows.map(([label, count]) => (
                  <li key={label}>
                    <span className="meter-label">{label}</span>
                    <span className="meter-track">
                      <span className="meter-fill" style={{ width: `${(count / maxSewa) * 100}%` }} />
                    </span>
                    <span className="meter-value">{count}</span>
                  </li>
                ))}
              </ul>
              {!isSewadar && data.pendingRequests > 0 && (
                <Link to="/requests" className="callout">
                  {data.pendingRequests} zone change request
                  {data.pendingRequests === 1 ? '' : 's'} awaiting review
                </Link>
              )}
            </>
          )}
        </section>

        <section className="promo">
          <h3>Service unites us</h3>
          <p>{BRAND.quote}</p>
        </section>
      </div>
    </div>
  )
}
