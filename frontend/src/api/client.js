import axios from 'axios'

const TOKEN_KEY = 'ums.token'
const USER_KEY = 'ums.user'

/*
 * One signed-in session per tab, not per browser.
 *
 * The session used to live in localStorage, which every tab of a browser shares.
 * Signing in as a second person therefore replaced the first person's token
 * instead of sitting beside it, and the two tabs came apart: the one still
 * showing the Admin menu was now sending the other account's token, while the one
 * that had just signed in as a Sewadar was holding Admin's. The screen and the
 * token disagreed about whose session it was, which is how Admin screens ended up
 * in front of someone who had signed in as somebody else.
 *
 * sessionStorage is scoped to the tab, so two people can work side by side in the
 * same browser and neither can reach the other's access. The cost is that closing
 * the tab ends that session - which is the right default for a shared office
 * machine, and the reason this is not a "remember me" box.
 *
 * Reads and writes are wrapped because storage throws rather than returning null
 * in a private window or when a browser is set to block site data; the session
 * then lasts as long as the page, which still works.
 */
const store = {
  read: (key) => {
    try {
      return sessionStorage.getItem(key)
    } catch {
      return null
    }
  },
  write: (key, value) => {
    try {
      sessionStorage.setItem(key, value)
    } catch {
      // Nothing to do: this tab keeps the session in memory for this visit.
    }
  },
  remove: (key) => {
    try {
      sessionStorage.removeItem(key)
    } catch {
      // Nothing stored, nothing to drop.
    }
  },
}

/*
 * A token written by an earlier build is still in localStorage, where every tab
 * can read it. Clear it once on the way past so the shared copy cannot be picked
 * up again - everyone signs in once more after this change, and then each tab
 * holds its own.
 */
try {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
} catch {
  // A browser that will not let us read storage has nothing of ours in it.
}

export const tokenStore = {
  get: () => store.read(TOKEN_KEY),
  set: (token) => store.write(TOKEN_KEY, token),
  clear: () => {
    store.remove(TOKEN_KEY)
    store.remove(USER_KEY)
  },
  getUser: () => {
    try {
      const raw = store.read(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  },
  setUser: (user) => store.write(USER_KEY, JSON.stringify(user)),
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
/*
 * What a failure says to the person looking at the screen.
 *
 * These are read by sewadars and office staff, not by whoever wrote the code, so
 * they say what has happened and what to do about it - never a port number, a
 * stack trace or the name of a class. The detail that is useful for fixing a fault
 * still exists: it goes to the browser console, where a developer will look and
 * nobody else has to.
 */
const SERVICE_DOWN =
  'The service is unavailable at the moment - maintenance may be in progress. ' +
  'Please wait a few minutes and try again.'

const SERVER_FAULT =
  'Something went wrong at our end. Please try again, and let the office know if it keeps happening.'

const NO_ACCESS =
  'You do not have access to that. Ask the office if you think you should.'

const SESSION_OVER = 'Your sign in has ended. Please sign in again.'

const NOT_FOUND = 'We could not find what you asked for. It may have been removed.'

const TOO_BIG = 'That file is too large. Choose a smaller one and try again.'

/** mobileNo -> "Mobile No", so a field error reads like the label above the box. */
function label(field) {
  const spaced = String(field)
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .replace(/[._-]+/g, ' ')
    .trim()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

/** Kept out of the message, kept for whoever has to fix it. */
function note(error, shown) {
  try {
    // eslint-disable-next-line no-console
    console.warn('[api]', shown, {
      status: error?.response?.status,
      code: error?.code,
      url: error?.config?.url,
      body: error?.response?.data,
    })
  } catch {
    // Logging must never be the reason a screen fails.
  }
}

/**
 * Turns a failed request into one sentence somebody can act on.
 *
 * <p>The server's own messages are written for the office and are passed through as
 * they are - "Anita is already checked in", "A mobile number is 10 digits". What is
 * replaced is everything that is not: a network error, a gateway status, an HTML
 * error page from a proxy, an empty body.</p>
 */
export function errorMessage(error, fallback = 'That did not work. Please try again.') {
  const status = error?.response?.status
  const data = error?.response?.data

  // No response at all: the service is not answering, which to the person at the
  // desk means the same thing whether it is stopped, restarting or unreachable.
  if (error?.message === 'Network Error' || error?.code === 'ERR_NETWORK'
      || error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') {
    note(error, SERVICE_DOWN)
    return SERVICE_DOWN
  }

  // Our own errors always carry a numeric `status` in the body. Anything else with
  // a gateway status is the service being down rather than a considered refusal.
  const isOurEnvelope = data && typeof data === 'object' && typeof data.status === 'number'
  if (!isOurEnvelope && [502, 503, 504].includes(status)) {
    note(error, SERVICE_DOWN)
    return SERVICE_DOWN
  }
  if (!isOurEnvelope && status === 500) {
    note(error, SERVICE_DOWN)
    return SERVICE_DOWN
  }

  if (status === 401) {
    note(error, SESSION_OVER)
    return SESSION_OVER
  }
  if (status === 413) {
    note(error, TOO_BIG)
    return TOO_BIG
  }

  // A field the form can name: "Mobile No: must be 10 digits".
  if (data && typeof data === 'object' && data.fieldErrors) {
    const first = Object.entries(data.fieldErrors)[0]
    if (first) {
      const shown = `${label(first[0])}: ${first[1]}`
      note(error, shown)
      return shown
    }
  }

  // The server's own sentence, which is already written for the office.
  if (data && typeof data === 'object' && data.message) {
    note(error, data.message)
    return data.message
  }

  if (status === 403) {
    note(error, NO_ACCESS)
    return NO_ACCESS
  }
  if (status === 404) {
    note(error, NOT_FOUND)
    return NOT_FOUND
  }
  if (status === 500) {
    note(error, SERVER_FAULT)
    return SERVER_FAULT
  }

  /*
   * Anything left is a body we cannot read - a plain-text refusal from a filter, an
   * HTML page from a proxy, or a fault in our own code before the request went out.
   * The caller's fallback is a plain sentence; the particulars go to the console.
   */
  note(error, fallback)
  return fallback
}

export default api
