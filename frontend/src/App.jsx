import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import AppLayout from './layout/AppLayout'
import Login from './pages/Login'
import Home from './pages/Home'
import About from './pages/About'
import Sewadars from './pages/Sewadars'
import Attendance from './pages/Attendance'
import Reports from './pages/Reports'
import Requests from './pages/Requests'
import Zones from './pages/Zones'
import Users from './pages/Users'
import Contact from './pages/Contact'
import Profile from './pages/Profile'
import NotAllowed from './pages/NotAllowed'

/**
 * Every screen except the login sits behind AppLayout, which draws the left menu.
 * The `screen` prop on ProtectedRoute repeats the role check the server applies,
 * so typing a URL cannot get past it.
 */
export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<Login />} />

        <Route
          element={
            <ProtectedRoute>
              <AppLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<Home />} />
          <Route path="/about" element={<About />} />
          <Route
            path="/sewadars"
            element={
              <ProtectedRoute screen="SEWADAR">
                <Sewadars />
              </ProtectedRoute>
            }
          />
          <Route path="/attendance" element={<Attendance />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/requests" element={<Requests />} />
          <Route
            path="/zones"
            element={
              <ProtectedRoute screen="ZONES">
                <Zones />
              </ProtectedRoute>
            }
          />
          <Route
            path="/users"
            element={
              <ProtectedRoute screen="USERS">
                <Users />
              </ProtectedRoute>
            }
          />
          <Route path="/contact" element={<Contact />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/not-allowed" element={<NotAllowed />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
