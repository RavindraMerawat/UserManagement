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

export const zoneApi = {
  list: (includeInactive = false) =>
    api.get('/api/zones', { params: { includeInactive } }).then((r) => r.data),
  create: (payload) => api.post('/api/zones', payload).then((r) => r.data),
  update: (id, payload) => api.put(`/api/zones/${id}`, payload).then((r) => r.data),
  remove: (id) => api.delete(`/api/zones/${id}`).then((r) => r.data),
}

export const sewadarApi = {
  search: (params) => api.get('/api/sewadars', { params }).then((r) => r.data),
  forAttendance: (zoneId) =>
    api.get('/api/sewadars/for-attendance', { params: { zoneId } }).then((r) => r.data),
  me: () => api.get('/api/sewadars/me').then((r) => r.data),
  get: (id) => api.get(`/api/sewadars/${id}`).then((r) => r.data),
  create: (payload) => api.post('/api/sewadars', payload).then((r) => r.data),
  update: (id, payload) => api.put(`/api/sewadars/${id}`, payload).then((r) => r.data),
  remove: (id) => api.delete(`/api/sewadars/${id}`).then((r) => r.data),
}

export const attendanceApi = {
  search: (params) => api.get('/api/attendance', { params }).then((r) => r.data),
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
   * @param format 'excel' or 'csv'
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
    const fileName = match ? match[1] : `attendance-report.${format === 'excel' ? 'xlsx' : 'csv'}`

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
  create: (payload) => api.post('/api/requests/zone-change', payload).then((r) => r.data),
  review: (id, payload) =>
    api.put(`/api/requests/zone-change/${id}/review`, payload).then((r) => r.data),
  cancel: (id) => api.put(`/api/requests/zone-change/${id}/cancel`).then((r) => r.data),
}

export const userApi = {
  search: (params) => api.get('/api/users', { params }).then((r) => r.data),
  create: (payload) => api.post('/api/users', payload).then((r) => r.data),
  update: (id, payload) => api.put(`/api/users/${id}`, payload).then((r) => r.data),
  remove: (id) => api.delete(`/api/users/${id}`).then((r) => r.data),
}
