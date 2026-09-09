import axios from 'axios'

const TOKEN_KEY = 'ums.token'
const USER_KEY = 'ums.user'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
  getUser: () => {
    try {
      const raw = localStorage.getItem(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  },
  setUser: (user) => localStorage.setItem(USER_KEY, JSON.stringify(user)),
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  headers: { 'Content-Type': 'application/json' },
})

api.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// A 401 means the token expired or was revoked: drop it and send the user to login.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    if (status === 401 && !window.location.pathname.startsWith('/login')) {
      tokenStore.clear()
      window.location.replace('/login?expired=1')
    }
    return Promise.reject(error)
  },
)

/** Pulls a readable message out of the backend error envelope. */
const BACKEND_DOWN =
  'Cannot reach the API on port 8080. Start the backend in IntelliJ ' +
  '(run UserManagementApplication) and try again.'

/**
 * Pulls a readable message out of the backend error envelope.
 *
 * The backend runs separately from this dev server, so "the API is not up yet" is a
 * normal state and gets its own message. Vite turns a refused proxy connection into
 * a 500 with a non-JSON body, which is why a gateway status without our envelope is
 * treated as the backend being down rather than as a real server error.
 */
export function errorMessage(error, fallback = 'Something went wrong. Please try again.') {
  if (error?.message === 'Network Error' || error?.code === 'ERR_NETWORK') {
    return BACKEND_DOWN
  }

  const status = error?.response?.status
  const data = error?.response?.data

  // Our own errors always carry a numeric `status` in the body.
  const isOurEnvelope = data && typeof data === 'object' && typeof data.status === 'number'
  if (!isOurEnvelope && [500, 502, 503, 504].includes(status)) {
    return BACKEND_DOWN
  }

  if (!data) {
    return fallback
  }
  if (data.fieldErrors) {
    const first = Object.entries(data.fieldErrors)[0]
    if (first) return `${first[0]}: ${first[1]}`
  }
  return data.message || fallback
}

export default api
