import { useCallback, useEffect, useState } from 'react'
import { metaApi, reportApi, setupApi, sewadarApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { EmptyRow, Field } from '../components/Bits'
import { fromIso } from '../dates'

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

/**
 * 8.75 -> 08:45. The month's time as a clock reads it.
 *
 * <p>A decimal is how the hours are stored and a poor way to read them: nobody
 * works "8.75 hours". Minutes are rounded to the nearest whole one, because the
 * decimal came from times to the second.</p>
 */
function hhmm(hours) {
  const total = Math.round((hours || 0) * 60)
  const h = Math.floor(total / 60)
  const m = total % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

const now = new Date()

export default function Reports() {
  const { isSewadar, canViewMonthlyReport } = useAuth()

  /*
   * Two reports over the same month and the same columns: everybody, or one person
   * looked up by name. The second is the question the office is actually asked -
   * "how many hours has this sewadar done" - and answering it by generating the
   * whole register and scrolling was the long way round.
   */
  /*
   * The whole-register report belongs to the office; one sewadar's hours are a
   * lookup anybody with reach may do. A role without the first opens on the
   * second rather than on a tab that is not there.
   */
  // A Sewadar's monthly report is one row - their own - so it stays with them for
  // the same reason My Attendance does. The office's register is the gated one.
  const canOpenMonthly = canViewMonthlyReport || isSewadar
  const [kind, setKind] = useState(canOpenMonthly ? 'monthly' : 'sewadar')
  const [filters, setFilters] = useState({
    year: now.getFullYear(),
    month: now.getMonth() + 1,
    zoneId: '',
    designationId: '',
    /*
     * Local by default: the local register is what the office prints almost every
     * time, and defaulting to everyone meant deselecting the outstation sewadars by
     * hand on each download. "All localities" is still one choice away.
     */
    locality: 'LOCAL',
  })

  // The one-sewadar report's own lookup.
  const [lookup, setLookup] = useState('')
  const [searching, setSearching] = useState(false)
  const [hits, setHits] = useState(null)
  const [sewadar, setSewadar] = useState(null)

  const [zones, setZones] = useState([])
  const [designations, setDesignations] = useState([])
  const [channels, setChannels] = useState({ email: false, whatsapp: false })

  const [report, setReport] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [shareOpen, setShareOpen] = useState(false)

  useEffect(() => {
    zoneApi.list(true).then(setZones).catch(() => setZones([]))
    setupApi
      .designations({ active: true })
      .then(setDesignations)
      .catch(() => setDesignations([]))
    metaApi.channels().then(setChannels).catch(() => {})
  }, [])

  const queryParams = useCallback(
    () => ({
      zoneId: kind === 'sewadar' ? undefined : filters.zoneId || undefined,
      /*
       * One sewadar's report is already about one person, so a designation on it
       * could only ever agree or return nothing. It is a filter for the register,
       * and goes out with the monthly report only.
       */
      designationId: kind === 'sewadar' ? undefined : filters.designationId || undefined,
      // One sewadar's report is about one person, whose locality is already settled.
      locality: kind === 'sewadar' ? undefined : filters.locality || undefined,
      sewadarId: kind === 'sewadar' ? sewadar?.id : undefined,
      year: Number(filters.year),
      month: Number(filters.month),
    }),
    [filters, kind, sewadar],
  )

  const resetLookup = () => {
    setLookup('')
    setHits(null)
    setSewadar(null)
    setReport(null)
    setError('')
  }

  const findSewadar = async (event) => {
    event.preventDefault()
    setError('')
    setNotice('')
    const term = lookup.trim()
    if (term.length < 2) {
      setError('Enter at least 2 characters of a GR. No, name, mobile or F/H name.')
      return
    }
    setSearching(true)
    setSewadar(null)
    setHits(null)
    setReport(null)
    try {
      const found = await sewadarApi.search({ query: term, size: 10 })
      const rows = found.content || []
      if (rows.length === 0) {
        setError(`No sewadar found for "${term}" in the zones you can reach.`)
      } else if (rows.length === 1) {
        setSewadar(rows[0])
      } else {
        setHits(rows)
      }
    } catch (err) {
      setError(errorMessage(err, 'Search failed'))
    } finally {
      setSearching(false)
    }
  }

  /*
   * One sewadar's report runs itself. The search already named who it is about and
   * the month is already chosen, so there was nothing left for a Generate button to
   * ask - it only stood between the answer and the person who wanted it.
   */
  useEffect(() => {
    if (kind !== 'sewadar' || !sewadar) return
    let cancelled = false
    setLoading(true)
    setError('')
    reportApi
      .monthly({ sewadarId: sewadar.id, year: Number(filters.year), month: Number(filters.month) })
      .then((result) => {
        if (!cancelled) setReport(result)
      })
      .catch((err) => {
        if (!cancelled) setError(errorMessage(err, 'Could not generate the report'))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [kind, sewadar, filters.year, filters.month])

  const generate = async () => {
    setLoading(true)
    setError('')
    setReport(null)
    try {
      setReport(await reportApi.monthly(queryParams()))
    } catch (err) {
      setError(errorMessage(err, 'Could not generate the report'))
    } finally {
      setLoading(false)
    }
  }

  const download = async (format) => {
    setError('')
    try {
      const fileName = await reportApi.download(format, queryParams())
      setNotice(`Downloaded ${fileName}`)
    } catch (err) {
      setError(errorMessage(err, 'Could not download the report'))
    }
  }

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

      <div className="tabs">
        {[
          canOpenMonthly && { key: 'monthly', label: 'Monthly Report' },
          { key: 'sewadar', label: 'Sewadar Monthly Hours' },
        ]
          .filter(Boolean)
          .map((item) => (
          <button
            key={item.key}
            type="button"
            className={kind === item.key ? 'active' : ''}
            onClick={() => {
              setKind(item.key)
              setReport(null)
              setError('')
            }}
          >
            {item.label}
          </button>
        ))}
      </div>

      <div className="card">
        {kind === 'sewadar' && (
          <>
            {/* A form, so Enter searches without reaching for the button. */}
            <form className="cs-lookup" onSubmit={findSewadar}>
              <Field label="GR. No, Name, Mobile No or F/H Name">
                <input
                  value={lookup}
                  onChange={(e) => setLookup(e.target.value)}
                  placeholder="Type and press Enter, or use Search"
                />
              </Field>
              <button type="submit" className="btn" disabled={searching}>
                {searching ? 'Searching...' : 'Search'}
              </button>
              <button type="button" className="btn ghost" onClick={resetLookup}>
                Reset
              </button>
            </form>

            {hits && (
              <div className="cs-hits">
                <p className="hint">{hits.length} sewadars match. Choose the one you mean.</p>
                {hits.map((hit) => (
                  <button
                    type="button"
                    key={hit.id}
                    className="cs-hit"
                    onClick={() => {
                      setHits(null)
                      setSewadar(hit)
                    }}
                  >
                    <strong>{hit.name}</strong>
                    <span className="muted">
                      {hit.badgeNumber} · {hit.fatherOrHusbandName || 'no F/H name'} ·{' '}
                      {hit.mobile || 'no mobile'}
                    </span>
                  </button>
                ))}
              </div>
            )}

            {sewadar && (
              <p className="report-chosen">
                <strong>{sewadar.name}</strong> · {sewadar.badgeNumber} ·{' '}
                {sewadar.zoneName || 'no zone'}
              </p>
            )}
          </>
        )}


        <div className="filters">
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

          {!isSewadar && kind === 'monthly' && (
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

          {/*
            Designation narrows the register the same way Zone does, and the two
            combine: Zone 1A's supervisors is a question the office asks. Choosing
            one changes nothing on screen until Generate report is pressed, like
            every other filter here.
          */}
          {!isSewadar && kind === 'monthly' && (
            <Field label="Designation">
              <select
                value={filters.designationId}
                onChange={(e) => setFilters({ ...filters, designationId: e.target.value })}
              >
                <option value="">All designations</option>
                {designations.map((designation) => (
                  <option key={designation.id} value={designation.id}>
                    {designation.name}
                  </option>
                ))}
              </select>
            </Field>
          )}

          {kind === 'monthly' && (
            <Field label="Locality">
              <select
                value={filters.locality}
                onChange={(e) => setFilters({ ...filters, locality: e.target.value })}
              >
                <option value="LOCAL">Local</option>
                <option value="OUTSTATION">Outstation</option>
                <option value="">All localities</option>
              </select>
            </Field>
          )}

        </div>

        <div className="btn-row" style={{ marginTop: 16 }}>
          {kind === 'monthly' && (
            <button type="button" className="btn" onClick={generate} disabled={loading}>
              {loading ? 'Generating...' : 'Generate report'}
            </button>
          )}
          {report && (
            <>
              {/*
                The sheet people actually hand round: one table per zone, five
                columns - S. NO., GR NO., NAME, ZONE and the month's hours - with
                the co-ordinator and the incharges at the top in bold. ZONE is the
                grouping, and the hours are the rounded ones.
              */}
              <button type="button" className="btn ghost" onClick={() => download('pdf')}>
                Download PDF
              </button>
              <button type="button" className="btn ghost" onClick={() => download('excel')}>
                Download Excel
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
          <span className="report-chip strong">
            {t.sewadarCount} sewadar{t.sewadarCount === 1 ? '' : 's'}
          </span>
        </div>
      </header>

      <div className="table-wrap">
        <table className="table-md report-table">
          <thead>
            <tr>
              <th className="col-num">S.No</th>
              <th>GR. No</th>
              <th>Sewadar</th>
              <th>Area</th>
              <th>Zone</th>
              <th>Satsang Point</th>
              <th>Department</th>
              <th className="num">Present</th>
              <th className="num">Hours</th>
              <th className="num">Effective Hours</th>
            </tr>
          </thead>
          <tbody>
            {report.rows.length === 0 ? (
              <EmptyRow colSpan={10}>No attendance was marked in this period</EmptyRow>
            ) : (
              report.rows.map((row, index) => (
                <tr key={row.sewadarId}>
                  <td className="col-num">{index + 1}</td>
                  <td>{row.badgeNumber}</td>
                  <td className="cell-wrap">
                    <strong>{row.sewadarName}</strong>
                  </td>
                  <td>{row.area || '-'}</td>
                  <td>{row.zoneName}</td>
                  <td>{row.satsangPoint || '-'}</td>
                  <td className="muted">{row.department || '-'}</td>
                  <td className="num">{row.presentDays}</td>
                  <td className="num">{hhmm(row.totalHours)}</td>
                  <td className="num">
                    <strong>{row.effectiveHours}</strong>
                  </td>
                </tr>
              ))
            )}
          </tbody>
          {report.rows.length > 0 && (
            <tfoot>
              <tr>
                <td colSpan={7}>Total ({t.sewadarCount} sewadars)</td>
                <td className="num">{t.presentDays}</td>
                <td className="num">{hhmm(t.totalHours)}</td>
                <td className="num">{t.effectiveHours}</td>
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </div>
  )
}

function ShareDialog({ open, onClose, report, params, channels, onDone, onError }) {
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
      const result = await reportApi.share(params, payload)
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
