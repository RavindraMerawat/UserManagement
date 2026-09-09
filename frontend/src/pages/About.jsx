import { useAuth } from '../auth/AuthContext'

const ROLE_TABLE = [
  {
    role: 'Admin',
    data: 'Every zone, every sewadar',
    actions: 'Full control including login accounts and zones',
  },
  {
    role: 'Office Admin',
    data: 'Every zone, every sewadar',
    actions: 'Sewadar CRUD, attendance, reports, approve zone changes',
  },
  {
    role: 'Co-ordinator',
    data: 'Only the zones assigned to the account',
    actions: 'Mark and update attendance, raise zone change requests, reports',
  },
  {
    role: 'Zone Incharge',
    data: 'Only the zones assigned to the account',
    actions: 'Mark and update attendance, raise zone change requests, reports',
  },
  {
    role: 'Supervisor',
    data: 'Only the zones assigned to the account',
    actions: 'Mark and update attendance, raise zone change requests, reports',
  },
  {
    role: 'Office User',
    data: 'Every zone, read only',
    actions: 'View sewadars and attendance, generate and share reports',
  },
  {
    role: 'Sewadar',
    data: 'Only their own records',
    actions: 'View own attendance and reports, raise a zone change request',
  },
]

export default function About() {
  const { user } = useAuth()

  return (
    <div>
      <div className="card">
        <h3>About this application</h3>
        <p>
          This system keeps the sewadar register and the daily sewa attendance for every zone. It
          records <strong>roster sewa</strong>, <strong>construction sewa</strong> and office sewa,
          rolls them up into monthly reports, and shares those reports over email and WhatsApp.
        </p>
        <p className="muted" style={{ marginBottom: 0 }}>
          Spring Boot 3 on Java 21 with MySQL and a JWT secured REST API, React on the front end.
          The interactive API documentation lives at{' '}
          <a href="/swagger-ui.html" target="_blank" rel="noreferrer">
            /swagger-ui.html
          </a>
          .
        </p>
      </div>

      <div className="card">
        <h3>What each role can see and do</h3>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Role</th>
                <th>Data it can reach</th>
                <th>What it can do</th>
              </tr>
            </thead>
            <tbody>
              {ROLE_TABLE.map((row) => (
                <tr
                  key={row.role}
                  style={
                    row.role === user?.roleDisplayName
                      ? { background: '#fffbeb', fontWeight: 600 }
                      : undefined
                  }
                >
                  <td>{row.role}</td>
                  <td>{row.data}</td>
                  <td>{row.actions}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="muted" style={{ marginTop: 12, marginBottom: 0 }}>
          Your row is highlighted. Every list, report and export is filtered on the server by the
          role on your account, so a screen can never show data outside your reach.
        </p>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3>Modules</h3>
          <ul style={{ margin: 0, paddingLeft: 20, lineHeight: 1.9 }}>
            <li>
              <strong>Sewadar</strong> - add, edit, deactivate and search the sewadar register
            </li>
            <li>
              <strong>Attendance</strong> - mark a whole sewa sheet at once or edit a single entry
            </li>
            <li>
              <strong>Report</strong> - monthly, roster sewa, construction sewa and custom range
            </li>
            <li>
              <strong>Request</strong> - zone change requests with an approval trail
            </li>
            <li>
              <strong>Contact</strong> - reach the office team from inside the app
            </li>
          </ul>
        </div>
        <div className="card">
          <h3>Sharing a report</h3>
          <p style={{ marginTop: 0 }}>
            Any report can be downloaded as an Excel workbook or CSV, mailed as an HTML table with
            the workbook attached, or sent to WhatsApp as a short summary with the top ten sewadars.
          </p>
          <p className="muted" style={{ marginBottom: 0 }}>
            Both channels stay off until the server is configured with mail credentials and a
            WhatsApp Cloud API token. Until then a share is accepted and logged, and the response
            says so.
          </p>
        </div>
      </div>
    </div>
  )
}
