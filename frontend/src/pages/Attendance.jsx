import { useCallback, useEffect, useMemo, useState } from 'react'
import { attendanceApi, metaApi, sewadarApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { Badge, EmptyRow, Field, Pager } from '../components/Bits'

const today = () => new Date().toISOString().slice(0, 10)
const monthStart = () => {
  const now = new Date()
  return new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10)
}

export default function Attendance() {
  const { canMarkAttendance, isSewadar, canManageSewadars } = useAuth()
  const [tab, setTab] = useState(canMarkAttendance ? 'mark' : 'records')

  const [zones, setZones] = useState([])
  const [options, setOptions] = useState({ sewaTypes: [], attendanceStatuses: [] })
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    zoneApi.list(false).then(setZones).catch(() => setZones([]))
    metaApi.options().then(setOptions).catch(() => {})
  }, [])

  return (
    <div>
      <Alert kind="error" onClose={() => setError('')}>
        {error}
      </Alert>
      <Alert kind="success" onClose={() => setNotice('')}>
        {notice}
      </Alert>

      <div className="tabs">
        {canMarkAttendance && (
          <button
            type="button"
            className={tab === 'mark' ? 'active' : ''}
            onClick={() => setTab('mark')}
          >
            Mark attendance
          </button>
        )}
        <button
          type="button"
          className={tab === 'records' ? 'active' : ''}
          onClick={() => setTab('records')}
        >
          {isSewadar ? 'My attendance' : 'Attendance records'}
        </button>
      </div>

      {tab === 'mark' && canMarkAttendance ? (
        <MarkSheet
          zones={zones}
          options={options}
          onError={setError}
          onNotice={(message) => {
            setNotice(message)
            setError('')
          }}
        />
      ) : (
        <Records
          zones={zones}
          options={options}
          isSewadar={isSewadar}
          canEdit={canMarkAttendance}
          canDelete={canManageSewadars}
          onError={setError}
          onNotice={setNotice}
        />
      )}
    </div>
  )
}

/** Sheet that marks a whole zone for one date and sewa type in a single call. */
function MarkSheet({ zones, options, onError, onNotice }) {
  const [header, setHeader] = useState({
    attendanceDate: today(),
    sewaType: 'ROSTER_SEWA',
    zoneId: '',
    inTime: '',
    outTime: '',
  })
  const [sewadars, setSewadars] = useState([])
  const [entries, setEntries] = useState({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (zones.length > 0 && !header.zoneId) {
      setHeader((current) => ({ ...current, zoneId: String(zones[0].id) }))
    }
  }, [zones, header.zoneId])

  const loadSheet = useCallback(() => {
    if (!header.zoneId) return
    setLoading(true)
    sewadarApi
      .forAttendance(header.zoneId)
      .then((list) => {
        setSewadars(list)
        // Default everyone to Present so a normal day is one click.
        const seeded = {}
        list.forEach((s) => {
          seeded[s.id] = { status: 'PRESENT', remarks: '' }
        })
        setEntries(seeded)
      })
      .catch((err) => onError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [header.zoneId, onError])

  useEffect(() => {
    loadSheet()
  }, [loadSheet])

  const setAll = (status) => {
    setEntries((current) => {
      const next = {}
      Object.keys(current).forEach((id) => {
        next[id] = { ...current[id], status }
      })
      return next
    })
  }

  const counts = useMemo(() => {
    const tally = {}
    Object.values(entries).forEach((entry) => {
      tally[entry.status] = (tally[entry.status] || 0) + 1
    })
    return tally
  }, [entries])

  const onSubmit = async (event) => {
    event.preventDefault()
    setSaving(true)
    try {
      const payload = {
        attendanceDate: header.attendanceDate,
        sewaType: header.sewaType,
        inTime: header.inTime || null,
        outTime: header.outTime || null,
        entries: sewadars.map((s) => ({
          sewadarId: s.id,
          status: entries[s.id]?.status || 'PRESENT',
          remarks: entries[s.id]?.remarks || null,
        })),
      }
      const saved = await attendanceApi.markBulk(payload)
      onNotice(`Attendance saved for ${saved.length} sewadars on ${header.attendanceDate}.`)
    } catch (err) {
      onError(errorMessage(err, 'Could not save the attendance sheet'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <div className="card">
        <h3>Sewa sheet</h3>
        <div className="filters">
          <Field label="Date" required>
            <input
              type="date"
              max={today()}
              value={header.attendanceDate}
              onChange={(e) => setHeader({ ...header, attendanceDate: e.target.value })}
              required
            />
          </Field>
          <Field label="Sewa type" required>
            <select
              value={header.sewaType}
              onChange={(e) => setHeader({ ...header, sewaType: e.target.value })}
            >
              {(options.sewaTypes || []).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Zone" required>
            <select
              value={header.zoneId}
              onChange={(e) => setHeader({ ...header, zoneId: e.target.value })}
              required
            >
              {zones.map((zone) => (
                <option key={zone.id} value={zone.id}>
                  {zone.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="In time">
            <input
              type="time"
              value={header.inTime}
              onChange={(e) => setHeader({ ...header, inTime: e.target.value })}
            />
          </Field>
          <Field label="Out time">
            <input
              type="time"
              value={header.outTime}
              onChange={(e) => setHeader({ ...header, outTime: e.target.value })}
            />
          </Field>
        </div>
        <p className="muted" style={{ marginBottom: 0, marginTop: 12 }}>
          In and out time apply to everyone on the sheet and produce the sewa hours. Marking the
          same sewadar, date and sewa type again updates the existing entry instead of duplicating
          it.
        </p>
      </div>

      <div className="card">
        <div className="card-head">
          <h3>
            {sewadars.length} sewadar{sewadars.length === 1 ? '' : 's'} on this sheet
          </h3>
          <div className="btn-row">
            <button type="button" className="btn ghost small" onClick={() => setAll('PRESENT')}>
              All present
            </button>
            <button type="button" className="btn ghost small" onClick={() => setAll('ABSENT')}>
              All absent
            </button>
            <button type="button" className="btn ghost small" onClick={loadSheet}>
              Reset
            </button>
          </div>
        </div>

        {Object.keys(counts).length > 0 && (
          <div className="chips" style={{ marginBottom: 12 }}>
            {Object.entries(counts).map(([status, count]) => (
              <span key={status} className={`badge ${status.toLowerCase()}`}>
                {status.replace(/_/g, ' ')}: {count}
              </span>
            ))}
          </div>
        )}

        {loading ? (
          <Spinner label="Loading the sheet" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th style={{ width: 50 }}>#</th>
                  <th>Badge</th>
                  <th>Sewadar</th>
                  <th>Department</th>
                  <th style={{ width: 150 }}>Status</th>
                  <th>Remarks</th>
                </tr>
              </thead>
              <tbody>
                {sewadars.length === 0 ? (
                  <EmptyRow colSpan={6}>No active sewadars in this zone</EmptyRow>
                ) : (
                  sewadars.map((sewadar, index) => (
                    <tr key={sewadar.id}>
                      <td>{index + 1}</td>
                      <td>{sewadar.badgeNumber}</td>
                      <td>{sewadar.name}</td>
                      <td className="muted">{sewadar.department || '-'}</td>
                      <td>
                        <select
                          value={entries[sewadar.id]?.status || 'PRESENT'}
                          onChange={(e) =>
                            setEntries((current) => ({
                              ...current,
                              [sewadar.id]: { ...current[sewadar.id], status: e.target.value },
                            }))
                          }
                        >
                          {(options.attendanceStatuses || []).map((option) => (
                            <option key={option.value} value={option.value}>
                              {option.label}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>
                        <input
                          placeholder="Optional"
                          value={entries[sewadar.id]?.remarks || ''}
                          onChange={(e) =>
                            setEntries((current) => ({
                              ...current,
                              [sewadar.id]: { ...current[sewadar.id], remarks: e.target.value },
                            }))
                          }
                        />
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        <div className="btn-row" style={{ marginTop: 16 }}>
          <button type="submit" className="btn" disabled={saving || sewadars.length === 0}>
            {saving ? 'Saving...' : `Save attendance for ${sewadars.length} sewadars`}
          </button>
        </div>
      </div>
    </form>
  )
}

/** Searchable list of existing entries with inline edit and delete. */
function Records({ zones, options, isSewadar, canEdit, canDelete, onError, onNotice }) {
  const [filters, setFilters] = useState({
    fromDate: monthStart(),
    toDate: today(),
    zoneId: '',
    sewaType: '',
    status: '',
  })
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  const params = useMemo(
    () => ({
      fromDate: filters.fromDate || undefined,
      toDate: filters.toDate || undefined,
      zoneId: filters.zoneId || undefined,
      sewaType: filters.sewaType || undefined,
      status: filters.status || undefined,
      page,
      size: 20,
    }),
    [filters, page],
  )

  const load = useCallback(() => {
    setLoading(true)
    attendanceApi
      .search(params)
      .then(setResult)
      .catch((err) => onError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [params, onError])

  useEffect(() => {
    load()
  }, [load])

  const onSave = async (event) => {
    event.preventDefault()
    setFormError('')
    setSaving(true)
    try {
      await attendanceApi.update(editing.id, {
        sewadarId: editing.sewadarId,
        attendanceDate: editing.attendanceDate,
        sewaType: editing.sewaType,
        status: editing.status,
        inTime: editing.inTime || null,
        outTime: editing.outTime || null,
        remarks: editing.remarks || null,
      })
      onNotice('Attendance entry updated')
      setEditing(null)
      load()
    } catch (err) {
      setFormError(errorMessage(err, 'Could not update the entry'))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row) => {
    try {
      await attendanceApi.remove(row.id)
      onNotice('Attendance entry deleted')
      load()
    } catch (err) {
      onError(errorMessage(err, 'Could not delete the entry'))
    }
  }

  const setFilter = (key) => (event) => {
    setPage(0)
    setFilters((current) => ({ ...current, [key]: event.target.value }))
  }

  return (
    <div>
      <div className="card">
        <h3>Filters</h3>
        <div className="filters">
          <Field label="From date">
            <input type="date" value={filters.fromDate} onChange={setFilter('fromDate')} />
          </Field>
          <Field label="To date">
            <input type="date" value={filters.toDate} onChange={setFilter('toDate')} />
          </Field>
          {!isSewadar && (
            <Field label="Zone">
              <select value={filters.zoneId} onChange={setFilter('zoneId')}>
                <option value="">All my zones</option>
                {zones.map((zone) => (
                  <option key={zone.id} value={zone.id}>
                    {zone.name}
                  </option>
                ))}
              </select>
            </Field>
          )}
          <Field label="Sewa type">
            <select value={filters.sewaType} onChange={setFilter('sewaType')}>
              <option value="">All</option>
              {(options.sewaTypes || []).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Status">
            <select value={filters.status} onChange={setFilter('status')}>
              <option value="">All</option>
              {(options.attendanceStatuses || []).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </Field>
        </div>
      </div>

      <div className="card">
        {loading ? (
          <Spinner label="Loading attendance" />
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Date</th>
                    {!isSewadar && <th>Sewadar</th>}
                    <th>Zone</th>
                    <th>Sewa type</th>
                    <th>Status</th>
                    <th>In</th>
                    <th>Out</th>
                    <th>Hours</th>
                    <th>Remarks</th>
                    <th>Marked by</th>
                    {(canEdit || canDelete) && <th>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {result?.content?.length ? (
                    result.content.map((row) => (
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
                        <td>{row.inTime || '-'}</td>
                        <td>{row.outTime || '-'}</td>
                        <td>{row.hours ?? '-'}</td>
                        <td className="muted">{row.remarks || '-'}</td>
                        <td className="muted">{row.markedBy || '-'}</td>
                        {(canEdit || canDelete) && (
                          <td>
                            <div className="btn-row">
                              {canEdit && (
                                <button
                                  type="button"
                                  className="btn ghost small"
                                  onClick={() => {
                                    setFormError('')
                                    setEditing({
                                      ...row,
                                      inTime: row.inTime || '',
                                      outTime: row.outTime || '',
                                      remarks: row.remarks || '',
                                    })
                                  }}
                                >
                                  Edit
                                </button>
                              )}
                              {canDelete && (
                                <button
                                  type="button"
                                  className="btn danger small"
                                  onClick={() => onDelete(row)}
                                >
                                  Delete
                                </button>
                              )}
                            </div>
                          </td>
                        )}
                      </tr>
                    ))
                  ) : (
                    <EmptyRow colSpan={11}>No attendance in this range</EmptyRow>
                  )}
                </tbody>
              </table>
            </div>
            {result && (
              <Pager
                page={result.page}
                totalPages={result.totalPages}
                totalElements={result.totalElements}
                onChange={setPage}
              />
            )}
          </>
        )}
      </div>

      <Modal
        narrow
        title="Update attendance"
        open={Boolean(editing)}
        onClose={() => setEditing(null)}
        footer={
          <>
            <button type="button" className="btn ghost" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" form="attendance-edit" className="btn" disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </button>
          </>
        }
      >
        {editing && (
          <form id="attendance-edit" onSubmit={onSave}>
            <Alert kind="error">{formError}</Alert>
            <p style={{ marginTop: 0 }}>
              <strong>{editing.sewadarName}</strong> ({editing.badgeNumber}) &middot;{' '}
              {editing.zoneName}
            </p>
            <div className="form-grid">
              <Field label="Date" required>
                <input
                  type="date"
                  max={today()}
                  value={editing.attendanceDate}
                  onChange={(e) => setEditing({ ...editing, attendanceDate: e.target.value })}
                  required
                />
              </Field>
              <Field label="Sewa type" required>
                <select
                  value={editing.sewaType}
                  onChange={(e) => setEditing({ ...editing, sewaType: e.target.value })}
                >
                  {(options.sewaTypes || []).map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Status" required>
                <select
                  value={editing.status}
                  onChange={(e) => setEditing({ ...editing, status: e.target.value })}
                >
                  {(options.attendanceStatuses || []).map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="In time">
                <input
                  type="time"
                  value={editing.inTime}
                  onChange={(e) => setEditing({ ...editing, inTime: e.target.value })}
                />
              </Field>
              <Field label="Out time">
                <input
                  type="time"
                  value={editing.outTime}
                  onChange={(e) => setEditing({ ...editing, outTime: e.target.value })}
                />
              </Field>
              <Field label="Remarks" wide>
                <textarea
                  value={editing.remarks}
                  onChange={(e) => setEditing({ ...editing, remarks: e.target.value })}
                />
              </Field>
            </div>
          </form>
        )}
      </Modal>
    </div>
  )
}
