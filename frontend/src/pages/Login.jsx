import { cloneElement, useState } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import {
  ArrowRight,
  BarChart3,
  CalendarDays,
  ChevronDown,
  Eye,
  EyeOff,
  FileText,
  Lock,
  User,
  Users,
} from 'lucide-react'
import { useAuth } from '../auth/AuthContext'
import { errorMessage } from '../api/client'
import { BRAND } from '../brand'
import Alert from '../components/Alert'
import '../styles/login.css'

/**
 * The login screen, drawn to the supplied design.
 *
 * <p>Left: the sky, the brand, the promise and the four modules the app is made
 * of. Right: the form, which is the only part that does anything.</p>
 *
 * <p>Google sign-in, password reset and self-registration are all drawn because
 * the design has them, and none of the three is built - accounts are created by
 * an Admin. Rather than doing nothing when clicked, each says what to do instead;
 * a control that silently ignores you is worse than one that explains itself.</p>
 */
export default function Login() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [params] = useSearchParams()

  const [form, setForm] = useState({ username: '', password: '' })
  const [showPassword, setShowPassword] = useState(false)
  const [rememberMe, setRememberMe] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)

  if (isAuthenticated) {
    navigate('/', { replace: true })
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    setNotice('')
    setBusy(true)
    try {
      const result = await login(form.username.trim(), form.password)
      // A freshly created account has to set its own password before going further.
      const target = result.mustChangePassword ? '/profile' : location.state?.from || '/'
      navigate(target, { replace: true })
    } catch (err) {
      setError(errorMessage(err, 'Invalid login or password'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-page">
      {/* ----------------------------------------------------------- the sky */}
      <section className="login-hero">
        <div className="hero-overlay" />
        <Scenery />

        <div className="brand">
          <div className="brand-logo">{BRAND.mark}</div>
          <div>
            <h2>{BRAND.name}</h2>
            <p>{BRAND.tagline}</p>
          </div>
        </div>

          {/* Decoration only - the modules below say the same thing in words. */}
        <div className="cloud-illustration" aria-hidden="true">
          <div className="orbit orbit-one" />
          <div className="orbit orbit-two" />

          <div className="cloud">
            <div className="cloud-circle circle-1" />
            <div className="cloud-circle circle-2" />
            <div className="cloud-circle circle-3" />
            <div className="cloud-body">
              <div className="cloud-logo">{BRAND.mark}</div>
            </div>
          </div>

          <div className="floating-icon icon-users">
            <Users size={26} />
          </div>
          <div className="floating-icon icon-calendar">
            <CalendarDays size={25} />
          </div>
          <div className="floating-icon icon-document">
            <FileText size={25} />
          </div>
          <div className="floating-icon icon-report">
            <BarChart3 size={25} />
          </div>
        </div>

        <div className="hero-content">
          <div className="hero-text">
            <h1>
              Radha Soami
              <br />
              Satsang Indore
            </h1>
            <p>
              Manage People. Simplify Processes.
              <br />
              Build a Stronger Community.
            </p>
          </div>

          <div className="feature-list">
            <Feature icon={<Users />} title="Sewadar" subtitle="Management" />
            <Feature icon={<CalendarDays />} title="Attendance" subtitle="Tracking" />
            <Feature icon={<FileText />} title="Requests" subtitle="& Approvals" />
            <Feature icon={<BarChart3 />} title="Reports" subtitle="& Insights" />
          </div>
        </div>

        <div className="hero-quote">
          <div className="quote-line" />
          <p>
            &ldquo;Small acts of service
            <br />
            make a big difference.&rdquo;
          </p>
        </div>

        <p className="hero-script" aria-hidden="true">
          Cloud for a Better Tomorrow
          <svg width="150" height="12" viewBox="0 0 150 12" fill="none">
            <path
              d="M2 8C28 2 74 1 110 4c14 1 26 3 38 6"
              stroke="#4f9ee8"
              strokeWidth="2.5"
              strokeLinecap="round"
            />
          </svg>
        </p>
      </section>

      {/* ---------------------------------------------------------- the form */}
      <section className="login-section">
        <div className="language-selector">
          English
          <ChevronDown size={16} aria-hidden="true" />
        </div>

        <div className="login-container">
          <div className="login-heading">
            <h1>Welcome!</h1>
            <p>Login to your account to continue</p>
          </div>

          {params.get('expired') && (
            <Alert kind="warn">Your session expired. Please sign in again.</Alert>
          )}
          <Alert kind="error" onClose={() => setError('')}>
            {error}
          </Alert>
          <Alert kind="info" onClose={() => setNotice('')}>
            {notice}
          </Alert>

          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="username">Email / Username</label>
              <div className="input-wrapper">
                <User size={19} aria-hidden="true" />
                <input
                  id="username"
                  type="text"
                  autoFocus
                  autoComplete="username"
                  placeholder="Enter your email or username"
                  value={form.username}
                  onChange={(e) => setForm({ ...form, username: e.target.value })}
                  required
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="password">Password</label>
              <div className="input-wrapper">
                <Lock size={19} aria-hidden="true" />
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="Enter your password"
                  value={form.password}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                  required
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  aria-pressed={showPassword}
                >
                  {showPassword ? <EyeOff size={19} /> : <Eye size={19} />}
                </button>
              </div>
            </div>

            <div className="form-options">
              <label className="remember">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                />
                <span>Remember me</span>
              </label>

              <button
                type="button"
                className="forgot-button"
                onClick={() =>
                  setNotice(
                    'Password resets are done by an administrator. Ask your office admin to set a new one for you.',
                  )
                }
              >
                Forgot password?
              </button>
            </div>

            <button type="submit" className="login-button" disabled={busy}>
              {busy ? 'Signing in...' : 'Login'}
              <ArrowRight size={19} aria-hidden="true" />
            </button>
          </form>

          <div className="divider">
            <span />
            <p>Or login with</p>
            <span />
          </div>

          <div className="social-buttons">
            <button
              type="button"
              className="social-button"
              onClick={() =>
                setNotice('Google sign-in is not configured. Use your username and password.')
              }
            >
              <GoogleMark />
              Google
            </button>
          </div>
           </div>
      </section>
    </div>
  )
}

/**
 * The Google G, drawn inline.
 *
 * <p>The design loads this from a Google CDN. It is drawn here instead so the
 * login screen makes no third-party request before anyone has signed in - the
 * one page where that matters most.</p>
 */
function GoogleMark() {
  return (
    <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true">
      <path
        fill="#4285F4"
        d="M45.12 24.5c0-1.56-.14-3.06-.4-4.5H24v8.51h11.84a10.13 10.13 0 0 1-4.4 6.65v5.52h7.12c4.16-3.83 6.56-9.47 6.56-16.18z"
      />
      <path
        fill="#34A853"
        d="M24 46c5.94 0 10.92-1.97 14.56-5.33l-7.12-5.52c-1.97 1.32-4.49 2.1-7.44 2.1-5.73 0-10.58-3.87-12.31-9.07H4.34v5.7A21.99 21.99 0 0 0 24 46z"
      />
      <path
        fill="#FBBC05"
        d="M11.69 28.18A13.2 13.2 0 0 1 11 24c0-1.45.25-2.86.69-4.18v-5.7H4.34A22 22 0 0 0 2 24c0 3.55.85 6.91 2.34 9.88l7.35-5.7z"
      />
      <path
        fill="#EA4335"
        d="M24 10.75c3.23 0 6.13 1.11 8.41 3.29l6.31-6.31C34.91 4.18 29.93 2 24 2 15.4 2 7.96 6.93 4.34 14.12l7.35 5.7c1.73-5.2 6.58-9.07 12.31-9.07z"
      />
    </svg>
  )
}

/** One of the four modules named under the headline. */
function Feature({ icon, title, subtitle }) {
  return (
    <div className="feature">
      {/* The size is set here rather than at each call, so the four match. */}
      <div className="feature-icon">{cloneElement(icon, { size: 23 })}</div>
      <div>
        <strong>{title}</strong>
        <span>{subtitle}</span>
      </div>
    </div>
  )
}

/**
 * The cloud bank across the foot of the hero, with a temple showing through it.
 *
 * <p>Drawn rather than photographed: the login screen is the first thing anyone
 * sees, and it should not be waiting on an image download to look finished.</p>
 */
function Scenery() {
  return (
    <svg
      className="hero-scenery"
      viewBox="0 0 1000 500"
      preserveAspectRatio="xMidYMax slice"
      aria-hidden="true"
    >
      <defs>
        <linearGradient id="loginCloudFar" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#ffffff" stopOpacity="0.18" />
          <stop offset="100%" stopColor="#ffffff" stopOpacity="0.55" />
        </linearGradient>
        <linearGradient id="loginCloudNear" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#ffffff" stopOpacity="0.92" />
          <stop offset="100%" stopColor="#dceeff" stopOpacity="1" />
        </linearGradient>
      </defs>

      {/*
        The temple, kept faint - it belongs behind the weather, not in front.
        Five spires on a stepped plinth, with the cloud bank covering its feet.
      */}
      <g fill="#a9d3ff" opacity="0.22" transform="translate(287 262) scale(0.42)">
        <path d="M296 434V330q0-42 32-64 32 22 32 64v104z" />
        <path d="M368 434V286q0-54 40-82 40 28 40 82v148z" />
        <path d="M456 434V212q0-92 52-136 52 44 52 136v222z" />
        <path d="M568 434V286q0-54 40-82 40 28 40 82v148z" />
        <path d="M656 434V330q0-42 32-64 32 22 32 64v104z" />
        <circle cx="328" cy="258" r="10" />
        <circle cx="408" cy="196" r="12" />
        <circle cx="508" cy="62" r="16" />
        <circle cx="608" cy="196" r="12" />
        <circle cx="688" cy="258" r="10" />
        <rect x="500" y="24" width="16" height="30" rx="8" />
        <rect x="272" y="434" width="472" height="24" />
        <rect x="248" y="458" width="520" height="22" />
      </g>

      {/* Far cloud, then the near bank that the temple rises out of. */}
      <g fill="url(#loginCloudFar)">
        <ellipse cx="180" cy="330" rx="170" ry="58" />
        <ellipse cx="830" cy="300" rx="190" ry="62" />
        <ellipse cx="520" cy="352" rx="210" ry="50" />
      </g>

      <g fill="url(#loginCloudNear)">
        <ellipse cx="110" cy="452" rx="270" ry="106" />
        <ellipse cx="390" cy="430" rx="235" ry="92" />
        <ellipse cx="660" cy="450" rx="255" ry="100" />
        <ellipse cx="910" cy="428" rx="240" ry="94" />
        <rect y="450" width="1000" height="80" />
      </g>
    </svg>
  )
}
