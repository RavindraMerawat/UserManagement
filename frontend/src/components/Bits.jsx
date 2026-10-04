/** Small presentational helpers shared across the screens. */

export function StatCard({ label, value, hint, accent }) {
  return (
    <div className="stat" style={accent ? { borderLeftColor: accent } : undefined}>
      <div className="label">{label}</div>
      <div className="value">{value}</div>
      {hint && <div className="hint">{hint}</div>}
    </div>
  )
}

export function Badge({ value, label }) {
  if (!value) return <span className="muted">-</span>
  return <span className={`badge ${String(value).toLowerCase()}`}>{label || value}</span>
}

export function Pager({ page, totalPages, totalElements, onChange }) {
  const safeTotal = Math.max(totalPages, 1)
  return (
    <div className="pager">
      <span>
        {totalElements} record{totalElements === 1 ? '' : 's'} &middot; page {page + 1} of {safeTotal}
      </span>
      <div className="btn-row">
        <button
          type="button"
          className="btn ghost small"
          disabled={page <= 0}
          onClick={() => onChange(page - 1)}
        >
          Previous
        </button>
        <button
          type="button"
          className="btn ghost small"
          disabled={page >= safeTotal - 1}
          onClick={() => onChange(page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  )
}

/** Horizontal bar breakdown, used for the status and sewa type summaries. */
/**
 * The tab strip a list screen is filtered with: "All (1,248)  Active (1,102)".
 *
 * <p>The counts come from the server with the caller's scope applied, so a Zone
 * Incharge's "All" is their zones rather than the whole register. A tab whose count
 * has not arrived yet simply shows no number rather than a zero, because a zero
 * that later turns into 1,248 reads as a bug.</p>
 */
export function TabStrip({ tabs, value, onChange }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((tab) => (
        <button
          key={tab.key}
          type="button"
          role="tab"
          aria-selected={tab.key === value}
          className={tab.key === value ? 'active' : undefined}
          onClick={() => onChange(tab.key)}
        >
          {tab.label}
          {tab.count != null && <span className="tab-count">{tab.count.toLocaleString()}</span>}
        </button>
      ))}
    </div>
  )
}

export function BarBreakdown({ data, emptyLabel = 'No data for this period' }) {
  const entries = Object.entries(data || {})
  if (entries.length === 0) {
    return <p className="muted">{emptyLabel}</p>
  }
  const max = Math.max(...entries.map(([, v]) => v), 1)
  return (
    <div>
      {entries.map(([key, value]) => (
        <div className="bar-row" key={key}>
          <span className="bar-label">{key}</span>
          <span className="bar-track">
            <span className="bar-fill" style={{ width: `${(value / max) * 100}%` }} />
          </span>
          <span className="bar-value">{value}</span>
        </div>
      ))}
    </div>
  )
}

export function EmptyRow({ colSpan, children = 'No records found' }) {
  return (
    <tr>
      <td colSpan={colSpan}>
        <div className="empty">{children}</div>
      </td>
    </tr>
  )
}

export function Field({ label, required, children, wide }) {
  return (
    <div className={wide ? 'field wide' : 'field'}>
      <label>
        {label} {required && <span className="req">*</span>}
      </label>
      {children}
    </div>
  )
}

/**
 * A clock reading in hours, minutes and AM/PM.
 *
 * <p>`<input type="time">` looks like a 24 hour box or a 12 hour one depending on
 * the machine's locale, and the office reads the clock the way they say it - "4:51
 * pm", not "16:51". A native input cannot be told which to be, so this is three
 * plain selects instead. They say the same thing on every machine.</p>
 *
 * <p>The value in and out is still `HH:mm` on the 24 hour clock, which is what the
 * server stores and what the old input produced - nothing downstream changed.</p>
 */
export function TimeInput12({ id, value, disabled, onChange }) {
  // "" until all three are set, so a half-made time is never sent as a whole one.
  const [h24, minute] = (value || '').split(':')
  const hour24 = h24 === undefined || h24 === '' ? null : Number(h24)

  const meridiem = hour24 === null ? '' : hour24 < 12 ? 'AM' : 'PM'
  const hour12 =
    hour24 === null ? '' : String(hour24 % 12 === 0 ? 12 : hour24 % 12).padStart(2, '0')
  const mins = minute ?? ''

  /** Back to the 24 hour clock, which is the only form that leaves this component. */
  const emit = (nextHour12, nextMinute, nextMeridiem) => {
    if (!nextHour12 || !nextMeridiem) {
      onChange('')
      return
    }
    const h = Number(nextHour12) % 12 + (nextMeridiem === 'PM' ? 12 : 0)
    onChange(`${String(h).padStart(2, '0')}:${(nextMinute || '00').padStart(2, '0')}`)
  }

  // Picking an hour alone is a complete thought - "three o'clock" - so the minutes
  // and the half of the day take a sensible value rather than blocking the save.
  const pickHour = (next) => emit(next, mins || '00', meridiem || 'AM')

  return (
    <span className="time12">
      <select
        id={id}
        aria-label="Hour"
        value={hour12}
        disabled={disabled}
        onChange={(e) => pickHour(e.target.value)}
      >
        <option value="">--</option>
        {Array.from({ length: 12 }, (_, i) => String(i + 1).padStart(2, '0')).map((h) => (
          <option key={h} value={h}>
            {h}
          </option>
        ))}
      </select>
      <span className="time12-sep">:</span>
      <select
        aria-label="Minute"
        value={mins}
        disabled={disabled || !hour12}
        onChange={(e) => emit(hour12, e.target.value, meridiem || 'AM')}
      >
        <option value="">--</option>
        {Array.from({ length: 60 }, (_, i) => String(i).padStart(2, '0')).map((m) => (
          <option key={m} value={m}>
            {m}
          </option>
        ))}
      </select>
      <select
        aria-label="AM or PM"
        value={meridiem}
        disabled={disabled || !hour12}
        onChange={(e) => emit(hour12, mins || '00', e.target.value)}
      >
        <option value="">--</option>
        <option value="AM">AM</option>
        <option value="PM">PM</option>
      </select>
    </span>
  )
}
