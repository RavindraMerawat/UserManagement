import { Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import ProtectedRoute from './auth/ProtectedRoute'
import AppLayout from './layout/AppLayout'
import Login from './pages/Login'
import Home from './pages/Home'
import BadgeSection from './pages/BadgeSection'
import SewadarSection from './pages/SewadarSection'
import SewadarList from './pages/SewadarList'
import Attendance from './pages/Attendance'
import Reports from './pages/Reports'
import Requests from './pages/Requests'
import Users from './pages/Users'
import Contact from './pages/Contact'
import Profile from './pages/Profile'
import NotAllowed from './pages/NotAllowed'
import Setup from './pages/Setup'
import About from './pages/About'

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
          <Route
            path="/badges"
            element={
              <ProtectedRoute screen="BADGES">
                <BadgeSection />
              </ProtectedRoute>
            }
          />
          {/*
            Reached by tapping a dashboard tile. Deliberately NOT behind the SEWADAR
            screen guard: a Sewadar has a dashboard too, and the server narrows the
            list to their own record. What each role may see is decided there, not by
            hiding the route.
          */}
          <Route path="/sewadar-list" element={<SewadarList />} />

          <Route
            path="/sewadars"
            element={
              <ProtectedRoute screen="SEWADAR">
                <SewadarSection />
              </ProtectedRoute>
            }
          />
          <Route path="/attendance" element={<Attendance />} />
          <Route path="/reports" element={<Reports />} />
          <Route path="/requests" element={<Requests />} />
          <Route
            path="/users"
            element={
              <ProtectedRoute screen="USERS">
                <Users />
              </ProtectedRoute>
            }
          />
          {/*
            Zones and About existed as pages but had no route, so neither could be
            reached - the Zones screen requirement ZN-1 calls done was unreachable.
            Zones keeps its role guard; About is informational and open to anyone
            signed in.
          */}
          {/*
            Setup replaces the old Zones screen: the same zone list, plus the two
            levels beneath it. The old /zones path still resolves here so a
            bookmark does not break.
          */}
          <Route
            path="/setup"
            element={
              <ProtectedRoute screen="SETUP">
                <Setup />
              </ProtectedRoute>
            }
          />
          <Route path="/zones" element={<Navigate to="/setup" replace />} />
          <Route path="/about" element={<About />} />

          <Route path="/contact" element={<Contact />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/not-allowed" element={<NotAllowed />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </AuthProvider>
  )
}
