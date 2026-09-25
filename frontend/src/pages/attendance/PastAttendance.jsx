import { useState } from 'react'
import {
  ArrowRight,
  CalendarCheck,
  CalendarDays,
  IdCard,
  Info,
  Map,
  MapPin,
  RotateCcw,
  Save,
} from 'lucide-react'
import { attendanceApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import { Avatar } from '../../components/Photo'
import { Badge, EmptyRow } from '../../components/Bits'
import { fromIso, monthStartIso, prettyDate, todayIso, yesterdayIso } from '../../dates'

/** The server accepts 400; the box is capped shorter so the counter means something. */
const REMARKS_MAX = 200

const today = todayIso
const yesterday = yesterdayIso

/*
 * The window this screen may write to: the first of the current month through to
 * yesterday. Attendance is closed off and reported monthly, so a month that has
 * been reported should not gain new rows afterwards, and today belongs to Mark
 * Attendance, where the clock is the record.
 *
 * On the first of a month the window is empty - there is no past day in it yet -
 * and the form says so rather than offering a date it would refuse.
 */

const monthStart = monthStartIso

/** September 2026 - the month named in the subtitle. */
const monthName = () =>
  new Date().toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })

const asDate = fromIso

/** yyyy-mm-dd -> Saturday, 5 September 2026. */
const longDate = (iso) =>
  !iso
    ? ''
    : asDate(iso).toLocaleDateString('en-GB', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      })

const shortDate = prettyDate

/** Today, Yesterday, or the weekday - the chip under the date field. */
const relativeLabel = (iso) => {
  if (iso === today()) return 'Today'
  if (iso === yesterday()) return 'Yesterday'
  return asDate(iso).toLocaleDateString('en-GB', { weekday: 'long' })
}

/** 09:12:00 -> 09:12 AM */
function pretty(time) {
  if (!time) return null
  const [h, m] = time.split(':')
  const hour = Number(h)
  const suffix = hour < 12 ? 'AM' : 'PM'
  const twelve = hour % 12 === 0 ? 12 : hour % 12
  return `${String(twelve).padStart(2, '0')}:${m} ${suffix}`
}

/** 8.75 -> 08h 45m */
function hoursLabel(hours) {
  if (hours == null) return '-'
  const h = Math.floor(hours)
  const m = Math.round((hours - h) * 60)
  return `${String(h).padStart(2, '0')}h ${String(m).padStart(2, '0')}m`
}

/**
 * Manage Past Attendance: a day that was missed, entered afterwards.
 *
 * <p>Mark Attendance is for the person standing in front of you now - it stamps the
 * clock and needs one press. This is the other job: a date that has already gone,
 * with the times typed in by hand. Separate tasks, so separate tabs, and a date
 * field on the live screen would be a standing invitation to record today's arrival
 * against last Tuesday.</p>
 *
 * <p>The server has always accepted an explicit date, time and remark on check in and
 * check out, and it is what enforces the rules: no future date, no second check in,
 * and a check out later than the check in. Nothing here is a new privilege - whoever
 * may mark attendance may enter a missed day.</p>
 */
export default function PastAttendance({ sewaTypes, sewaType, setSewaType, onNotice, onViewHistory }) {
  const windowStart = monthStart()
  const windowEnd = yesterday()
  // True on the 1st: the month has no past day in it yet.
  const windowEmpty = windowEnd < windowStart

  const [date, setDate] = useState(() => (windowEnd < monthStart() ? '' : yesterday()))

  const [query, setQuery] = useState('')
  const [hits, setHits] = useState(null)
  const [selected, setSelected] = useState(null)
  const [log, setLog] = useState([])

  const [inTime, setInTime] = useState('')
  const [outTime, setOutTime] = useState('')
  const [remarks, setRemarks] = useState('')

  const [searching, setSearching] = useState(false)
  const [saving, setSaving] = useState(false)
  const [localError, setLocalError] = useState('')

  /** Why this date cannot be used, in the words the person will read, or null. */
  const dateProblem = (iso) => {
    if (!iso) return 'Choose the attendance date.'
    if (iso > windowEnd) {
      return iso === today()
        ? 'This screen is for past days. Mark today under Mark Attendance.'
        : 'Attendance cannot be entered for a future date.'
    }
    if (iso < windowStart) {
      return `Only ${monthName()} can be entered here - ${shortDate(windowStart)} onwards.`
    }
    return null
  }

  const loadLog = (sewadarId) =>
    attendanceApi
      .search({ sewadarId, size: 10 })
      .then((res) => setLog(res.content || []))
      .catch(() => setLog([]))

  /*
   * Reads the chosen day's row for this sewadar, so the form shows what is already
   * there rather than offering to enter it twice. Called on pick, after every save,
   * and whenever the date changes underneath a selected person.
   */
  const loadDay = async (sewadarId, onDate, type = sewaType) => {
    const fresh = await attendanceApi.status(sewadarId, type, onDate)
    setSelected(fresh)
    setInTime(fresh.inTime ? fresh.inTime.slice(0, 5) : '')
    setOutTime(fresh.outTime ? fresh.outTime.slice(0, 5) : '')
    await loadLog(sewadarId)
    return fresh
  }

  /* A different sewa type is a different row, so the day is read again. */
  const onSewaTypeChange = async (value) => {
    setSewaType(value)
    setLocalError('')
    if (selected && date && !dateProblem(date)) {
      try {
        await loadDay(selected.sewadarId, date, value)
      } catch (err) {
        setLocalError(errorMessage(err, 'Could not read that day'))
      }
    }
  }

  const onDateChange = async (value) => {
    const next = value || ''
    setDate(next)
    // Say why straight away rather than waiting for Save to refuse it.
    setLocalError(next ? dateProblem(next) || '' : '')
    if (selected && next && !dateProblem(next)) {
      try {
        await loadDay(selected.sewadarId, next)
      } catch (err) {
        setLocalError(errorMessage(err, 'Could not read that day'))
      }
    }
  }

  const onSearch = async (event) => {
    event.preventDefault()
    setLocalError('')
    const term = query.trim()
    if (term.length < 2) {
      setLocalError('Enter at least 2 characters of a badge number, name, mobile or Aadhaar.')
      return
    }
    setSearching(true)
    setSelected(null)
    setHits(null)
    try {
      const found = await attendanceApi.lookup(term, sewaType, date)
      if (found.length === 0) {
        setLocalError(`No sewadar found for "${term}" in the zones you can reach.`)
      } else if (found.length === 1) {
        await loadDay(found[0].sewadarId, date)
      } else {
        setHits(found)
      }
    } catch (err) {
      setLocalError(errorMessage(err, 'Search failed'))
    } finally {
      setSearching(false)
    }
  }

  const clearAll = () => {
    setQuery('')
    setHits(null)
    setSelected(null)
    setLog([])
    setInTime('')
    setOutTime('')
    setRemarks('')
    setLocalError('')
  }

  /** Reset puts the form back to the opening day as stored, not to empty. */
  const resetForm = () => {
    setLocalError('')
    setRemarks('')
    const back = windowEmpty ? '' : windowEnd
    setDate(back)
    if (selected && back) {
      loadDay(selected.sewadarId, back).catch(() => {})
    }
  }

  /*
   * One button for the whole day. Check in and check out are two calls on the
   * server, but "I forgot to record Tuesday" is one thing to do, so the screen does
   * whichever halves are still missing and reports what it did.
   */
  const save = async () => {
    setLocalError('')

    // Checked again here, not only on the field: the date could have been typed
    // straight into the box, which no min/max attribute stops.
    const badDate = dateProblem(date)
    if (badDate) {
      setLocalError(badDate)
      return
    }
    if (!inTime && !selected.checkedIn) {
      setLocalError('Enter the check in time.')
      return
    }
    if (inTime && outTime && outTime <= inTime) {
      setLocalError('The check out time has to be later than the check in time.')
      return
    }

    setSaving(true)
    const done = []
    try {
      if (!selected.checkedIn) {
        const saved = await attendanceApi.checkIn({
          sewadarId: selected.sewadarId,
          sewaType,
          attendanceDate: date,
          time: inTime,
          remarks: remarks.trim() || null,
        })
        done.push(`checked in at ${pretty(saved.inTime)}`)
      }
      if (outTime && !selected.checkedOut) {
        const saved = await attendanceApi.checkOut({
          sewadarId: selected.sewadarId,
          sewaType,
          attendanceDate: date,
          time: outTime,
          remarks: remarks.trim() || null,
        })
        done.push(`checked out at ${pretty(saved.outTime)} (${hoursLabel(saved.hours)})`)
      }

      const fresh = await loadDay(selected.sewadarId, date)
      if (done.length === 0) {
        setLocalError('That day is already recorded. Nothing left to enter.')
      } else {
        setRemarks('')
        onNotice(`${fresh.name} ${done.join(' and ')} on ${longDate(date)}.`)
      }
    } catch (err) {
      // Half of it may have gone in before the failure, so re-read before reporting.
      await loadDay(selected.sewadarId, date).catch(() => {})
      const message = errorMessage(err, 'Could not save that day')
      setLocalError(done.length ? `${done.join(' and ')}, but then: ${message}` : message)
    } finally {
      setSaving(false)
    }
  }

  const complete = selected?.checkedIn && selected?.checkedOut

  const dayStatus = !selected
    ? '-'
    : complete
      ? `${pretty(selected.inTime)} - ${pretty(selected.outTime)}`
      : selected.checkedIn
        ? `In at ${pretty(selected.inTime)}, no check out`
        : 'Nothing recorded'

  return (
    <div className="bd">
      <div className="bd-head">
        <div className="bd-head-row">
          <div>
            <h1 className="bd-title">Manage Past Attendance</h1>
            <p className="bd-sub">
              Mark or update attendance for a past date in {monthName()}.
            </p>
          </div>
        </div>
      </div>

      {windowEmpty && (
        <Alert kind="warn">
          {monthName()} has no past day yet - today is the first of the month. Today&apos;s
          attendance is marked under <strong>Mark Attendance</strong>.
        </Alert>
      )}

      {/* The design arrives with a sewadar already chosen; this tab has to find one. */}
      <section className="bd-card bd-find">
        <form className="mark-search" onSubmit={onSearch}>
          <div className="mark-search-field">
            <span className="search-icon">⌕</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search the sewadar by Badge No, Name, Mobile No or Aadhaar Card..."
              aria-label="Search sewadar"
            />
            {query && (
              <button type="button" className="mark-clear" onClick={clearAll} aria-label="Clear">
                ✕
              </button>
            )}
          </div>
          <button type="submit" className="btn" disabled={searching}>
            {searching ? 'Searching...' : 'Search'}
          </button>
          <button type="button" className="btn secondary" onClick={clearAll} disabled={searching}>
            Reset
          </button>
        </form>
      </section>

      <Alert kind="error" onClose={() => setLocalError('')}>
        {localError}
      </Alert>

      {searching && <Spinner label="Searching" />}

      {hits && (
        <section className="bd-card">
          <header className="bd-card-head">
            <h2>{hits.length} matches — pick one</h2>
          </header>
          <ul className="hit-list">
            {hits.map((h) => (
              <li key={h.sewadarId}>
                <button
                  type="button"
                  onClick={() => {
                    setHits(null)
                    loadDay(h.sewadarId, date).catch((err) =>
                      setLocalError(errorMessage(err, 'Could not read that day')),
                    )
                  }}
                >
                  <Avatar
                    kind="sewadars"
                    id={h.sewadarId}
                    stamp={h.photoUpdatedAt}
                    name={h.name}
                    size={38}
                  />
                  <span className="hit-body">
                    <strong>{h.name}</strong>
                    <span className="muted">
                      {h.badgeNumber} · {h.zoneName}
                      {h.mobile ? ` · ${h.mobile}` : ''}
                    </span>
                  </span>
                  <span className="hit-state">
                    {h.checkedOut ? 'Recorded' : h.checkedIn ? 'In only' : 'Not marked'}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {selected && (
        <>
          <section className="bd-card">
            {/* ---------- who ---------- */}
            <div className="bd-person">
              <Avatar
                kind="sewadars"
                id={selected.sewadarId}
                stamp={selected.photoUpdatedAt}
                name={selected.name}
                size={96}
              />
              <div className="bd-person-main">
                <h2>{selected.name}</h2>
                <span className={`bd-pill ${selected.active ? 'on' : 'off'}`}>
                  <i aria-hidden="true" />
                  {selected.active ? 'Active' : 'Inactive'}
                </span>
                <p className="bd-role">Sewadar</p>
              </div>
            </div>

            <div className="bd-facts">
              <Fact icon={<IdCard size={19} />} label="Badge No" value={selected.badgeNumber} />
              <Fact icon={<MapPin size={19} />} label="Zone" value={selected.zoneName || '-'} />
              <Fact
                icon={<Map size={19} />}
                label="Area / Point"
                value={[selected.area, selected.centerPoint].filter(Boolean).join(' / ') || '-'}
              />
              <Fact
                icon={<CalendarDays size={19} />}
                label={date ? `Status on ${shortDate(date)}` : 'Status'}
                value={dayStatus}
              />
            </div>

            {/* ---------- what happened that day ---------- */}
            <div className="bd-form">
              <div className="bd-fields">
                <div className="bd-field">
                  <label htmlFor="past-date">
                    Attendance Date <span className="req">*</span>
                  </label>
                  <input
                    id="past-date"
                    type="date"
                    value={date}
                    min={windowStart}
                    max={windowEnd}
                    disabled={windowEmpty}
                    onChange={(e) => onDateChange(e.target.value)}
                  />
                  <span className="bd-chip">
                    <CalendarDays size={14} aria-hidden="true" />
                    {date ? relativeLabel(date) : 'No date'}
                  </span>
                  <span className="bd-hint">
                    {shortDate(windowStart)} to {shortDate(windowEnd)}
                  </span>
                </div>

                <div className="bd-field">
                  <label htmlFor="past-sewa-type">Sewa Type</label>
                  <select
                    id="past-sewa-type"
                    value={sewaType}
                    onChange={(e) => onSewaTypeChange(e.target.value)}
                  >
                    {sewaTypes.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                  <span className="bd-hint">Which sewa this day was</span>
                </div>

                <div className="bd-field">
                  <label htmlFor="past-in">
                    Check In Time <span className="req">*</span>
                  </label>
                  <div className="bd-time">
                    <input
                      id="past-in"
                      type="time"
                      value={inTime}
                      disabled={selected.checkedIn}
                      onChange={(e) => setInTime(e.target.value)}
                    />
                  </div>
                  {selected.checkedIn && <span className="bd-hint">Already recorded</span>}
                </div>

                <div className="bd-field">
                  <label htmlFor="past-out">Check Out Time</label>
                  <div className="bd-time">
                    <input
                      id="past-out"
                      type="time"
                      value={outTime}
                      disabled={selected.checkedOut}
                      onChange={(e) => setOutTime(e.target.value)}
                    />
                  </div>
                  <span className="bd-hint">
                    {selected.checkedOut
                      ? 'Already recorded'
                      : 'Leave blank if check out is not available yet'}
                  </span>
                </div>

                <div className="bd-field bd-field-wide">
                  <label htmlFor="past-remarks">
                    Remarks <span className="opt">(Optional)</span>
                  </label>
                  <textarea
                    id="past-remarks"
                    rows={3}
                    maxLength={REMARKS_MAX}
                    value={remarks}
                    placeholder="Add any remarks (e.g. manual entry, field duty, system issue)..."
                    onChange={(e) => setRemarks(e.target.value)}
                  />
                  <span className="bd-count">
                    {remarks.length}/{REMARKS_MAX}
                  </span>
                </div>

                <div className="bd-actions">
                  <button
                    type="button"
                    className="btn"
                    onClick={save}
                    disabled={saving || complete || !selected.active || !!dateProblem(date)}
                  >
                    <Save size={17} aria-hidden="true" />
                    {saving ? 'Saving...' : complete ? 'Day already recorded' : 'Save Attendance'}
                  </button>
                  <button
                    type="button"
                    className="btn secondary"
                    onClick={resetForm}
                    disabled={saving}
                  >
                    <RotateCcw size={17} aria-hidden="true" />
                    Reset
                  </button>
                </div>
              </div>

              <aside className="bd-note">
                <h3>
                  <Info size={18} aria-hidden="true" />
                  Note
                </h3>
                <ul>
                  <li>Use this option to mark attendance for previous dates only.</li>
                  <li>Fill check in time (required).</li>
                  <li>Fill check out time if available.</li>
                  <li>Add remarks for manual entries if needed.</li>
                </ul>
              </aside>
            </div>

            {!selected.active && (
              <Alert kind="warn">
                This sewadar is inactive, so attendance cannot be entered.
              </Alert>
            )}
            {complete && (
              <Alert kind="info">
                {longDate(date)} is fully recorded for {selected.name}. To change it, edit the
                entry under <strong>Records</strong>.
              </Alert>
            )}
          </section>

          {/* ---------- what is already there ---------- */}
          <section className="bd-card">
            <header className="bd-card-head">
              <h2>
                <span className="bd-card-icon">
                  <CalendarCheck size={18} aria-hidden="true" />
                </span>
                Recent Attendance
              </h2>
              <button type="button" className="bd-viewall" onClick={onViewHistory}>
                View All
                <ArrowRight size={16} aria-hidden="true" />
              </button>
            </header>
            <div className="table-wrap">
              <table className="table-md">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Check In</th>
                    <th>Check Out</th>
                    <th>Total Hours</th>
                    <th className="col-status">Status</th>
                    <th>Remarks</th>
                  </tr>
                </thead>
                <tbody>
                  {log.length === 0 ? (
                    <EmptyRow colSpan={6}>No attendance recorded yet</EmptyRow>
                  ) : (
                    log.map((row) => (
                      <tr key={row.id} className={row.attendanceDate === date ? 'row-focus' : ''}>
                        <td>{shortDate(row.attendanceDate)}</td>
                        <td>{pretty(row.inTime) || '-'}</td>
                        <td>{pretty(row.outTime) || '-'}</td>
                        <td>{hoursLabel(row.hours)}</td>
                        <td>
                          <Badge value={row.status} label={row.statusLabel} />
                        </td>
                        <td className="muted">{row.remarks || '-'}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </div>
  )
}

/** One of the four facts above the form: icon tile, label, value. */
function Fact({ icon, label, value }) {
  return (
    <div className="bd-fact">
      <span className="bd-fact-icon">{icon}</span>
      <span className="bd-fact-text">
        <span className="bd-fact-label">{label}</span>
        <strong className="bd-fact-value">{value}</strong>
      </span>
    </div>
  )
}
