import { useEffect, useState } from 'react'
import { metaApi, zoneApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import MarkAttendance from './attendance/MarkAttendance'
import PastAttendance from './attendance/PastAttendance'
import ZoneAttendance from './attendance/ZoneAttendance'
import AttendanceRecords from './attendance/AttendanceRecords'

export default function Attendance() {
  const { canMarkAttendance, isSewadar, canManageSewadars } = useAuth()
  const [tab, setTab] = useState(canMarkAttendance ? 'mark' : 'records')

  const [sewaType, setSewaType] = useState('ROSTER_SEWA')
  const [zones, setZones] = useState([])
  const [options, setOptions] = useState({ sewaTypes: [], attendanceStatuses: [] })
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    zoneApi.list(false).then(setZones).catch(() => setZones([]))
    metaApi.options().then(setOptions).catch(() => {})
  }, [])

  const tabs = [
    canMarkAttendance && { key: 'mark', label: 'Mark Attendance' },
    canMarkAttendance && { key: 'zone', label: 'Zone Attendance' },
    // A missed day is its own job, with typed times and no clock, so it gets its
    // own tab rather than a date field on the live Mark Attendance screen.
    canMarkAttendance && { key: 'past', label: 'Manage Past Attendance' },
    { key: 'records', label: isSewadar ? 'My Attendance' : 'Records' },
  ].filter(Boolean)

  /*
   * The sewa type lives here, above the tabs, because an attendance row is keyed
   * by (sewadar, date, sewa type) - so two tabs set to different types are looking
   * at two different records for the same person on the same day. Marking someone
   * in under Construction Sewa on one tab left them reading "not checked in" on
   * another, and checking them in there wrote a second row. One value, shared, is
   * what stops that; each tab shows it so nobody has to guess which is in force.
   */
  const shared = {
    zones,
    sewaTypes: options.sewaTypes || [],
    sewaType,
    setSewaType,
    onNotice: (message) => {
      setNotice(message)
      setError('')
    },
    onError: (message) => {
      setError(message)
      setNotice('')
    },
  }

  return (
    <div>
      <Alert kind="error" onClose={() => setError('')}>
        {error}
      </Alert>
      <Alert kind="success" onClose={() => setNotice('')}>
        {notice}
      </Alert>

      <div className="tabs">
        {tabs.map((t) => (
          <button
            key={t.key}
            type="button"
            className={tab === t.key ? 'active' : ''}
            onClick={() => setTab(t.key)}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'mark' && canMarkAttendance && <MarkAttendance {...shared} />}
      {tab === 'zone' && canMarkAttendance && <ZoneAttendance {...shared} />}
      {tab === 'past' && canMarkAttendance && (
        <PastAttendance {...shared} onViewHistory={() => setTab('records')} />
      )}
      {tab === 'records' && (
        <AttendanceRecords
          {...shared}
          statuses={options.attendanceStatuses || []}
          isSewadar={isSewadar}
          canEdit={canMarkAttendance}
          canDelete={canManageSewadars}
        />
      )}
    </div>
  )
}
