import { useCallback, useEffect, useMemo, useState } from 'react'
import { metaApi, sewadarApi, userApi, userApiPhoto, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { Badge, EmptyRow, Field, Pager, TabStrip } from '../components/Bits'
import { Avatar, PhotoPicker } from '../components/Photo'
import { useAuth, ZONE_SCOPED_ROLES } from '../auth/AuthContext'

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

/** Digits only, never more than ten - the same rule the server enforces. */
const digitsOnly = (value) => value.replace(/[^0-9]/g, '').slice(0, 10)

export default function Users() {
  const { user, refresh } = useAuth()
  const [filters, setFilters] = useState({ query: '', role: '', enabled: '' })
  const [counts, setCounts] = useState(null)
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
  const [viewing, setViewing] = useState(null)
  const [photoFile, setPhotoFile] = useState(null)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [photoError, setPhotoError] = useState('')

  const params = useMemo(
    () => ({
      query: filters.query || undefined,
      role: filters.role || undefined,
      enabled: filters.enabled === '' ? undefined : filters.enabled === 'true',
      page,
      size: 20,
    }),
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

  // Reloaded with the list, so creating or disabling an account moves the tab
  // numbers at the same moment it moves the rows.
  useEffect(() => {
    userApi.counts().then(setCounts).catch(() => setCounts(null))
  }, [result])

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
      const saved = await userApi.create({
        ...creating,
        zoneIds: ZONE_SCOPED.includes(creating.role) ? creating.zoneIds.map(Number) : [],
        sewadarId: creating.role === 'SEWADAR' ? Number(creating.sewadarId) : null,
      })
      if (photoFile) {
        try {
          await userApiPhoto.upload(saved.id, photoFile)
        } catch (photoErr) {
          setNotice(
            `Account ${creating.username} created, but the photo failed: ${errorMessage(photoErr)}`,
          )
          setCreating(null)
          setPhotoFile(null)
          load()
          return
        }
      }
      setPhotoFile(null)
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
      /*
       * The photo goes with the save, not on pick. It is sent after the field update
       * so a rejected image cannot roll back details that were already correct - and
       * if it does fail, the message says the details were saved, because they were.
       */
      if (photoFile) {
        try {
          await userApiPhoto.upload(editing.id, photoFile)
        } catch (photoErr) {
          setFormError(
            `Details saved, but the photo did not upload: ${errorMessage(photoErr, 'upload failed')}`,
          )
          setPhotoFile(null)
          load()
          return
        }
      }

      /*
       * Editing your own account changes what the topbar shows - the name beside
       * the photo, and the photo itself. Re-read the session so the shell follows,
       * instead of holding the old one until the next full page load.
       */
      if (editing.id === user?.userId) {
        refresh().catch(() => {})
      }

      setNotice(`Account ${editing.username} updated`)
      setPhotoFile(null)
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
      <div className="page-head">
        <div>
          <h1 className="page-title">User Account Management</h1>
          <p className="page-sub">Manage system users and their roles.</p>
        </div>
      </div>

      <TabStrip
        value={filters.enabled}
        onChange={(next) => {
          setPage(0)
          setFilters((f) => ({ ...f, enabled: next }))
        }}
        tabs={[
          { key: '', label: 'All Users', count: counts?.total },
          { key: 'true', label: 'Active', count: counts?.byStatus?.active },
          { key: 'false', label: 'Inactive', count: counts?.byStatus?.inactive },
        ]}
      />

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
              setPhotoFile(null)
              setPhotoError('')
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
                    <th style={{ width: 62 }}>Photo</th>
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
                          <Avatar
                            kind="users"
                            id={row.id}
                            stamp={row.photoUpdatedAt}
                            name={row.fullName}
                            size={36}
                          />
                        </td>
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
                              onClick={() => setViewing(row)}
                            >
                              View
                            </button>
                            <button
                              type="button"
                              className="btn ghost small"
                              onClick={() => {
                                setFormError('')
                                setPhotoFile(null)
                                setPhotoError('')
                                setEditing({
                                  ...row,
                                  email: row.email || '',
                                  mobile: row.mobile || '',
                                  zoneIds: (row.zoneIds || []).map(String),
                                  newPassword: '',
                                  photoUpdatedAt: row.photoUpdatedAt,
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
                    <EmptyRow colSpan={9}>No accounts match these filters</EmptyRow>
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
            <PhotoPicker
              onReject={setPhotoError}
              kind="users"
              name={creating.fullName}
              file={photoFile}
              onPick={setPhotoFile}
              error={photoError}
            />
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
                  onChange={(e) =>
                    setCreating({ ...creating, mobile: digitsOnly(e.target.value) })
                  }
                  inputMode="numeric"
                  maxLength={10}
                  placeholder="9876543210"
                />
                {creating.mobile && creating.mobile.length !== 10 && (
                  <p className="photo-error">A mobile number is 10 digits.</p>
                )}
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
            {/*
              The chosen photo is held here and sent by Save changes, with the rest
              of the form. It used to upload the moment a file was picked, which put
              a separate failure on screen before anything had been saved - and made
              "update the photo and the other details" two actions instead of one.
            */}
            <PhotoPicker
              onReject={setPhotoError}
              kind="users"
              id={editing.id}
              stamp={editing.photoUpdatedAt}
              name={editing.fullName}
              file={photoFile}
              onPick={(chosen) => {
                setPhotoError('')
                setPhotoFile(chosen)
              }}
              busy={photoBusy}
              error={photoError}
              onRemove={async () => {
                setPhotoBusy(true)
                try {
                  await userApiPhoto.remove(editing.id)
                  setEditing((cur) => ({ ...cur, photoUpdatedAt: null }))
                  load()
                } catch (err) {
                  setPhotoError(errorMessage(err, 'Could not remove the photo'))
                } finally {
                  setPhotoBusy(false)
                }
              }}
            />
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
                  onChange={(e) =>
                    setEditing({ ...editing, mobile: digitsOnly(e.target.value) })
                  }
                  inputMode="numeric"
                  maxLength={10}
                  placeholder="9876543210"
                />
                {/*
                  Said here rather than only on save. Accounts created before the rule
                  existed can hold a number of another length, and without this the
                  form looked fine until saving failed on a field nobody had touched.
                */}
                {editing.mobile && editing.mobile.length !== 10 && (
                  <p className="photo-error">A mobile number is 10 digits.</p>
                )}
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

      {/* ---------- read-only account detail ---------- */}
      <Modal
        title={viewing ? viewing.fullName : 'Account'}
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        footer={
          <>
            <button
              type="button"
              className="btn"
              onClick={() => {
                const row = viewing
                setViewing(null)
                setFormError('')
                setPhotoFile(null)
                setEditing({
                  ...row,
                  email: row.email || '',
                  mobile: row.mobile || '',
                  zoneIds: (row.zoneIds || []).map(String),
                  newPassword: '',
                  photoUpdatedAt: row.photoUpdatedAt,
                })
              }}
            >
              Edit
            </button>
            <button type="button" className="btn ghost" onClick={() => setViewing(null)}>
              Close
            </button>
          </>
        }
      >
        {viewing && (
          <div className="view-doc">
            <div className="view-head">
              <Avatar
                kind="users"
                id={viewing.id}
                stamp={viewing.photoUpdatedAt}
                name={viewing.fullName}
                size={88}
                square
              />
              <div>
                <h3 className="view-name">{viewing.fullName}</h3>
                <p className="view-meta">{viewing.username}</p>
                <span className="badge role">{viewing.roleDisplayName}</span>{' '}
                <Badge
                  value={viewing.enabled ? 'active' : 'inactive'}
                  label={viewing.enabled ? 'Enabled' : 'Disabled'}
                />
              </div>
            </div>

            <h4 className="view-section">Account</h4>
            <dl className="kv">
              <dt>Login</dt>
              <dd>{viewing.username}</dd>
              <dt>Full name</dt>
              <dd>{viewing.fullName}</dd>
              <dt>Role</dt>
              <dd>{viewing.roleDisplayName}</dd>
              <dt>Email</dt>
              <dd>{viewing.email || '-'}</dd>
              <dt>Mobile</dt>
              <dd>{viewing.mobile || '-'}</dd>
            </dl>

            <h4 className="view-section">Access</h4>
            <dl className="kv">
              <dt>Zones</dt>
              <dd>{viewing.zoneNames?.length ? viewing.zoneNames.join(', ') : 'All zones'}</dd>
              <dt>Status</dt>
              <dd>{viewing.enabled ? 'Enabled' : 'Disabled'}</dd>
              <dt>Must change password</dt>
              <dd>{viewing.mustChangePassword ? 'Yes' : 'No'}</dd>
              <dt>Last sign in</dt>
              <dd>
                {viewing.lastLoginAt
                  ? viewing.lastLoginAt.slice(0, 16).replace('T', ' ')
                  : 'never'}
              </dd>
              <dt>Photo on file</dt>
              <dd>{viewing.hasPhoto ? 'Yes' : 'No'}</dd>
            </dl>
          </div>
        )}
      </Modal>
    </div>
  )
}
