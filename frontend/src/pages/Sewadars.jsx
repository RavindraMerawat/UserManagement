import { useCallback, useEffect, useMemo, useState } from 'react'
import { metaApi, setupApi, sewadarApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { Badge, EmptyRow, Field, Pager, TabStrip } from '../components/Bits'
import { Avatar, PhotoPicker } from '../components/Photo'

const EMPTY_FORM = {
  badgeNumber: '',
  badgeReceived: false,
  // registration fields, in the order they appear on the form
  name: '',
  fatherOrHusbandName: '',
  dateOfBirth: '',
  mobile: '',
  zoneId: '',
  address: '',
  aadharNumber: '',
  bloodGroup: '',
  area: '',
  centerPoint: '',
  // additional details
  gender: '',
  email: '',
  city: '',
  pincode: '',
  department: '',
  primarySewaType: '',
  joiningDate: '',
  active: true,
  createLogin: false,
  loginUsername: '',
}

const BLOOD_GROUPS = ['A+', 'A-', 'B+', 'B-', 'O+', 'O-', 'AB+', 'AB-']

/** Formats a stored 12 digit Aadhaar as 1234 5678 9012 for reading. */
/**
 * Groups the digits in fours for reading. A value the server masked for this role
 * already arrives display-ready as `XXXX XXXX 9012`, and stripping non-digits from it
 * would leave just the last four, so it is passed straight through.
 */
function formatAadhar(value) {
  if (!value) return ''
  if (/[Xx]/.test(value)) return value
  const digits = value.replace(/[^0-9]/g, '')
  return digits.replace(/(\d{4})(?=\d)/g, '$1 ').trim()
}

export default function Sewadars() {
  const { canManageSewadars } = useAuth()

  const [filters, setFilters] = useState({ query: '', zoneId: '', active: 'true' })
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [zones, setZones] = useState([])
  const [areas, setAreas] = useState([])
  const [points, setPoints] = useState([])
  const [counts, setCounts] = useState(null)
  const [options, setOptions] = useState({ genders: [], sewaTypes: [] })

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [editing, setEditing] = useState(null)
  const [showExtras, setShowExtras] = useState(false)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [viewing, setViewing] = useState(null)
  const [photoFile, setPhotoFile] = useState(null)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [photoError, setPhotoError] = useState('')

  const params = useMemo(
    () => ({
      query: filters.query || undefined,
      zoneId: filters.zoneId || undefined,
      active: filters.active === '' ? undefined : filters.active === 'true',
      page,
      size: 20,
    }),
    [filters, page],
  )

  const load = useCallback(() => {
    setLoading(true)
    sewadarApi
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
    // The Setup lists behind the Area and Point pickers. Only the active ones - a
    // retired area should not be offered for a new record - and only for the roles
    // that can open the form at all, since Setup is theirs.
    if (canManageSewadars) {
      setupApi.areas({ active: true }).then(setAreas).catch(() => setAreas([]))
      setupApi.points({ active: true }).then(setPoints).catch(() => setPoints([]))
    }
  }, [canManageSewadars])

  // The tab numbers are reloaded whenever the list is, so adding or deactivating
  // a sewadar moves the counts at the same moment it moves the rows.
  useEffect(() => {
    sewadarApi.counts().then(setCounts).catch(() => setCounts(null))
  }, [result])

  const openCreate = () => {
    setFormError('')
    setShowExtras(false)
    setPhotoFile(null)
    setPhotoError('')
    setEditing({ ...EMPTY_FORM, zoneId: zones[0]?.id ?? '' })
  }

  const openEdit = (row) => {
    setFormError('')
    setShowExtras(false)
    setPhotoFile(null)
    setPhotoError('')
    // Null columns come back as undefined, which React would treat as uncontrolled.
    setEditing({
      id: row.id,
      badgeNumber: row.badgeNumber || '',
      badgeReceived: Boolean(row.badgeReceived),
      name: row.name || '',
      fatherOrHusbandName: row.fatherOrHusbandName || '',
      dateOfBirth: row.dateOfBirth || '',
      mobile: row.mobile || '',
      zoneId: row.zoneId ?? '',
      address: row.address || '',
      aadharNumber: row.aadharNumber || '',
      bloodGroup: row.bloodGroup || '',
      area: row.area || '',
      centerPoint: row.centerPoint || '',
      gender: row.gender || '',
      email: row.email || '',
      city: row.city || '',
      pincode: row.pincode || '',
      department: row.department || '',
      primarySewaType: row.primarySewaType || '',
      joiningDate: row.joiningDate || '',
      active: row.active,
      createLogin: false,
      loginUsername: '',
      hasLogin: row.hasLogin,
      existingLogin: row.loginUsername,
      photoUpdatedAt: row.photoUpdatedAt,
    })
  }

  const onSave = async (event) => {
    event.preventDefault()
    setFormError('')
    setSaving(true)

    // Blank dates and enums must reach the server as null, not as empty strings.
    const payload = {
      ...editing,
      zoneId: Number(editing.zoneId),
      dateOfBirth: editing.dateOfBirth || null,
      joiningDate: editing.joiningDate || null,
      gender: editing.gender || null,
      primarySewaType: editing.primarySewaType || null,
      aadharNumber: editing.aadharNumber ? editing.aadharNumber.replace(/[^0-9]/g, '') : null,
      loginUsername: editing.createLogin ? editing.loginUsername || null : null,
    }
    delete payload.hasLogin
    delete payload.existingLogin
    delete payload.photoUpdatedAt

    try {
      // A new record has no id until it is saved, so the chosen photo is uploaded
      // straight after the create rather than as part of it.
      const saved = editing.id
        ? await sewadarApi.update(editing.id, payload)
        : await sewadarApi.create(payload)

      if (photoFile) {
        try {
          await sewadarApi.photo.upload(saved.id, photoFile)
        } catch (photoErr) {
          setNotice(`${payload.name} saved, but the photo failed: ${errorMessage(photoErr)}`)
          setEditing(null)
          setPhotoFile(null)
          load()
          return
        }
      }
      setNotice(`${payload.name} ${editing.id ? 'updated' : 'added'}`)
      setPhotoFile(null)
      setEditing(null)
      load()
    } catch (err) {
      setFormError(errorMessage(err, 'Could not save the sewadar'))
    } finally {
      setSaving(false)
    }
  }

  const onDelete = async () => {
    try {
      await sewadarApi.remove(confirmDelete.id)
      setNotice(
        `${confirmDelete.name} removed. A sewadar with attendance history is deactivated instead of deleted.`,
      )
      setConfirmDelete(null)
      load()
    } catch (err) {
      setError(errorMessage(err, 'Could not delete the sewadar'))
      setConfirmDelete(null)
    }
  }

  const set = (key) => (event) => {
    const value = event.target.type === 'checkbox' ? event.target.checked : event.target.value
    setEditing((current) => ({ ...current, [key]: value }))
  }

  // Digits only, and never more than ten - the server enforces the same rule, this
  // just means a wrong number cannot be typed in the first place.
  const onMobileChange = (event) => {
    const digits = event.target.value.replace(/[^0-9]/g, '').slice(0, 10)
    setEditing((current) => ({ ...current, mobile: digits }))
  }

  // Aadhaar is typed as digits but shown in groups of four.
  const onAadharChange = (event) => {
    const digits = event.target.value.replace(/[^0-9]/g, '').slice(0, 12)
    setEditing((current) => ({ ...current, aadharNumber: digits }))
  }

  // Narrow the pickers to what sits under the current selection.
  const zoneAreas = areas.filter((area) => String(area.zoneId) === String(editing?.zoneId))
  const areaPoints = points.filter((point) => point.areaName === editing?.area)

  /*
   * A record may already name an area or point that is not in the Setup list - one
   * typed before the lists existed, or since retired. Keeping the current value as an
   * option means opening the form does not silently blank it. The startup backfill
   * seeds the lists from existing records, so this is the safety net rather than the
   * mechanism.
   */
  const withCurrent = (list, current) =>
    current && !list.some((item) => item.name === current)
      ? [{ id: `current:${current}`, name: current }, ...list]
      : list

  return (
    <div>
      <div className="page-head">
        <div>
          <h1 className="page-title">Sewadar Management</h1>
          <p className="page-sub">Manage your sewadar list, details and seva assignments.</p>
        </div>
      </div>

      <TabStrip
        value={filters.active}
        onChange={(next) => {
          setPage(0)
          setFilters((f) => ({ ...f, active: next }))
        }}
        tabs={[
          { key: '', label: 'All', count: counts?.total },
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
          <h3>Sewadar register</h3>
          {canManageSewadars && (
            <button type="button" className="btn" onClick={openCreate}>
              + Add sewadar
            </button>
          )}
        </div>

        <div className="filters">
          <Field label="Search">
            <input
              placeholder="Name, F/H name, badge, mobile, area or center"
              value={filters.query}
              onChange={(e) => {
                setPage(0)
                setFilters({ ...filters, query: e.target.value })
              }}
            />
          </Field>
          <Field label="Zone">
            <select
              value={filters.zoneId}
              onChange={(e) => {
                setPage(0)
                setFilters({ ...filters, zoneId: e.target.value })
              }}
            >
              <option value="">All my zones</option>
              {zones.map((zone) => (
                <option key={zone.id} value={zone.id}>
                  {zone.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Status">
            <select
              value={filters.active}
              onChange={(e) => {
                setPage(0)
                setFilters({ ...filters, active: e.target.value })
              }}
            >
              <option value="true">Active</option>
              <option value="false">Inactive</option>
              <option value="">All</option>
            </select>
          </Field>
        </div>
      </div>

      <div className="card">
        {loading ? (
          <Spinner label="Loading sewadars" />
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Badge No</th>
                    <th style={{ width: 62 }}>Photo</th>
                    <th>Name</th>
                    <th>F/H Name</th>
                    <th>Mobile No</th>
                    <th>Zone</th>
                    <th>Area / Point</th>
                    <th>Aadhaar Card</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {result?.content?.length ? (
                    result.content.map((row) => (
                      <tr key={row.id}>
                        <td>{row.badgeNumber}</td>
                        <td>
                          <Avatar
                            kind="sewadars"
                            id={row.id}
                            stamp={row.photoUpdatedAt}
                            name={row.name}
                            size={36}
                          />
                        </td>
                        <td>
                          {row.name}
                          {row.hasLogin && (
                            <div className="muted" style={{ fontSize: 11.5 }}>
                              login: {row.loginUsername}
                            </div>
                          )}
                        </td>
                        <td>{row.fatherOrHusbandName || '-'}</td>
                        <td>{row.mobile || '-'}</td>
                        <td>{row.zoneName}</td>
                        <td>{[row.area, row.centerPoint].filter(Boolean).join(' / ') || '-'}</td>
                        <td>{formatAadhar(row.aadharNumber) || '-'}</td>
                        <td>
                          <Badge
                            value={row.active ? 'active' : 'inactive'}
                            label={row.active ? 'Active' : 'Inactive'}
                          />
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
                            {canManageSewadars && (
                              <>
                                <button
                                  type="button"
                                  className="btn ghost small"
                                  onClick={() => openEdit(row)}
                                >
                                  Edit
                                </button>
                                <button
                                  type="button"
                                  className="btn danger small"
                                  onClick={() => setConfirmDelete(row)}
                                >
                                  Delete
                                </button>
                              </>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <EmptyRow colSpan={10}>No sewadars match these filters</EmptyRow>
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

      {/* ---------- add / edit form ---------- */}
      <Modal
        title={editing?.id ? `Edit ${editing.name || 'sewadar'}` : 'Add sewadar'}
        open={Boolean(editing)}
        onClose={() => setEditing(null)}
        footer={
          <>
            <button type="button" className="btn ghost" onClick={() => setEditing(null)}>
              Cancel
            </button>
            <button type="submit" form="sewadar-form" className="btn" disabled={saving}>
              {saving ? 'Saving...' : editing?.id ? 'Update sewadar' : 'Save sewadar'}
            </button>
          </>
        }
      >
        {editing && (
          <form id="sewadar-form" onSubmit={onSave}>
            <Alert kind="error">{formError}</Alert>

            <PhotoPicker
              onReject={setPhotoError}
              kind="sewadars"
              id={editing.id}
              stamp={editing.photoUpdatedAt}
              name={editing.name}
              file={photoFile}
              onPick={setPhotoFile}
              busy={photoBusy}
              error={photoError}
              onUpload={async (chosen) => {
                setPhotoBusy(true)
                setPhotoError('')
                try {
                  const updated = await sewadarApi.photo.upload(editing.id, chosen)
                  setEditing((cur) => ({ ...cur, photoUpdatedAt: updated.photoUpdatedAt }))
                  load()
                } catch (err) {
                  setPhotoError(errorMessage(err, 'Upload failed'))
                } finally {
                  setPhotoBusy(false)
                }
              }}
              onRemove={async () => {
                setPhotoBusy(true)
                try {
                  await sewadarApi.photo.remove(editing.id)
                  setEditing((cur) => ({ ...cur, photoUpdatedAt: null }))
                  load()
                } catch (err) {
                  setPhotoError(errorMessage(err, 'Could not remove the photo'))
                } finally {
                  setPhotoBusy(false)
                }
              }}
            />

            <Field label="Badge Number" required>
              <input
                value={editing.badgeNumber}
                onChange={set('badgeNumber')}
                placeholder="SWD-1001"
                required
              />
            </Field>
            <p className="muted" style={{ fontSize: 11.5, margin: '4px 0 16px' }}>
              The badge number is the unique sewadar id every attendance and report row
              points at.
            </p>

            <div className="form-grid">
              <Field label="Name" required>
                <input value={editing.name} onChange={set('name')} required />
              </Field>

              <Field label="Birth Date">
                <input
                  type="date"
                  max={new Date().toISOString().slice(0, 10)}
                  value={editing.dateOfBirth}
                  onChange={set('dateOfBirth')}
                />
              </Field>

              <Field label="Mobile No">
                <input
                  value={editing.mobile}
                  onChange={onMobileChange}
                  inputMode="numeric"
                  maxLength={10}
                  placeholder="9876543210"
                />
                {editing.mobile && editing.mobile.length !== 10 && (
                  <p className="photo-error">A mobile number is 10 digits.</p>
                )}
              </Field>

              <Field label="Email">
                <input type="email" value={editing.email} onChange={set('email')} />
              </Field>

              <Field label="Aadhaar No">
                <input
                  value={formatAadhar(editing.aadharNumber)}
                  onChange={onAadharChange}
                  inputMode="numeric"
                  placeholder="1234 5678 9012"
                />
              </Field>

              <Field label="Zone" required>
                <select value={editing.zoneId} onChange={set('zoneId')} required>
                  <option value="">Select a zone</option>
                  {zones.map((zone) => (
                    <option key={zone.id} value={zone.id}>
                      {zone.name}
                    </option>
                  ))}
                </select>
              </Field>

              <Field label="Blood Group">
                <select value={editing.bloodGroup} onChange={set('bloodGroup')}>
                  <option value="">Not set</option>
                  {BLOOD_GROUPS.map((group) => (
                    <option key={group} value={group}>
                      {group}
                    </option>
                  ))}
                </select>
              </Field>

              {/*
                Area and Point come from Setup rather than being typed, so the same
                place is not recorded three different ways. Both are narrowed by what
                is above them: areas by the chosen zone, points by the chosen area.
              */}
              <Field label="Area">
                <select
                  value={editing.area}
                  onChange={(e) => setEditing((c) => ({ ...c, area: e.target.value, centerPoint: '' }))}
                >
                  <option value="">Not set</option>
                  {withCurrent(zoneAreas, editing.area).map((area) => (
                    <option key={area.id} value={area.name}>
                      {area.name}
                    </option>
                  ))}
                </select>
                {editing.zoneId && zoneAreas.length === 0 && (
                  <p className="hint">No areas set up for this zone yet. Add one under Setup.</p>
                )}
              </Field>

              <Field label="Satsang Point">
                <select
                  value={editing.centerPoint}
                  onChange={set('centerPoint')}
                  disabled={!editing.area}
                >
                  <option value="">{editing.area ? 'Not set' : 'Choose an area first'}</option>
                  {withCurrent(areaPoints, editing.centerPoint).map((point) => (
                    <option key={point.id} value={point.name}>
                      {point.name}
                    </option>
                  ))}
                </select>
                {editing.area && areaPoints.length === 0 && (
                  <p className="hint">No satsang points set up for this area yet.</p>
                )}
              </Field>

              <Field label="Address" wide>
                <textarea value={editing.address} onChange={set('address')} />
              </Field>
            </div>

            {/* Kept out of the way so the form stays close to the registration slip,
                but still editable because the reports read department. */}
            <button
              type="button"
              className="btn ghost small"
              style={{ marginTop: 16 }}
              onClick={() => setShowExtras((open) => !open)}
            >
              {showExtras ? '- Hide additional details' : '+ Additional details'}
            </button>

            {showExtras && (
              <div className="form-grid" style={{ marginTop: 14 }}>
                <Field label="F/H Name">
                  <input value={editing.fatherOrHusbandName} onChange={set('fatherOrHusbandName')} placeholder="Father or husband name" />
                </Field>
                <Field label="Department / sewa group">
                  <input value={editing.department} onChange={set('department')} />
                </Field>
                <Field label="Primary sewa type">
                  <select value={editing.primarySewaType} onChange={set('primarySewaType')}>
                    <option value="">Not set</option>
                    {(options.sewaTypes || []).map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Gender">
                  <select value={editing.gender} onChange={set('gender')}>
                    <option value="">Not set</option>
                    {(options.genders || []).map((option) => (
                      <option key={option.value} value={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="City">
                  <input value={editing.city} onChange={set('city')} />
                </Field>
                <Field label="Pincode">
                  <input value={editing.pincode} onChange={set('pincode')} maxLength={6} />
                </Field>
                <Field label="Joining date">
                  <input type="date" value={editing.joiningDate} onChange={set('joiningDate')} />
                </Field>
              </div>
            )}

            <div style={{ marginTop: 18, display: 'grid', gap: 10 }}>
              <label className="checkline">
                <input type="checkbox" checked={editing.active} onChange={set('active')} />
                Active sewadar
              </label>
              <label className="checkline">
                <input type="checkbox" checked={editing.badgeReceived} onChange={set('badgeReceived')} />
                Badge received by sewadar
              </label>

              {editing.hasLogin ? (
                <p className="muted" style={{ margin: 0 }}>
                  Login already exists: <strong>{editing.existingLogin}</strong>
                </p>
              ) : (
                <>
                  <label className="checkline">
                    <input
                      type="checkbox"
                      checked={editing.createLogin}
                      onChange={set('createLogin')}
                    />
                    Create a sewadar login so this person can sign in and see their own records
                  </label>
                  {editing.createLogin && (
                    <>
                      <Field label="Login username (defaults to the badge number)">
                        <input value={editing.loginUsername} onChange={set('loginUsername')} />
                      </Field>
                      <Alert kind="info">
                        The new login starts with the password <code>Sewa@12345</code> and must be
                        changed on first sign in.
                      </Alert>
                    </>
                  )}
                </>
              )}
            </div>
          </form>
        )}
      </Modal>

      {/* ---------- read-only detail ---------- */}
      <Modal
        title={viewing?.name || 'Sewadar'}
        open={Boolean(viewing)}
        onClose={() => setViewing(null)}
        footer={
          <>
            {canManageSewadars && viewing && (
              <button
                type="button"
                className="btn"
                onClick={() => {
                  const row = viewing
                  setViewing(null)
                  openEdit(row)
                }}
              >
                Edit
              </button>
            )}
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
                kind="sewadars"
                id={viewing.id}
                stamp={viewing.photoUpdatedAt}
                name={viewing.name}
                size={88}
                square
              />
              <div>
                <h3 className="view-name">{viewing.name}</h3>
                <p className="view-meta">
                  {viewing.badgeNumber} &middot; {viewing.zoneName}
                </p>
                <Badge
                  value={viewing.active ? 'active' : 'inactive'}
                  label={viewing.active ? 'Active' : 'Inactive'}
                />
              </div>
            </div>

            <h4 className="view-section">Registration details</h4>
            <dl className="kv">
              <dt>Badge No</dt>
              <dd>{viewing.badgeNumber}</dd>
              <dt>Name</dt>
              <dd>{viewing.name}</dd>
              <dt>Birth Date</dt>
              <dd>{viewing.dateOfBirth || '-'}</dd>
              <dt>Mobile No</dt>
              <dd>{viewing.mobile || '-'}</dd>
              <dt>Email</dt>
              <dd>{viewing.email || '-'}</dd>
              <dt>Aadhaar No</dt>
              <dd>
                {formatAadhar(viewing.aadharNumber) || '-'}
                {viewing.aadharMasked && <span className="muted"> - hidden for your role</span>}
              </dd>
              <dt>Zone</dt>
              <dd>{viewing.zoneName}</dd>
              <dt>Blood Group</dt>
              <dd>{viewing.bloodGroup || '-'}</dd>
              <dt>Area</dt>
              <dd>{viewing.area || '-'}</dd>
              <dt>Point</dt>
              <dd>{viewing.centerPoint || '-'}</dd>
              <dt>Address</dt>
              <dd style={{ whiteSpace: 'pre-wrap' }}>{viewing.address || '-'}</dd>
            </dl>

            <h4 className="view-section">Sewa and contact</h4>
            <dl className="kv">
              <dt>F/H Name</dt>
              <dd>{viewing.fatherOrHusbandName || '-'}</dd>
              <dt>Department</dt>
              <dd>{viewing.department || '-'}</dd>
              <dt>Primary sewa</dt>
              <dd>{viewing.primarySewaType?.replace(/_/g, ' ') || '-'}</dd>
              <dt>Gender</dt>
              <dd>{viewing.gender ? viewing.gender[0] + viewing.gender.slice(1).toLowerCase() : '-'}</dd>
              <dt>City</dt>
              <dd>{viewing.city || '-'}</dd>
              <dt>Pincode</dt>
              <dd>{viewing.pincode || '-'}</dd>
              <dt>Joining date</dt>
              <dd>{viewing.joiningDate || '-'}</dd>
            </dl>

            <h4 className="view-section">Login</h4>
            <dl className="kv">
              <dt>Has login</dt>
              <dd>{viewing.hasLogin ? 'Yes' : 'No'}</dd>
              <dt>Username</dt>
              <dd>{viewing.loginUsername || '-'}</dd>
              <dt>Photo on file</dt>
              <dd>{viewing.hasPhoto ? 'Yes' : 'No'}</dd>
            </dl>
          </div>
        )}
      </Modal>

      {/* ---------- delete confirm ---------- */}
      <Modal
        narrow
        title="Delete sewadar"
        open={Boolean(confirmDelete)}
        onClose={() => setConfirmDelete(null)}
        footer={
          <>
            <button type="button" className="btn ghost" onClick={() => setConfirmDelete(null)}>
              Cancel
            </button>
            <button type="button" className="btn danger" onClick={onDelete}>
              Delete
            </button>
          </>
        }
      >
        <p>
          Delete <strong>{confirmDelete?.name}</strong> ({confirmDelete?.badgeNumber})?
        </p>
        <p className="muted">
          A sewadar who already has attendance history is deactivated rather than removed, so the
          past records stay intact.
        </p>
      </Modal>
    </div>
  )
}
