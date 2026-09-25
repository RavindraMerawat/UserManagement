import { useCallback, useEffect, useState } from 'react'
import { metaApi, reportApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { EmptyRow, Field } from '../components/Bits'
import { fromIso, monthStartIso, todayIso } from '../dates'

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
const isoToday = () => todayIso()

export default function Reports() {
  const { isSewadar } = useAuth()

  const [kind, setKind] = useState('monthly')
  const [filters, setFilters] = useState({
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    zoneId: '',
    sewaType: '',
    fromDate: monthStartIso(),
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
      <div className="page-head">
        <div>
          <h1 className="page-title">Reports</h1>
          <p className="page-sub">Generate, share and export attendance and sewa reports.</p>
        </div>
      </div>

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
              {/*
                The sheet people actually hand round: S.No, GR. No, Name, Age, Zone
                and the month's total hours.
              */}
              <button type="button" className="btn ghost" onClick={() => download('pdf')}>
                Download PDF
              </button>
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

/** 2026-09-01 -> 1 Sep 2026, which is how a date is read rather than stored. */
function niceDate(iso) {
  if (!iso) return ''
  return fromIso(iso).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}

/**
 * The report as one sheet: what it covers, then the figures.
 *
 * <p>The summary tiles and the two breakdown panels that used to sit above the
 * table are gone. They repeated the table and nothing else - every figure in them
 * is a column here or a number in the totals row - and five tiles wrapping four
 * and one, over two panels each drawing a single bar, was a lot of page saying
 * very little.</p>
 */
function ReportView({ report }) {
  const t = report.totals

  return (
    <div className="card report-sheet">
      <header className="report-head">
        <div className="report-head-main">
          <h3 className="report-title">{report.title}</h3>
          <p className="report-period">
            {niceDate(report.fromDate)} to {niceDate(report.toDate)} · {report.daysInPeriod} days
          </p>
        </div>
        {/* What was asked for, as chips, so the filters that produced this sheet
            are readable at a glance instead of a run-on grey sentence. */}
        <div className="report-chips">
          <span className="report-chip">{report.zoneName || 'All zones'}</span>
          <span className="report-chip">{report.sewaTypeLabel || 'All sewa types'}</span>
          <span className="report-chip strong">
            {t.sewadarCount} sewadar{t.sewadarCount === 1 ? '' : 's'}
          </span>
        </div>
      </header>

      <div className="table-wrap">
        <table className="table-md report-table">
          <thead>
            <tr>
              <th className="col-num">#</th>
              <th>Badge</th>
              <th>Sewadar</th>
              <th>Zone</th>
              <th>Department</th>
              <th className="num">Present</th>
              <th className="num">Half day</th>
              <th className="num">Leave</th>
              <th className="num">Absent</th>
              <th className="num">Roster</th>
              <th className="num">Construction</th>
              <th className="num">Hours</th>
              <th className="num">Effective</th>
              <th className="num">Attendance</th>
            </tr>
          </thead>
          <tbody>
            {report.rows.length === 0 ? (
              <EmptyRow colSpan={14}>No attendance was marked in this period</EmptyRow>
            ) : (
              report.rows.map((row, index) => (
                <tr key={row.sewadarId}>
                  <td className="col-num">{index + 1}</td>
                  <td>{row.badgeNumber}</td>
                  <td className="cell-wrap">
                    <strong>{row.sewadarName}</strong>
                  </td>
                  <td>{row.zoneName}</td>
                  <td className="muted">{row.department || '-'}</td>
                  <td className="num">{row.presentDays}</td>
                  <td className="num">{row.halfDays}</td>
                  <td className="num">{row.leaveDays}</td>
                  <td className="num">{row.absentDays}</td>
                  <td className="num">{row.rosterSewaDays}</td>
                  <td className="num">{row.constructionSewaDays}</td>
                  <td className="num">{row.totalHours}</td>
                  <td className="num">{row.effectiveDays}</td>
                  <td className="num">
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
                <td className="num">{t.presentDays}</td>
                <td className="num">{t.halfDays}</td>
                <td className="num">{t.leaveDays}</td>
                <td className="num">{t.absentDays}</td>
                <td className="num">{t.rosterSewaDays}</td>
                <td className="num">{t.constructionSewaDays}</td>
                <td className="num">{t.totalHours}</td>
                <td className="num">-</td>
                <td className="num">{t.averageAttendancePercent}%</td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </div>
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
