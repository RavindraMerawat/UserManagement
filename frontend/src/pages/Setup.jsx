import { useCallback, useEffect, useState } from 'react'
import { setupApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { Badge, EmptyRow, Field, TabStrip } from '../components/Bits'

/**
 * Setup: the master lists a sewadar record is built from.
 *
 * <p>Three levels, in the order they nest - <b>Zone</b>, then <b>Area</b> inside a
 * zone, then <b>Satsang Point</b> inside an area. The sewadar form picks from these
 * rather than offering a free text box, which is what stopped "Geeta Vihar" and
 * "geeta vihar" becoming two different places.</p>
 *
 * <p>Reading follows the caller's zone scope, so a Zone Incharge opening Setup sees
 * only their own zones. Changing anything is Admin and Office Admin, and the server
 * is what enforces that - the buttons below only hide what would be refused.</p>
 */
const TABS = [
  { key: 'zones', label: 'Zone' },
  { key: 'areas', label: 'Area' },
  { key: 'points', label: 'Satsang Point' },
]

export default function Setup() {
  const { canManageSewadars } = useAuth()

  const [tab, setTab] = useState('zones')
  const [zones, setZones] = useState([])
  const [areas, setAreas] = useState([])
  const [points, setPoints] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  const load = useCallback(() => {
    setLoading(true)
    Promise.all([
      zoneApi.list(true).catch(() => []),
      setupApi.areas().catch(() => []),
      setupApi.points().catch(() => []),
    ])
      .then(([z, a, p]) => {
        setZones(z)
        setAreas(a)
        setPoints(p)
      })
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  useEffect(load, [load])

  const counts = {
    zones: zones.length,
    areas: areas.length,
    points: points.length,
  }

  const openNew = () => {
    setFormError('')
    if (tab === 'zones') setEditing({ kind: 'zones', name: '', code: '', active: true })
    if (tab === 'areas') {
      setEditing({ kind: 'areas', name: '', zoneId: zones[0]?.id ?? '', active: true })
    }
    if (tab === 'points') {
      setEditing({ kind: 'points', name: '', areaId: areas[0]?.id ?? '', active: true })
    }
  }

  const onSave = async (event) => {
    event.preventDefault()
    setFormError('')
    setSaving(true)
    try {
      const { kind, id } = editing
      if (kind === 'zones') {
        const payload = { name: editing.name, code: editing.code, active: editing.active }
        if (id) await zoneApi.update(id, payload)
        else await zoneApi.create(payload)
      }
      if (kind === 'areas') {
        const payload = { name: editing.name, zoneId: Number(editing.zoneId), active: editing.active }
        if (id) await setupApi.updateArea(id, payload)
        else await setupApi.createArea(payload)
      }
      if (kind === 'points') {
        const payload = { name: editing.name, areaId: Number(editing.areaId), active: editing.active }
        if (id) await setupApi.updatePoint(id, payload)
        else await setupApi.createPoint(payload)
      }
      setEditing(null)
      setNotice('Saved.')
      load()
    } catch (err) {
      setFormError(errorMessage(err, 'Could not save'))
    } finally {
      setSaving(false)
    }
  }

  const onRemove = async (kind, row) => {
    const what = kind === 'zones' ? 'zone' : kind === 'areas' ? 'area' : 'satsang point'
    if (!window.confirm(`Remove the ${what} "${row.name}"? Anything still using it is kept.`)) return
    try {
      if (kind === 'zones') await zoneApi.remove(row.id)
      if (kind === 'areas') await setupApi.removeArea(row.id)
      if (kind === 'points') await setupApi.removePoint(row.id)
      setNotice('Removed.')
      load()
    } catch (err) {
      setError(errorMessage(err, 'Could not remove it'))
    }
  }

  /**
   * One table, three shapes, always the same five columns: ID, Code, Name, Status,
   * Action.
   *
   * Only a zone actually has a code. An area and a satsang point are identified by
   * what they sit inside, so that column carries the parent and is labelled for it -
   * writing "Code" over a zone name would be a lie about the data. The point rows
   * put their zone underneath the area, which keeps the grid at five columns without
   * dropping anything the old six-column version showed.
   */
  const rowsFor = () => {
    if (tab === 'zones') {
      return { codeLabel: 'Code', rows: zones, code: (z) => z.code, under: () => null }
    }
    if (tab === 'areas') {
      return { codeLabel: 'Zone', rows: areas, code: (a) => a.zoneName, under: () => null }
    }
    return {
      codeLabel: 'Area',
      rows: points,
      code: (p) => p.areaName,
      under: (p) => p.zoneName,
    }
  }

  const { codeLabel, rows, code, under } = rowsFor()

  return (
    <div>
      <div className="page-head">
        <div>
          <h1 className="page-title">Setup</h1>
          <p className="page-sub">
            The lists a sewadar record is built from: zones, the areas inside them, and the
            satsang points inside those.
          </p>
        </div>
        {canManageSewadars && (
          <div className="page-actions">
            <button type="button" className="btn" onClick={openNew}>
              + Add {TABS.find((t) => t.key === tab)?.label}
            </button>
          </div>
        )}
      </div>

      <Alert kind="error" onClose={() => setError('')}>
        {error}
      </Alert>
      <Alert kind="success" onClose={() => setNotice('')}>
        {notice}
      </Alert>

      <TabStrip
        value={tab}
        onChange={setTab}
        tabs={TABS.map((t) => ({ ...t, count: counts[t.key] }))}
      />

      {loading ? (
        <Spinner label="Loading the setup lists" />
      ) : (
        <div className="table-wrap">
          <table className="table-md">
            <thead>
              <tr>
                <th className="col-id">ID</th>
                <th className="col-code">{codeLabel}</th>
                <th>Name</th>
                <th className="col-status">Status</th>
                {canManageSewadars && <th className="col-action">Action</th>}
                {/*
                  The spare width goes here, at the end. Without it the table
                  stretches to fill the card by growing whichever column has no
                  size of its own - which put a hand's width of nothing between
                  the name and its status.
                */}
                <th className="col-fill" aria-hidden="true" />
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 ? (
                <EmptyRow colSpan={canManageSewadars ? 6 : 5}>
                  {tab === 'zones'
                    ? 'No zones yet.'
                    : tab === 'areas'
                      ? 'No areas yet. Add one to a zone, then its satsang points.'
                      : 'No satsang points yet. Add an area first, then its points.'}
                </EmptyRow>
              ) : (
                rows.map((row) => (
                  <tr key={row.id}>
                    {/* The record's own id, not a row number - it is what the API and
                        the support logs call this thing. */}
                    <td className="col-id muted">{row.id}</td>
                    <td>
                      {code(row) || '-'}
                      {under(row) && <span className="cell-sub">{under(row)}</span>}
                    </td>
                    <td>
                      <strong>{row.name}</strong>
                    </td>
                    <td>
                      <Badge
                        value={row.active ? 'active' : 'inactive'}
                        label={row.active ? 'Active' : 'Retired'}
                      />
                    </td>
                    {canManageSewadars && (
                      <td>
                        {/* One row, never wrapped: two stacked buttons made every
                            row twice as tall as the text it held. */}
                        <div className="btn-row nowrap">
                          <button
                            type="button"
                            className="btn ghost small"
                            onClick={() => {
                              setFormError('')
                              setEditing({ ...row, kind: tab })
                            }}
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            className="btn ghost small"
                            onClick={() => onRemove(tab, row)}
                          >
                            Remove
                          </button>
                        </div>
                      </td>
                    )}
                    <td className="col-fill" />
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        narrow
        open={Boolean(editing)}
        onClose={() => setEditing(null)}
        title={
          editing
            ? `${editing.id ? 'Edit' : 'Add'} ${TABS.find((t) => t.key === editing.kind)?.label}`
            : ''
        }
        footer={
          <>
            <button type="button" className="btn ghost" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" form="setup-form" className="btn" disabled={saving}>
              {saving ? 'Saving...' : 'Save'}
            </button>
          </>
        }
      >
        {editing && (
          <form id="setup-form" onSubmit={onSave} style={{ display: 'grid', gap: 14 }}>
            <Alert kind="error">{formError}</Alert>

            {/*
              The identifying field first, then the name - the same order the grid
              lists them in, so the form reads the way the table does.
            */}
            {editing.kind === 'zones' && (
              <Field label="Code" required>
                <input
                  autoFocus
                  value={editing.code || ''}
                  onChange={(e) => setEditing({ ...editing, code: e.target.value })}
                  required
                />
              </Field>
            )}

            {editing.kind === 'areas' && (
              <Field label="Zone" required>
                <select
                  value={editing.zoneId}
                  onChange={(e) => setEditing({ ...editing, zoneId: e.target.value })}
                  required
                >
                  <option value="">Select a zone</option>
                  {zones.map((zone) => (
                    <option key={zone.id} value={zone.id}>
                      {zone.name}
                    </option>
                  ))}
                </select>
              </Field>
            )}

            {editing.kind === 'points' && (
              <Field label="Area" required>
                <select
                  value={editing.areaId}
                  onChange={(e) => setEditing({ ...editing, areaId: e.target.value })}
                  required
                >
                  <option value="">Select an area</option>
                  {areas.map((area) => (
                    <option key={area.id} value={area.id}>
                      {area.name} ({area.zoneName})
                    </option>
                  ))}
                </select>
                {areas.length === 0 && (
                  <p className="photo-error">Add an area first - a point sits inside one.</p>
                )}
              </Field>
            )}

            <Field label="Name" required>
              <input
                autoFocus={editing.kind !== 'zones'}
                value={editing.name}
                onChange={(e) => setEditing({ ...editing, name: e.target.value })}
                required
              />
            </Field>

            <label className="checkline">
              <input
                type="checkbox"
                checked={Boolean(editing.active)}
                onChange={(e) => setEditing({ ...editing, active: e.target.checked })}
              />
              Active
            </label>
          </form>
        )}
      </Modal>
    </div>
  )
}
