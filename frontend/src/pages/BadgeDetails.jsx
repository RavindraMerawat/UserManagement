import { useEffect, useState } from 'react'
import { sewadarApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import Alert from '../components/Alert'
import Spinner from '../components/Spinner'
import { Avatar } from '../components/Photo'
import { Badge } from '../components/Bits'
import { useAuth } from '../auth/AuthContext'

function ageOn(dateOfBirth) {
  if (!dateOfBirth) return '-'
  const birth = new Date(`${dateOfBirth}T00:00:00`)
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const beforeBirthday =
    today.getMonth() < birth.getMonth() ||
    (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate())
  return `${age - (beforeBirthday ? 1 : 0)} years`
}

export default function BadgeDetails() {
  const { canManageBadges } = useAuth()
  const [summary, setSummary] = useState(null)
  const [query, setQuery] = useState('')
  const [matches, setMatches] = useState([])
  const [selected, setSelected] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [acting, setActing] = useState(false)
  const [gridStatus, setGridStatus] = useState('')
  const [gridRows, setGridRows] = useState([])

  const loadSummary = () => sewadarApi.badgeSummary().then(setSummary).catch((err) => setError(errorMessage(err)))

  useEffect(() => {
    loadSummary()
  }, [])

  const search = async (event) => {
    event.preventDefault()
    const term = query.trim()
    if (term.length < 2) {
      setError('Enter at least 2 characters of a badge number or name.')
      return
    }
    setLoading(true)
    setError('')
    setSelected(null)
    setMatches([])
    try {
      const result = await sewadarApi.search({ query: term, active: true, size: 20 })
      if (result.content.length === 0) setError(`No badge record found for "${term}".`)
      else if (result.content.length === 1) setSelected(result.content[0])
      else setMatches(result.content)
    } catch (err) {
      setError(errorMessage(err, 'Badge search failed'))
    } finally {
      setLoading(false)
    }
  }

  const reset = () => {
    setQuery('')
    setMatches([])
    setSelected(null)
    setError('')
  }

  const openGrid = async (status) => {
    setLoading(true)
    setError('')
    setGridStatus(status)
    try {
      const result = await sewadarApi.search({ size: 200 })
      setGridRows(result.content.filter((person) => status === 'Issued' ? person.badgeIssued : status === 'Received' ? person.badgeReceived : !person.badgeIssued))
    } catch (err) {
      setError(errorMessage(err, 'Could not load badge records'))
    } finally {
      setLoading(false)
    }
  }

  const markBadge = async (action) => {
    setActing(true)
    setError('')
    try {
      const updated = action === 'issue' ? await sewadarApi.issueBadge(selected.id) : await sewadarApi.receiveBadge(selected.id)
      setSelected(updated)
      await loadSummary()
      if (gridStatus) await openGrid(gridStatus)
    } catch (err) {
      setError(errorMessage(err, `Could not ${action} badge`))
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="badge-details">
      <div className="page-head">
        <div>
          <h1 className="page-title">Badge Detail</h1>
          <p className="page-sub">Manage sewadar badges and card details, within your zones.</p>
        </div>
      </div>

      <div className="tile-grid badge-stats">
        {[
          ['Badge Issued', summary?.issued, 'blue', 'Issued'],
          ['Badge Received', summary?.received, 'green', 'Received'],
          ['Badge Pending', summary?.pending, 'amber', 'Pending'],
        ].map(([label, value, tone, status]) => (
          <button type="button" className="tile" key={label} onClick={() => openGrid(status)}>
            <span className={`tile-icon ${tone}`}>▣</span>
            <div className="tile-body"><span className="tile-label">{label}</span><span className="tile-value">{value ?? '—'}</span></div>
          </button>
        ))}
      </div>

      <section className="panel">
        <form className="mark-search" onSubmit={search}>
          <div className="mark-search-field">
            <span className="search-icon">⌕</span>
            <input autoFocus value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search by Badge No or Name..." aria-label="Search badges" />
          </div>
          <button type="submit" className="btn" disabled={loading}>{loading ? 'Searching...' : 'Search'}</button>
          <button type="button" className="btn secondary" onClick={reset} disabled={loading}>Reset</button>
        </form>
      </section>

      <Alert kind="error" onClose={() => setError('')}>{error}</Alert>
      {loading && <Spinner label="Searching badge records" />}

      {matches.length > 0 && (
        <section className="panel"><header className="panel-head"><h2>{matches.length} matches — pick one</h2></header>
          <ul className="hit-list">{matches.map((person) => (
            <li key={person.id}><button type="button" onClick={() => { setSelected(person); setMatches([]) }}>
              <Avatar kind="sewadars" id={person.id} stamp={person.photoUpdatedAt} name={person.name} size={38} />
              <span className="hit-body"><strong>{person.name}</strong><span className="muted">{person.badgeNumber} · {person.zoneName}</span></span>
              <Badge value={person.badgeReceived ? 'active' : person.badgeIssued ? 'pending' : 'inactive'} label={person.badgeReceived ? 'Received' : person.badgeIssued ? 'Issued' : 'Pending'} />
            </button></li>
          ))}</ul>
        </section>
      )}

      {selected && (
        <section className="panel badge-card">
          <div className="person">
            <Avatar kind="sewadars" id={selected.id} stamp={selected.photoUpdatedAt} name={selected.name} size={84} />
            <div className="person-facts"><div className="person-name"><h2>{selected.name}</h2><Badge value={selected.badgeReceived ? 'active' : selected.badgeIssued ? 'pending' : 'inactive'} label={selected.badgeReceived ? 'Badge Received' : selected.badgeIssued ? 'Badge Issued' : 'Badge Pending'} /></div>
              <dl className="person-kv">
                <dt>Badge No</dt><dd>{selected.badgeNumber}</dd><dt>Age</dt><dd>{ageOn(selected.dateOfBirth)}</dd>
                <dt>Mobile No</dt><dd>{selected.mobile || '-'}</dd><dt>Zone</dt><dd>{selected.zoneName || '-'}</dd>
                <dt>Area</dt><dd>{selected.area || '-'}</dd><dt>Point</dt><dd>{selected.centerPoint || '-'}</dd><dt>Badge Issued</dt><dd>{selected.badgeIssued ? 'Yes' : 'No'}</dd>
                <dt>Badge Received</dt><dd>{selected.badgeReceived ? 'Yes' : 'Pending'}</dd>
              </dl>
            </div>
          </div>
          {canManageBadges && (
            <div className="act-grid">
              <button type="button" className="btn ok" disabled={acting || selected.badgeIssued} onClick={() => markBadge('issue')}>Issue Badge</button>
              <button type="button" className="btn danger" disabled={acting || !selected.badgeIssued || selected.badgeReceived} onClick={() => markBadge('receive')}>Receive Badge</button>
            </div>
          )}
        </section>
      )}

      {gridStatus && (
        <section className="panel">
          <header className="panel-head"><h2>{gridStatus} Badges</h2><button type="button" className="btn ghost small" onClick={() => { setGridStatus(''); setGridRows([]) }}>Close</button></header>
          <div className="table-wrap"><table>
            <thead><tr><th>Photo</th><th>Badge No</th><th>Name</th><th>Mobile No</th><th>Zone</th><th>Area</th><th>Point</th><th>Status</th></tr></thead>
            <tbody>{gridRows.length === 0 ? <tr><td colSpan="8" className="empty">No {gridStatus.toLowerCase()} badge records</td></tr> : gridRows.map((person) => (
              <tr key={person.id} onClick={() => setSelected(person)} style={{ cursor: 'pointer' }}>
                <td><Avatar kind="sewadars" id={person.id} stamp={person.photoUpdatedAt} name={person.name} size={32} /></td><td>{person.badgeNumber}</td><td>{person.name}</td><td>{person.mobile || '-'}</td><td>{person.zoneName || '-'}</td><td>{person.area || '-'}</td><td>{person.centerPoint || '-'}</td>
                <td><Badge value={person.badgeReceived ? 'active' : person.badgeIssued ? 'pending' : 'inactive'} label={person.badgeReceived ? 'Received' : person.badgeIssued ? 'Issued' : 'Pending'} /></td>
              </tr>
            ))}</tbody>
          </table></div>
        </section>
      )}
    </div>
  )
}
