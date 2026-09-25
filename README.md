# Pandal Office Management

*People · Service · Community*

Role based application for the sewadar register, daily sewa attendance (roster sewa,
construction sewa and office sewa), monthly reports, zone change requests, and report
sharing over email and WhatsApp.

- **Backend** — Spring Boot 3.3 on **Java 21**, Spring Security with JWT, Spring Data JPA, MySQL, Swagger/OpenAPI, Apache POI for Excel
- **Frontend** — React 19 + Vite 6, React Router 7, Axios
- **Database** — MySQL 8

```
UserManagement/
├── backend/          Spring Boot REST API  -> run in IntelliJ IDEA
├── frontend/         React single page app -> run in VS Code
├── db/               Reference SQL schema
├── package.json      Front end shortcuts (npm start)
├── README.md         This file
├── REQUIREMENTS.md   Every requirement, with status and where it is implemented
└── CHANGELOG.md      Every change made, and why
```

> Picking the project up cold, or reviewing what was asked for?
>
> - **[REQUIREMENTS.md](REQUIREMENTS.md)** — every requirement with an ID and a
>   status, the role access matrix, assumptions taken, what was deliberately not
>   built, and the open questions.
> - **[CHANGELOG.md](CHANGELOG.md)** — the history: each change set, the bugs found
>   along the way, and the reasoning behind the design decisions.

## Quick start

The two halves are developed in different IDEs:

| Part | Where it runs | How |
|---|---|---|
| **Backend** (Spring Boot) | **IntelliJ IDEA only** | Run `UserManagementApplication` |
| **Frontend** (React) | VS Code | `npm start` from the repository root |

Start MySQL first, then the backend in IntelliJ, then the frontend.

### 1. Backend, in IntelliJ IDEA

1. **File > Open** and select the `backend` folder (or `backend/pom.xml`). Open
   `backend`, not the repository root, so IntelliJ treats it as the Maven project.
2. **File > Project Structure > Project**: set the **SDK to 21** and the language
   level to 21.
3. Let Maven finish importing (the elephant icon in the Maven tool window reloads it).
4. Pick **UserManagementApplication** in the run configuration dropdown and hit
   **Run** (Shift+F10). The configuration is checked in at
   [backend/.run/UserManagementApplication.run.xml](backend/.run/UserManagementApplication.run.xml),
   so it should already be there.
5. Put your MySQL password and any email or WhatsApp credentials in **Edit
   Configurations > Environment variables** on that run configuration.

The API comes up on <http://localhost:8080>, Swagger on
<http://localhost:8080/swagger-ui.html>.

Run the tests from IntelliJ too: right-click `RoleScopeTest` and **Run**, or use the
Maven tool window > **Lifecycle > test**.

> Lombok is used throughout. IntelliJ bundles the Lombok plugin, but if entity
> getters and builders show as unresolved, enable **Settings > Build, Execution,
> Deployment > Compiler > Annotation Processors > Enable annotation processing**.

### 2. Frontend, in VS Code

From the **repository root**:

```powershell
npm run setup     # installs the frontend dependencies (once)
npm start         # starts the Vite dev server
```

| Command | What it does |
|---|---|
| `npm start` | Vite dev server on <http://localhost:5173> (`npm run dev` is an alias) |
| `npm run build` | production bundle into `frontend/dist` |
| `npm run preview` | serves the built bundle |

These only ever touch the front end. `vite.config.js` proxies `/api` to
`localhost:8080`, so the backend must already be running in IntelliJ.

Open <http://localhost:5173> and sign in as `admin` / `Admin@123`, then change the
password when prompted.

### Backend packages

Base package **`com.user.management`**:

```
com.user.management
├── UserManagementApplication      entry point
├── controller/                    REST endpoints
│   ├── AuthorizationController      /api/auth/**  login, profile, dashboard, password
│   ├── SewadarController            /api/sewadars/**
│   ├── AttendanceController         /api/attendance/**
│   ├── ReportController             /api/reports/**
│   ├── ZoneChangeRequestController  /api/requests/zone-change/**
│   ├── ZoneController               /api/zones/**
│   ├── UserController               /api/users/**
│   └── MetaController               /api/meta/**  dropdowns, channels, contact
├── entity/                        JPA entities and the enums they persist
│   ├── User, Sewadar, Zone, Attendance, ZoneChangeRequest, Auditable
│   └── Role (7 roles), SewaType, AttendanceStatus, RequestStatus, Gender
├── model/                         request and response payloads (Java records)
├── config/                        SecurityConfig, OpenApiConfig, AppProperties, AuditorConfig
├── security/                      JWT, principal, DataScope, CurrentUserService
├── service/                       business logic
├── repository/                    Spring Data JPA, with projection/ for report rows
├── integration/                   Email, WhatsApp, NotificationService
├── report/                        ReportExporter (Excel, CSV, HTML, WhatsApp text)
├── exception/                     domain exceptions and GlobalExceptionHandler
└── bootstrap/                     DataBootstrap, first-run seeding
```

The enums live in `entity` rather than `model` so the dependency direction stays
one-way: `controller` → `service` → `repository` → `entity`, and `model` depends on
`entity` for the enum types it exposes, never the reverse.

---

## 1. Roles and what each one can reach

| Role | Data it can see | What it can do |
|---|---|---|
| **ADMIN** | Every zone, every sewadar | Everything, plus login accounts and zones |
| **OFFICE_ADMIN** | Every zone, every sewadar | Sewadar CRUD, attendance, reports, approve zone changes, zones, login accounts\* |
| **COORDINATOR** | Only the zones assigned to the account | Mark and update attendance, raise zone change requests, reports |
| **ZONE_INCHARGE** | Only the zones assigned to the account | Mark and update attendance, raise zone change requests, reports |
| **SUPERVISOR** | Only the zones assigned to the account | Mark and update attendance, raise zone change requests, reports |
| **OFFICE_USER** | Every zone, read only | View sewadars and attendance, generate and share reports |
| **SEWADAR** | **Only their own records** | View own attendance and reports, raise a zone change request |

The rule lives in one place on the server: `CurrentUserService.scope()` turns the
signed-in role into a [`DataScope`](backend/src/main/java/com/user/management/security/DataScope.java),
and every repository query takes that scope as a parameter. A `null` zone list means
"all zones", a populated one means "these zones only", and a `sewadarId` means "this
sewadar's own rows only". The React side repeats the check to hide menu items and
guard routes, but it is never the thing that enforces it.

\* An Office Admin may administer login accounts but **not** an ADMIN account, and
cannot give any account the ADMIN role. Otherwise opening account administration to
Office Admin would be a way to promote yourself to Admin.

There is no SuperAdmin role: **ADMIN is the super admin**.

`RoleScopeTest` asserts this for all six roles.

---

## 2. Prerequisites

| Tool | Version | Used by | Note |
|---|---|---|---|
| IntelliJ IDEA | 2023.1+ | backend | Community Edition is fine |
| JDK | **21** | backend | This machine has it at `C:\Program Files\Java\jdk-21` |
| Node.js | 18+ / 20 / 22+ | frontend | Installed: v20.10.0. See the note below |
| MySQL | 8.x | backend | Must be running before the backend starts |

> **Node version ceiling.** Vite 7 and 8 require Node `^20.19.0 || >=22.12.0`, and
> this machine has **v20.10.0**, so the front end is pinned to **Vite 6** and
> **@vitejs/plugin-react 4**, which fully support it. React itself is on the latest
> **19.2.x** either way. Upgrading Node to 20.19+ or 22 LTS would let you move to
> Vite 8 and plugin-react 6; nothing in the app code needs to change for that.

The backend is built and run by IntelliJ, so set **JDK 21 as the project SDK**
there (**File > Project Structure > Project > SDK**). IntelliJ ships its own
Maven, so you do not need Maven on your PATH and you never have to touch
`JAVA_HOME`.

> For reference: `JAVA_HOME` on this machine points at **JDK 8**, which cannot
> build this project. That only matters if you run Maven from a terminal
> yourself. IntelliJ uses its project SDK instead and is unaffected.

---

## 3. Database

The backend creates the schema itself. You only need the database to exist, and even
that is handled by `createDatabaseIfNotExist=true` in the JDBC URL. To create it by
hand:

```sql
CREATE DATABASE IF NOT EXISTS sewa_ums
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Tables are created by
**[backend/src/main/resources/db/schema.sql](backend/src/main/resources/db/schema.sql)**,
which runs on startup: `zones`, `users`, `user_zones`, `sewadars`, `attendance`,
`zone_change_requests`, `photos`.

Hibernate does **not** create the schema. It runs at `ddl-auto=validate` and checks
the entities against that script, refusing to start if they have drifted apart.

- **Why a script.** Hibernate appends every new column to the end of a table and has
  no concept of column order. The order is deliberate - identity, then the record,
  then flags and links, then `createdAt, createdBy, updatedAt, updatedBy` as the last
  four columns of every table - and a script is the only way to get it.
- **Column names are camelCase**, matching the Java field: `badgeNo`, `mobileNo`,
  `emailId`, `aadharNo`, `createdAt`. This needs
  `hibernate.naming.physical-strategy: PhysicalNamingStrategyStandardImpl`, because
  Spring's default rewrites even an explicit `@Column(name = "badgeNo")` to
  `badge_no`.
- **Every statement is `CREATE TABLE IF NOT EXISTS`.** The first start builds the
  schema, every start after it does nothing, and your data survives a restart.
- **Nothing in the application ever drops a table.** That is on purpose: a script
  that drops on boot is one stray restart away from deleting everything.

### Starting over

```sql
DROP DATABASE office_management;
```

Start the backend again. The script recreates the schema and `DataBootstrap` seeds
the four zones and the first admin account.

> Adding a column? Add it to the entity **and** to `schema.sql` in the position you
> want it, before the audit four. `validate` will tell you at startup if you forget
> one of the two. Changing an existing column means writing the `ALTER` yourself -
> this is a single schema file, not a migration history.

### Aadhaar numbers are masked by role

`sewadars.aadhar_number` holds the 12 digits as typed. What protects them is
**masking on the server**: only the roles that register and correct a number ever
receive all twelve.

| Role | Sees |
|---|---|
| Admin, Office Admin | all 12 digits |
| Sewadar | their own number in full |
| Co-ordinator, Zone Incharge, Supervisor, Office User | `XXXX XXXX 9012` |

The decision is `CurrentUserService.canViewFullAadhar(sewadarId)`, next to every other
role rule, and it is applied before the response leaves the server - so a masked
response never carries the other eight digits and no UI mistake can expose them. It
covers the grid, the detail view and the Mark Attendance card alike.

> **This is access control, not encryption.** Anyone who can read the database table -
> a DBA, a backup file, a stolen dump - sees the numbers. Encryption at rest was built
> and then deliberately dropped; [CHANGELOG](CHANGELOG.md) change set 11 records what
> that decision costs and what it buys, and what to re-read if you ever want it back.

---

## 4. Running the backend (IntelliJ)

Pick **UserManagementApplication** in the run configuration dropdown and press
**Run** (Shift+F10). Nothing about the backend starts from VS Code or from an npm
script.

Connection settings default to `localhost:3306`, `root`/`root`, database
`sewa_ums`. To point somewhere else open **Run > Edit Configurations >
UserManagementApplication > Environment variables** and set:

| Variable | Default |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `sewa_ums` |
| `DB_USER` | `root` |
| `DB_PASSWORD` | `root` |
| `ADMIN_PASSWORD` | `Admin@123` |

For verbose SQL logging set **Active profiles** to `dev` on the same
configuration.

The API comes up on <http://localhost:8080>.

**Swagger UI:** <http://localhost:8080/swagger-ui.html>
**OpenAPI JSON:** <http://localhost:8080/v3/api-docs>

### First sign in

On an empty database the app seeds four zones and one admin account:

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` (or the `ADMIN_PASSWORD` env var) | ADMIN |

It is flagged *must change password*, so the UI sends you straight to the Profile
screen. Change it before doing anything else.

To get a token from Swagger: run **POST /api/auth/login**, copy the `token` from the
response, click **Authorize** at the top right and paste it.

---

## 5. Running the frontend (VS Code)

```powershell
cd D:\Handson\UserManagement
npm run setup    # once
npm start
```

`npm start` and `npm run dev` are the same thing, and both work from inside
`frontend/` as well.

Opens on <http://localhost:5173>. `vite.config.js` proxies `/api` to
`localhost:8080`, so there are no CORS surprises in development. For a different
backend host, copy `.env.example` to `.env` and set `VITE_API_BASE_URL`.

Production bundle:

```powershell
npm run build     # writes frontend/dist
```

---

## 6. Screens (left-hand menu)

The menu, in order:

| Screen | Route | Visible to |
|---|---|---|
| Dashboard | `/` | everyone — figures scoped to the role; the tiles open the people behind them |
| Sewadar | `/sewadars` | all except SEWADAR — search, add, edit, delete |
| Attendance | `/attendance` | everyone — four tabs, below |
| Badge Detail | `/badges` | everyone — issue and collect is ADMIN, OFFICE_ADMIN and OFFICE_USER; the zone roles read only |
| Report | `/reports` | everyone — monthly, roster sewa, construction sewa, custom range |
| Request | `/requests` | everyone — raise, review and cancel zone changes |
| User Account | `/users` | ADMIN, OFFICE_ADMIN — only an ADMIN may touch an ADMIN account |
| Setup | `/setup` | ADMIN, OFFICE_ADMIN — zones, areas and satsang points |
| Contact | `/contact` | everyone — message to the office admins |

Reached from elsewhere rather than the menu: `/profile` (My Profile), `/about`,
`/sewadar-list` (a dashboard tile), `/login`.

The menu is built from the `menu` array in the login response, so the server decides
what appears; the client repeats the check on each route so typing a URL cannot get
past it.

### The four attendance tabs

| Tab | For | Who |
|---|---|---|
| Mark Attendance | the person in front of you, now — one press, the clock is the record | markers |
| Zone Attendance | a whole zone at once, ticked and checked in or out together | markers |
| Manage Past Attendance | a day that was missed, with the times typed by hand | markers |
| Records | everything on file, searchable and editable | everyone (*My Attendance* for a SEWADAR) |

**Manage Past Attendance** accepts the **current month's past days only** — the first
of the month through yesterday. Today belongs to Mark Attendance, where the clock is
the record. The rule is enforced on the picker, on a typed date and again on save,
and the allowed range is printed under the field. It is a screen policy: the server
still accepts any non-future date, so a direct API call is not bound by it.

Times already on file arrive filled in and locked, so only the missing half is
editable — enter the check out for a day that has only a check in, and one **Save
Attendance** press writes whichever halves are missing.

### Sewadar registration form

The add and edit form carries these fields, in this order:

| Field | Notes |
|---|---|
| Badge Number | required, unique - every attendance and report row keys off it |
| Name | required |
| F/H Name | father or husband name |
| Birth Date | |
| Mobile No | |
| Zone | required |
| Address | |
| Aadhaar No | 12 digits, unique where present; spaces and dashes are stripped on save |
| Blood Group | picker: A+, A-, B+, B-, O+, O-, AB+, AB- |
| Area | area inside the zone |
| Center / Point | the center or point the sewadar reports to |

Behind an **Additional details** toggle: Department, Primary sewa type, Gender,
Email, City, Pincode, Joining date. These are kept because the reports read
`department`, and they stay out of the way so the main form matches the
registration slip.

Free text search covers name, F/H name, badge number, mobile, area, center and
department. **View** shows the whole record read-only; **Edit** opens the same form
with everything populated.

Aadhaar numbers are personal data. They are stored as plain digits and are not shown
in the list, only on the form and the detail view. If your rules require them
encrypted at rest or masked for some roles, say so and it can be added.

### Marking attendance

The **Mark attendance** tab loads every active sewadar in the chosen zone, defaults
everyone to *Present*, and saves the sheet in one call
(`POST /api/attendance/bulk`). In and out time on the header apply to the whole
sheet and produce the sewa hours. The unique key is
*(sewadar, date, sewa type)*, so re-saving the same sheet updates rather than
duplicates.

### Reports

Every report is the same aggregation with a different filter:

- `GET /api/reports/monthly?year=&month=` — all sewa types
- `GET /api/reports/roster-sewa?year=&month=` — roster sewa only
- `GET /api/reports/construction-sewa?year=&month=` — construction sewa only
- `GET /api/reports/range?fromDate=&toDate=` — arbitrary window

Each returns per-sewadar rows (present, half day, leave, absent, roster days,
construction days, hours, effective days, attendance %) plus period totals and
status/sewa-type breakdowns. Add `/excel` or `/csv` to any of them to download.

---

## 7. Email and WhatsApp sharing

Both channels are **off by default**. While off, a share request is accepted, the
message is written to the log, and the response carries a warning saying it was not
delivered — so the flow is testable without credentials.

Set these in IntelliJ under **Run > Edit Configurations >
UserManagementApplication > Environment variables** (one per line, or paste the
whole `NAME=value;NAME=value` string into the field).

### Email (SMTP)

| Variable | Example |
|---|---|
| `EMAIL_ENABLED` | `true` |
| `MAIL_HOST` | `smtp.gmail.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | `you@gmail.com` |
| `MAIL_PASSWORD` | your 16 character Gmail **app password**, not the login password |
| `MAIL_FROM` | `you@gmail.com` |

The mail carries the report as an HTML table (first 100 rows) with the full Excel
workbook attached.

### WhatsApp (Meta WhatsApp Cloud API)

| Variable | Example |
|---|---|
| `WHATSAPP_ENABLED` | `true` |
| `WHATSAPP_PHONE_NUMBER_ID` | your phone number id |
| `WHATSAPP_ACCESS_TOKEN` | your permanent access token |
| `WHATSAPP_COUNTRY_CODE` | `91` |

Get these from **Meta for Developers → your app → WhatsApp → API Setup**. Ten digit
numbers are automatically prefixed with the country code.

One Cloud API rule to know: a **free-form text** message only reaches a number that
messaged your business in the last 24 hours. Outside that window Meta requires an
approved **template**, which `WhatsAppService.sendTemplate(...)` covers — create the
template in Meta Business Manager first.

Notifications also fire on their own for zone change requests: the office admins are
mailed when one is raised, and the sewadar is mailed and messaged when it is decided.

---

## 8. API summary

| Group | Endpoints |
|---|---|
| Authorization | `POST /api/auth/login`, `GET /api/auth/me`, `GET /api/auth/dashboard`, `POST /api/auth/change-password` |
| Zones | `GET/POST /api/zones`, `GET/PUT/DELETE /api/zones/{id}` |
| Sewadars | `GET/POST /api/sewadars`, `GET/PUT/DELETE /api/sewadars/{id}`, `GET /api/sewadars/me`, `GET /api/sewadars/for-attendance`, `GET /api/sewadars/by-status` (the people behind a dashboard tile) |
| Attendance | `GET/POST /api/attendance`, `POST /api/attendance/bulk`, `GET/PUT/DELETE /api/attendance/{id}`, `GET /api/attendance/me` |
| Reports | `/api/reports/monthly`, `/roster-sewa`, `/construction-sewa`, `/range`, each with `/excel`, `/csv`, `/share` |
| Requests | `GET/POST /api/requests/zone-change`, `PUT /api/requests/zone-change/{id}/review`, `.../cancel` |
| Users | `GET/POST /api/users`, `GET/PUT/DELETE /api/users/{id}`, `POST/GET/DELETE /api/users/{id}/photo` (ADMIN and OFFICE_ADMIN; only ADMIN may touch an ADMIN account) |
| Reference | `GET /api/meta/options`, `GET /api/meta/channels`, `POST /api/meta/contact` |

Swagger UI documents every parameter and response.

---

## 9. Tests

In IntelliJ: right-click `RoleScopeTest` and **Run**, or use the Maven tool
window > **Lifecycle > test** for the whole suite.

`RoleScopeTest` runs against in-memory H2 and asserts, for each of the six roles,
which sewadars and attendance rows come back and which calls are refused.

`PhotoStorageTest` covers storing and, more importantly, **replacing** a photo. Its
fixtures are real PNGs of a few hundred kB on purpose: a token 20 byte image would
have fitted the `tinyblob` column that change set 10 fixed, and proved nothing.

`AadharPrivacyTest` asserts the masking rule for every role, on the grid, the detail
view and the attendance card, and that a masked value cannot be saved back over a real
one.

---

## 10. Deleting things

Nothing with history is hard deleted:

- a **sewadar** with attendance is deactivated, so the past records stay valid
- a **zone** with active sewadars is deactivated
- **attendance** entries can be deleted, by ADMIN and OFFICE_ADMIN only
- the last enabled **ADMIN** account cannot be deleted or disabled

---

## 11. Before going to production

1. Set a real `JWT_SECRET` (32+ bytes) and a strong `ADMIN_PASSWORD`.
2. Switch `spring.jpa.hibernate.ddl-auto` to `validate` and add Flyway.
3. Point `CORS_ORIGINS` at the real UI origin only.
4. Serve both over HTTPS.
5. Use a MySQL account scoped to `sewa_ums` rather than `root`.
6. The JWT is held in `localStorage`; if your threat model needs it, move to an
   HttpOnly refresh cookie.
7. Aadhaar numbers are stored as typed and only masked by role. If your rules
   require them unreadable at rest, that is encryption, and it is a separate piece of
   work - see [CHANGELOG](CHANGELOG.md) change set 11.

---

## 12. The interface

The look is defined in one file,
[frontend/src/styles/app.css](frontend/src/styles/app.css), written as ten numbered
sections: tokens, base, controls, data, overlays, shell, login, dashboard, screens,
responsive. Read the header comment before adding to it.

**The login screen keeps its own sheet**,
[frontend/src/styles/login.css](frontend/src/styles/login.css), imported by the page
itself. It is a full-bleed piece of artwork with a palette of its own, and the class
names its design uses — `.brand`, `.feature`, `.divider`, `.signup` — are far too
general to sit loose beside the rest of the application. Every rule in it is scoped
under `.login-page`. Note that `app.css` loads *after* it, so a bare element selector
there (`input:focus`, say) will win on source order: login rules that must hold are
written with `.login-page` in front of them.

**Dark mode** is two blocks of token overrides at the end of the file - every
component is written against the tokens, so nothing else needed changing. There are
three states: *system* (the default, follows the OS), *light* and *dark*, chosen on
the Profile screen under Appearance and remembered per browser. The explicit choice
has to beat the OS in both directions, which is why there is a `:not([data-theme=
'light'])` guard on the media query and a `[data-theme='dark']` block after it.

Two rules hold the design together:

1. **Colour is spent on two things only** - the primary action, and the status of a
   row. Everything else is ink on paper, which is what keeps a dense table readable.
2. **Status never speaks through colour alone.** Every pill carries its word, and the
   dashboard trend carries an arrow and a sign as well as a colour.

**The login screen's artwork is drawn, not photographed** — a gradient sky, a cloud
bank and a temple silhouette in SVG, plus a CSS cloud-and-orbit illustration. Nothing
is downloaded, so the first screen anyone sees never waits on an image. The Google
mark is inline for the same reason: the login page makes no third-party request
before anyone has signed in.

The product name and tagline live in [frontend/src/brand.js](frontend/src/brand.js) -
one edit to rename the application. The backend has its own copy for the Swagger title
(`OpenApiConfig`) and the report footer (`ReportExporter`).

Colours are taken from the supplied design: `#1a3a5f` navy rail, `#1e4e8c` primary
action, `#3b82f6` accent, `#f7f9fc` canvas. The chart is blue Present and green
Absent, as drawn.

Three controls on the login screen - **Google** sign-in, *Forgot password* and *Sign
up* - are drawn because the design calls for them, but nothing is wired behind them:
single sign-on is not configured, and accounts and password resets are an
administrator's job. Each says what to do instead when it is used, rather than doing
nothing at all. Microsoft sign-in was removed on request. If single sign-on is ever
wanted, that is its own piece of work.

**Dates on screen come from `frontend/src/dates.js`**, never from
`toISOString().slice(0, 10)`. That returns the date in UTC, and east of Greenwich the
UTC date is still yesterday for part of every morning - which once had attendance
marked at 01:00 disappearing from the zone sheet, and bulk marking written to the
previous day.

---

## 13. On a phone

The application is used mostly on phones, and is built for that: Android and iOS,
portrait and landscape.

- Below **640px** the first four screens also appear in a tab bar along the bottom,
  where a thumb reaches them; "More" opens the drawer.
- Below **900px** the left navigation becomes a drawer. The menu button is in the
  topbar; tapping the dimmed area beside the drawer closes it, and the page behind it
  does not scroll while it is open.
- Below **640px** panels and form grids go to one column, padding tightens, dialogs
  become bottom sheets, and the tab strips scroll sideways rather than wrapping.
- Below **340px** the dashboard tiles and the check in / out buttons go to one column.
  A 360px Android screen deliberately keeps two tiles across.
- **Form fields are 16px on phones.** That is not a style choice: iOS Safari zooms the
  whole page in when a focused field is smaller, and does not zoom back out. Pinch
  zoom is left enabled, because disabling it is an accessibility problem.
- **Notch and home indicator** are handled with `viewport-fit=cover` and
  `env(safe-area-inset-*)`, so nothing important sits under them.
- **Wide tables scroll inside their own box**, not by moving the whole page. If you
  add a table, put it in a `.table-wrap`.
- **Touch targets** are at least 44px on any device without a mouse.

All of it lives in one commented block at the end of
[app.css](frontend/src/styles/app.css) - start there before adding a breakpoint of
your own.

---

## 14. Troubleshooting

### ERR_CONNECTION_REFUSED on http://localhost:5173

The Vite dev server is not running, or it bound to only one IP stack.

1. Start it: `npm start` from the repository root. Leave that terminal open, it is
   the server.
2. If it is running and the browser still refuses, check which addresses it bound to:

   ```powershell
   netstat -ano | findstr :5173
   ```

   You want to see both `0.0.0.0:5173` and `[::]:5173`. If you only see `[::1]:5173`,
   the server is IPv6 only and a browser reaching for IPv4 will be refused. That is
   why `server.host` is set to `true` in
   [frontend/vite.config.js](frontend/vite.config.js) - it makes Vite listen on both.
3. Port already taken by something else? `strictPort` makes Vite fail with a clear
   message instead of quietly moving to 5174, so read the terminal output.

Because `host: true` also publishes the dev server on your LAN (Vite prints a
Network URL), change it to `host: false` if you would rather keep it to this machine
only. Note that IPv4 or IPv6 refusals can come back if you do.

### The UI loads but sign in fails

If the message says the API on port 8080 cannot be reached, the backend is not
running. Start **UserManagementApplication** in IntelliJ, then try again. The front
end proxies `/api` to `127.0.0.1:8080`, so it needs the backend up.

### Cannot reach the server, or 500 on every API call

Check in this order: MySQL running, backend running in IntelliJ, the backend log for
a database connection error (wrong `DB_PASSWORD` is the usual cause).

### java: JDK isn't specified for module 'user-management'

IntelliJ has no SDK assigned to the module. It usually means `backend/.idea/misc.xml`
is missing or the project was opened before a JDK was registered.

1. **File > Project Structure > Project** and set **SDK** to a JDK **21** and
   **Language level** to 21.
2. **File > Project Structure > Modules > user-management > Dependencies** and set
   **Module SDK** to *Project SDK*.
3. In the **Maven** tool window, click **Reload All Maven Projects** (the circular
   arrows). This regenerates the module from `pom.xml`.

`backend/.idea/misc.xml` is checked in with `project-jdk-name="21"`, which is the name
this machine uses for Oracle OpenJDK 21.0.7. If your JDK is registered under a
different name (**File > Project Structure > SDKs** shows it), either rename it to
`21` or update that file.

If no JDK 21 is listed at all: **SDKs > + > Add JDK** and point it at
`C:\Program Files\Java\jdk-21`.

### My photo does not show next to my name

First check the account actually has one: a photo is not required, and initials are
the correct display when there is none. **My Profile → Change photo** sets your own
picture and the topbar follows immediately, without a reload.

The control appears for ADMIN and OFFICE_ADMIN only, because `/api/users/**` is
restricted to those two roles; other roles see the picture but cannot change it, and
an administrator sets it for them from **User Account → Edit**.

If a photo is on the account and still does not appear, check that
`GET /api/auth/me` returns `photoUpdatedAt`. The client keys its image cache on that
stamp and shows initials without it, so an older backend build - one from before the
field was added - produces exactly this symptom. Restart the backend.

### "Upload failed" under the photo picker

As of change set 22 the photo is sent by **Save changes** along with the rest of the
form, not the moment it is picked, and a failure names its cause rather than saying
only that it failed. Change set 24 fixed the specific case reported twice: a missing
import meant the upload threw before any request was sent, and the message swallowed
the reason. If you see a bare "Upload failed" now, the frontend is stale - reload the
dev server.

### The older "Upload failed", for reference

Fixed in change set 17. The usual cause was an image over the limit: the container cut
the request off before the application could say so, and the result was a bare 500.

The limit is **3 MB**, JPEG, PNG or WebP. The picker now checks that before sending -
so an oversized file is refused instantly and by name - and the server checks it again.
Anything past 15 MB is refused by the container with a clear message rather than a 500.

### "The photo could not be saved because its stored image record conflicts"

Fixed in change set 10 — that message no longer exists. It was reporting a
`Data too long for column 'data'` failure as if it were a duplicate row: the `photos`
`data` column had been generated as `tinyblob`, which holds **255 bytes**, so every
real photo failed to save.

If you are on a database created before that fix, the column is widened to
`mediumblob` automatically on the next backend start:

```
PhotoSchemaRepair : Widened photos.data from tinyblob to mediumblob
```

No manual SQL and no data loss. To check the column by hand:

```sql
SELECT column_type FROM information_schema.columns
WHERE table_name = 'photos' AND column_name = 'data';
-- mediumblob
```

A genuine size failure now returns **413** and says so. An image over 3 MB is refused
by the service before the database is touched.

### Lombok errors in IntelliJ

Getters, setters or `.builder()` reported as unresolved means annotation processing
is off. Enable **Settings > Build, Execution, Deployment > Compiler > Annotation
Processors > Enable annotation processing**, then **Build > Rebuild Project**.

### Port 8080 already in use

A previous backend run is still alive. Find and stop it:

```powershell
netstat -ano | findstr :8080
taskkill /F /PID <the pid from the last column>
```
