import api from './client'

export const authApi = {
  login: (payload) => api.post('/api/auth/login', payload).then((r) => r.data),
  me: () => api.get('/api/auth/me').then((r) => r.data),
  dashboard: () => api.get('/api/auth/dashboard').then((r) => r.data),
  changePassword: (payload) => api.post('/api/auth/change-password', payload).then((r) => r.data),
}

export const metaApi = {
  options: () => api.get('/api/meta/options').then((r) => r.data),
  channels: () => api.get('/api/meta/channels').then((r) => r.data),
  contact: (payload) => api.post('/api/meta/contact', payload).then((r) => r.data),
}

/** Setup master data: the areas inside a zone, and the points inside an area. */
export const setupApi = {
  areas: (params) => api.get('/api/setup/areas', { params }).then((r) => r.data),
  createArea: (payload) => api.post('/api/setup/areas', payload).then((r) => r.data),
  updateArea: (id, payload) => api.put(`/api/setup/areas/${id}`, payload).then((r) => r.data),
  removeArea: (id) => api.delete(`/api/setup/areas/${id}`).then((r) => r.data),

  points: (params) => api.get('/api/setup/points', { params }).then((r) => r.data),
  createPoint: (payload) => api.post('/api/setup/points', payload).then((r) => r.data),
  updatePoint: (id, payload) => api.put(`/api/setup/points/${id}`, payload).then((r) => r.data),
  removePoint: (id) => api.delete(`/api/setup/points/${id}`).then((r) => r.data),
}

export const zoneApi = {
  list: (includeInactive = false) =>
    api.get('/api/zones', { params: { includeInactive } }).then((r) => r.data),
  create: (payload) => api.post('/api/zones', payload).then((r) => r.data),
  update: (id, payload) => api.put(`/api/zones/${id}`, payload).then((r) => r.data),
  remove: (id) => api.delete(`/api/zones/${id}`).then((r) => r.data),
}

/**
 * Loads a photo as an object URL.
 *
 * The image endpoint is authenticated, and an <img src> cannot carry the bearer
 * token, so the bytes are fetched with axios and wrapped in a blob URL. Results are
 * cached per owner and per upload stamp, so a re-render does not refetch and a new
 * upload busts the entry.
 */
const photoCache = new Map()
const PHOTO_CACHE_LIMIT = 200

export async function loadPhoto(kind, id, stamp) {
  const key = `${kind}/${id}/${stamp || ''}`
  if (photoCache.has(key)) return photoCache.get(key)

  const promise = api
    .get(`/api/${kind}/${id}/photo`, { responseType: 'blob' })
    .then((r) => URL.createObjectURL(r.data))
    .catch(() => null)

  // Keep the cache bounded; oldest entry goes first.
  if (photoCache.size >= PHOTO_CACHE_LIMIT) {
    const oldest = photoCache.keys().next().value
    const url = await photoCache.get(oldest)
    if (url) URL.revokeObjectURL(url)
    photoCache.delete(oldest)
  }
  photoCache.set(key, promise)
  return promise
}

/** Drops cached blobs for one owner so the next render refetches. */
export function forgetPhoto(kind, id) {
  for (const key of [...photoCache.keys()]) {
    if (key.startsWith(`${kind}/${id}/`)) photoCache.delete(key)
  }
}

const photoActions = (kind) => ({
  upload: (id, file) => {
    const body = new FormData()
    body.append('file', file)
    forgetPhoto(kind, id)
    // No Content-Type here: the browser has to set it, because only the browser
    // knows the multipart boundary. Naming it by hand produces a header without
    // one, and a server cannot parse that.
    return api.post(`/api/${kind}/${id}/photo`, body).then((r) => r.data)
  },
  remove: (id) => {
    forgetPhoto(kind, id)
    return api.delete(`/api/${kind}/${id}/photo`).then((r) => r.data)
  },
})

export const sewadarApi = {
  photo: photoActions('sewadars'),
  search: (params) => api.get('/api/sewadars', { params }).then((r) => r.data),
  /** The people behind a dashboard tile; scoped on the server like every other read. */
  byStatus: (params) => api.get('/api/sewadars/by-status', { params }).then((r) => r.data),
  forAttendance: (zoneId) =>
    api.get('/api/sewadars/for-attendance', { params: { zoneId } }).then((r) => r.data),
  me: () => api.get('/api/sewadars/me').then((r) => r.data),
  get: (id) => api.get(`/api/sewadars/${id}`).then((r) => r.data),
  create: (payload) => api.post('/api/sewadars', payload).then((r) => r.data),
  update: (id, payload) => api.put(`/api/sewadars/${id}`, payload).then((r) => r.data),
  remove: (id) => api.delete(`/api/sewadars/${id}`).then((r) => r.data),
  badgeSummary: () => api.get('/api/sewadars/badges/summary').then((r) => r.data),
  /** All / active / inactive, scoped, for the list tabs. */
  counts: () => api.get('/api/sewadars/counts').then((r) => r.data),
  issueBadge: (id) => api.post(`/api/sewadars/${id}/badge/issue`).then((r) => r.data),
  receiveBadge: (id) => api.post(`/api/sewadars/${id}/badge/receive`).then((r) => r.data),
}

export const userApiPhoto = photoActions('users')

export const attendanceApi = {
  search: (params) => api.get('/api/attendance', { params }).then((r) => r.data),

  /** Mark Attendance screen: badge no, name, mobile or Aadhaar. */
  lookup: (query, sewaType, onDate) =>
    api
      .get('/api/attendance/lookup', { params: { query, sewaType, onDate } })
      .then((r) => r.data),
  status: (sewadarId, sewaType, onDate) =>
    api
      .get(`/api/attendance/status/${sewadarId}`, { params: { sewaType, onDate } })
      .then((r) => r.data),
  checkIn: (payload) => api.post('/api/attendance/check-in', payload).then((r) => r.data),
  checkOut: (payload) => api.post('/api/attendance/check-out', payload).then((r) => r.data),
  bulkCheckIn: (payload) =>
    api.post('/api/attendance/bulk-check-in', payload).then((r) => r.data),
  bulkCheckOut: (payload) =>
    api.post('/api/attendance/bulk-check-out', payload).then((r) => r.data),
  mine: (fromDate, toDate) =>
    api.get('/api/attendance/me', { params: { fromDate, toDate } }).then((r) => r.data),
  mark: (payload) => api.post('/api/attendance', payload).then((r) => r.data),
  markBulk: (payload) => api.post('/api/attendance/bulk', payload).then((r) => r.data),
  update: (id, payload) => api.put(`/api/attendance/${id}`, payload).then((r) => r.data),
  remove: (id) => api.delete(`/api/attendance/${id}`).then((r) => r.data),
}

export const reportApi = {
  monthly: (params) => api.get('/api/reports/monthly', { params }).then((r) => r.data),
  rosterSewa: (params) => api.get('/api/reports/roster-sewa', { params }).then((r) => r.data),
  constructionSewa: (params) =>
    api.get('/api/reports/construction-sewa', { params }).then((r) => r.data),
  range: (params) => api.get('/api/reports/range', { params }).then((r) => r.data),
  share: (params, payload) =>
    api.post('/api/reports/monthly/share', payload, { params }).then((r) => r.data),
  shareRange: (params, payload) =>
    api.post('/api/reports/range/share', payload, { params }).then((r) => r.data),
  /**
   * Downloads a report through the browser.
   *
   * @param format 'excel', 'csv' or 'pdf'
   * @param params year/month, or fromDate/toDate when `range` is true
   * @param range  true to hit the custom range endpoint
   */
  download: async (format, params, range = false) => {
    const path = range ? `/api/reports/range/${format}` : `/api/reports/monthly/${format}`
    const response = await api.get(path, {
      params,
      responseType: 'blob',
    })
    const disposition = response.headers['content-disposition'] || ''
    const match = disposition.match(/filename="?([^"]+)"?/)
    const fallbackExtension = { excel: 'xlsx', csv: 'csv', pdf: 'pdf' }[format] || format
    const fileName = match ? match[1] : `attendance-report.${fallbackExtension}`

    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.download = fileName
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
    return fileName
  },
}

export const requestApi = {
  list: (params) => api.get('/api/requests/zone-change', { params }).then((r) => r.data),
  counts: () => api.get('/api/requests/zone-change/counts').then((r) => r.data),
  create: (payload) => api.post('/api/requests/zone-change', payload).then((r) => r.data),
  review: (id, payload) =>
    api.put(`/api/requests/zone-change/${id}/review`, payload).then((r) => r.data),
  cancel: (id) => api.put(`/api/requests/zone-change/${id}/cancel`).then((r) => r.data),
}

export const userApi = {
  search: (params) => api.get('/api/users', { params }).then((r) => r.data),
  counts: () => api.get('/api/users/counts').then((r) => r.data),
  create: (payload) => api.post('/api/users', payload).then((r) => r.data),
  update: (id, payload) => api.put(`/api/users/${id}`, payload).then((r) => r.data),
  remove: (id) => api.delete(`/api/users/${id}`).then((r) => r.data),
}
