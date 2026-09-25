import { useCallback, useEffect, useMemo, useState } from 'react'
import { attendanceApi, sewadarApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import { Avatar } from '../../components/Photo'
import { EmptyRow, Field } from '../../components/Bits'
import { todayIso } from '../../dates'

function pretty(time) {
  if (!time) return '-'
  const [h, m] = time.split(':')
  const hour = Number(h)
  const suffix = hour < 12 ? 'AM' : 'PM'
  const twelve = hour % 12 === 0 ? 12 : hour % 12
  return `${String(twelve).padStart(2, '0')}:${m} ${suffix}`
}

/**
 * Zone wise check in and check out. Pick a zone, tick the sewadars present, then
 * check the whole selection in or out in one call.
 */
export default function ZoneAttendance({ zones, sewaTypes, sewaType, setSewaType, onNotice, onError }) {
  // The date and time are this sheet's own; the sewa type is the shared one.
  const [header, setHeader] = useState({
    zoneId: '',
    attendanceDate: todayIso(),
    time: '',
  })
  const [rows, setRows] = useState([])
  const [picked, setPicked] = useState(() => new Set())
  const [filter, setFilter] = useState('')
  const [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)

  useEffect(() => {
    if (zones.length > 0 && !header.zoneId) {
      setHeader((h) => ({ ...h, zoneId: String(zones[0].id) }))
    }
  }, [zones, header.zoneId])

  /**
   * The sheet needs each sewadar plus their status for the chosen date and sewa
   * type, so the roster comes from the sewadar endpoint and the statuses from the
   * attendance search in one go, rather than one status call per row.
   */
  const load = useCallback(async () => {
    if (!header.zoneId) return
    setLoading(true)
    setResult(null)
    try {
      const [roster, marked] = await Promise.all([
        sewadarApi.forAttendance(header.zoneId),
        attendanceApi.search({
          zoneId: header.zoneId,
          sewaType: sewaType,
          fromDate: header.attendanceDate,
          toDate: header.attendanceDate,
          size: 200,
        }),
      ])
      const byId = new Map((marked.content || []).map((a) => [a.sewadarId, a]))
      setRows(
        roster.map((s) => {
          const a = byId.get(s.id)
          return {
            ...s,
            attendance: a || null,
            checkedIn: Boolean(a?.inTime),
            checkedOut: Boolean(a?.outTime),
          }
        }),
      )
      setPicked(new Set())
    } catch (err) {
      onError(errorMessage(err, 'Could not load the zone sheet'))
    } finally {
      setLoading(false)
    }
  }, [header.zoneId, sewaType, header.attendanceDate, onError])

  useEffect(() => {
    load()
  }, [load])

  const visible = useMemo(() => {
    const q = filter.trim().toLowerCase()
    if (!q) return rows
    return rows.filter(
      (r) =>
        r.name.toLowerCase().includes(q) ||
        r.badgeNumber.toLowerCase().includes(q) ||
        (r.area || '').toLowerCase().includes(q) ||
        (r.centerPoint || '').toLowerCase().includes(q) ||
        (r.mobile || '').includes(q),
    )
  }, [rows, filter])

  const toggle = (id) =>
    setPicked((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  // Selects the rows an action can actually affect, which is the useful default.
  const selectAll = (mode) => {
    if (mode === 'none') return setPicked(new Set())
    const eligible = visible.filter((r) =>
      mode === 'in' ? !r.checkedIn : r.checkedIn && !r.checkedOut,
    )
    setPicked(new Set(eligible.map((r) => r.id)))
  }

  const run = async (which) => {
    if (picked.size === 0) {
      onError('Tick at least one sewadar first.')
      return
    }
    setBusy(true)
    setResult(null)
    try {
      const payload = {
        sewadarIds: [...picked],
        sewaType: sewaType,
        attendanceDate: header.attendanceDate,
        time: header.time || null,
      }
      const call = which === 'in' ? attendanceApi.bulkCheckIn : attendanceApi.bulkCheckOut
      const res = await call(payload)
      setResult({ which, ...res })
      onNotice(
        `${res.succeeded} sewadar${res.succeeded === 1 ? '' : 's'} checked ${which === 'in' ? 'in' : 'out'}` +
          (res.skipped > 0 ? `, ${res.skipped} skipped.` : '.'),
      )
      await load()
    } catch (err) {
      onError(errorMessage(err, `Bulk check ${which} failed`))
    } finally {
      setBusy(false)
    }
  }

  const counts = useMemo(
    () => ({
      total: rows.length,
      in: rows.filter((r) => r.checkedIn).length,
      out: rows.filter((r) => r.checkedOut).length,
    }),
    [rows],
  )

  return (
    <div>
      <section className="panel">
        <header className="panel-head">
          <h2>Zone wise attendance</h2>
        </header>
        <div className="filters">
          <Field label="Zone" required>
            <select
              value={header.zoneId}
              onChange={(e) => setHeader({ ...header, zoneId: e.target.value })}
            >
              {zones.map((z) => (
                <option key={z.id} value={z.id}>
                  {z.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Sewa type" required>
            <select value={sewaType} onChange={(e) => setSewaType(e.target.value)}>
              {sewaTypes.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Date" required>
            <input
              type="date"
              max={todayIso()}
              value={header.attendanceDate}
              onChange={(e) => setHeader({ ...header, attendanceDate: e.target.value })}
            />
          </Field>
          <Field label="Time (blank = now)">
            <input
              type="time"
              value={header.time}
              onChange={(e) => setHeader({ ...header, time: e.target.value })}
            />
          </Field>
          <Field label="Filter the list">
            <input
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              placeholder="Name, badge, area or center"
            />
          </Field>
        </div>
      </section>

      {result && (
        <Alert kind={result.skipped > 0 ? 'warn' : 'success'} onClose={() => setResult(null)}>
          Checked {result.which === 'in' ? 'in' : 'out'} {result.succeeded} of {result.requested}.
          {result.skipped > 0 && (
            <ul style={{ margin: '8px 0 0', paddingLeft: 18 }}>
              {result.skippedRows.map((s) => (
                <li key={s.sewadarId}>
                  {s.sewadarName || `#${s.sewadarId}`}: {s.reason}
                </li>
              ))}
            </ul>
          )}
        </Alert>
      )}

      <section className="panel">
        <header className="panel-head">
          <h2>
            {counts.total} sewadars &middot; {counts.in} in &middot; {counts.out} out &middot;{' '}
            <span className="muted">{picked.size} selected</span>
          </h2>
          <div className="btn-row">
            <button type="button" className="btn ghost small" onClick={() => selectAll('in')}>
              Select not-checked-in
            </button>
            <button type="button" className="btn ghost small" onClick={() => selectAll('out')}>
              Select awaiting check-out
            </button>
            <button type="button" className="btn ghost small" onClick={() => selectAll('none')}>
              Clear
            </button>
          </div>
        </header>

        {loading ? (
          <Spinner label="Loading the zone sheet" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th style={{ width: 40 }}>
                    <input
                      type="checkbox"
                      aria-label="Select all listed"
                      checked={visible.length > 0 && picked.size === visible.length}
                      onChange={(e) =>
                        setPicked(e.target.checked ? new Set(visible.map((r) => r.id)) : new Set())
                      }
                    />
                  </th>
                  <th>Badge No</th>
                  <th style={{ width: 56 }}>Photo</th>
                  <th>Name</th>
                  <th>Area / Center</th>
                  <th>Check In</th>
                  <th>Check Out</th>
                  <th>Hours</th>
                </tr>
              </thead>
              <tbody>
                {visible.length === 0 ? (
                  <EmptyRow colSpan={8}>No active sewadars in this zone</EmptyRow>
                ) : (
                  visible.map((r) => (
                    <tr key={r.id} className={picked.has(r.id) ? 'row-picked' : undefined}>
                      <td>
                        <input
                          type="checkbox"
                          checked={picked.has(r.id)}
                          onChange={() => toggle(r.id)}
                          aria-label={`Select ${r.name}`}
                        />
                      </td>
                      <td>{r.badgeNumber}</td>
                      <td>
                        <Avatar
                          kind="sewadars"
                          id={r.id}
                          stamp={r.photoUpdatedAt}
                          name={r.name}
                          size={34}
                        />
                      </td>
                      <td>{r.name}</td>
                      <td className="muted">
                        {[r.area, r.centerPoint].filter(Boolean).join(' / ') || '-'}
                      </td>
                      <td>{pretty(r.attendance?.inTime)}</td>
                      <td>{pretty(r.attendance?.outTime)}</td>
                      <td>{r.attendance?.hours ?? '-'}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        <div className="btn-row" style={{ marginTop: 16 }}>
          <button
            type="button"
            className="btn ok"
            disabled={busy || picked.size === 0}
            onClick={() => run('in')}
          >
            ✓ Check In {picked.size > 0 ? `(${picked.size})` : ''}
          </button>
          <button
            type="button"
            className="btn danger"
            disabled={busy || picked.size === 0}
            onClick={() => run('out')}
          >
            ⇥ Check Out {picked.size > 0 ? `(${picked.size})` : ''}
          </button>
        </div>
        <p className="muted" style={{ marginBottom: 0, marginTop: 10, fontSize: 12.5 }}>
          A sewadar who cannot be marked (already checked in, not checked in yet, inactive) is
          reported as skipped rather than failing the whole batch.
        </p>
      </section>
    </div>
  )
}
