import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Spinner from '../components/Spinner'
import Alert from '../components/Alert'
import { BarBreakdown, Badge, StatCard, EmptyRow } from '../components/Bits'

export default function Home() {
  const { user, isSewadar, canMarkAttendance } = useAuth()
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

  return (
    <div>
      <div className="card">
        <h3 style={{ marginBottom: 6 }}>
          Sat Sri Akal, {data.greetingName}
        </h3>
        <p className="muted" style={{ margin: 0 }}>
          You are signed in as <strong>{data.roleDisplayName}</strong>. Data reach:{' '}
          <strong>{data.scopeLabel}</strong>. Today is {data.today}.
        </p>
        {user?.mustChangePassword && (
          <div style={{ marginTop: 12 }}>
            <Alert kind="warn">
              Your password is still the one that was set for you.{' '}
              <Link to="/profile">Change it now</Link>.
            </Alert>
          </div>
        )}
      </div>

      <div className="stat-grid">
        {isSewadar ? (
          <>
            <StatCard label="Present this month" value={data.myMonthPresentDays} hint="Days marked present" />
            <StatCard
              label="Sewa hours this month"
              value={data.myMonthHours}
              hint="From in and out time"
              accent="#2563eb"
            />
            <StatCard
              label="Marked today"
              value={data.presentToday > 0 ? 'Present' : '-'}
              hint="Your attendance for today"
              accent="#059669"
            />
            <StatCard
              label="My pending requests"
              value={data.pendingRequests}
              hint="Zone change requests"
              accent="#d97706"
            />
          </>
        ) : (
          <>
            <StatCard
              label="Active sewadars"
              value={data.totalSewadars}
              hint={data.scopeLabel}
            />
            <StatCard
              label="Present today"
              value={data.presentToday}
              hint={data.today}
              accent="#059669"
            />
            <StatCard label="Absent today" value={data.absentToday} hint={data.today} accent="#dc2626" />
            <StatCard
              label="Pending requests"
              value={data.pendingRequests}
              hint="Zone change requests"
              accent="#d97706"
            />
          </>
        )}
      </div>

      <div className="grid-2">
        <div className="card">
          <h3>This month by status</h3>
          <BarBreakdown data={data.monthStatusBreakdown} />
        </div>
        <div className="card">
          <h3>This month by sewa type</h3>
          <BarBreakdown data={data.monthSewaTypeBreakdown} />
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h3>Recent attendance</h3>
          <div className="btn-row">
            {canMarkAttendance && (
              <Link className="btn small" to="/attendance">
                Mark attendance
              </Link>
            )}
            <Link className="btn ghost small" to="/reports">
              Open reports
            </Link>
          </div>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                {!isSewadar && <th>Sewadar</th>}
                <th>Zone</th>
                <th>Sewa type</th>
                <th>Status</th>
                <th>Hours</th>
                <th>Marked by</th>
              </tr>
            </thead>
            <tbody>
              {data.recentAttendance.length === 0 ? (
                <EmptyRow colSpan={isSewadar ? 6 : 7}>No attendance marked yet</EmptyRow>
              ) : (
                data.recentAttendance.map((row) => (
                  <tr key={row.id}>
                    <td>{row.attendanceDate}</td>
                    {!isSewadar && (
                      <td>
                        {row.sewadarName}
                        <div className="muted" style={{ fontSize: 11.5 }}>
                          {row.badgeNumber}
                        </div>
                      </td>
                    )}
                    <td>{row.zoneName}</td>
                    <td>{row.sewaTypeLabel}</td>
                    <td>
                      <Badge value={row.status} label={row.statusLabel} />
                    </td>
                    <td>{row.hours ?? '-'}</td>
                    <td className="muted">{row.markedBy || '-'}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
