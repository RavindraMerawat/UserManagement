import { useCallback, useEffect, useState } from 'react'
import { constructionApi, sewadarApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import { EmptyRow, Field, Pager } from '../../components/Bits'
import { prettyDate, todayIso } from '../../dates'

/**
 * The construction sewa register, one sewadar at a time.
 *
 * <p>Find somebody by GR. No, name or mobile and the grid shows their days and
 * nobody else's - the question this register answers is always about a particular
 * person, and a list of everyone was a list nobody had asked for.</p>
 *
 * <p>Recording is a date, not a figure: a day either had construction sewa on it or
 * it did not, and the count is how many days are on file. Whoever may not record
 * still searches and reads; the add form simply is not drawn for them.</p>
 */
export default function ConstructionSewa() {
  const { canManageConstruction } = useAuth()

  const [lookup, setLookup] = useState('')
  const [searching, setSearching] = useState(false)
  const [hits, setHits] = useState(null)
  const [found, setFound] = useState(null)

  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)

  const [sewaDate, setSewaDate] = useState(todayIso())
  const [remarks, setRemarks] = useState('')
  const [saving, setSaving] = useState(false)
  const [removing, setRemoving] = useState(null)

  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const sewadarId = found?.id

  const load = useCallback(() => {
    if (!sewadarId) {
      setResult(null)
      return
    }
    setLoading(true)
    constructionApi
      .forSewadar(sewadarId, { page, size: 25 })
      .then(setResult)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [sewadarId, page])

  useEffect(() => {
    load()
  }, [load])

  const reset = () => {
    setLookup('')
    setHits(null)
    setFound(null)
    setResult(null)
    setPage(0)
    setSewaDate(todayIso())
    setRemarks('')
    setError('')
  }

  const pick = (sewadar) => {
    setError('')
    setHits(null)
    setPage(0)
    setFound(sewadar)
    setSewaDate(todayIso())
    setRemarks('')
  }

  const onLookup = async (event) => {
    event.preventDefault()
    setError('')
    setNotice('')
    const term = lookup.trim()
    if (term.length < 2) {
      setError('Enter at least 2 characters of a GR. No, name or mobile number.')
      return
    }
    setSearching(true)
    setFound(null)
    setHits(null)
    setResult(null)
    try {
      const search = await sewadarApi.search({ query: term, size: 10 })
      const rows = search.content || []
      if (rows.length === 0) {
        setError(`No sewadar found for "${term}" in the zones you can reach.`)
      } else if (rows.length === 1) {
        // One match is the answer, so it opens rather than asking to be clicked.
        pick(rows[0])
      } else {
        setHits(rows)
      }
    } catch (err) {
      setError(errorMessage(err, 'Search failed'))
    } finally {
      setSearching(false)
    }
  }

  const onAdd = async (event) => {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const saved = await constructionApi.record({
        sewadarId: found.id,
        sewaDate,
        remarks: remarks || null,
      })
      setNotice(`${saved.name} (${saved.badgeNumber}) recorded for ${prettyDate(saved.sewaDate)}.`)
      // Back to an empty search, the way the desk works: one sewadar is finished
      // and the next one is looked up from scratch.
      reset()
    } catch (err) {
      // The commonest one is "that day is already recorded", which says so by name
      // and date - it belongs in front of the person, not in a console.
      setError(errorMessage(err, 'Could not record that day'))
    } finally {
      setSaving(false)
    }
  }

  const onRemove = async (row) => {
    setRemoving(row.id)
    setError('')
    try {
      await constructionApi.remove(row.id)
      setNotice(`${prettyDate(row.sewaDate)} removed for ${row.name}.`)
      load()
    } catch (err) {
      setError(errorMessage(err, 'Could not remove that day'))
    } finally {
      setRemoving(null)
    }
  }

  const rows = result?.content || []
  const total = result?.totalElements ?? 0
  const firstSerial = (result?.page ?? 0) * (result?.size ?? 20) + 1
  const columns = canManageConstruction ? 6 : 5

  return (
    <div>
      <div className="page-head">
        <div>
          <h1 className="page-title">Construction Sewa</h1>
          <p className="page-sub">
            Search a sewadar to see the days they have done, and record another.
          </p>
        </div>
      </div>

      <Alert kind="error" onClose={() => setError('')}>
        {error}
      </Alert>
      <Alert kind="success" onClose={() => setNotice('')}>
        {notice}
      </Alert>

      <div className="card">
        {/* A form, so Enter in the box searches without reaching for the button. */}
        <form className="cs-lookup" onSubmit={onLookup}>
          <Field label="GR. No, Name or Mobile No">
            <input
              value={lookup}
              onChange={(e) => setLookup(e.target.value)}
              placeholder="Type and press Enter, or use Search"
            />
          </Field>
          <button type="submit" className="btn" disabled={searching}>
            {searching ? 'Searching...' : 'Search'}
          </button>
          <button type="button" className="btn ghost" onClick={reset}>
            Reset
          </button>
        </form>

        {hits && (
          <div className="cs-hits">
            <p className="hint">{hits.length} sewadars match. Choose the one you mean.</p>
            {hits.map((hit) => (
              <button type="button" key={hit.id} className="cs-hit" onClick={() => pick(hit)}>
                <strong>{hit.name}</strong>
                <span className="muted">
                  {hit.badgeNumber} · {hit.zoneName || 'no zone'} · {hit.mobile || 'no mobile'}
                </span>
              </button>
            ))}
          </div>
        )}

        {found && (
          <div className="cs-found">
            <dl className="kv cs-facts">
              <dt>GR. No</dt>
              <dd>{found.badgeNumber}</dd>
              <dt>Name</dt>
              <dd>{found.name}</dd>
              <dt>Zone</dt>
              <dd>{found.zoneName || '-'}</dd>
              <dt>Mobile No</dt>
              <dd>{found.mobile || '-'}</dd>
              <dt>Area / Point</dt>
              <dd>{[found.area, found.centerPoint].filter(Boolean).join(' / ') || '-'}</dd>
            </dl>

            {canManageConstruction && (
              <form onSubmit={onAdd} className="cs-lookup">
                <Field label="Sewa date" required>
                  <input
                    type="date"
                    value={sewaDate}
                    max={todayIso()}
                    onChange={(e) => setSewaDate(e.target.value)}
                    required
                  />
                </Field>
                <Field label="Remarks">
                  <input
                    value={remarks}
                    onChange={(e) => setRemarks(e.target.value.slice(0, 300))}
                    placeholder="Optional"
                  />
                </Field>
                <button type="submit" className="btn" disabled={saving || !sewaDate}>
                  {saving ? 'Adding...' : 'Add construction sewa'}
                </button>
              </form>
            )}
          </div>
        )}
      </div>

      {found && (
        <div className="card">
          <div className="card-head">
            <h3>
              {found.name} · {found.badgeNumber}
            </h3>
          </div>

          {loading ? (
            <Spinner />
          ) : (
            <>
              <div className="table-wrap">
                <table className="table-md">
                  <thead>
                    <tr>
                      <th>S.No</th>
                      <th>Sewa Date</th>
                      <th>Remarks</th>
                      <th>Recorded By</th>
                      {canManageConstruction && <th>Action</th>}
                      <th className="col-fill" />
                    </tr>
                  </thead>
                  <tbody>
                    {rows.length === 0 ? (
                      <EmptyRow colSpan={columns}>
                        No construction sewa recorded for this sewadar yet
                      </EmptyRow>
                    ) : (
                      rows.map((row, i) => (
                        <tr key={row.id}>
                          <td>{firstSerial + i}</td>
                          <td>{prettyDate(row.sewaDate)}</td>
                          <td className="muted">{row.remarks || '-'}</td>
                          <td className="muted">{row.updatedBy || '-'}</td>
                          {canManageConstruction && (
                            <td>
                              <button
                                type="button"
                                className="btn ghost small"
                                onClick={() => onRemove(row)}
                                disabled={removing === row.id}
                              >
                                {removing === row.id ? 'Removing...' : 'Remove'}
                              </button>
                            </td>
                          )}
                          <td className="col-fill" />
                        </tr>
                      ))
                    )}
                  </tbody>
                  {total > 0 && (
                    /* The count, at the end of the grid: how many days are on file. */
                    <tfoot>
                      <tr>
                        <td colSpan={columns - 1}>Total construction sewa</td>
                        <td>
                          <strong>{total}</strong>
                        </td>
                        <td className="col-fill" />
                      </tr>
                    </tfoot>
                  )}
                </table>
              </div>

              <Pager
                page={result?.page ?? 0}
                totalPages={result?.totalPages ?? 0}
                totalElements={total}
                onChange={setPage}
              />
            </>
          )}
        </div>
      )}
    </div>
  )
}
