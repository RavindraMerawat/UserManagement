import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { metaApi, setupApi, sewadarApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { Badge, EmptyRow, Field, Pager, TabStrip } from '../components/Bits'
import { Avatar, PhotoPicker } from '../components/Photo'
import { todayIso } from '../dates'

const EMPTY_FORM = {
  badgeNumber: '',
  // registration fields, in the order they appear on the form
  name: '',
  fatherOrHusbandName: '',
  dateOfBirth: '',
  age: '',
  mobile: '',
  designationId: '',
  zoneId: '',
  extraZoneIds: [],
  address: '',
  aadharNumber: '',
  bloodGroup: '',
  area: '',
  grouping: '',
  locality: '',
  centerPoint: '',
  // additional details
  gender: '',
  email: '',
  department: 'Pandal',
  status: '',
  joiningDate: '',
  exempted: false,
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

  const [filters, setFilters] = useState({ query: '', zoneId: '', designationId: '' })
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [zones, setZones] = useState([])
  const [areas, setAreas] = useState([])
  const [designations, setDesignations] = useState([])
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

  // Bulk add. The template carries its own instructions - red headings for the
  // required columns, a dropdown on each column that has a fixed set of values - so
  // the button downloads it and nothing else.
  const [bulkOpen, setBulkOpen] = useState(false)
  const [bulkBusy, setBulkBusy] = useState(false)
  const [uploadProblems, setUploadProblems] = useState(null)
  const [uploadError, setUploadError] = useState('')
  // The file picker is opened by the Upload button rather than sitting on the
  // window as a field, so the window asks two questions and no more.
  const filePicker = useRef(null)
  const [viewing, setViewing] = useState(null)
  const [photoFile, setPhotoFile] = useState(null)
  const [photoBusy, setPhotoBusy] = useState(false)
  const [photoError, setPhotoError] = useState('')

  const params = useMemo(
    () => ({
      query: filters.query || undefined,
      zoneId: filters.zoneId || undefined,
      designationId: filters.designationId || undefined,
      page,
      size: 25,
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
    // Designations are both a filter above the grid and a field on the form, so
    // they are loaded for everyone who can open the screen.
    setupApi
      .designations({ active: true })
      .then(setDesignations)
      .catch(() => setDesignations([]))
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

  const openBulk = () => {
    setUploadProblems(null)
    setUploadError('')
    setBulkOpen(true)
  }

  const downloadTemplate = async () => {
    setBulkBusy(true)
    setError('')
    try {
      const name = await sewadarApi.template()
      // The window has done its first job, so it gets out of the way; the message
      // stays on the page to say what happened and what comes next.
      setBulkOpen(false)
      setNotice(`${name} downloaded. Fill it in, save it as CSV, then upload it here.`)
    } catch (err) {
      setError(errorMessage(err, 'Could not download the template'))
    } finally {
      setBulkBusy(false)
    }
  }

  /** Runs as soon as a file is chosen - there is nothing else to ask. */
  const onFileChosen = async (event) => {
    const file = event.target.files?.[0]
    // Cleared straight away so choosing the same file after a fix still counts as
    // a change and uploads again.
    event.target.value = ''
    if (!file) return

    setBulkBusy(true)
    setUploadError('')
    setUploadProblems(null)
    try {
      const result = await sewadarApi.bulkUpload(file)
      if (result.problems.length === 0) {
        setBulkOpen(false)
        setNotice(`${result.created} sewadars added from ${file.name}`)
        load()
      } else {
        setUploadProblems(result)
      }
    } catch (err) {
      setUploadError(errorMessage(err, 'The upload failed'))
    } finally {
      setBulkBusy(false)
    }
  }

  /*
   * The two rules this form carries beyond its fields.
   *
   * A co-ordinator covers more than one zone, so their Zone field takes several
   * and everybody else's takes one. And only Indore's areas are grouped, so
   * Grouping is offered there and left out everywhere else rather than sitting
   * disabled on every record.
   */
  const chosenDesignation = designations.find(
    (d) => String(d.id) === String(editing?.designationId),
  )
  const isCoordinator =
    (chosenDesignation?.designation || '').toLowerCase().replace(/[^a-z]/g, '') === 'coordinator'
  const areaHasGrouping = (editing?.area || '').trim().toLowerCase() === 'indore'

  /*
   * Ticking a zone off the list. The first zone ticked is the one the record
   * belongs to and the rest are the ones it reaches - so untucking that first one
   * hands its place to the next, rather than leaving the record in no zone at all.
   * Areas belong to that first zone, so they start again whenever it changes.
   */
  const toggleZone = (value) => {
    const id = String(value)
    setEditing((c) => {
      const own = String(c.zoneId || '')
      const covered = c.extraZoneIds.map(String)
      if (own === id) {
        const [next = '', ...rest] = covered
        return { ...c, zoneId: next, extraZoneIds: rest, area: '', centerPoint: '' }
      }
      if (covered.includes(id)) {
        return { ...c, extraZoneIds: covered.filter((x) => x !== id) }
      }
      if (!own) {
        return { ...c, zoneId: id, extraZoneIds: covered, area: '', centerPoint: '' }
      }
      return { ...c, extraZoneIds: [...covered, id] }
    })
  }

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
      name: row.name || '',
      fatherOrHusbandName: row.fatherOrHusbandName || '',
      dateOfBirth: row.dateOfBirth || '',
      age: row.age ?? '',
      mobile: row.mobile || '',
      zoneId: row.zoneId ?? '',
      extraZoneIds: (row.extraZoneIds || []).map(String),
      address: row.address || '',
      aadharNumber: row.aadharNumber || '',
      bloodGroup: row.bloodGroup || '',
      area: row.area || '',
      grouping: row.grouping || '',
      locality: row.locality || '',
      centerPoint: row.centerPoint || '',
      gender: row.gender || '',
      email: row.email || '',
      department: row.department || '',
      status: row.status || '',
      designationId: row.designationId ? String(row.designationId) : '',
      joiningDate: row.joiningDate || '',
      exempted: row.exempted ?? false,
      hasLogin: row.hasLogin,
      existingLogin: row.loginUsername,
      photoUpdatedAt: row.photoUpdatedAt,
    })
  }

  const onSave = async (event) => {
    event.preventDefault()
    setFormError('')

    /*
     * A tick box has no "required" the browser will enforce, so the one rule the
     * zone field carries is checked here: a record belongs to a zone, whether it
     * was picked from a list or ticked off one.
     */
    if (!editing.zoneId) {
      setFormError('Pick at least one zone')
      return
    }

    setSaving(true)

    // Blank dates and enums must reach the server as null, not as empty strings.
    const payload = {
      ...editing,
      zoneId: Number(editing.zoneId),
      // Only a co-ordinator covers more than their own zone; anybody else sends
      // none, so a designation change cannot leave stale reach behind.
      extraZoneIds: isCoordinator ? editing.extraZoneIds.map(Number) : [],
      dateOfBirth: editing.dateOfBirth || null,
      age: editing.age === '' ? null : Number(editing.age),
      joiningDate: editing.joiningDate || null,
      gender: editing.gender || null,
      locality: editing.locality || null,
      status: editing.status || null,
      designationId: editing.designationId ? Number(editing.designationId) : null,
      aadharNumber: editing.aadharNumber ? editing.aadharNumber.replace(/[^0-9]/g, '') : null,
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

  // Zone, area and satsang point are three separate lists in Setup, so each picker
  // offers all of its own and none of them narrows another.

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
            <div className="head-actions">
              <button type="button" className="btn" onClick={openCreate}>
                + Add sewadar
              </button>
              <button type="button" className="btn ghost" onClick={openBulk}>
                Bulk Add Sewadar
              </button>
            </div>
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
          {/* The office looks for "the supervisors" as often as for a name. */}
          <Field label="Designation">
            <select
              value={filters.designationId}
              onChange={(e) => {
                setPage(0)
                setFilters({ ...filters, designationId: e.target.value })
              }}
            >
              <option value="">All designations</option>
              {designations.map((designation) => (
                <option key={designation.id} value={designation.id}>
                  {designation.name}
                </option>
              ))}
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
                    <th>GR. No</th>
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
                          {row.statusLabel ? (
                            <Badge
                              value={row.status === 'PERMANENT' ? 'active' : 'inactive'}
                              label={row.statusLabel}
                            />
                          ) : (
                            <span className="muted">-</span>
                          )}
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
              {saving ? 'Saving...' : editing?.id ? 'Update sewadar' : 'Save'}
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

            <Field label="GR. No" required>
              <input
                value={editing.badgeNumber}
                onChange={set('badgeNumber')}
                placeholder="SWD-1001"
                required
              />
            </Field>
            <p className="muted" style={{ fontSize: 11.5, margin: '4px 0 16px' }}>
              The GR. No is the unique sewadar id every attendance and report row
              points at.
            </p>

            <p className="form-section">Personal details</p>
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

              {/*
                Gender sits on the main form, not behind "additional details".
                The dashboard counts male and female as separate cards, so a record
                saved without it is counted in neither - and hidden behind a
                collapsed section is exactly how it came to be left unset.
              */}
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

              <Field label="Birth Date">
                <input
                  type="date"
                  max={todayIso()}
                  value={editing.dateOfBirth}
                  onChange={set('dateOfBirth')}
                />
              </Field>

              <Field label="Age">
                <input
                  value={editing.age}
                  onChange={(e) =>
                    setEditing((c) => ({ ...c, age: e.target.value.replace(/[^0-9]/g, '').slice(0, 3) }))
                  }
                  inputMode="numeric"
                  placeholder="34"
                />
                {editing.age !== '' && (Number(editing.age) < 1 || Number(editing.age) > 120) && (
                  <p className="photo-error">Age must be between 1 and 120.</p>
                )}
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

              <Field label="Aadhaar No">
                <input
                  value={formatAadhar(editing.aadharNumber)}
                  onChange={onAadharChange}
                  inputMode="numeric"
                  placeholder="1234 5678 9012"
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
              {/*
                Designation first, because it decides what the zone field is: a
                co-ordinator picks several zones, everybody else picks one.
              */}
              <Field label="Designation">
                <select
                  value={editing.designationId}
                  onChange={(e) =>
                    setEditing((c) => ({
                      ...c,
                      designationId: e.target.value,
                      // The zone field itself changes shape here, so its value
                      // starts again rather than carrying one pick into a list.
                      zoneId: '',
                      extraZoneIds: [],
                      area: '',
                      centerPoint: '',
                    }))
                  }
                >
                  <option value="">Not set</option>
                  {designations.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.designation}
                    </option>
                  ))}
                </select>
              </Field>

              <Field label={isCoordinator ? 'Zones' : 'Zone'} required>
                {isCoordinator ? (
                  <div className="zone-picks">
                    {zones.map((zone) => (
                      <label key={zone.id} className="checkline">
                        <input
                          type="checkbox"
                          checked={
                            String(editing.zoneId) === String(zone.id) ||
                            editing.extraZoneIds.includes(String(zone.id))
                          }
                          onChange={() => toggleZone(zone.id)}
                        />
                        {zone.name}
                      </label>
                    ))}
                  </div>
                ) : (
                  <select value={editing.zoneId} onChange={set('zoneId')} required>
                    <option value="">Select a zone</option>
                    {zones.map((zone) => (
                      <option key={zone.id} value={zone.id}>
                        {zone.name}
                      </option>
                    ))}
                  </select>
                )}
                {isCoordinator && (
                  <p className="hint">
                    Tick every zone they cover. The first ticked is their own.
                  </p>
                )}
              </Field>

              {/*
                Area and Point come from Setup rather than being typed, so the same
                place is not recorded three different ways. Both are narrowed by what
                is above them: areas by the chosen zone, points by the chosen area.
                They read left to right in that order - zone, then area, then point.
              */}
              <Field label="Area">
                <select
                  value={editing.area}
                  onChange={(e) =>
                    setEditing((c) => ({
                      ...c,
                      area: e.target.value,
                      // The point is its own list now, so it survives an area change.
                      // Grouping does not: it belongs to Indore and nowhere else.
                      grouping:
                        e.target.value.trim().toLowerCase() === 'indore' ? c.grouping : '',
                    }))
                  }
                >
                  <option value="">Not set</option>
                  {withCurrent(areas, editing.area).map((area) => (
                    <option key={area.id} value={area.name}>
                      {area.name}
                    </option>
                  ))}
                </select>
                {areas.length === 0 && (
                  <p className="hint">No areas set up yet. Add one under Setup.</p>
                )}
              </Field>

              <Field label="Locality" required>
                <select value={editing.locality} onChange={set('locality')} required>
                  <option value="">Select</option>
                  <option value="LOCAL">Local</option>
                  <option value="OUTSTATION">Outstation</option>
                </select>
              </Field>

              {areaHasGrouping && (
                <Field label="Grouping" required>
                  <input
                    value={editing.grouping}
                    onChange={set('grouping')}
                    placeholder="Group inside Indore"
                    required
                  />
                </Field>
              )}

              <Field label="Satsang Point">
                <select value={editing.centerPoint} onChange={set('centerPoint')}>
                  <option value="">Not set</option>
                  {withCurrent(points, editing.centerPoint).map((point) => (
                    <option key={point.id} value={point.name}>
                      {point.name}
                    </option>
                  ))}
                </select>
                {points.length === 0 && (
                  <p className="hint">No satsang points set up yet. Add one under Setup.</p>
                )}
              </Field>

            </div>

            {/*
              Designation is what the permission rules read, so it is on the main
              form rather than in a collapsed section - out of sight is how it ends
              up unset. Exempted belongs here too: it qualifies the status beside it.
            */}
            <p className="form-section">Role and status</p>
            <div className="form-grid">
              <Field label="Status">
                <select value={editing.status} onChange={set('status')}>
                  <option value="">Not set</option>
                  <option value="PERMANENT">Permanent</option>
                  <option value="OPEN">Open</option>
                </select>
              </Field>

              {/*
                Exempted is excused from attendance - the person stays on the roster
                and in the reports, and a low figure against their name is expected
                rather than a finding. No is the default, so the flag only ever means
                something when someone has deliberately set it.
              */}
              <div className="radio-field">
                <span className="radio-field-label">Exempted</span>
                <div className="radio-row">
                  <label className="checkline">
                    <input
                      type="radio"
                      name="exempted"
                      checked={!editing.exempted}
                      onChange={() => setEditing((e) => ({ ...e, exempted: false }))}
                    />
                    No
                  </label>
                  <label className="checkline">
                    <input
                      type="radio"
                      name="exempted"
                      checked={Boolean(editing.exempted)}
                      onChange={() => setEditing((e) => ({ ...e, exempted: true }))}
                    />
                    Yes
                  </label>
                </div>
              </div>
            </div>

            {/* Kept out of the way so the form stays close to the registration slip,
                but still editable because the reports read department. */}
            <button
              type="button"
              className="btn ghost small"
              style={{ marginTop: 18 }}
              onClick={() => setShowExtras((open) => !open)}
            >
              {showExtras ? '- Hide additional details' : '+ Additional details'}
            </button>

            {showExtras && (
              <div className="form-grid" style={{ marginTop: 14 }}>
                <Field label="Address" wide>
                  <textarea value={editing.address} onChange={set('address')} />
                </Field>
                <Field label="Department">
                  <input value={editing.department} onChange={set('department')} />
                </Field>
                <Field label="Joining date">
                  <input type="date" value={editing.joiningDate} onChange={set('joiningDate')} />
                </Field>
              </div>
            )}

            {/*
              Active, Badge received and the login checkbox are gone from this form.
              Badge issue and collection are recorded on Badge Detail, which is where
              that work is actually done, and an existing login is shown here for
              reference.
            */}
            {editing.hasLogin && (
              <p className="muted" style={{ marginTop: 18 }}>
                Login: <strong>{editing.existingLogin}</strong>
              </p>
            )}

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
                {viewing.statusLabel && (
                  <Badge
                    value={viewing.status === 'PERMANENT' ? 'active' : 'inactive'}
                    label={viewing.statusLabel}
                  />
                )}
              </div>
            </div>

            <h4 className="view-section">Registration details</h4>
            <dl className="kv">
              <dt>GR. No</dt>
              <dd>{viewing.badgeNumber}</dd>
              <dt>Name</dt>
              <dd>{viewing.name}</dd>
              <dt>Birth Date</dt>
              <dd>{viewing.dateOfBirth || '-'}</dd>
              <dt>Age</dt>
              <dd>{viewing.age ?? '-'}</dd>
              <dt>Mobile No</dt>
              <dd>{viewing.mobile || '-'}</dd>
              <dt>Email</dt>
              <dd>{viewing.email || '-'}</dd>
              <dt>Aadhaar No</dt>
              <dd>
                {formatAadhar(viewing.aadharNumber) || '-'}
                {viewing.aadharMasked && <span className="muted"> - hidden for your role</span>}
              </dd>
              <dt>{viewing.extraZoneNames?.length ? 'Zones' : 'Zone'}</dt>
              <dd>{[viewing.zoneName, ...(viewing.extraZoneNames || [])].join(', ')}</dd>
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
              <dt>Status</dt>
              <dd>{viewing.statusLabel || '-'}</dd>
              <dt>Grouping</dt>
              <dd>{viewing.grouping || '-'}</dd>
              <dt>Locality</dt>
              <dd>{viewing.localityLabel || '-'}</dd>
              <dt>Designation</dt>
              <dd>{viewing.designationName || '-'}</dd>
              <dt>Gender</dt>
              <dd>{viewing.gender ? viewing.gender[0] + viewing.gender.slice(1).toLowerCase() : '-'}</dd>
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

      {/* ---------- bulk add: download the template, or upload a filled-in one ---------- */}
      <Modal
        title="Bulk Add Sewadar"
        open={bulkOpen}
        onClose={() => setBulkOpen(false)}
        footer={
          <button type="button" className="btn ghost" onClick={() => setBulkOpen(false)}>
            Close
          </button>
        }
      >
        <Alert kind="error">{uploadError}</Alert>

        <p>Adding sewadars in bulk takes two steps.</p>
        <ol className="bulk-steps">
          <li>
            Download the template and fill in one row per sewadar. Red headings must be
            filled in; the others may be left empty.
          </li>
          <li>
            In Excel choose <strong>File &gt; Save As</strong> and pick{' '}
            <strong>CSV (Comma delimited)</strong>, then upload that file here.
          </li>
        </ol>

        <div className="bulk-actions">
          <button type="button" className="btn ghost" onClick={downloadTemplate} disabled={bulkBusy}>
            {bulkBusy && !uploadProblems ? 'Working...' : 'Download Template'}
          </button>
          <button
            type="button"
            className="btn"
            onClick={() => filePicker.current?.click()}
            disabled={bulkBusy}
          >
            {bulkBusy ? 'Uploading...' : 'Bulk Upload Sewadar'}
          </button>
          <input
            ref={filePicker}
            type="file"
            accept=".csv,text/csv"
            onChange={onFileChosen}
            style={{ display: 'none' }}
          />
        </div>

        {uploadProblems && (
          <>
            <Alert kind="error">
              {uploadProblems.problems.length === 1
                ? 'One row needs fixing, so nothing was saved.'
                : `${uploadProblems.problems.length} things need fixing, so nothing was saved.`}{' '}
              Correct the rows below in your file, save it as CSV again, and upload it once
              more.
            </Alert>
            <div className="table-wrap">
              <table className="problem-table">
                <thead>
                  <tr>
                    <th>Row</th>
                    <th>Column</th>
                    <th>Value</th>
                    <th>Problem</th>
                  </tr>
                </thead>
                <tbody>
                  {uploadProblems.problems.map((problem, i) => (
                    <tr key={`${problem.line}-${problem.column}-${i}`}>
                      <td>{problem.line}</td>
                      <td>{problem.column || '-'}</td>
                      <td className="muted">{problem.value || '-'}</td>
                      <td>{problem.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Modal>
    </div>
  )
}
