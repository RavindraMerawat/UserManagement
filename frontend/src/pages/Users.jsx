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
  roleId: '',
  badgeNumber: '',
  gender: '',
  zoneIds: [],
  sewadarId: '',
  mustChangePassword: true,
}

/**
 * Shortens a long value and hands the whole of it to the browser's tooltip.
 *
 * <p>A table of accounts has to stay readable at a glance, and one long name or a
 * work email address pushes every column after it out of line. The full text is
 * never lost - it is on the cell as a title, which is what a tooltip is - and the
 * ellipsis is the sign that there is more to see.</p>
 */
function clipped(value, limit) {
  const text = value == null || value === '' ? '' : String(value)
  if (!text) return { text: '-', title: undefined }
  return text.length > limit
    ? { text: text.slice(0, limit).trimEnd() + '…', title: text }
    : { text, title: undefined }
}

/** Digits only, never more than ten - the same rule the server enforces. */
const digitsOnly = (value) => value.replace(/[^0-9]/g, '').slice(0, 10)

export default function Users() {
  const { user, refresh } = useAuth()
  const [filters, setFilters] = useState({ query: '', roleId: '', enabled: '' })
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
  // The GR. No lookup on the create form: what it found, or why it found nothing.
  const [grLookup, setGrLookup] = useState({ state: 'idle', message: '' })
  const [viewing, setViewing] = useState(null)
  const [photoFile, setPhotoFile] = useState(null)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [photoError, setPhotoError] = useState('')

  /*
   * Which account type a designation implies is the server's decision - it is what
   * decides whether the form has to ask for zones or for a sewadar to link to. The
   * options call carries the mapping so the rule is not written down twice.
   */
  const accountTypeOf = useCallback(
    (roleId) =>
      (options.roleAccountTypes || []).find((o) => String(o.value) === String(roleId))?.label || '',
    [options],
  )
  const creatingType = accountTypeOf(creating?.roleId)
  const editingType = accountTypeOf(editing?.roleId) || editing?.role || ''

  const params = useMemo(
    () => ({
      query: filters.query || undefined,
      roleId: filters.roleId || undefined,
      enabled: filters.enabled === '' ? undefined : filters.enabled === 'true',
      page,
      size: 25,
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
    if (creatingType !== 'SEWADAR') return
    sewadarApi
      .search({ size: 200, sortBy: 'name', direction: 'asc' })
      .then((res) => setSewadars((res.content || []).filter((s) => !s.hasLogin)))
      .catch(() => setSewadars([]))
  }, [creatingType])


  /**
   * Fills the form from the sewadar record a GR. No names.
   *
   * <p>An account belongs to somebody already on the register, so the office types
   * the number they know and the rest arrives with it - name, gender, designation,
   * zone, email, mobile. Nothing is guessed: a number that matches nobody fills in
   * nothing and says so, and every field stays editable afterwards.</p>
   *
   * <p>It waits for five characters because a GR. No is a letter and five digits,
   * and searching on "L0" would ask the server for half the register.</p>
   */
  const lookUpBadge = useCallback(async (badge) => {
    const term = (badge || '').trim()
    if (term.length < 5) {
      setGrLookup({ state: 'idle', message: '' })
      return
    }
    setGrLookup({ state: 'searching', message: 'Looking up ' + term + '...' })
    try {
      const found = await sewadarApi.search({ query: term, size: 10 })
      const match = (found.content || []).find(
        (row) => (row.badgeNumber || '').toLowerCase() === term.toLowerCase(),
      )
      if (!match) {
        setGrLookup({ state: 'none', message: `No sewadar with GR. No ${term}.` })
        return
      }
      /*
       * The picker below lists sewadars without a login, and only for a Sewadar
       * account. The one just found belongs in it whatever the designation, or the
       * field would read "Not linked" while the link was in fact being made.
       */
      setSewadars((list) =>
        list.some((row) => row.id === match.id) ? list : [match, ...list],
      )
      setCreating((current) => ({
        ...current,
        badgeNumber: match.badgeNumber,
        // The sewadar's name as the register holds it, which is already the whole
        // name - the register keeps one name field, not three.
        fullName: match.name || current.fullName,
        gender: match.gender || current.gender,
        roleId: match.designationId ? String(match.designationId) : current.roleId,
        email: match.email || current.email,
        mobile: match.mobile || current.mobile,
        sewadarId: String(match.id),
        zoneIds: match.zoneId ? [String(match.zoneId)] : current.zoneIds,
      }))
      setGrLookup({
        state: 'found',
        message: `${match.name} - ${match.zoneName || 'no zone'}${match.designationName ? ', ' + match.designationName : ''}`,
      })
    } catch (err) {
      setGrLookup({ state: 'none', message: errorMessage(err, 'Could not look that GR. No up') })
    }
  }, [])

  const onCreate = async (event) => {
    event.preventDefault()
    setFormError('')
    setSaving(true)
    try {
      const saved = await userApi.create({
        ...creating,
        roleId: Number(creating.roleId),
        badgeNumber: creating.badgeNumber || null,
        // Empty means "both registers", which is what the server reads a null as.
        gender: creating.gender || null,
        zoneIds: ZONE_SCOPED.includes(creatingType) ? creating.zoneIds.map(Number) : [],
        sewadarId: creating.sewadarId ? Number(creating.sewadarId) : null,
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
        username: editing.username,
        fullName: editing.fullName,
        email: editing.email || null,
        mobile: editing.mobile || null,
        badgeNumber: editing.badgeNumber || null,
        gender: editing.gender || null,
        roleId: editing.roleId ? Number(editing.roleId) : undefined,
        zoneIds: ZONE_SCOPED.includes(editingType) ? editing.zoneIds.map(Number) : [],
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
          <Field label="Designation">
            <select
              value={filters.roleId}
              onChange={(e) => {
                setPage(0)
                setFilters({ ...filters, roleId: e.target.value })
              }}
            >
              <option value="">All designations</option>
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
                    {/* The order the office reads an account in: who it is, then
                        what it may do, then whether it is in use. */}
                    <th>GR. No</th>
                    <th style={{ width: 62 }}>Photo</th>
                    <th>Name</th>
                    <th>Username</th>
                    <th>Gender</th>
                    <th>Designation</th>
                    <th>Zone</th>
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
                        <td className="muted">{row.badgeNumber || '-'}</td>
                        <td>
                          {/*
                            The account's own photo if it has one, otherwise the
                            photo of the sewadar it belongs to - pointed at, not
                            copied, so there is one picture of a person and changing
                            it on the register changes it here.
                          */}
                          {row.hasPhoto ? (
                            <Avatar
                              kind="users"
                              id={row.id}
                              stamp={row.photoUpdatedAt}
                              name={row.fullName}
                              size={36}
                            />
                          ) : (
                            <Avatar
                              kind="sewadars"
                              id={row.sewadarId}
                              stamp={row.sewadarPhotoUpdatedAt}
                              name={row.fullName}
                              size={36}
                            />
                          )}
                        </td>
                        {/* Hover for the whole of a name longer than ten letters. */}
                        <td title={clipped(row.fullName, 10).title}>
                          {clipped(row.fullName, 10).text}
                        </td>
                        <td>
                          <strong>{row.username}</strong>
                          {row.mustChangePassword && (
                            <div className="muted" style={{ fontSize: 11.5 }}>
                              must change password
                            </div>
                          )}
                        </td>
                        {/* Which register this account reads. A dash means both. */}
                        <td className="muted">
                          {row.gender === 'MALE' ? 'Male' : row.gender === 'FEMALE' ? 'Female' : '-'}
                        </td>
                        <td>
                          <span className="badge role">
                            {row.designationName || row.roleDisplayName}
                          </span>
                        </td>
                        <td className="muted">
                          {row.zoneNames?.length ? row.zoneNames.join(', ') : 'All zones'}
                        </td>
                        {/* And for an address longer than twenty characters. */}
                        <td className="muted" title={clipped(row.email, 20).title}>
                          {clipped(row.email, 20).text}
                        </td>
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
                                  originalUsername: row.username,
                                  email: row.email || '',
                                  mobile: row.mobile || '',
                                  roleId: row.roleId ? String(row.roleId) : '',
                                  badgeNumber: row.badgeNumber || '',
                                  gender: row.gender || '',
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
                    <EmptyRow colSpan={11}>No accounts match these filters</EmptyRow>
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
              {/*
                First, because everything else on this form comes from it: type the
                GR. No and the sewadar's details arrive with it.
              */}
              <Field label="GR. No" required>
                <input
                  value={creating.badgeNumber}
                  onChange={(e) => {
                    const badge = e.target.value
                    setCreating({ ...creating, badgeNumber: badge })
                    lookUpBadge(badge)
                  }}
                  onBlur={(e) => lookUpBadge(e.target.value)}
                  placeholder="L04822"
                  required
                />
                {grLookup.state !== 'idle' && (
                  <p className={grLookup.state === 'none' ? 'photo-error' : 'photo-hint'}>
                    {grLookup.message}
                  </p>
                )}
              </Field>
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
              <Field label="Designation" required>
                <select
                  value={creating.roleId}
                  onChange={(e) =>
                    setCreating({ ...creating, roleId: e.target.value, zoneIds: [] })
                  }
                  required
                >
                  <option value="">Select a designation</option>
                  {(options.roles || []).map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Field>
              {/*
                Whose register this account reads. Left empty the account sees both,
                which is how every account behaved before this field existed; the
                server applies it to everything the account asks for, not the screen.
              */}
              <Field label="Gender">
                <select
                  value={creating.gender}
                  onChange={(e) => setCreating({ ...creating, gender: e.target.value })}
                >
                  <option value="">Both (not set)</option>
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
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

            {ZONE_SCOPED.includes(creatingType) && (
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

{/*
              Offered for every account type, not only Sewadar. A co-ordinator's
              zones are recorded on their sewadar record - their own and the others
              they cover - and that reach only reaches this login once the two are
              linked.
            */}
            <div style={{ marginTop: 16 }}>
              <Field
                label="Link to sewadar record"
                required={creatingType === 'SEWADAR'}
              >
                <select
                  value={creating.sewadarId}
                  onChange={(e) => setCreating({ ...creating, sewadarId: e.target.value })}
                  required={creatingType === 'SEWADAR'}
                >
                  <option value="">
                    {creatingType === 'SEWADAR'
                      ? 'Select a sewadar without a login'
                      : 'Not linked'}
                  </option>
                  {sewadars.map((sewadar) => (
                    <option key={sewadar.id} value={sewadar.id}>
                      {sewadar.name} ({sewadar.badgeNumber}) - {sewadar.zoneName}
                    </option>
                  ))}
                </select>
                {creatingType !== 'SEWADAR' && (
                  <p className="hint">
                    Linking the record brings the zones it covers to this login.
                  </p>
                )}
              </Field>
            </div>

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

            {['ADMIN', 'OFFICE_ADMIN', 'OFFICE_USER'].includes(creatingType) && (
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
            {/*
              An account with no picture of its own shows the sewadar's, here as
              well as on the grid: same person, one photo, pointed at rather than
              copied. Choosing a file still sets this account's own.
            */}
            <PhotoPicker
              onReject={setPhotoError}
              kind={editing.photoUpdatedAt ? 'users' : 'sewadars'}
              id={editing.photoUpdatedAt ? editing.id : editing.sewadarId}
              stamp={editing.photoUpdatedAt || editing.sewadarPhotoUpdatedAt}
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
              <Field label="Username" required>
                <input
                  value={editing.username}
                  onChange={(e) => setEditing({ ...editing, username: e.target.value })}
                  minLength={3}
                  required
                />
                {/* Said here because it is not obvious: the token carries the name. */}
                {editing.username !== editing.originalUsername && (
                  <p className="photo-hint">
                    Renaming signs this account out of any session it has open.
                  </p>
                )}
              </Field>
              <Field label="Full name" required>
                <input
                  value={editing.fullName}
                  onChange={(e) => setEditing({ ...editing, fullName: e.target.value })}
                  required
                />
              </Field>
              <Field label="Role">
                <select
                  value={editing.roleId || ''}
                  onChange={(e) => setEditing({ ...editing, roleId: e.target.value, zoneIds: [] })}
                >
                  <option value="">Select a designation</option>
                  {(options.roles || []).map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="GR. No">
                <input
                  value={editing.badgeNumber}
                  onChange={(e) => setEditing({ ...editing, badgeNumber: e.target.value })}
                  placeholder="L04822"
                />
              </Field>
              {/*
                Whose register this account reads. Left empty the account sees both,
                which is how every account behaved before this field existed; the
                server applies it to everything the account asks for, not the screen.
              */}
              <Field label="Gender">
                <select
                  value={editing.gender}
                  onChange={(e) => setEditing({ ...editing, gender: e.target.value })}
                >
                  <option value="">Both (not set)</option>
                  <option value="MALE">Male</option>
                  <option value="FEMALE">Female</option>
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

            {ZONE_SCOPED.includes(editingType) && (
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

            {editingType === 'SEWADAR' && (
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
                <span className="badge role">
                  {viewing.designationName || viewing.roleDisplayName}
                </span>{' '}
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
              <dt>Designation</dt>
              <dd>{viewing.designationName || '-'}</dd>
              <dt>Access level</dt>
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
