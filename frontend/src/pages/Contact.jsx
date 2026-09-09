import { useState } from 'react'
import { metaApi } from '../api/endpoints'
import { errorMessage } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Alert from '../components/Alert'
import { Field } from '../components/Bits'

export default function Contact() {
  const { user } = useAuth()
  const [form, setForm] = useState({
    name: user?.fullName || '',
    email: user?.email || '',
    mobile: '',
    subject: '',
    message: '',
  })
  const [sending, setSending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const onSubmit = async (event) => {
    event.preventDefault()
    setSending(true)
    setError('')
    try {
      const result = await metaApi.contact({ ...form, mobile: form.mobile || null })
      setNotice(result.message)
      setForm({ ...form, subject: '', message: '' })
    } catch (err) {
      setError(errorMessage(err, 'Could not send your message'))
    } finally {
      setSending(false)
    }
  }

  const set = (key) => (event) => setForm({ ...form, [key]: event.target.value })

  return (
    <div className="grid-2">
      <div className="card">
        <h3>Send a message to the office</h3>
        <Alert kind="error" onClose={() => setError('')}>
          {error}
        </Alert>
        <Alert kind="success" onClose={() => setNotice('')}>
          {notice}
        </Alert>

        <form onSubmit={onSubmit} style={{ display: 'grid', gap: 14 }}>
          <Field label="Your name" required>
            <input value={form.name} onChange={set('name')} required />
          </Field>
          <Field label="Email" required>
            <input type="email" value={form.email} onChange={set('email')} required />
          </Field>
          <Field label="Mobile">
            <input value={form.mobile} onChange={set('mobile')} placeholder="9876543210" />
          </Field>
          <Field label="Subject" required>
            <input value={form.subject} onChange={set('subject')} required />
          </Field>
          <Field label="Message" required>
            <textarea
              value={form.message}
              onChange={set('message')}
              required
              style={{ minHeight: 130 }}
            />
          </Field>
          <div className="btn-row">
            <button type="submit" className="btn" disabled={sending}>
              {sending ? 'Sending...' : 'Send message'}
            </button>
          </div>
        </form>
      </div>

      <div>
        <div className="card">
          <h3>Office contact</h3>
          <dl className="kv">
            <dt>Sewa office</dt>
            <dd>Main Centre, Sewadar Coordination Desk</dd>
            <dt>Office hours</dt>
            <dd>Monday to Saturday, 9:00 AM to 6:00 PM</dd>
            <dt>Email</dt>
            <dd>
              <a href="mailto:office@sewa.local">office@sewa.local</a>
            </dd>
            <dt>Phone</dt>
            <dd>+91 98765 43210</dd>
          </dl>
        </div>

        <div className="card">
          <h3>Who to reach for what</h3>
          <ul style={{ margin: 0, paddingLeft: 20, lineHeight: 1.9 }}>
            <li>
              <strong>Wrong attendance</strong> - your zone incharge or supervisor can correct an
              entry
            </li>
            <li>
              <strong>Zone change</strong> - raise it on the Request screen; the office team reviews
              it
            </li>
            <li>
              <strong>Badge or profile details</strong> - the office admin maintains the register
            </li>
            <li>
              <strong>Login trouble</strong> - the admin can reset your password
            </li>
          </ul>
        </div>
      </div>
    </div>
  )
}
