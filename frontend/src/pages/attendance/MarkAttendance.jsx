import { useEffect, useRef, useState } from 'react'
import { attendanceApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import { Avatar } from '../../components/Photo'
import { Badge, EmptyRow } from '../../components/Bits'

const clock = () =>
  new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', hour12: true })

const longDate = () =>
  new Date().toLocaleDateString('en-GB', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })

/** 09:12:00 -> 09:12 AM */
function pretty(time) {
  if (!time) return null
  const [h, m] = time.split(':')
  const hour = Number(h)
  const suffix = hour < 12 ? 'AM' : 'PM'
  const twelve = hour % 12 === 0 ? 12 : hour % 12
  return `${String(twelve).padStart(2, '0')}:${m} ${suffix}`
}

/** 8.75 -> 8h 45m */
function hoursLabel(hours) {
  if (hours == null) return '-'
  const h = Math.floor(hours)
  const m = Math.round((hours - h) * 60)
  return `${h}h ${String(m).padStart(2, '0')}m`
}

/**
 * Hides all but the last four digits on screen, so the number is not left on a
 * shared monitor. This is the on-screen half only: for a role that may not read the
 * number at all, the server has already masked it before it reaches the browser and
 * `aadharMasked` says so.
 */
function maskAadhar(value) {
  if (!value) return '-'
  const d = value.replace(/[^0-9]/g, '')
  if (d.length !== 12) return value
  return `XXXX XXXX ${d.slice(8)}`
}

export default function MarkAttendance({ sewaType, onNotice, onError }) {
  const [now, setNow] = useState(clock)
  const [query, setQuery] = useState('')

  const [hits, setHits] = useState(null)
  const [selected, setSelected] = useState(null)
  const [log, setLog] = useState([])

  const [searching, setSearching] = useState(false)
  const [acting, setActing] = useState(false)
  const [localError, setLocalError] = useState('')
  const [revealAadhar, setRevealAadhar] = useState(false)
  const searchBox = useRef(null)

  // Live clock in the header, like the reference screen.
  useEffect(() => {
    const timer = setInterval(() => setNow(clock()), 30000)
    return () => clearInterval(timer)
  }, [])

  const loadLog = (sewadarId) =>
    attendanceApi
      .search({ sewadarId, size: 10 })
      .then((res) => setLog(res.content || []))
      .catch(() => setLog([]))

  /*
   * The selected sewadar is re-read whenever the sewa type changes, because the
   * card and the two buttons describe one particular (sewadar, date, sewa type)
   * row - and that is a different row the moment the type does.
   */
  useEffect(() => {
    if (!selected) return
    let cancelled = false
    attendanceApi
      .status(selected.sewadarId, sewaType)
      .then((fresh) => {
        if (!cancelled) setSelected(fresh)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
    // Keyed on the id, not the object: refresh() replaces `selected` after every
    // action, and depending on the object itself would loop.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sewaType, selected?.sewadarId])

  const pick = async (hit) => {
    setSelected(hit)
    setRevealAadhar(false)
    setHits(null)
    await loadLog(hit.sewadarId)
  }

  const onSearch = async (event) => {
    event.preventDefault()
    setLocalError('')
    const term = query.trim()
    if (term.length < 2) {
      setLocalError('Enter at least 2 characters of a GR. No, name, mobile or Aadhaar.')
      return
    }
    setSearching(true)
    setSelected(null)
    setHits(null)
    try {
      const found = await attendanceApi.lookup(term, sewaType)
      if (found.length === 0) {
        setLocalError(`No sewadar found for "${term}" in the zones you can reach.`)
      } else if (found.length === 1) {
        await pick(found[0])
      } else {
        setHits(found)
      }
    } catch (err) {
      setLocalError(errorMessage(err, 'Search failed'))
    } finally {
      setSearching(false)
    }
  }

  const clear = () => {
    setQuery('')
    setHits(null)
    setSelected(null)
    setLog([])
    setLocalError('')
    // The next thing anyone does here is look up another sewadar, so the cursor
    // goes back to the search box rather than being put there by hand.
    searchBox.current?.focus()
  }

  /*
   * Marking someone finishes that person, so the screen goes back to an empty
   * search rather than holding them on it. There is a queue in front of this desk:
   * leaving the last sewadar on screen made the next person's check-in look like a
   * second action on the one before, and left a Check Out button in view for
   * someone who had only just arrived.
   */
  const act = async (which) => {
    setActing(true)
    setLocalError('')
    try {
      const call = which === 'in' ? attendanceApi.checkIn : attendanceApi.checkOut
      const saved = await call({ sewadarId: selected.sewadarId, sewaType })
      onNotice(
        which === 'in'
          ? `${saved.sewadarName} checked in at ${pretty(saved.inTime)}.`
          : `${saved.sewadarName} checked out at ${pretty(saved.outTime)} — ${hoursLabel(saved.hours)}.`,
      )
      clear()
    } catch (err) {
      setLocalError(errorMessage(err, `Could not check ${which === 'in' ? 'in' : 'out'}`))
    } finally {
      setActing(false)
    }
  }

  /*
   * What the two state pills say on hover. Everything the pills used to print
   * inline lives here instead, so the pill itself stays one short phrase.
   */
  const checkedInDetail = !selected
    ? ''
    : selected.checkedIn
      ? [`Checked in at ${pretty(selected.inTime)}`, selected.centerPoint || selected.zoneName]
          .filter(Boolean)
          .join(' · ')
      : 'Not checked in yet'

  const checkedOutDetail = !selected
    ? ''
    : selected.checkedOut
      ? [
          `Checked out at ${pretty(selected.outTime)}`,
          selected.hours != null ? `${hoursLabel(selected.hours)} of sewa` : null,
        ]
          .filter(Boolean)
          .join(' · ')
      : 'Not checked out yet'

  return (
    <div>
      <div className="mark-head">
        <div>
          <h1 className="mark-title">Mark Attendance</h1>
          <p className="mark-sub">
            Search a sewadar by GR. No, Name, Mobile Number or Aadhaar Card to check in or
            check out.
          </p>
        </div>
        <div className="mark-clock">
          <span className="mark-clock-icon">🗓</span>
          <div>
            <span className="mark-clock-date">{longDate()}</span>
            <strong className="mark-clock-time">{now}</strong>
          </div>
        </div>
      </div>

      {/* ---------- search ---------- */}
      <section className="panel">
        <form className="mark-search" onSubmit={onSearch}>
          <div className="mark-search-field">
            <span className="search-icon">⌕</span>
            <input
              autoFocus
              ref={searchBox}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by GR. No, Name, Mobile No or Aadhaar Card..."
              aria-label="Search sewadar"
            />
            {query && (
              <button type="button" className="mark-clear" onClick={clear} aria-label="Clear">
                ✕
              </button>
            )}
          </div>
          <button type="submit" className="btn" disabled={searching}>
            {searching ? 'Searching...' : 'Search'}
          </button>
          <button type="button" className="btn secondary" onClick={clear} disabled={searching}>
            Reset
          </button>
        </form>

        <p className="mark-example">
          Example: B00123 or Amit or 9876543210 or 1234 5678 9012 · marking for today; a day
          that was missed goes in under <strong>Manage Past Attendance</strong>.
        </p>

      </section>

      <Alert kind="error" onClose={() => setLocalError('')}>
        {localError}
      </Alert>

      {searching && <Spinner label="Searching" />}

      {/* ---------- multiple hits ---------- */}
      {hits && (
        <section className="panel">
          <header className="panel-head">
            <h2>{hits.length} matches — pick one</h2>
          </header>
          <ul className="hit-list">
            {hits.map((h) => (
              <li key={h.sewadarId}>
                <button type="button" onClick={() => pick(h)}>
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
                    {h.checkedOut ? 'Checked out' : h.checkedIn ? 'Checked in' : 'Not marked'}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* ---------- the selected sewadar ---------- */}
      {selected && (
        <>
          <section className="panel">
            <div className="person">
              <Avatar
                kind="sewadars"
                id={selected.sewadarId}
                stamp={selected.photoUpdatedAt}
                name={selected.name}
                size={84}
              />
              <div className="person-facts">
                <div className="person-name">
                  <h2>{selected.name}</h2>
                  <Badge
                    value={selected.active ? 'active' : 'inactive'}
                    label={selected.active ? 'Active' : 'Inactive'}
                  />
                </div>
                <dl className="person-kv">
                  <dt>GR. No</dt>
                  <dd>{selected.badgeNumber}</dd>
                  <dt>F/H Name</dt>
                  <dd>{selected.fatherOrHusbandName || '-'}</dd>
                  <dt>Zone</dt>
                  <dd>{selected.zoneName || '-'}</dd>
                  <dt>Area / Point</dt>
                  <dd>
                    {[selected.area, selected.centerPoint].filter(Boolean).join(' / ') || '-'}
                  </dd>
                  <dt>Mobile No</dt>
                  <dd>{selected.mobile || '-'}</dd>
                  <dt>Aadhaar No</dt>
                  <dd>
                    {selected.aadharMasked || revealAadhar
                      ? selected.aadharNumber || '-'
                      : maskAadhar(selected.aadharNumber)}
                    {/* Nothing to reveal when the server sent only the last four. */}
                    {selected.aadharNumber && !selected.aadharMasked && (
                      <button
                        type="button"
                        className="linkish"
                        onClick={() => setRevealAadhar((v) => !v)}
                      >
                        {revealAadhar ? 'hide' : 'show'}
                      </button>
                    )}
                  </dd>
                </dl>
              </div>
            </div>

            {/*
              Two status pills, not panels. They are the height of the Check In and
              Check Out buttons below and only as wide as their own text, so the state
              reads at a glance without taking a whole band of the screen. Rounded to
              a pill rather than the buttons' 9px corner on purpose: they are the same
              size as the buttons but must not look tappable.

              Nothing is shown for a time or a place that does not exist yet, which is
              what removed the stray "-" the old panels displayed before check out.
            */}
            {/*
              Two states over two buttons, on the same two-column grid, so all four
              cells are the same width and line up in a block.

              Each state shows only what it is - Checked In, Checked Out - and keeps
              the time, the place and the hours in its tooltip. Those details were
              printed inline before, which made the two pills different widths and
              pushed the whole row out of line with the buttons under it.
            */}
            <div className="state-grid">
              <div
                className={`state ${selected.checkedIn ? 'ok' : 'idle'}`}
                title={checkedInDetail}
              >
                <span className="state-icon">{selected.checkedIn ? '✓' : '○'}</span>
                <span className="state-text">
                  {selected.checkedIn ? 'Checked In' : 'Not Checked In'}
                </span>
              </div>

              {/*
                Only once they are in. Before that, a red "Not Yet Checked Out" is
                true but says nothing: nobody checks out before checking in, and it
                read as a second thing needing attention.
              */}
              {selected.checkedIn && (
                <div
                  className={`state ${selected.checkedOut ? 'ok' : 'bad'}`}
                  title={checkedOutDetail}
                >
                  <span className="state-icon">{selected.checkedOut ? '✓' : '🕐'}</span>
                  <span className="state-text">
                    {selected.checkedOut ? 'Checked Out' : 'Not Yet Checked Out'}
                  </span>
                </div>
              )}
            </div>

            {/*
              One button, never two. A sewadar who has not arrived can only check in
              and one who has can only check out, so offering both and greying out
              the wrong one asked the person at the desk to work out which applied.
              Once the day is marked there is nothing left to press at all.
            */}
            <div className="act-single">
              {!selected.checkedIn && (
                <button
                  type="button"
                  className="btn ok"
                  disabled={acting || !selected.active}
                  onClick={() => act('in')}
                >
                  {acting ? 'Checking in...' : '✓ Check In'}
                </button>
              )}
              {selected.checkedIn && !selected.checkedOut && (
                <button
                  type="button"
                  className="btn danger"
                  disabled={acting}
                  onClick={() => act('out')}
                >
                  {acting ? 'Checking out...' : '⇥ Check Out'}
                </button>
              )}
              {selected.checkedIn && selected.checkedOut && (
                <p className="act-done">
                  Marked for today — in at {pretty(selected.inTime)}, out at{' '}
                  {pretty(selected.outTime)}
                  {selected.hours != null ? ` · ${hoursLabel(selected.hours)}` : ''}.
                </p>
              )}
            </div>
            {!selected.active && (
              <Alert kind="warn">
                This sewadar is inactive, so attendance cannot be marked.
              </Alert>
            )}
          </section>

          <section className="panel">
            <header className="panel-head">
              <h2>Attendance Log</h2>
              <span className="muted" style={{ fontSize: 12.5 }}>
                last {log.length} entr{log.length === 1 ? 'y' : 'ies'}
              </span>
            </header>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Sewa Type</th>
                    <th>Check In</th>
                    <th>Check Out</th>
                    <th>Work Hours</th>
                    <th>Location</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {log.length === 0 ? (
                    <EmptyRow colSpan={7}>No attendance recorded yet</EmptyRow>
                  ) : (
                    log.map((row) => (
                      <tr key={row.id}>
                        <td>{row.attendanceDate}</td>
                        <td>{row.sewaTypeLabel}</td>
                        <td>{pretty(row.inTime) || '-'}</td>
                        <td>{pretty(row.outTime) || '-'}</td>
                        <td>{hoursLabel(row.hours)}</td>
                        <td>{row.zoneName}</td>
                        <td>
                          <Badge value={row.status} label={row.statusLabel} />
                        </td>
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
