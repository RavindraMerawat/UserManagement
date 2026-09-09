import { useCallback, useEffect, useMemo, useState } from 'react'
import { metaApi, sewadarApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { Badge, EmptyRow, Field, Pager } from '../components/Bits'

const EMPTY_FORM = {
  badgeNumber: '',
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
function formatAadhar(value) {
  if (!value) return ''
  const digits = value.replace(/[^0-9]/g, '')
  return digits.replace(/(\d{4})(?=\d)/g, '$1 ').trim()
}

export default function Sewadars() {
  const { canManageSewadars } = useAuth()

  const [filters, setFilters] = useState({ query: '', zoneId: '', active: 'true' })
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [zones, setZones] = useState([])
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
  }, [])

  const openCreate = () => {
    setFormError('')
    setShowExtras(false)
    setEditing({ ...EMPTY_FORM, zoneId: zones[0]?.id ?? '' })
  }

  const openEdit = (row) => {
    setFormError('')
    setShowExtras(false)
    // Null columns come back as undefined, which React would treat as uncontrolled.
    setEditing({
      id: row.id,
      badgeNumber: row.badgeNumber || '',
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

    try {
      if (editing.id) {
        await sewadarApi.update(editing.id, payload)
        setNotice(`${payload.name} updated`)
      } else {
        await sewadarApi.create(payload)
        setNotice(`${payload.name} added`)
      }
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

  // Aadhaar is typed as digits but shown in groups of four.
  const onAadharChange = (event) => {
    const digits = event.target.value.replace(/[^0-9]/g, '').slice(0, 12)
    setEditing((current) => ({ ...current, aadharNumber: digits }))
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
                    <th>Badge</th>
                    <th>Name</th>
                    <th>F/H Name</th>
                    <th>Birth Date</th>
                    <th>Mobile No</th>
                    <th>Zone</th>
                    <th>Area</th>
                    <th>Center / Point</th>
                    <th>Blood Group</th>
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
                          {row.name}
                          {row.hasLogin && (
                            <div className="muted" style={{ fontSize: 11.5 }}>
                              login: {row.loginUsername}
                            </div>
                          )}
                        </td>
                        <td>{row.fatherOrHusbandName || '-'}</td>
                        <td>{row.dateOfBirth || '-'}</td>
                        <td>{row.mobile || '-'}</td>
                        <td>{row.zoneName}</td>
                        <td>{row.area || '-'}</td>
                        <td>{row.centerPoint || '-'}</td>
                        <td>{row.bloodGroup || '-'}</td>
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
                    <EmptyRow colSpan={11}>No sewadars match these filters</EmptyRow>
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

              <Field label="F/H Name">
                <input
                  value={editing.fatherOrHusbandName}
                  onChange={set('fatherOrHusbandName')}
                  placeholder="Father or husband name"
                />
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
                <input value={editing.mobile} onChange={set('mobile')} placeholder="9876543210" />
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

              <Field label="Aadhaar No">
                <input
                  value={formatAadhar(editing.aadharNumber)}
                  onChange={onAadharChange}
                  inputMode="numeric"
                  placeholder="1234 5678 9012"
                />
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

              <Field label="Area">
                <input value={editing.area} onChange={set('area')} placeholder="Sector 12" />
              </Field>

              <Field label="Center / Point">
                <input
                  value={editing.centerPoint}
                  onChange={set('centerPoint')}
                  placeholder="Main Center"
                />
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
                <Field label="Email">
                  <input type="email" value={editing.email} onChange={set('email')} />
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
          <dl className="kv">
            <dt>Badge Number</dt>
            <dd>{viewing.badgeNumber}</dd>
            <dt>Name</dt>
            <dd>{viewing.name}</dd>
            <dt>F/H Name</dt>
            <dd>{viewing.fatherOrHusbandName || '-'}</dd>
            <dt>Birth Date</dt>
            <dd>{viewing.dateOfBirth || '-'}</dd>
            <dt>Mobile No</dt>
            <dd>{viewing.mobile || '-'}</dd>
            <dt>Zone</dt>
            <dd>{viewing.zoneName}</dd>
            <dt>Address</dt>
            <dd style={{ whiteSpace: 'pre-wrap' }}>{viewing.address || '-'}</dd>
            <dt>Aadhaar No</dt>
            <dd>{formatAadhar(viewing.aadharNumber) || '-'}</dd>
            <dt>Blood Group</dt>
            <dd>{viewing.bloodGroup || '-'}</dd>
            <dt>Area</dt>
            <dd>{viewing.area || '-'}</dd>
            <dt>Center / Point</dt>
            <dd>{viewing.centerPoint || '-'}</dd>
            <dt>Department</dt>
            <dd>{viewing.department || '-'}</dd>
            <dt>Primary sewa</dt>
            <dd>{viewing.primarySewaType?.replace(/_/g, ' ') || '-'}</dd>
            <dt>Status</dt>
            <dd>
              <Badge
                value={viewing.active ? 'active' : 'inactive'}
                label={viewing.active ? 'Active' : 'Inactive'}
              />
            </dd>
          </dl>
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
