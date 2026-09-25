import { useCallback, useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { sewadarApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Spinner from '../components/Spinner'
import { Avatar } from '../components/Photo'
import { EmptyRow, Pager } from '../components/Bits'

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

/** Whole years, counting a birthday that has not arrived this year as not yet had. */
function age(dateOfBirth) {
  if (!dateOfBirth) return '-'
  const birth = new Date(`${dateOfBirth}T00:00:00`)
  if (Number.isNaN(birth.getTime())) return '-'
  const today = new Date()
  const years = today.getFullYear() - birth.getFullYear()
  const beforeBirthday =
    today.getMonth() < birth.getMonth() ||
    (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate())
  return years - (beforeBirthday ? 1 : 0)
}

const GENDER_LABEL = { MALE: 'Male', FEMALE: 'Female' }

const PAGE_SIZE = 25

export default function SewadarList() {
  const [params, setParams] = useSearchParams()
  const { user } = useAuth()

  const key = params.get('metric') || 'total'
  const metric = METRICS[key] || METRICS.total
  // The dashboard card that opened this list may have counted one gender only.
  const gender = params.get('gender') || ''
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
        page,
        size: PAGE_SIZE,
      })
      .then(setResult)
      .catch((err) => setError(errorMessage(err, 'Could not load the list')))
      .finally(() => setLoading(false))
  }, [metric.status, gender, page])

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
                  <th>Badge No</th>
                  <th>Name</th>
                  <th>F/H Name</th>
                  <th>Mobile No</th>
                  <th>Age</th>
                  <th>Zone</th>
                  <th>Area</th>
                  <th>Point</th>
                  <th>Blood Group</th>
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
                      <td>{age(row.dateOfBirth)}</td>
                      <td>{row.zoneName || '-'}</td>
                      <td>{row.area || '-'}</td>
                      <td>{row.centerPoint || '-'}</td>
                      <td>{row.bloodGroup || '-'}</td>
                    </tr>
                  ))
                ) : (
                  <EmptyRow colSpan={10}>
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
