import { useCallback, useEffect, useMemo, useState } from 'react'
import { metaApi, sewadarApi, userApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { Badge, EmptyRow, Field, Pager } from '../components/Bits'
import { ZONE_SCOPED_ROLES } from '../auth/AuthContext'

// Single source of truth, shared with the route guards.
const ZONE_SCOPED = ZONE_SCOPED_ROLES

const EMPTY = {
  username: '',
  password: '',
  fullName: '',
  email: '',
  mobile: '',
  role: 'OFFICE_USER',
  zoneIds: [],
  sewadarId: '',
  mustChangePassword: true,
}

export default function Users() {
  const [filters, setFilters] = useState({ query: '', role: '' })
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [zones, setZones] = useState([])
  const [options, setOptions] = useState({ roles: [] })
  const [sewadars, setSewadars] = useState([])

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [creating, setCreating] = useState(null)
  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')

  const params = useMemo(
    () => ({ query: filters.query || undefined, role: filters.role || undefined, page, size: 20 }),
    [filters, page],
  )

  const load = useCallback(() => {
    setLoading(true)
    userApi
      .search(params)
      .then(setResult)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [params])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    zoneApi.list(true).then(setZones).catch(() => setZones([]))
    metaApi.options().then(setOptions).catch(() => {})
  }, [])

  // Sewadar accounts have to be linked to a sewadar record, so load the ones without a login.
  useEffect(() => {
    if (creating?.role !== 'SEWADAR') return
    sewadarApi
      .search({ active: true, size: 200, sortBy: 'name', direction: 'asc' })
      .then((res) => setSewadars((res.content || []).filter((s) => !s.hasLogin)))
      .catch(() => setSewadars([]))
  }, [creating?.role])

  const onCreate = async (event) => {
    event.preventDefault()
    setFormError('')
    setSaving(true)
    try {
      await userApi.create({
        ...creating,
        zoneIds: ZONE_SCOPED.includes(creating.role) ? creating.zoneIds.map(Number) : [],
        sewadarId: creating.role === 'SEWADAR' ? Number(creating.sewadarId) : null,
      })
      setNotice(`Account ${creating.username} created`)
      setCreating(null)
      load()
    } catch (err) {
      setFormError(errorMessage(err, 'Could not create the account'))
    } finally {
      setSaving(false)
    }
  }

  const onUpdate = async (event) => {
    event.preventDefault()
    setFormError('')
    setSaving(true)
    try {
      await userApi.update(editing.id, {
        fullName: editing.fullName,
        email: editing.email || null,
        mobile: editing.mobile || null,
        role: editing.role,
        zoneIds: ZONE_SCOPED.includes(editing.role) ? editing.zoneIds.map(Number) : [],
        enabled: editing.enabled,
        newPassword: editing.newPassword || null,
      })
      setNotice(`Account ${editing.username} updated`)
      setEditing(null)
      load()
    } catch (err) {
      setFormError(errorMessage(err, 'Could not update the account'))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async (row) => {
    try {
      await userApi.remove(row.id)
      setNotice(`Account ${row.username} deleted`)
      load()
    } catch (err) {
      setError(errorMessage(err, 'Could not delete the account'))
    }
  }

  const toggleZone = (target, setTarget) => (zoneId) => {
    const ids = target.zoneIds.map(String)
    const next = ids.includes(String(zoneId))
      ? ids.filter((id) => id !== String(zoneId))
      : [...ids, String(zoneId)]
    setTarget({ ...target, zoneIds: next })
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
          <h3>Login accounts</h3>
          <button
            type="button"
            className="btn"
            onClick={() => {
              setFormError('')
              setCreating({ ...EMPTY })
            }}
          >
            + Add account
          </button>
        </div>
        <div className="filters">
          <Field label="Search">
            <input
              placeholder="Username, name or email"
              value={filters.query}
              onChange={(e) => {
                setPage(0)
                setFilters({ ...filters, query: e.target.value })
              }}
            />
          </Field>
          <Field label="Role">
            <select
              value={filters.role}
              onChange={(e) => {
                setPage(0)
                setFilters({ ...filters, role: e.target.value })
              }}
            >
              <option value="">All roles</option>
              {(options.roles || []).map((option) => (
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
          <Spinner label="Loading accounts" />
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Username</th>
                    <th>Name</th>
                    <th>Role</th>
                    <th>Zones</th>
                    <th>Email</th>
                    <th>Status</th>
                    <th>Last sign in</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {result?.content?.length ? (
                    result.content.map((row) => (
                      <tr key={row.id}>
                        <td>
                          <strong>{row.username}</strong>
                          {row.mustChangePassword && (
                            <div className="muted" style={{ fontSize: 11.5 }}>
                              must change password
                            </div>
                          )}
                        </td>
                        <td>{row.fullName}</td>
                        <td>
                          <span className="badge role">{row.roleDisplayName}</span>
                        </td>
                        <td className="muted">
                          {row.zoneNames?.length ? row.zoneNames.join(', ') : 'All zones'}
                        </td>
                        <td className="muted">{row.email || '-'}</td>
                        <td>
                          <Badge
                            value={row.enabled ? 'active' : 'inactive'}
                            label={row.enabled ? 'Enabled' : 'Disabled'}
                          />
                        </td>
                        <td className="muted">
                          {row.lastLoginAt ? row.lastLoginAt.slice(0, 16).replace('T', ' ') : 'never'}
                        </td>
                        <td>
                          <div className="btn-row">
                            <button
                              type="button"
                              className="btn ghost small"
                              onClick={() => {
                                setFormError('')
                                setEditing({
                                  ...row,
                                  email: row.email || '',
                                  mobile: row.mobile || '',
                                  zoneIds: (row.zoneIds || []).map(String),
                                  newPassword: '',
                                })
                              }}
                            >
                              Edit
                            </button>
                            <button
                              type="button"
                              className="btn danger small"
                              onClick={() => onDelete(row)}
                            >
                              Delete
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <EmptyRow colSpan={8}>No accounts match these filters</EmptyRow>
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

      {/* Create */}
      <Modal
        title="Add login account"
        open={Boolean(creating)}
        onClose={() => setCreating(null)}
        footer={
          <>
            <button type="button" className="btn ghost" onClick={() => setCreating(null)}>
              Cancel
            </button>
            <button type="submit" form="create-user" className="btn" disabled={saving}>
              {saving ? 'Saving...' : 'Create account'}
            </button>
          </>
        }
      >
        {creating && (
          <form id="create-user" onSubmit={onCreate}>
            <Alert kind="error">{formError}</Alert>
            <div className="form-grid">
              <Field label="Username" required>
                <input
                  value={creating.username}
                  onChange={(e) => setCreating({ ...creating, username: e.target.value })}
                  required
                />
              </Field>
              <Field label="Password" required>
                <input
                  type="password"
                  value={creating.password}
                  onChange={(e) => setCreating({ ...creating, password: e.target.value })}
                  minLength={8}
                  required
                />
              </Field>
              <Field label="Full name" required>
                <input
                  value={creating.fullName}
                  onChange={(e) => setCreating({ ...creating, fullName: e.target.value })}
                  required
                />
              </Field>
              <Field label="Role" required>
                <select
                  value={creating.role}
                  onChange={(e) => setCreating({ ...creating, role: e.target.value, zoneIds: [] })}
                >
                  {(options.roles || []).map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Email">
                <input
                  type="email"
                  value={creating.email}
                  onChange={(e) => setCreating({ ...creating, email: e.target.value })}
                />
              </Field>
              <Field label="Mobile">
                <input
                  value={creating.mobile}
                  onChange={(e) => setCreating({ ...creating, mobile: e.target.value })}
                />
              </Field>
            </div>

            {ZONE_SCOPED.includes(creating.role) && (
              <div style={{ marginTop: 16 }}>
                <label style={{ fontSize: 12, fontWeight: 600 }}>
                  Zones this account can reach <span className="req">*</span>
                </label>
                <div className="chips" style={{ marginTop: 8 }}>
                  {zones.map((zone) => (
                    <label key={zone.id} className="checkline">
                      <input
                        type="checkbox"
                        checked={creating.zoneIds.map(String).includes(String(zone.id))}
                        onChange={() => toggleZone(creating, setCreating)(zone.id)}
                      />
                      {zone.name}
                    </label>
                  ))}
                </div>
              </div>
            )}

            {creating.role === 'SEWADAR' && (
              <div style={{ marginTop: 16 }}>
                <Field label="Link to sewadar record" required>
                  <select
                    value={creating.sewadarId}
                    onChange={(e) => setCreating({ ...creating, sewadarId: e.target.value })}
                    required
                  >
                    <option value="">Select a sewadar without a login</option>
                    {sewadars.map((sewadar) => (
                      <option key={sewadar.id} value={sewadar.id}>
                        {sewadar.name} ({sewadar.badgeNumber}) - {sewadar.zoneName}
                      </option>
                    ))}
                  </select>
                </Field>
              </div>
            )}

            <label className="checkline" style={{ marginTop: 16 }}>
              <input
                type="checkbox"
                checked={creating.mustChangePassword}
                onChange={(e) =>
                  setCreating({ ...creating, mustChangePassword: e.target.checked })
                }
              />
              Force a password change on first sign in
            </label>

            {['ADMIN', 'OFFICE_ADMIN', 'OFFICE_USER'].includes(creating.role) && (
              <Alert kind="info">
                This role reaches every zone, so no zone selection is needed.
              </Alert>
            )}
          </form>
        )}
      </Modal>

      {/* Edit */}
      <Modal
        title={editing ? `Edit ${editing.username}` : 'Edit account'}
        open={Boolean(editing)}
        onClose={() => setEditing(null)}
        footer={
          <>
            <button type="button" className="btn ghost" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" form="edit-user" className="btn" disabled={saving}>
              {saving ? 'Saving...' : 'Save changes'}
            </button>
          </>
        }
      >
        {editing && (
          <form id="edit-user" onSubmit={onUpdate}>
            <Alert kind="error">{formError}</Alert>
            <div className="form-grid">
              <Field label="Full name" required>
                <input
                  value={editing.fullName}
                  onChange={(e) => setEditing({ ...editing, fullName: e.target.value })}
                  required
                />
              </Field>
              <Field label="Role">
                <select
                  value={editing.role}
                  onChange={(e) => setEditing({ ...editing, role: e.target.value, zoneIds: [] })}
                  disabled={editing.role === 'SEWADAR'}
                >
                  {(options.roles || []).map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Email">
                <input
                  type="email"
                  value={editing.email}
                  onChange={(e) => setEditing({ ...editing, email: e.target.value })}
                />
              </Field>
              <Field label="Mobile">
                <input
                  value={editing.mobile}
                  onChange={(e) => setEditing({ ...editing, mobile: e.target.value })}
                />
              </Field>
              <Field label="Reset password (optional)">
                <input
                  type="password"
                  value={editing.newPassword}
                  onChange={(e) => setEditing({ ...editing, newPassword: e.target.value })}
                  placeholder="Leave blank to keep it"
                />
              </Field>
            </div>

            {ZONE_SCOPED.includes(editing.role) && (
              <div style={{ marginTop: 16 }}>
                <label style={{ fontSize: 12, fontWeight: 600 }}>
                  Zones this account can reach <span className="req">*</span>
                </label>
                <div className="chips" style={{ marginTop: 8 }}>
                  {zones.map((zone) => (
                    <label key={zone.id} className="checkline">
                      <input
                        type="checkbox"
                        checked={editing.zoneIds.map(String).includes(String(zone.id))}
                        onChange={() => toggleZone(editing, setEditing)(zone.id)}
                      />
                      {zone.name}
                    </label>
                  ))}
                </div>
              </div>
            )}

            <label className="checkline" style={{ marginTop: 16 }}>
              <input
                type="checkbox"
                checked={editing.enabled}
                onChange={(e) => setEditing({ ...editing, enabled: e.target.checked })}
              />
              Account enabled
            </label>

            {editing.role === 'SEWADAR' && (
              <Alert kind="info">
                A sewadar account stays linked to its sewadar record, so its role cannot be changed
                here.
              </Alert>
            )}
          </form>
        )}
      </Modal>
    </div>
  )
}
