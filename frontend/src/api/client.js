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

/*
 * No default Content-Type here on purpose. Axios sets application/json for a plain
 * object by itself, and forcing it for every request means a file upload starts out
 * labelled as JSON - the browser has to be allowed to set multipart/form-data with
 * its boundary, and a header it cannot override is how that breaks.
 */
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
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

  /*
   * Everything below exists so a failure never arrives as a bare "it failed". A
   * response that is not our JSON envelope - a plain-text 403 from the CORS filter,
   * an HTML error page from a proxy, an empty body - used to fall through to the
   * caller's fallback, which told whoever was looking at the screen nothing at all.
   * The status is always worth printing; it is the one thing that narrows the cause.
   */
  if (data && typeof data === 'object' && data.fieldErrors) {
    const first = Object.entries(data.fieldErrors)[0]
    if (first) return `${first[0]}: ${first[1]}`
  }
  if (data && typeof data === 'object' && data.message) {
    return data.message
  }
  if (typeof data === 'string' && data.trim() && !data.trim().startsWith('<')) {
    return `${fallback} (${status || 'no status'}: ${data.trim().slice(0, 120)})`
  }
  if (status) {
    return `${fallback} (HTTP ${status})`
  }
  if (error?.code) {
    return `${fallback} (${error.code})`
  }
  /*
   * No response, no axios code: this is not an HTTP failure at all but a fault in
   * our own code that happened before the request went out. Returning the bare
   * fallback here once hid a ReferenceError behind the words "upload failed" - so
   * whatever the error says, say it.
   */
  if (error?.message) {
    return `${fallback} (${error.message})`
  }
  return fallback
}

export default api
