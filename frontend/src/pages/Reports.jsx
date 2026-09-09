import { useCallback, useEffect, useState } from 'react'
import { metaApi, reportApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { BarBreakdown, EmptyRow, Field } from '../components/Bits'

const REPORT_KINDS = [
  { key: 'monthly', label: 'Monthly attendance', loader: reportApi.monthly },
  { key: 'roster', label: 'Roster sewa', loader: reportApi.rosterSewa },
  { key: 'construction', label: 'Construction sewa', loader: reportApi.constructionSewa },
  { key: 'range', label: 'Custom date range', loader: reportApi.range },
]

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

const now = new Date()
const isoToday = () => new Date().toISOString().slice(0, 10)

export default function Reports() {
  const { isSewadar } = useAuth()

  const [kind, setKind] = useState('monthly')
  const [filters, setFilters] = useState({
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    zoneId: '',
    sewaType: '',
    fromDate: new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10),
    toDate: isoToday(),
  })

  const [zones, setZones] = useState([])
  const [options, setOptions] = useState({ sewaTypes: [] })
  const [channels, setChannels] = useState({ email: false, whatsapp: false })

  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [shareOpen, setShareOpen] = useState(false)

  useEffect(() => {
    zoneApi.list(true).then(setZones).catch(() => setZones([]))
    metaApi.options().then(setOptions).catch(() => {})
    metaApi.channels().then(setChannels).catch(() => {})
  }, [])

  const queryParams = useCallback(() => {
    const base = {
      zoneId: filters.zoneId || undefined,
      sewaType: filters.sewaType || undefined,
    }
    if (kind === 'range') {
      return { ...base, fromDate: filters.fromDate, toDate: filters.toDate }
    }
    return { ...base, year: Number(filters.year), month: Number(filters.month) }
  }, [filters, kind])

  const generate = async () => {
    setLoading(true)
    setError('')
    setReport(null)
    try {
      const loader = REPORT_KINDS.find((r) => r.key === kind).loader
      const params = queryParams()
      // The dedicated sewa reports already pin the sewa type on the server.
      if (kind === 'roster' || kind === 'construction') {
        delete params.sewaType
      }
      setReport(await loader(params))
    } catch (err) {
      setError(errorMessage(err, 'Could not generate the report'))
    } finally {
      setLoading(false)
    }
  }

  const download = async (format) => {
    setError('')
    try {
      // CSV is only wired up for the month based reports on the server.
      const isRange = kind === 'range'
      const fileName = await reportApi.download(format, queryParams(), isRange)
      setNotice(`Downloaded ${fileName}`)
    } catch (err) {
      setError(errorMessage(err, 'Could not download the report'))
    }
  }

  const showMonthPickers = kind !== 'range'

  return (
    <div>
      <Alert kind="error" onClose={() => setError('')}>
        {error}
      </Alert>
      <Alert kind="success" onClose={() => setNotice('')}>
        {notice}
      </Alert>

      <div className="card">
        <div className="card-head">
          <h3>Report options</h3>
        </div>

        <div className="tabs" style={{ marginBottom: 14 }}>
          {REPORT_KINDS.map((item) => (
            <button
              key={item.key}
              type="button"
              className={kind === item.key ? 'active' : ''}
              onClick={() => {
                setKind(item.key)
                setReport(null)
              }}
            >
              {item.label}
            </button>
          ))}
        </div>

        <div className="filters">
          {showMonthPickers ? (
            <>
              <Field label="Month">
                <select
                  value={filters.month}
                  onChange={(e) => setFilters({ ...filters, month: e.target.value })}
                >
                  {MONTHS.map((name, index) => (
                    <option key={name} value={index + 1}>
                      {name}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Year">
                <select
                  value={filters.year}
                  onChange={(e) => setFilters({ ...filters, year: e.target.value })}
                >
                  {Array.from({ length: 8 }, (_, i) => now.getFullYear() - i).map((year) => (
                    <option key={year} value={year}>
                      {year}
                    </option>
                  ))}
                </select>
              </Field>
            </>
          ) : (
            <>
              <Field label="From date">
                <input
                  type="date"
                  value={filters.fromDate}
                  onChange={(e) => setFilters({ ...filters, fromDate: e.target.value })}
                />
              </Field>
              <Field label="To date">
                <input
                  type="date"
                  value={filters.toDate}
                  onChange={(e) => setFilters({ ...filters, toDate: e.target.value })}
                />
              </Field>
            </>
          )}

          {!isSewadar && (
            <Field label="Zone">
              <select
                value={filters.zoneId}
                onChange={(e) => setFilters({ ...filters, zoneId: e.target.value })}
              >
                <option value="">All my zones</option>
                {zones.map((zone) => (
                  <option key={zone.id} value={zone.id}>
                    {zone.name}
                  </option>
                ))}
              </select>
            </Field>
          )}

          {(kind === 'monthly' || kind === 'range') && (
            <Field label="Sewa type">
              <select
                value={filters.sewaType}
                onChange={(e) => setFilters({ ...filters, sewaType: e.target.value })}
              >
                <option value="">All sewa types</option>
                {(options.sewaTypes || []).map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </Field>
          )}
        </div>

        <div className="btn-row" style={{ marginTop: 16 }}>
          <button type="button" className="btn" onClick={generate} disabled={loading}>
            {loading ? 'Generating...' : 'Generate report'}
          </button>
          {report && (
            <>
              <button type="button" className="btn ghost" onClick={() => download('excel')}>
                Download Excel
              </button>
              <button type="button" className="btn ghost" onClick={() => download('csv')}>
                Download CSV
              </button>
              <button type="button" className="btn ok" onClick={() => setShareOpen(true)}>
                Share report
              </button>
            </>
          )}
        </div>

        {(!channels.email || !channels.whatsapp) && (
          <p className="muted" style={{ marginTop: 12, marginBottom: 0, fontSize: 12.5 }}>
            Delivery channels: email {channels.email ? 'on' : 'off'}, WhatsApp{' '}
            {channels.whatsapp ? 'on' : 'off'}. A channel that is off still accepts a share, logs
            it on the server and tells you in the response.
          </p>
        )}
      </div>

      {loading && <Spinner label="Building the report" />}

      {report && !loading && <ReportView report={report} />}

      <ShareDialog
        open={shareOpen}
        onClose={() => setShareOpen(false)}
        report={report}
        params={queryParams()}
        isRange={kind === 'range'}
        channels={channels}
        onDone={(message) => {
          setNotice(message)
          setShareOpen(false)
        }}
        onError={setError}
      />
    </div>
  )
}

function ReportView({ report }) {
  const t = report.totals
  return (
    <>
      <div className="card">
        <h3 style={{ marginBottom: 4 }}>{report.title}</h3>
        <p className="muted" style={{ marginTop: 0 }}>
          {report.fromDate} to {report.toDate} &middot; {report.daysInPeriod} days &middot;{' '}
          {report.zoneName} &middot; {report.sewaTypeLabel}
        </p>
        <div className="stat-grid" style={{ marginBottom: 0 }}>
          <div className="stat">
            <div className="label">Sewadars</div>
            <div className="value">{t.sewadarCount}</div>
          </div>
          <div className="stat" style={{ borderLeftColor: '#059669' }}>
            <div className="label">Present days</div>
            <div className="value">{t.presentDays}</div>
          </div>
          <div className="stat" style={{ borderLeftColor: '#dc2626' }}>
            <div className="label">Absent days</div>
            <div className="value">{t.absentDays}</div>
          </div>
          <div className="stat" style={{ borderLeftColor: '#2563eb' }}>
            <div className="label">Total sewa hours</div>
            <div className="value">{t.totalHours}</div>
          </div>
          <div className="stat" style={{ borderLeftColor: '#d97706' }}>
            <div className="label">Average attendance</div>
            <div className="value">{t.averageAttendancePercent}%</div>
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3>By status</h3>
          <BarBreakdown data={report.statusBreakdown} />
        </div>
        <div className="card">
          <h3>By sewa type</h3>
          <BarBreakdown data={report.sewaTypeBreakdown} />
        </div>
      </div>

      <div className="card">
        <h3>Sewadar wise detail</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Badge</th>
                <th>Sewadar</th>
                <th>Zone</th>
                <th>Department</th>
                <th>Present</th>
                <th>Half day</th>
                <th>Leave</th>
                <th>Absent</th>
                <th>Roster</th>
                <th>Construction</th>
                <th>Hours</th>
                <th>Effective</th>
                <th>Attendance</th>
              </tr>
            </thead>
            <tbody>
              {report.rows.length === 0 ? (
                <EmptyRow colSpan={14}>No attendance was marked in this period</EmptyRow>
              ) : (
                report.rows.map((row, index) => (
                  <tr key={row.sewadarId}>
                    <td>{index + 1}</td>
                    <td>{row.badgeNumber}</td>
                    <td>{row.sewadarName}</td>
                    <td>{row.zoneName}</td>
                    <td className="muted">{row.department || '-'}</td>
                    <td>{row.presentDays}</td>
                    <td>{row.halfDays}</td>
                    <td>{row.leaveDays}</td>
                    <td>{row.absentDays}</td>
                    <td>{row.rosterSewaDays}</td>
                    <td>{row.constructionSewaDays}</td>
                    <td>{row.totalHours}</td>
                    <td>{row.effectiveDays}</td>
                    <td>
                      <strong>{row.attendancePercent}%</strong>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
            {report.rows.length > 0 && (
              <tfoot>
                <tr>
                  <td colSpan={5}>Total ({t.sewadarCount} sewadars)</td>
                  <td>{t.presentDays}</td>
                  <td>{t.halfDays}</td>
                  <td>{t.leaveDays}</td>
                  <td>{t.absentDays}</td>
                  <td>{t.rosterSewaDays}</td>
                  <td>{t.constructionSewaDays}</td>
                  <td>{t.totalHours}</td>
                  <td>-</td>
                  <td>{t.averageAttendancePercent}%</td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </div>
    </>
  )
}

function ShareDialog({ open, onClose, report, params, isRange, channels, onDone, onError }) {
  const [form, setForm] = useState({
    emailTo: '',
    whatsappTo: '',
    subject: '',
    note: '',
    attachExcel: true,
  })
  const [sending, setSending] = useState(false)
  const [localError, setLocalError] = useState('')

  const onSubmit = async (event) => {
    event.preventDefault()
    const emailTo = form.emailTo.split(',').map((v) => v.trim()).filter(Boolean)
    const whatsappTo = form.whatsappTo.split(',').map((v) => v.trim()).filter(Boolean)

    if (emailTo.length === 0 && whatsappTo.length === 0) {
      setLocalError('Add at least one email address or WhatsApp number.')
      return
    }
    setLocalError('')
    setSending(true)
    try {
      const payload = {
        emailTo,
        whatsappTo,
        subject: form.subject || null,
        note: form.note || null,
        attachExcel: form.attachExcel,
      }
      const result = isRange
        ? await reportApi.shareRange(params, payload)
        : await reportApi.share(params, payload)
      // A recipient list comes back for a channel that was accepted; the *Sent flag
      // says whether it actually left the server (a disabled channel only logs).
      const parts = []
      if (result.emailRecipients?.length) {
        parts.push(
          `${result.emailSent ? 'emailed' : 'queued for email'} to ${result.emailRecipients.join(', ')}`,
        )
      }
      if (result.whatsappRecipients?.length) {
        parts.push(
          `${result.whatsappSent ? 'sent on WhatsApp' : 'queued for WhatsApp'} to ${result.whatsappRecipients.join(', ')}`,
        )
      }
      const warnings = result.warnings?.length ? ` ${result.warnings.join(' ')}` : ''
      onDone(`Report ${parts.join(' and ')}.${warnings}`)
    } catch (err) {
      onError(errorMessage(err, 'Could not share the report'))
      onClose()
    } finally {
      setSending(false)
    }
  }

  return (
    <Modal
      title="Share report"
      open={open}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="share-form" className="btn ok" disabled={sending}>
            {sending ? 'Sending...' : 'Send'}
          </button>
        </>
      }
    >
      <form id="share-form" onSubmit={onSubmit}>
        <Alert kind="error">{localError}</Alert>
        {report && (
          <p className="muted" style={{ marginTop: 0 }}>
            Sharing <strong>{report.title}</strong> with {report.totals.sewadarCount} sewadars.
          </p>
        )}
        <div style={{ display: 'grid', gap: 14 }}>
          <Field label="Email recipients (comma separated)">
            <input
              value={form.emailTo}
              onChange={(e) => setForm({ ...form, emailTo: e.target.value })}
              placeholder="office@sewa.local, incharge@sewa.local"
            />
          </Field>
          <Field label="WhatsApp numbers (comma separated)">
            <input
              value={form.whatsappTo}
              onChange={(e) => setForm({ ...form, whatsappTo: e.target.value })}
              placeholder="9876543210, 919812345678"
            />
          </Field>
          <Field label="Subject (optional)">
            <input
              value={form.subject}
              onChange={(e) => setForm({ ...form, subject: e.target.value })}
              placeholder={report?.title || 'Attendance report'}
            />
          </Field>
          <Field label="Note added at the top of the message (optional)">
            <textarea
              value={form.note}
              onChange={(e) => setForm({ ...form, note: e.target.value })}
            />
          </Field>
          <label className="checkline">
            <input
              type="checkbox"
              checked={form.attachExcel}
              onChange={(e) => setForm({ ...form, attachExcel: e.target.checked })}
            />
            Attach the Excel workbook to the email
          </label>
        </div>
        {(!channels.email || !channels.whatsapp) && (
          <Alert kind="warn">
            Email is {channels.email ? 'configured' : 'not configured'} and WhatsApp is{' '}
            {channels.whatsapp ? 'configured' : 'not configured'} on this server. An unconfigured
            channel logs the message instead of delivering it.
          </Alert>
        )}
      </form>
    </Modal>
  )
}
