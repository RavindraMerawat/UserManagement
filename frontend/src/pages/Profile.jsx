import { useEffect, useState } from 'react'
import { authApi, sewadarApi, userApiPhoto } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { applyTheme, readTheme } from '../theme'
import Alert from '../components/Alert'
import { Field } from '../components/Bits'
import { Avatar, PhotoPicker } from '../components/Photo'

/**
 * Profile and preferences, as three sections behind a sub-navigation: who you are,
 * your password, and how the application looks.
 *
 * <p>The design also draws a Notifications section. There is no notification
 * preference to store - nothing in this application emails or pushes to a person -
 * so it is not drawn, rather than offering switches that control nothing.</p>
 */
const SECTIONS = [
  { key: 'profile', label: 'Profile', icon: '\u{1F464}' },
  { key: 'password', label: 'Change Password', icon: '\u{1F511}' },
  { key: 'appearance', label: 'Appearance', icon: '\u{1F3A8}' },
]

const THEMES = [
  { key: 'system', label: 'Match my device', hint: 'Follows your system setting' },
  { key: 'light', label: 'Light', hint: 'Always light' },
  { key: 'dark', label: 'Dark', hint: 'Always dark' },
]

export default function Profile() {
  const { user, isSewadar, refresh, can } = useAuth()
  const [section, setSection] = useState('profile')
  const [sewadar, setSewadar] = useState(null)
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [show, setShow] = useState({ current: false, next: false, confirm: false })
  const [theme, setTheme] = useState(readTheme)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  /*
   * Your own picture, set here rather than only from the User Account screen.
   * The photo endpoints live under /api/users, which the server opens to Admin
   * and Office Admin alone - so the picker is shown to exactly those roles and
   * everyone else keeps the plain avatar, instead of a button that would 403.
   */
  const canSetPhoto = can('USERS')
  const [photoBusy, setPhotoBusy] = useState(false)
  const [photoError, setPhotoError] = useState('')

  const uploadPhoto = async (chosen) => {
    setPhotoBusy(true)
    setPhotoError('')
    try {
      await userApiPhoto.upload(user.userId, chosen)
      // Re-read the session so the topbar's avatar changes with this one.
      await refresh()
      setNotice('Your photo has been updated')
    } catch (err) {
      setPhotoError(errorMessage(err, 'Could not upload the photo'))
    } finally {
      setPhotoBusy(false)
    }
  }

  const removePhoto = async () => {
    setPhotoBusy(true)
    setPhotoError('')
    try {
      await userApiPhoto.remove(user.userId)
      await refresh()
      setNotice('Your photo has been removed')
    } catch (err) {
      setPhotoError(errorMessage(err, 'Could not remove the photo'))
    } finally {
      setPhotoBusy(false)
    }
  }

  useEffect(() => {
    if (!isSewadar) return
    sewadarApi.me().then(setSewadar).catch(() => setSewadar(null))
  }, [isSewadar])

  const chooseTheme = (next) => {
    setTheme(next)
    applyTheme(next)
  }

  const onSubmit = async (event) => {
    event.preventDefault()
    setNotice('')
    if (form.newPassword !== form.confirm) {
      setError('The new password and the confirmation do not match.')
      return
    }
    setBusy(true)
    setError('')
    try {
      await authApi.changePassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
      })
      setNotice('Your password has been updated.')
      setForm({ currentPassword: '', newPassword: '', confirm: '' })
      await refresh()
    } catch (err) {
      setError(errorMessage(err, 'Could not change your password'))
    } finally {
      setBusy(false)
    }
  }

  /** A password box with the reveal toggle inside it. */
  const secret = (key, label, field, autoComplete) => (
    <Field label={label} required>
      <div className="password-field">
        <input
          type={show[key] ? 'text' : 'password'}
          autoComplete={autoComplete}
          value={form[field]}
          onChange={(e) => setForm({ ...form, [field]: e.target.value })}
          required
        />
        <button
          type="button"
          className="password-eye"
          onClick={() => setShow((s) => ({ ...s, [key]: !s[key] }))}
          aria-label={show[key] ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
          aria-pressed={show[key]}
        >
          {show[key] ? '\u{1F648}' : '\u{1F441}'}
        </button>
      </div>
    </Field>
  )

  return (
    <div>
      <div className="page-head">
        <div>
          <h1 className="page-title">My Profile</h1>
          <p className="page-sub">Manage your profile and preferences.</p>
        </div>
      </div>

      <div className="settings-shell">
        <nav className="settings-nav" aria-label="Profile sections">
          {SECTIONS.map((item) => (
            <button
              key={item.key}
              type="button"
              className={item.key === section ? 'active' : undefined}
              onClick={() => setSection(item.key)}
            >
              <span aria-hidden="true">{item.icon}</span>
              {item.label}
            </button>
          ))}
        </nav>

        <div className="settings-body">
          {section === 'profile' && (
            <div className="card">
              <div className="view-head">
                {canSetPhoto ? (
                  <PhotoPicker
                    kind="users"
                    id={user?.userId}
                    stamp={user?.photoUpdatedAt}
                    name={user?.fullName}
                    busy={photoBusy}
                    error={photoError}
                    onReject={setPhotoError}
                    onUpload={uploadPhoto}
                    onRemove={removePhoto}
                  />
                ) : (
                  <Avatar
                    kind="users"
                    id={user?.userId}
                    stamp={user?.photoUpdatedAt}
                    name={user?.fullName}
                    size={76}
                    square
                  />
                )}
                <div>
                  <h3 className="view-name">{user?.fullName}</h3>
                  <p className="view-meta">{user?.roleDisplayName}</p>
                </div>
              </div>

              <div className="form-grid" style={{ marginTop: 18 }}>
                <Field label="Full name">
                  <input value={user?.fullName || ''} readOnly />
                </Field>
                <Field label="Username">
                  <input value={user?.username || ''} readOnly />
                </Field>
                <Field label="Email">
                  <input value={user?.email || '—'} readOnly />
                </Field>
                <Field label="Role">
                  <input value={user?.roleDisplayName || ''} readOnly />
                </Field>
              </div>

              <p className="hint" style={{ marginTop: 14 }}>
                {/* Saying who changes it beats a disabled field with no explanation. */}
                Your details are maintained by an administrator. Ask them to change any of
                these, or use Change Password to set your own password.
              </p>

              <dl className="kv" style={{ marginTop: 18 }}>
                <dt>Zones you can reach</dt>
                <dd>{user?.zoneNames?.length ? user.zoneNames.join(', ') : 'All zones'}</dd>
                {sewadar && (
                  <>
                    <dt>GR. No</dt>
                    <dd>{sewadar.badgeNumber}</dd>
                    <dt>Zone</dt>
                    <dd>{sewadar.zoneName}</dd>
                  </>
                )}
              </dl>
            </div>
          )}

          {section === 'password' && (
            <div className="card">
              <div className="card-head">
                <h3>Change Password</h3>
              </div>
              <Alert kind="error" onClose={() => setError('')}>
                {error}
              </Alert>
              <Alert kind="success" onClose={() => setNotice('')}>
                {notice}
              </Alert>
              <form onSubmit={onSubmit} style={{ display: 'grid', gap: 14, maxWidth: 420 }}>
                {secret('current', 'Current password', 'currentPassword', 'current-password')}
                {secret('next', 'New password', 'newPassword', 'new-password')}
                {secret('confirm', 'Confirm new password', 'confirm', 'new-password')}
                <p className="hint">At least 8 characters.</p>
                <button type="submit" className="btn" disabled={busy}>
                  {busy ? 'Updating...' : 'Update Password'}
                </button>
              </form>
            </div>
          )}

          {section === 'appearance' && (
            <div className="card">
              <div className="card-head">
                <h3>Appearance</h3>
              </div>
              <p className="hint" style={{ marginBottom: 16 }}>
                Saved on this device, so it does not follow you to another browser.
              </p>
              <div className="theme-choices">
                {THEMES.map((option) => (
                  <button
                    key={option.key}
                    type="button"
                    className={option.key === theme ? 'theme-choice active' : 'theme-choice'}
                    onClick={() => chooseTheme(option.key)}
                    aria-pressed={option.key === theme}
                  >
                    <span className={`theme-swatch ${option.key}`} aria-hidden="true" />
                    <strong>{option.label}</strong>
                    <span>{option.hint}</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
