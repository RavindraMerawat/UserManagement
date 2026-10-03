import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { sewadarApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Spinner from '../components/Spinner'
import { Avatar } from '../components/Photo'
import { EmptyRow, Pager } from '../components/Bits'
import { PAGE_SIZE } from '../pageSize'

/**
 * The people behind a dashboard tile.
 *
 * <p>The list comes from `/api/sewadars/by-status`, which runs the same query the
 * tile was counted with - so the row count always matches the number that was just
 * tapped - and is narrowed to the caller's zones on the server. A Zone Incharge
 * arriving here sees their zones and nothing else, whatever the URL says.</p>
 */

/** The four tiles, and what each one asks the server for. */
const METRICS = {
  total: { status: null, title: 'All Sewadars', blurb: 'Every active sewadar you can see' },
  present: { status: 'PRESENT', title: 'Present Today', blurb: 'Marked present today' },
  leave: { status: 'LEAVE', title: 'On Leave', blurb: 'Marked on leave today' },
  absent: { status: 'ABSENT', title: 'Absent Today', blurb: 'Marked absent today' },
}

/**
 * 8.75 -> 08:45. The day's hours as a clock reads them.
 *
 * <p>A dash where there is no check out yet: the hours are the distance between
 * check in and check out, so until the second one exists there is no number to
 * show, and a 0 would read as "was here and did nothing".</p>
 */
function hoursLabel(hours) {
  if (hours == null) return '-'
  const total = Math.round(hours * 60)
  const h = Math.floor(total / 60)
  const m = total % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

const GENDER_LABEL = { MALE: 'Male', FEMALE: 'Female' }
const LOCALITY_LABEL = { LOCAL: 'Local', OUTSTATION: 'Outstation' }


export default function SewadarList() {
  const [params, setParams] = useSearchParams()
  const { user } = useAuth()

  const key = params.get('metric') || 'total'
  const metric = METRICS[key] || METRICS.total
  // The dashboard card that opened this list may have counted one gender only.
  const gender = params.get('gender') || ''
  const locality = params.get('locality') || ''
  const page = Number(params.get('page') || 0)

  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    sewadarApi
      .byStatus({
        status: metric.status || undefined,
        gender: gender || undefined,
        locality: locality || undefined,
        page,
        size: PAGE_SIZE,
      })
      .then(setResult)
      .catch((err) => setError(errorMessage(err, 'Could not load the list')))
      .finally(() => setLoading(false))
  }, [metric.status, gender, locality, page])

  useEffect(load, [load])

  const goToPage = (next) => {
    const updated = new URLSearchParams(params)
    updated.set('page', String(next))
    setParams(updated)
  }

  return (
    <div>
      <div className="list-head">
        <div>
          <h2 className="list-title">
            {metric.title}
            {/* The card that was tapped, said back: "All Sewadars · Local · Male". */}
            {locality && (
              <span className="list-title-qualifier"> · {LOCALITY_LABEL[locality] || locality}</span>
            )}
            {gender && <span className="list-title-qualifier"> · {GENDER_LABEL[gender] || gender}</span>}
          </h2>
          <p className="list-sub">
            {metric.blurb}
            {' · '}
            {/* The server decides the scope; this only says which scope was applied. */}
            {user?.zoneNames?.length ? user.zoneNames.join(', ') : 'All zones'}
          </p>
        </div>
        <Link className="btn ghost" to="/">
          &larr; Back to dashboard
        </Link>
      </div>

      <Alert kind="error" onClose={() => setError('')}>
        {error}
      </Alert>

      {loading ? (
        <Spinner label="Loading the list" />
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th style={{ width: 62 }}>Photo</th>
                  <th>GR. No</th>
                  <th>Name</th>
                  <th>F/H Name</th>
                  <th>Mobile No</th>
                  <th>Zone</th>
                  <th>Area</th>
                  {/* Check in to check out for the day this tile counted. */}
                  <th>Today Hours</th>
                </tr>
              </thead>
              <tbody>
                {result?.content?.length ? (
                  result.content.map((row) => (
                    <tr key={row.id}>
                      <td>
                        <Avatar
                          kind="sewadars"
                          id={row.id}
                          stamp={row.photoUpdatedAt}
                          name={row.name}
                          size={38}
                        />
                      </td>
                      <td>{row.badgeNumber}</td>
                      <td>{row.name}</td>
                      <td>{row.fatherOrHusbandName || '-'}</td>
                      <td>{row.mobile || '-'}</td>
                      <td>{row.zoneName || '-'}</td>
                      <td>{row.area || '-'}</td>
                      <td>{hoursLabel(row.hoursOnDate)}</td>
                    </tr>
                  ))
                ) : (
                  <EmptyRow colSpan={8}>
                    No sewadars {metric.status ? 'with this status today' : 'to show'}.
                  </EmptyRow>
                )}
              </tbody>
            </table>
          </div>

          {result && (
            <Pager
              page={result.page}
              totalPages={result.totalPages}
              totalElements={result.totalElements}
              onChange={goToPage}
            />
          )}
        </>
      )}
    </div>
  )
}
