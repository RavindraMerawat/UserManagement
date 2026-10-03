import { useEffect, useState } from 'react'
import { metaApi, zoneApi } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import MarkAttendance from './attendance/MarkAttendance'
import PastAttendance from './attendance/PastAttendance'
import ZoneAttendance from './attendance/ZoneAttendance'
import AttendanceRecords from './attendance/AttendanceRecords'

/*
 * What the live screens mark. Attendance is keyed by (sewadar, date, sewa type),
 * so the value still travels with every call - it is simply no longer a question
 * the two live screens ask. Marking someone in is marking them in; the office does
 * not sort today's arrivals by kind of sewa at the desk.
 */
const LIVE_SEWA_TYPE = 'DAILY_SEWA'

export default function Attendance() {
  const { canMarkAttendance, isSewadar, canManageAttendanceRecords, canUseFullAttendance } =
    useAuth()
  /*
   * A sewadar's own history is a different thing from the office's register of
   * everyone, even though one screen draws both: the first is their own data and
   * stays with them, the second is the office's and is the Admin's and the Office
   * Incharge's.
   */
  const canOpenRecords = isSewadar || canManageAttendanceRecords
  const [tab, setTab] = useState(canMarkAttendance ? 'mark' : 'records')

  const [zones, setZones] = useState([])
  const [options, setOptions] = useState({ sewaTypes: [], attendanceStatuses: [] })
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    zoneApi.list(false).then(setZones).catch(() => setZones([]))
    metaApi.options().then(setOptions).catch(() => {})
  }, [])

  /*
   * Everyone who marks sees Mark Attendance. The rest of the module - marking a
   * zone sheet at once, and entering a day that has already gone - is the office's
   * own work: the Admin, the Office Incharge and the Office Sewadar. A co-ordinator
   * at the desk marks the person in front of them and nothing else.
   */
  const tabs = [
    canMarkAttendance && { key: 'mark', label: 'Mark Attendance' },
    canMarkAttendance && canUseFullAttendance && { key: 'zone', label: 'Zone Attendance' },
    // A missed day is its own job, with typed times and no clock, so it gets its
    // own tab rather than a date field on the live Mark Attendance screen.
    canMarkAttendance &&
      canUseFullAttendance && { key: 'past', label: 'Manage Past Attendance' },
    canOpenRecords && {
      key: 'records',
      label: isSewadar ? 'My Attendance' : 'All Attendance Record',
    },
  ].filter(Boolean)

  const shared = {
    zones,
    sewaTypes: options.sewaTypes || [],
    sewaType: LIVE_SEWA_TYPE,
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
      {tab === 'zone' && canMarkAttendance && canUseFullAttendance && <ZoneAttendance {...shared} />}
      {tab === 'past' && canMarkAttendance && canUseFullAttendance && (
        <PastAttendance {...shared} onViewHistory={() => setTab('records')} />
      )}
      {tab === 'records' && canOpenRecords && (
        <AttendanceRecords
          {...shared}
          statuses={options.attendanceStatuses || []}
          isSewadar={isSewadar}
          // Correcting what was marked is the Admin's and the Office Incharge's,
          // which is the same rule AttendanceService applies to the two endpoints.
          canEdit={canManageAttendanceRecords}
          canDelete={canManageAttendanceRecords}
        />
      )}
    </div>
  )
}
