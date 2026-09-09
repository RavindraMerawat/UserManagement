import { useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../api/client'
import Alert from '../components/Alert'

export default function Login() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()

  const [form, setForm] = useState({ username: '', password: '' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  if (isAuthenticated) {
    navigate('/', { replace: true })
  }

  const onSubmit = async (event) => {
    event.preventDefault()
    setError('')
    setBusy(true)
    try {
      const result = await login(form.username.trim(), form.password)
      // A freshly created account has to set its own password before going further.
      const target = result.mustChangePassword ? '/profile' : location.state?.from || '/'
      navigate(target, { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Invalid username or password'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={onSubmit}>
        <h1>Sewadar Management</h1>
        <p className="sub">Sign in to manage attendance, sewa records and reports.</p>

        {params.get('expired') && (
          <Alert kind="warn">Your session expired. Please sign in again.</Alert>
        )}
        <Alert kind="error" onClose={() => setError('')}>
          {error}
        </Alert>

        <div className="field wide" style={{ marginBottom: 14 }}>
          <label htmlFor="username">Username</label>
          <input
            id="username"
            autoFocus
            autoComplete="username"
            value={form.username}
            onChange={(e) => setForm({ ...form, username: e.target.value })}
            required
          />
        </div>

        <div className="field wide" style={{ marginBottom: 20 }}>
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            required
          />
        </div>

        <button type="submit" className="btn" style={{ width: '100%' }} disabled={busy}>
          {busy ? 'Signing in...' : 'Sign in'}
        </button>

        <div className="login-hint">
          First run seeds an <strong>admin</strong> account. The password is whatever
          <code> ADMIN_PASSWORD</code> was set to, or <code>Admin@123</code> by default. Change it
          right after your first sign in.
        </div>
      </form>
    </div>
  )
}
