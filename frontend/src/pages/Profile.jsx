import { useEffect, useState } from 'react'
import { authApi, sewadarApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import { Field } from '../components/Bits'

export default function Profile() {
  const { user, isSewadar, refresh } = useAuth()
  const [sewadar, setSewadar] = useState(null)
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (!isSewadar) return
    sewadarApi.me().then(setSewadar).catch(() => setSewadar(null))
  }, [isSewadar])

  const onSubmit = async (event) => {
    event.preventDefault()
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

  return (
    <div className="grid-2">
      <div>
        <div className="card">
          <h3>My account</h3>
          <dl className="kv">
            <dt>Username</dt>
            <dd>{user?.username}</dd>
            <dt>Full name</dt>
            <dd>{user?.fullName}</dd>
            <dt>Role</dt>
            <dd>
              <span className="badge role">{user?.roleDisplayName}</span>
            </dd>
            <dt>Email</dt>
            <dd>{user?.email || '-'}</dd>
            <dt>Zones</dt>
            <dd>{user?.zoneNames?.length ? user.zoneNames.join(', ') : 'All zones'}</dd>
          </dl>
        </div>

        {sewadar && (
          <div className="card">
            <h3>My sewadar record</h3>
            <dl className="kv">
              <dt>Badge number</dt>
              <dd>{sewadar.badgeNumber}</dd>
              <dt>Zone</dt>
              <dd>{sewadar.zoneName}</dd>
              <dt>Department</dt>
              <dd>{sewadar.department || '-'}</dd>
              <dt>Primary sewa</dt>
              <dd>{sewadar.primarySewaType?.replace(/_/g, ' ') || '-'}</dd>
              <dt>Mobile</dt>
              <dd>{sewadar.mobile || '-'}</dd>
              <dt>Joining date</dt>
              <dd>{sewadar.joiningDate || '-'}</dd>
            </dl>
            <p className="muted" style={{ marginBottom: 0, marginTop: 12 }}>
              Ask the office admin to correct anything that is out of date. Use the Request screen
              for a zone change.
            </p>
          </div>
        )}
      </div>

      <div className="card">
        <h3>Change password</h3>
        {user?.mustChangePassword && (
          <Alert kind="warn">
            Your account is still on the password that was set for you. Please change it now.
          </Alert>
        )}
        <Alert kind="error" onClose={() => setError('')}>
          {error}
        </Alert>
        <Alert kind="success" onClose={() => setNotice('')}>
          {notice}
        </Alert>

        <form onSubmit={onSubmit} style={{ display: 'grid', gap: 14 }}>
          <Field label="Current password" required>
            <input
              type="password"
              autoComplete="current-password"
              value={form.currentPassword}
              onChange={(e) => setForm({ ...form, currentPassword: e.target.value })}
              required
            />
          </Field>
          <Field label="New password" required>
            <input
              type="password"
              autoComplete="new-password"
              minLength={8}
              value={form.newPassword}
              onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
              required
            />
          </Field>
          <Field label="Confirm new password" required>
            <input
              type="password"
              autoComplete="new-password"
              minLength={8}
              value={form.confirm}
              onChange={(e) => setForm({ ...form, confirm: e.target.value })}
              required
            />
          </Field>
          <div className="btn-row">
            <button type="submit" className="btn" disabled={busy}>
              {busy ? 'Updating...' : 'Change password'}
            </button>
          </div>
        </form>
        <p className="muted" style={{ marginBottom: 0, marginTop: 14 }}>
          Use at least 8 characters. The new password must differ from the current one.
        </p>
      </div>
    </div>
  )
}
