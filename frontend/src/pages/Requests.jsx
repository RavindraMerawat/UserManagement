import { useCallback, useEffect, useMemo, useState } from 'react'
import { metaApi, requestApi, sewadarApi, zoneApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import Modal from '../components/Modal'
import Spinner from '../components/Spinner'
import { Badge, EmptyRow, Field, Pager } from '../components/Bits'

export default function Requests() {
  const { isSewadar, canReviewRequests, user } = useAuth()

  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [zones, setZones] = useState([])
  const [options, setOptions] = useState({ requestStatuses: [] })

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const [raising, setRaising] = useState(false)
  const [reviewing, setReviewing] = useState(null)

  const params = useMemo(() => ({ status: status || undefined, page, size: 20 }), [status, page])

  const load = useCallback(() => {
    setLoading(true)
    requestApi
      .list(params)
      .then(setResult)
      .catch((err) => setError(errorMessage(err)))
      .finally(() => setLoading(false))
  }, [params])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    zoneApi.list(false).then(setZones).catch(() => setZones([]))
    metaApi.options().then(setOptions).catch(() => {})
  }, [])

  const onCancel = async (row) => {
    try {
      await requestApi.cancel(row.id)
      setNotice('Request cancelled')
      load()
    } catch (err) {
      setError(errorMessage(err, 'Could not cancel the request'))
    }
  }

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
          <h3>Zone change requests</h3>
          <button type="button" className="btn" onClick={() => setRaising(true)}>
            + Raise a request
          </button>
        </div>
        <div className="filters">
          <Field label="Status">
            <select
              value={status}
              onChange={(e) => {
                setPage(0)
                setStatus(e.target.value)
              }}
            >
              <option value="">All</option>
              {(options.requestStatuses || []).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <p className="muted" style={{ marginTop: 12, marginBottom: 0 }}>
          {isSewadar
            ? 'You can raise a request to move to another zone. An office admin reviews it.'
            : canReviewRequests
              ? 'Approving a request moves the sewadar into the new zone straight away. Past attendance keeps its original zone.'
              : 'You can raise a request for a sewadar in your zone. An office admin approves or rejects it.'}
        </p>
      </div>

      <div className="card">
        {loading ? (
          <Spinner label="Loading requests" />
        ) : (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Raised</th>
                    <th>Sewadar</th>
                    <th>From zone</th>
                    <th>To zone</th>
                    <th>Reason</th>
                    <th>Status</th>
                    <th>Raised by</th>
                    <th>Reviewed</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {result?.content?.length ? (
                    result.content.map((row) => (
                      <tr key={row.id}>
                        <td>{row.requestedAt ? row.requestedAt.slice(0, 10) : '-'}</td>
                        <td>
                          {row.sewadarName}
                          <div className="muted" style={{ fontSize: 11.5 }}>
                            {row.badgeNumber}
                          </div>
                        </td>
                        <td>{row.fromZoneName}</td>
                        <td>
                          <strong>{row.toZoneName}</strong>
                        </td>
                        <td className="muted">{row.reason || '-'}</td>
                        <td>
                          <Badge value={row.status} label={row.statusLabel} />
                        </td>
                        <td className="muted">{row.requestedBy || '-'}</td>
                        <td className="muted">
                          {row.reviewedBy ? (
                            <>
                              {row.reviewedBy}
                              {row.reviewRemarks && (
                                <div style={{ fontSize: 11.5 }}>{row.reviewRemarks}</div>
                              )}
                            </>
                          ) : (
                            '-'
                          )}
                        </td>
                        <td>
                          <div className="btn-row">
                            {row.status === 'PENDING' && canReviewRequests && (
                              <button
                                type="button"
                                className="btn small"
                                onClick={() => setReviewing(row)}
                              >
                                Review
                              </button>
                            )}
                            {row.status === 'PENDING' &&
                              (canReviewRequests ||
                                row.requestedBy?.toLowerCase() === user?.username?.toLowerCase()) && (
                                <button
                                  type="button"
                                  className="btn ghost small"
                                  onClick={() => onCancel(row)}
                                >
                                  Cancel
                                </button>
                              )}
                            {row.status !== 'PENDING' && <span className="muted">-</span>}
                          </div>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <EmptyRow colSpan={9}>No zone change requests</EmptyRow>
                  )}
                </tbody>
              </table>
            </div>
            {result && (
              <Pager
                page={result.page}
                totalPages={result.totalPages}
                totalElements={result.totalElements}
                onChange={setPage}
              />
            )}
          </>
        )}
      </div>

      <RaiseDialog
        open={raising}
        onClose={() => setRaising(false)}
        zones={zones}
        isSewadar={isSewadar}
        onDone={(message) => {
          setNotice(message)
          setRaising(false)
          load()
        }}
      />

      <ReviewDialog
        request={reviewing}
        onClose={() => setReviewing(null)}
        onDone={(message) => {
          setNotice(message)
          setReviewing(null)
          load()
        }}
      />
    </div>
  )
}

function RaiseDialog({ open, onClose, zones, isSewadar, onDone }) {
  const [form, setForm] = useState({ sewadarId: '', toZoneId: '', reason: '' })
  const [sewadars, setSewadars] = useState([])
  const [me, setMe] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!open) return
    setError('')
    if (isSewadar) {
      sewadarApi.me().then(setMe).catch(() => setMe(null))
    } else {
      // A supervisor or incharge picks from the sewadars they can reach.
      sewadarApi
        .search({ active: true, size: 200, sortBy: 'name', direction: 'asc' })
        .then((res) => setSewadars(res.content || []))
        .catch(() => setSewadars([]))
    }
  }, [open, isSewadar])

  const onSubmit = async (event) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await requestApi.create({
        sewadarId: isSewadar ? null : Number(form.sewadarId),
        toZoneId: Number(form.toZoneId),
        reason: form.reason || null,
      })
      setForm({ sewadarId: '', toZoneId: '', reason: '' })
      onDone('Zone change request raised. The office team has been notified.')
    } catch (err) {
      setError(errorMessage(err, 'Could not raise the request'))
    } finally {
      setBusy(false)
    }
  }

  const currentZoneId = isSewadar
    ? me?.zoneId
    : sewadars.find((s) => String(s.id) === String(form.sewadarId))?.zoneId

  return (
    <Modal
      narrow
      title="Raise a zone change request"
      open={open}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form="raise-form" className="btn" disabled={busy}>
            {busy ? 'Sending...' : 'Submit request'}
          </button>
        </>
      }
    >
      <form id="raise-form" onSubmit={onSubmit} style={{ display: 'grid', gap: 14 }}>
        <Alert kind="error">{error}</Alert>

        {isSewadar ? (
          <p style={{ margin: 0 }}>
            Requesting for <strong>{me?.name || 'you'}</strong>, currently in{' '}
            <strong>{me?.zoneName || '-'}</strong>.
          </p>
        ) : (
          <Field label="Sewadar" required>
            <select
              value={form.sewadarId}
              onChange={(e) => setForm({ ...form, sewadarId: e.target.value })}
              required
            >
              <option value="">Select a sewadar</option>
              {sewadars.map((sewadar) => (
                <option key={sewadar.id} value={sewadar.id}>
                  {sewadar.name} ({sewadar.badgeNumber}) - {sewadar.zoneName}
                </option>
              ))}
            </select>
          </Field>
        )}

        <Field label="Move to zone" required>
          <select
            value={form.toZoneId}
            onChange={(e) => setForm({ ...form, toZoneId: e.target.value })}
            required
          >
            <option value="">Select the new zone</option>
            {zones
              .filter((zone) => String(zone.id) !== String(currentZoneId))
              .map((zone) => (
                <option key={zone.id} value={zone.id}>
                  {zone.name}
                </option>
              ))}
          </select>
        </Field>

        <Field label="Reason">
          <textarea
            value={form.reason}
            onChange={(e) => setForm({ ...form, reason: e.target.value })}
            placeholder="Shifted residence, sewa requirement, and so on"
          />
        </Field>
      </form>
    </Modal>
  )
}

function ReviewDialog({ request, onClose, onDone }) {
  const [decision, setDecision] = useState('APPROVED')
  const [remarks, setRemarks] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    setDecision('APPROVED')
    setRemarks('')
    setError('')
  }, [request])

  const onSubmit = async (event) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await requestApi.review(request.id, { decision, remarks: remarks || null })
      onDone(
        decision === 'APPROVED'
          ? `${request.sewadarName} moved to ${request.toZoneName}.`
          : `Request for ${request.sewadarName} rejected.`,
      )
    } catch (err) {
      setError(errorMessage(err, 'Could not record the decision'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      narrow
      title="Review request"
      open={Boolean(request)}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn ghost" onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            form="review-form"
            className={decision === 'APPROVED' ? 'btn ok' : 'btn danger'}
            disabled={busy}
          >
            {busy ? 'Saving...' : decision === 'APPROVED' ? 'Approve' : 'Reject'}
          </button>
        </>
      }
    >
      {request && (
        <form id="review-form" onSubmit={onSubmit} style={{ display: 'grid', gap: 14 }}>
          <Alert kind="error">{error}</Alert>
          <dl className="kv">
            <dt>Sewadar</dt>
            <dd>
              {request.sewadarName} ({request.badgeNumber})
            </dd>
            <dt>From zone</dt>
            <dd>{request.fromZoneName}</dd>
            <dt>To zone</dt>
            <dd>
              <strong>{request.toZoneName}</strong>
            </dd>
            <dt>Reason</dt>
            <dd>{request.reason || '-'}</dd>
            <dt>Raised by</dt>
            <dd>{request.requestedBy}</dd>
          </dl>

          <Field label="Decision" required>
            <select value={decision} onChange={(e) => setDecision(e.target.value)}>
              <option value="APPROVED">Approve and move the sewadar</option>
              <option value="REJECTED">Reject</option>
            </select>
          </Field>
          <Field label="Remarks">
            <textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </Field>
        </form>
      )}
    </Modal>
  )
}
