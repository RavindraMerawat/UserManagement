import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'
import Spinner from '../components/Spinner'

/**
 * Gate for every screen behind the login. Pass `screen` to also check the role,
 * so a Sewadar cannot reach the Sewadar master data screen by typing the URL.
 */
export default function ProtectedRoute({ screen, children }) {
  const { isAuthenticated, loading, can } = useAuth()
  const location = useLocation()

  if (loading) {
    return <Spinner label="Checking your session" />
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  if (screen && !can(screen)) {
    return <Navigate to="/not-allowed" replace />
  }
  return children
}
