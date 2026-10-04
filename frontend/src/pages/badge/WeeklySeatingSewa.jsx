import { useCallback, useEffect, useState } from 'react'
import { sewadarApi, weeklySeatingApi } from '../../api/endpoints'
import { errorMessage } from '../../api/client'
import { useAuth } from '../../auth/AuthContext'
import Alert from '../../components/Alert'
import Spinner from '../../components/Spinner'
import { Avatar } from '../../components/Photo'
import { Badge, EmptyRow, Field, Pager } from '../../components/Bits'
import { prettyDate, todayIso } from '../../dates'
import { PAGE_SIZE } from '../../pageSize'

/** Sunday or Thursday, worked out from the date so the two cannot disagree. */
function dayOf(iso) {
  if (!iso) return ''
  const [y, m, d] = iso.split('-').map(Number)
  const day = new Date(y, m - 1, d).getDay()
  if (day === 0) return 'SUNDAY'
  if (day === 4) return 'THURSDAY'
  return ''
}

const DAY_LABEL = { SUNDAY: 'Sunday', THURSDAY: 'Thursday' }

/** 14:05:00 -> 02:05 PM. A dash when nothing has happened yet. */
function prettyTime(time) {
  if (!time) return '-'
  const [h, m] = time.split(':')
  const hour = Number(h)
  const suffix = hour < 12 ? 'AM' : 'PM'
  const twelve = hour % 12 === 0 ? 12 : hour % 12
  return `${String(twelve).padStart(2, '0')}:${m} ${suffix}`
}

/**
 * The day the screen opens on: today, but only when today is a seating day.
 *
 * <p>On a Sunday or a Thursday the counts are the day's own business and are there
 * before anything is typed. On any other day there is no seating to count, so the
 * date starts empty and the cards stay away rather than reporting four zeroes for a
 * day nobody sat on.</p>
 */
function todaySeatingDay() {
  const weekday = new Date().getDay()
  return weekday === 0 || weekday === 4 ? todayIso() : ''
}

/**
 * Weekly seating sewa.
 *
 * <p>Search a sewadar, record the day they sat and the token they were given, and
 * their attendance for that day is marked with it - one act at the desk, one act on
 * the screen. The badge itself is issued and collected from the same card, because
 * that is the moment the person is standing there.</p>
 */
export default function WeeklySeatingSewa() {
  const { canManageBadges, accountGender } = useAuth()

  const [query, setQuery] = useState('')
  const [searching, setSearching] = useState(false)
  const [hits, setHits] = useState(null)
  const [selected, setSelected] = useState(null)

  const [sewaDate, setSewaDate] = useState(todaySeatingDay)
  const [tokenNo, setTokenNo] = useState('')
  const [acting, setActing] = useState('')

  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)

  // The day's four numbers, and whichever of them is open underneath.
  const [summary, setSummary] = useState(null)
  const [openCount, setOpenCount] = useState(null)
  const [countRows, setCountRows] = useState([])
  const [countPage, setCountPage] = useState({ page: 0, totalPages: 0, totalElements: 0 })
  const [countLoading, setCountLoading] = useState(false)

  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const sewadarId = selected?.id
  const weekDay = dayOf(sewaDate)

  const load = useCallback(() => {
    if (!sewadarId) {
      setResult(null)
      return
    }
    setLoading(true)
    weeklySeatingApi
      .forSewadar(sewadarId, { page, size: PAGE_SIZE })
      .then(setResult)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [sewadarId, page])

  useEffect(() => {
    load()
  }, [load])

  const loadSummary = useCallback(() => {
    if (!sewaDate || !weekDay) {
      setSummary(null)
      setOpenCount(null)
      return
    }
    weeklySeatingApi
      .summary(sewaDate)
      .then(setSummary)
      .catch(() => setSummary(null))
  }, [sewaDate, weekDay])

  useEffect(() => {
    loadSummary()
  }, [loadSummary])

  /**
   * Opens the people behind one card. Clicking the open one closes it again.
   *
   * <p>A day's list runs to hundreds on a busy Sunday, so it is read a page at a
   * time like every other list here. The card stays open while the page turns:
   * `toPage` of null is the click that closes it, 0 is a fresh open.</p>
   */
  const openCountDetail = async (action, gender, toPage = 0) => {
    const key = `${action}-${gender}`
    if (toPage === null) {
      setOpenCount(null)
      return
    }
    setOpenCount(key)
    setCountLoading(true)
    try {
      const found = await weeklySeatingApi.day({
        date: sewaDate, action, gender, page: toPage, size: PAGE_SIZE,
      })
      setCountRows(found.content || [])
      setCountPage({
        page: found.page ?? toPage,
        totalPages: found.totalPages ?? 0,
        totalElements: found.totalElements ?? 0,
      })
    } catch (err) {
      setError(errorMessage(err, 'Could not read that list'))
      setCountRows([])
      setCountPage({ page: 0, totalPages: 0, totalElements: 0 })
    } finally {
      setCountLoading(false)
    }
  }

  const reset = () => {
    setQuery('')
    setHits(null)
    setSelected(null)
    setResult(null)
    setPage(0)
    setSewaDate(todaySeatingDay())
    setTokenNo('')
    setError('')
  }

  const pick = (person) => {
    setError('')
    setHits(null)
    setPage(0)
    setSelected(person)
    setSewaDate(todaySeatingDay())
    setTokenNo('')
  }

  const onSearch = async (event) => {
    event.preventDefault()
    setError('')
    setNotice('')
    const term = query.trim()
    if (term.length < 2) {
      setError('Enter at least 2 characters of a GR. No, name or mobile number.')
      return
    }
    setSearching(true)
    setSelected(null)
    setHits(null)
    setResult(null)
    try {
      const search = await sewadarApi.search({ query: term, size: PAGE_SIZE })
      const rows = search.content || []
      if (rows.length === 0) {
        setError(`No sewadar found for "${term}" in the zones you can reach.`)
      } else if (rows.length === 1) {
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

  /*
   * Handing the badge over IS the record, so the button that does it is the button
   * that saves. One call writes the seating row, the day's attendance and the badge
   * flag; a separate Submit step only made it possible to do half the job.
   */
  const markBadge = async (action) => {
    setActing(action)
    setError('')
    setNotice('')
    try {
      const saved = await weeklySeatingApi.record({
        sewadarId: selected.id,
        sewaDate,
        weekDay,
        tokenNo,
        action,
      })
      setNotice(
        `Badge No ${saved.tokenNo} ${action === 'ISSUE' ? 'issued to' : 'received from'} ` +
          `${saved.name} for ${DAY_LABEL[saved.weekDay]} ${prettyDate(saved.sewaDate)}. ` +
          'Attendance marked present for that day.',
      )
      setSelected((current) => ({
        ...current,
        badgeIssued: action === 'ISSUE' ? true : current.badgeIssued,
        badgeReceived: action === 'RECEIVE' ? true : current.badgeReceived,
      }))
      setPage(0)
      load()
      loadSummary()
    } catch (err) {
      setError(errorMessage(err, 'Could not record the badge'))
    } finally {
      setActing('')
    }
  }

  /** Issue or receive straight from a row of the grid, on that row's own day. */
  const markRow = async (row, action) => {
    setActing(`row-${row.id}`)
    setError('')
    setNotice('')
    try {
      const saved = await weeklySeatingApi.record({
        sewadarId: row.sewadarId,
        sewaDate: row.sewaDate,
        weekDay: row.weekDay,
        tokenNo: row.tokenNo,
        action,
      })
      setNotice(
        `Badge No ${saved.tokenNo} ${action === 'ISSUE' ? 'issued' : 'received'} for ` +
          `${DAY_LABEL[saved.weekDay]} ${prettyDate(saved.sewaDate)}.`,
      )
      load()
      loadSummary()
    } catch (err) {
      setError(errorMessage(err, 'Could not record the badge'))
    } finally {
      setActing('')
    }
  }

  const rows = result?.content || []
  /*
   * The row for the day in the date box, if there is one. It is what says whether
   * the badge has already gone out or come back, so the two buttons can stop
   * offering what has been done.
   */
  const dayRow = rows.find((row) => row.sewaDate === sewaDate) || null
  // What both buttons need before either can mean anything: a day, a badge number
  // and no save already running.
  const canRecord = !acting && Boolean(weekDay) && Boolean(tokenNo.trim())
  const total = result?.totalElements ?? 0
  const firstSerial = (result?.page ?? 0) * (result?.size ?? 20) + 1
  const columns = canManageBadges ? 7 : 6
  const dateProblem = sewaDate && !weekDay

  return (
    <div>
      <div className="page-head">
        <div>
          <h1 className="page-title">Weekly Seating Sewa</h1>
          <p className="page-sub">
            Record who sat on a Sunday or Thursday and the badge they were given. The
            day's attendance is marked at the same time.
          </p>
        </div>
      </div>

      <Alert kind="error" onClose={() => setError('')}>
        {error}
      </Alert>
      <Alert kind="success" onClose={() => setNotice('')}>
        {notice}
      </Alert>

      {summary && (
        <>
          {/*
            The day's four numbers, for the Sunday or Thursday in the date box above.
            They are about the day rather than the sewadar on screen, which is why
            they follow the date and not the search.
          */}
          <div className="tile-grid badge-stats">
            {[
              ['Issued – Male', summary.issuedMale, 'blue', 'ISSUE', 'MALE'],
              ['Issued – Female', summary.issuedFemale, 'violet', 'ISSUE', 'FEMALE'],
              ['Received – Male', summary.receivedMale, 'green', 'RECEIVE', 'MALE'],
              ['Received – Female', summary.receivedFemale, 'amber', 'RECEIVE', 'FEMALE'],
            ]
              /*
                An account reads its own gender's register, so it is shown its own
                gender's cards. The numbers were already right - the server counts
                within the scope - but the two it may not see came back as 0, and a
                card reading "Issued - Male 0" is worse than no card: it looks like
                a day on which no man took a badge. Admin has no gender scope and
                keeps all four.
              */
              .filter(([, , , , gender]) => !accountGender || accountGender === gender)
              .map(([label, value, tone, action, gender]) => (
              <button
                type="button"
                className={`tile${openCount === `${action}-${gender}` ? ' tile-open' : ''}`}
                key={label}
                onClick={() =>
                  openCountDetail(action, gender, openCount === `${action}-${gender}` ? null : 0)
                }
              >
                <span className={`tile-icon ${tone}`}>▣</span>
                <div className="tile-body">
                  <span className="tile-label">{label}</span>
                  <span className="tile-value">{value}</span>
                  <span className="tile-hint">
                    {DAY_LABEL[weekDay]} {prettyDate(sewaDate)}
                  </span>
                </div>
              </button>
            ))}
          </div>

        </>
      )}

      <div className="card">
        <form className="cs-lookup" onSubmit={onSearch}>
          <Field label="GR. No, Name or Mobile No">
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
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

        {selected && (
          <div className="cs-found">
            <div className="ws-person">
              <Avatar
                kind="sewadars"
                id={selected.id}
                stamp={selected.photoUpdatedAt}
                name={selected.name}
                size={64}
              />
              <dl className="kv cs-facts">
                <dt>GR. No</dt>
                <dd>{selected.badgeNumber}</dd>
                <dt>Name</dt>
                <dd>{selected.name}</dd>
                <dt>Zone</dt>
                <dd>{selected.zoneName || '-'}</dd>
                <dt>Mobile No</dt>
                <dd>{selected.mobile || '-'}</dd>
                <dt>Area / Point</dt>
                <dd>
                  {[selected.area, selected.centerPoint].filter(Boolean).join(' / ') || '-'}
                </dd>
                <dt>Badge</dt>
                <dd>
                  <Badge
                    value={
                      selected.badgeReceived
                        ? 'active'
                        : selected.badgeIssued
                          ? 'pending'
                          : 'inactive'
                    }
                    label={
                      selected.badgeReceived
                        ? 'Received'
                        : selected.badgeIssued
                          ? 'Issued'
                          : 'Pending'
                    }
                  />
                </dd>
              </dl>
            </div>

            {canManageBadges && (
              <div className="ws-record">
                {/*
                  One block, titled, so the three fields and the two buttons read as
                  the single act they are: this badge, on this day, to this person.
                  Loose on the card they looked like leftovers of the panel above.
                */}
                <p className="form-section">Record the badge</p>

                {/* Each field is as wide as what goes in it: a date, one of two
                    words, and a short number. */}
                <div className="ws-entry">
                  <Field label="Date" required>
                    <input
                      type="date"
                      value={sewaDate}
                      max={todayIso()}
                      onChange={(e) => setSewaDate(e.target.value)}
                      required
                    />
                  </Field>
                  <Field label="Weekly" required>
                    {/*
                      Read from the date rather than chosen beside it. Two fields that
                      can disagree is a way to record a Sunday that was a Thursday, and
                      the server refuses that anyway - better not to offer it.
                    */}
                    <select value={weekDay} disabled>
                      <option value="">{sewaDate ? 'Not a seating day' : 'Pick a date'}</option>
                      <option value="SUNDAY">Sunday</option>
                      <option value="THURSDAY">Thursday</option>
                    </select>
                  </Field>
                  <Field label="Badge No" required>
                    <input
                      value={tokenNo}
                      onChange={(e) => setTokenNo(e.target.value.slice(0, 40))}
                      placeholder="T-014"
                      required
                    />
                  </Field>

                </div>

                {/* The buttons are the save, so they sit under the three fields they
                    save rather than beside the last of them, and start at the same
                    left edge - the block reads straight down. */}
                {/*
                  A badge is handed over and then taken back, in that order, so only
                  one of these is ever the thing to do: Issue until it has gone out,
                  Receive until it comes back, neither once the day is done. The
                  server refuses a receive that was never issued, and used to be the
                  only thing that did - the button was live and the answer to
                  pressing it was an error message. Disabling it says the same thing
                  before the press instead of after.
                */}
                <div className="ws-actions">
                  <button
                    type="button"
                    className="btn ok"
                    disabled={!canRecord || dayRow?.badgeIssued}
                    onClick={() => markBadge('ISSUE')}
                  >
                    {acting === 'ISSUE' ? 'Issuing...' : 'Issue Badge'}
                  </button>
                  <button
                    type="button"
                    className="btn danger"
                    disabled={!canRecord || !dayRow?.badgeIssued || dayRow?.badgeReceived}
                    onClick={() => markBadge('RECEIVE')}
                  >
                    {acting === 'RECEIVE' ? 'Receiving...' : 'Receive Badge'}
                  </button>
                </div>
                {/* A greyed-out button with no reason beside it is a dead end. */}
                {canRecord && !dayRow?.badgeIssued && (
                  <p className="hint ws-done">
                    Issue the badge first - it can only be taken back once it has gone out.
                  </p>
                )}
                {dayRow && (dayRow.badgeIssued || dayRow.badgeReceived) && (
                  <p className="hint ws-done">
                    {dayRow.badgeIssued && `Issued ${prettyTime(dayRow.issuedAt)}`}
                    {dayRow.badgeIssued && dayRow.badgeReceived && ' · '}
                    {dayRow.badgeReceived && `Received ${prettyTime(dayRow.receivedAt)}`}
                  </p>
                )}
                {dateProblem ? (
                  <p className="photo-error">
                    {prettyDate(sewaDate)} is not a Sunday or a Thursday.
                  </p>
                ) : (
                  !tokenNo.trim() && (
                    // Two pale buttons with nothing said is a screen that looks
                    // broken. This says which field is still empty.
                    <p className="hint ws-waiting">
                      Enter the Badge No to issue or receive it.
                    </p>
                  )
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {/*
        The people behind a card, under the search rather than above it.
        Opening a count used to push the search box and everything below it off
        the screen; the list is a result, so it reads after the controls like
        every other result on this page.
      */}
        {openCount && (
          <div className="card">
            <div className="card-head">
              <h3>
                {openCount.startsWith('ISSUE') ? 'Issued' : 'Received'} ·{' '}
                {openCount.endsWith('MALE') && !openCount.endsWith('FEMALE')
                  ? 'Male'
                  : 'Female'}{' '}
                · {prettyDate(sewaDate)}
              </h3>
              <button
                type="button"
                className="btn ghost small"
                onClick={() => setOpenCount(null)}
              >
                Close
              </button>
            </div>

            {countLoading ? (
              <Spinner />
            ) : (
              <div className="table-wrap">
                <table className="table-md">
                  <thead>
                    <tr>
                      <th>S.No</th>
                      <th>GR. No</th>
                      <th>Name</th>
                      <th>Mobile No</th>
                      <th>Age</th>
                      <th>Zone</th>
                      <th>Area</th>
                      <th>Badge No</th>
                      {/*
                        This desk's own two times: the badge going out is the check
                        in and coming back is the check out. Not read from the
                        attendance table - somebody marked present for another sewa
                        that day should not have that time turn up in this list.
                      */}
                      <th>Check In</th>
                      <th>Check Out</th>
                      <th className="col-fill" />
                    </tr>
                  </thead>
                  <tbody>
                    {countRows.length === 0 ? (
                      <EmptyRow colSpan={10}>Nobody on this day</EmptyRow>
                    ) : (
                      countRows.map((row, i) => (
                        <tr key={row.sewadarId}>
                          <td>{countPage.page * PAGE_SIZE + i + 1}</td>
                          <td>{row.badgeNumber}</td>
                          <td>{row.name}</td>
                          <td>{row.mobile || '-'}</td>
                          <td>{row.age ?? '-'}</td>
                          <td>{row.zoneName || '-'}</td>
                          <td>{row.area || '-'}</td>
                          <td>
                            <strong>{row.tokenNo}</strong>
                          </td>
                          <td>{prettyTime(row.checkInTime)}</td>
                          <td>{prettyTime(row.checkOutTime)}</td>
                          <td className="col-fill" />
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
                <Pager
                  page={countPage.page}
                  totalPages={countPage.totalPages}
                  totalElements={countPage.totalElements}
                  onChange={(next) => {
                    const [action, gender] = openCount.split('-')
                    openCountDetail(action, gender, next)
                  }}
                />
              </div>
            )}
          </div>
        )}

      {selected && (
        <div className="card">
          <div className="card-head">
            <h3>
              {selected.name} · {selected.badgeNumber}
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
                      <th>Date</th>
                      <th>Weekly</th>
                      <th>Badge No</th>
                      <th>Issue Time</th>
                      <th>Receive Time</th>
                      {canManageBadges && <th>Action</th>}
                      <th className="col-fill" />
                    </tr>
                  </thead>
                  <tbody>
                    {rows.length === 0 ? (
                      <EmptyRow colSpan={columns}>
                        No seating sewa recorded for this sewadar yet
                      </EmptyRow>
                    ) : (
                      rows.map((row, i) => (
                        <tr key={row.id}>
                          <td>{firstSerial + i}</td>
                          <td>{prettyDate(row.sewaDate)}</td>
                          <td>{row.weekDayLabel}</td>
                          <td>
                            <strong>{row.tokenNo}</strong>
                          </td>
                          <td>{prettyTime(row.issuedAt)}</td>
                          <td>{prettyTime(row.receivedAt)}</td>
                          {canManageBadges && (
                            <td>
                              {/*
                                The one thing left to do on this row. A day that has
                                been issued can only be received, and a day that has
                                both needs nothing - so the column shows the next
                                step rather than the same button on every line.
                              */}
                              {!row.badgeIssued ? (
                                <button
                                  type="button"
                                  className="btn ghost small"
                                  disabled={Boolean(acting)}
                                  onClick={() => markRow(row, 'ISSUE')}
                                >
                                  {acting === `row-${row.id}` ? 'Working...' : 'Issue Badge'}
                                </button>
                              ) : !row.badgeReceived ? (
                                <button
                                  type="button"
                                  className="btn ghost small"
                                  disabled={Boolean(acting)}
                                  onClick={() => markRow(row, 'RECEIVE')}
                                >
                                  {acting === `row-${row.id}` ? 'Working...' : 'Receive Badge'}
                                </button>
                              ) : (
                                <span className="muted">Done</span>
                              )}
                            </td>
                          )}
                          <td className="col-fill" />
                        </tr>
                      ))
                    )}
                  </tbody>
                  {total > 0 && (
                    <tfoot>
                      <tr>
                        <td colSpan={columns - 1}>Total seating sewa</td>
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
