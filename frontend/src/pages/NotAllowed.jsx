import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export default function NotAllowed() {
  const { user } = useAuth()
  return (
    <div className="card" style={{ maxWidth: 560 }}>
      <h3>This screen is not available for your role</h3>
      <p>
        You are signed in as <strong>{user?.roleDisplayName}</strong>, which does not have access to
        that screen. The server enforces the same rule, so the data behind it is never exposed
        either.
      </p>
      <Link className="btn" to="/">
        Back to Home
      </Link>
    </div>
  )
}
