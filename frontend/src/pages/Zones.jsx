import { useCallback, useEffect, useState } from 'react'
import { zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { Badge, EmptyRow, Field } from '../components/Bits'

const EMPTY = { code: '', name: '', description: '', centre: '', active: true }

export default function Zones() {
  const [zones, setZones] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  const load = useCallback(() => {
    setLoading(true)
    zoneApi
      .list(true)
      .then(setZones)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const onSave = async (event) => {
    event.preventDefault()
    setFormError('')
    setSaving(true)
    try {
      if (editing.id) {
        await zoneApi.update(editing.id, editing)
        setNotice(`${editing.name} updated`)
      } else {
        await zoneApi.create(editing)
        setNotice(`${editing.name} created`)
      }
      setEditing(null)
      load()
    } catch (err) {
      setFormError(errorMessage(err, 'Could not save the zone'))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (zone) => {
    try {
      await zoneApi.remove(zone.id)
      setNotice(`${zone.name} removed. A zone with active sewadars is deactivated instead.`)
      load()
    } catch (err) {
      setError(errorMessage(err, 'Could not delete the zone'))
    }
  }

  const set = (key) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value
    setEditing((current) => ({ ...current, [key]: value }))
  }

  return (
    <div>
      <Alert kind="error" onClose={() => setError('')}>
        {error}
      </Alert>
      <Alert kind="success" onClose={() => setNotice('')}>
        {notice}
      </Alert>

      <div className="card">
        <div className="card-head">
          <h3>Zones</h3>
          <button
            type="button"
            className="btn"
            onClick={() => {
              setFormError('')
              setEditing({ ...EMPTY })
            }}
          >
            + Add zone
          </button>
        </div>

        {loading ? (
          <Spinner label="Loading zones" />
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Name</th>
                  <th>Centre</th>
                  <th>Description</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {zones.length === 0 ? (
                  <EmptyRow colSpan={6}>No zones yet</EmptyRow>
                ) : (
                  zones.map((zone) => (
                    <tr key={zone.id}>
                      <td>{zone.code}</td>
                      <td>{zone.name}</td>
                      <td>{zone.centre || '-'}</td>
                      <td className="muted">{zone.description || '-'}</td>
                      <td>
                        <Badge
                          value={zone.active ? 'active' : 'inactive'}
                          label={zone.active ? 'Active' : 'Inactive'}
                        />
                      </td>
                      <td>
                        <div className="btn-row">
                          <button
                            type="button"
                            className="btn ghost small"
                            onClick={() => {
                              setFormError('')
                              setEditing({ ...zone, description: zone.description || '', centre: zone.centre || '' })
                            }}
                          >
                            Edit
                          </button>
                          <button
                            type="button"
                            className="btn danger small"
                            onClick={() => onDelete(zone)}
                          >
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <Modal
        narrow
        title={editing?.id ? `Edit ${editing.name}` : 'Add zone'}
        open={Boolean(editing)}
        onClose={() => setEditing(null)}
        footer={
          <>
            <button type="button" className="btn ghost" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" form="zone-form" className="btn" disabled={saving}>
              {saving ? 'Saving...' : 'Save zone'}
            </button>
          </>
        }
      >
        {editing && (
          <form id="zone-form" onSubmit={onSave} style={{ display: 'grid', gap: 14 }}>
            <Alert kind="error">{formError}</Alert>
            <Field label="Code" required>
              <input value={editing.code} onChange={set('code')} placeholder="Z-05" required />
            </Field>
            <Field label="Name" required>
              <input
                value={editing.name}
                onChange={set('name')}
                placeholder="Zone 5 - Central"
                required
              />
            </Field>
            <Field label="Centre">
              <input value={editing.centre} onChange={set('centre')} />
            </Field>
            <Field label="Description">
              <textarea value={editing.description} onChange={set('description')} />
            </Field>
            <label className="checkline">
              <input type="checkbox" checked={editing.active} onChange={set('active')} />
              Active
            </label>
          </form>
        )}
      </Modal>
    </div>
  )
}
