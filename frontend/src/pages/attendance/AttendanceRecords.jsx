import { useCallback, useEffect, useMemo, useState } from 'react'
import { attendanceApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import Alert from '../../components/Alert'
import Modal from '../../components/Modal'
import Spinner from '../../components/Spinner'
import { Badge, EmptyRow, Field, Pager } from '../../components/Bits'
import { monthStartIso, todayIso } from '../../dates'


function pretty(time) {
  if (!time) return '-'
  const [h, m] = time.split(':')
  const hour = Number(h)
  const suffix = hour < 12 ? 'AM' : 'PM'
  const twelve = hour % 12 === 0 ? 12 : hour % 12
  return `${String(twelve).padStart(2, '0')}:${m} ${suffix}`
}

/** Searchable attendance history with inline edit and delete. */
export default function AttendanceRecords({
  zones,
  sewaTypes,
  statuses,
  isSewadar,
  canEdit,
  canDelete,
  onNotice,
  onError,
}) {
  const [filters, setFilters] = useState({
    fromDate: monthStartIso(),
    toDate: todayIso(),
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

  const cols = 10 + (!isSewadar ? 1 : 0) + (canEdit || canDelete ? 1 : 0)

  return (
    <div>
      <section className="panel">
        <header className="panel-head">
          <h2>Filters</h2>
        </header>
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
              {sewaTypes.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Status">
            <select value={filters.status} onChange={setFilter('status')}>
              <option value="">All</option>
              {statuses.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </Field>
        </div>
      </section>

      <section className="panel">
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
                    <th>Check In</th>
                    <th>Check Out</th>
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
                        <td>{pretty(row.inTime)}</td>
                        <td>{pretty(row.outTime)}</td>
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
                    <EmptyRow colSpan={cols}>No attendance in this range</EmptyRow>
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
      </section>

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
                  max={todayIso()}
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
                  {sewaTypes.map((option) => (
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
                  {statuses.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Check in time">
                <input
                  type="time"
                  value={editing.inTime}
                  onChange={(e) => setEditing({ ...editing, inTime: e.target.value })}
                />
              </Field>
              <Field label="Check out time">
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
