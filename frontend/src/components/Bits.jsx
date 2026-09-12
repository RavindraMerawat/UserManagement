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
