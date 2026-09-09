# Sewadar User Management & Attendance System

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
└── CHANGELOG.md      Every change made, and why
```

> Picking the project up cold? [CHANGELOG.md](CHANGELOG.md) records every change set,
> the bugs found along the way, the design decisions and their reasoning, and what has
> and has not been verified against a live server.

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
| **OFFICE_ADMIN** | Every zone, every sewadar | Sewadar CRUD, attendance, reports, approve zone changes, zones |
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

Tables are generated by Hibernate (`spring.jpa.hibernate.ddl-auto=update`):
`zones`, `users`, `user_zones`, `sewadars`, `attendance`, `zone_change_requests`.

> For production, switch `ddl-auto` to `validate` and manage the schema with Flyway
> or Liquibase instead.

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

| Screen | Route | Visible to |
|---|---|---|
| Home | `/` | everyone — dashboard scoped to the role |
| About | `/about` | everyone — role matrix and module list |
| Sewadar | `/sewadars` | all except SEWADAR — search, add, edit, delete |
| Attendance | `/attendance` | everyone — *Mark* tab for markers, *Records* tab for all |
| Report | `/reports` | everyone — monthly, roster sewa, construction sewa, custom range |
| Request | `/requests` | everyone — raise, review and cancel zone changes |
| Zones | `/zones` | ADMIN, OFFICE_ADMIN |
| User Accounts | `/users` | ADMIN |
| Contact | `/contact` | everyone — message to the office admins |

The menu is built from the `menu` array in the login response, so the server decides
what appears.

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
| Sewadars | `GET/POST /api/sewadars`, `GET/PUT/DELETE /api/sewadars/{id}`, `GET /api/sewadars/me`, `GET /api/sewadars/for-attendance` |
| Attendance | `GET/POST /api/attendance`, `POST /api/attendance/bulk`, `GET/PUT/DELETE /api/attendance/{id}`, `GET /api/attendance/me` |
| Reports | `/api/reports/monthly`, `/roster-sewa`, `/construction-sewa`, `/range`, each with `/excel`, `/csv`, `/share` |
| Requests | `GET/POST /api/requests/zone-change`, `PUT /api/requests/zone-change/{id}/review`, `.../cancel` |
| Users | `GET/POST /api/users`, `GET/PUT/DELETE /api/users/{id}` (ADMIN only) |
| Reference | `GET /api/meta/options`, `GET /api/meta/channels`, `POST /api/meta/contact` |

Swagger UI documents every parameter and response.

---

## 9. Tests

In IntelliJ: right-click `RoleScopeTest` and **Run**, or use the Maven tool
window > **Lifecycle > test** for the whole suite.

`RoleScopeTest` runs against in-memory H2 and asserts, for each of the six roles,
which sewadars and attendance rows come back and which calls are refused.

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

---

## 12. Troubleshooting

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
