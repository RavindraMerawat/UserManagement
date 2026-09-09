# Change Log

Everything built and changed in this project, newest work last. Written to be read
by someone picking the project up cold, so each entry says what changed **and why**.

- **Project:** Sewadar User Management & Attendance System
- **Stack:** Spring Boot 3.3.4 on Java 21, MySQL 8, React 19 + Vite 6
- **Backend:** 81 main classes + 1 test class, base package `com.user.management`
- **Frontend:** 23 modules under `frontend/src`

| # | Change set | Outcome |
|---|---|---|
| 1 | [Initial implementation](#1-initial-implementation) | Full stack app, role scoped end to end |
| 2 | [Package restructure](#2-package-restructure) | `com.sewa.ums` → `com.user.management` |
| 3 | [Root npm scripts](#3-root-npm-scripts) | `npm start` works from the repo root |
| 4 | [IntelliJ-only backend](#4-intellij-only-backend) | Backend out of the VS Code workflow |
| 5 | [Dev server binding fix](#5-dev-server-binding-fix) | `ERR_CONNECTION_REFUSED` resolved |
| 6 | [Co-ordinator role and sewadar fields](#6-co-ordinator-role-and-sewadar-fields) | 7th role, 3 new fields, rebuilt form |
| 7 | [Accidental deletion and recovery](#7-accidental-deletion-and-recovery) | All source restored from a dangling git object |
| 8 | [IntelliJ SDK fix and React 19](#8-intellij-sdk-fix-and-react-19) | Module SDK resolved, front end on latest React |

---

## 1. Initial implementation

Built the whole application from an empty directory.

### Backend

| Area | What was added |
|---|---|
| Entities | `User`, `Sewadar`, `Zone`, `Attendance`, `ZoneChangeRequest`, `Auditable` (created/updated by and at) |
| Enums | `Role`, `SewaType`, `AttendanceStatus`, `RequestStatus`, `Gender` |
| Repositories | 5 Spring Data interfaces + 3 projections for report aggregation |
| Services | Auth, Sewadar, Attendance, Report, Dashboard, Zone, ZoneChangeRequest, User |
| Controllers | 8, one per module |
| Models | 35 request/response records |
| Errors | 4 domain exceptions + `GlobalExceptionHandler` returning one JSON envelope |

### Security

- **Spring Security** stateless filter chain, per-endpoint role rules, `@EnableMethodSecurity`
- **BCrypt** password hashing everywhere a password is stored
- **JWT** issued on login (`JwtService`), validated per request (`JwtAuthenticationFilter`)
- **CORS** driven by the `app.cors.allowed-origins` property

### The role model

The rule that matters most: **who can see what**. It lives in one place rather than
being re-implemented per query. `CurrentUserService.scope()` turns the signed-in role
into a `DataScope` record, and every repository query takes that scope as a parameter:

| `DataScope` | Meaning | Roles |
|---|---|---|
| `zoneIds == null, sewadarId == null` | every zone | ADMIN, OFFICE_ADMIN, OFFICE_USER |
| `zoneIds != null` | only those zones | COORDINATOR, ZONE_INCHARGE, SUPERVISOR |
| `sewadarId != null` | only that sewadar's own rows | SEWADAR |

`RoleScopeTest` asserts this for every role against in-memory H2.

### Attendance

One row per **(sewadar, date, sewa type)** as a unique key, so re-saving a sewa sheet
updates instead of duplicating. Sewa hours derive from in/out time. `zone_id` is
denormalised onto the attendance row so historic records keep the zone the sewa was
actually performed in, even after a zone change is approved.

### Reports

Monthly, roster sewa, construction sewa and custom range are the same aggregation
with a different filter. Each returns per-sewadar rows plus period totals and
status / sewa-type breakdowns, and each can be downloaded as Excel (Apache POI) or
CSV.

### Integrations

- **Email** (`spring-boot-starter-mail`) - HTML table plus the Excel workbook attached
- **WhatsApp** (Meta WhatsApp Cloud API) - text summary with the top ten sewadars

Both are **off by default**. While off a share is accepted, the composed message is
logged, and the response carries a warning saying it was not delivered - so the flow
is testable without credentials.

### Frontend

Login screen plus a left-hand sidebar (Home, About, Sewadar, Attendance, Report,
Request, Contact, and Zones / User Accounts for admins). The menu is built from the
`menu` array in the login response, so the **server** decides what appears; route
guards repeat the check but never enforce it alone.

### Fixes made during this change set

| Problem | Fix |
|---|---|
| Login threw 500: `Illegal base64 character` | `JwtService` caught `IllegalArgumentException`, but jjwt throws `DecodingException`. Now probes base64, falls back to raw bytes, and fails at startup if the secret is under 32 bytes |
| Authorization failures returned **401 instead of 403** | Spring was falling through to the authentication entry point for some denials. Added `RestAuthEntryPoints.Forbidden` as an explicit `AccessDeniedHandler`, so both now emit the same JSON envelope as every other error |
| Startup warning: `MySQLDialect does not need to be specified` | Removed the explicit `hibernate.dialect`; Boot infers it from the JDBC URL |
| Startup warning: `UserDetailsService beans will not be used` | Removed the hand-built `DaoAuthenticationProvider`; Spring Security wires our `UserDetailsService` and `PasswordEncoder` on its own |
| Share response said `emailSent: true` next to "was only logged" | `*Sent` now means it actually left the server; a disabled channel reports `false` plus the reason |
| Wrong current password on **change password** said "Invalid username or password" | Throws `BadRequestException` with a clear message; login keeps its deliberately vague 401 |

---

## 2. Package restructure

Renamed the base package and reorganised into the requested layout.

`com.sewa.ums` → **`com.user.management`**, 82 files moved:

| Was | Now | Holds |
|---|---|---|
| `web.controller` | `controller` | REST endpoints |
| `domain` | `entity` | JPA entities and the enums they persist |
| `web.dto` | `model` | request and response payloads |
| `config` | `config` | unchanged |

`AuthController` → **`AuthorizationController`** (still `/api/auth/**`), Swagger tag
renamed to "1. Authorization".

`service`, `repository`, `security`, `integration`, `report`, `exception` and
`bootstrap` came along unchanged under the new base package. Folding them into the
four named packages would have put business logic and JWT handling inside
controllers.

**Enums went into `entity`, not `model`,** to keep the dependency direction one-way:
`controller` → `service` → `repository` → `entity`, with `model` depending on
`entity` for the enum types it exposes. Putting them in `model` would have made
`entity` depend on `model`, inverting the layering.

### Things the rename touched beyond imports

- **JPQL string literals.** The monthly-report aggregation references enums by
  fully-qualified name inside `@Query` (`com.user.management.entity.AttendanceStatus.PRESENT`).
  A find-and-replace over imports alone would have missed these and the report would
  have failed at runtime.
- **`pom.xml`** groupId `com.sewa` → `com.user.management`
- **Logging config** in `application.yml` and `application-dev.yml`
- **Import blocks re-sorted** in the 19 files the mechanical rename left out of order

---

## 3. Root npm scripts

`npm start` from the repository root failed with `ENOENT: package.json` because the
React app lives in `frontend/` and its script is Vite's `dev`, not `start`.

- Added a root `package.json`
- Added `"start": "vite"` to `frontend/package.json` so both names work there too
- Added `scripts/run-backend.mjs`, which located a JDK 21 itself and applied it to
  the Maven child process only

That last part mattered because `JAVA_HOME` on this machine points at **JDK 8**,
which cannot build the project, so a script that just called Maven would have failed
confusingly.

> Superseded by change set 4, which removed the backend launcher.

---

## 4. IntelliJ-only backend

Requirement: the backend runs **only** in IntelliJ, never from VS Code.

**Removed**

- `scripts/run-backend.mjs` and the `concurrently` dependency
- Every Maven and Spring Boot invocation from the npm scripts

Root `npm start` now runs **only** Vite. There is no `npm run backend` and no
`npm test` calling Maven.

**Added** `backend/.run/UserManagementApplication.run.xml`, a checked-in IntelliJ run
configuration, so **UserManagementApplication** appears in the run dropdown ready to
go with `DB_USER` / `DB_PASSWORD` as editable environment variables.

**README rewritten** so no section tells you to run Maven from a terminal: JDK 21 as
IntelliJ's project SDK, database and integration credentials as run-configuration
environment variables, tests via right-click or the Maven tool window.

### Fix this surfaced

With the two halves started separately, "frontend up, backend not started yet"
becomes a routine state - and the login screen reported it as **"Invalid username or
password"**, sending you hunting for a password problem. Vite turns a refused proxy
connection into a 500 with a non-JSON body, so `errorMessage` in
`frontend/src/api/client.js` now distinguishes that from a real backend error:

> Cannot reach the API on port 8080. Start the backend in IntelliJ (run
> UserManagementApplication) and try again.

It keys off whether the response carries our own error envelope, so genuine 500s from
the backend still show their real message.

---

## 5. Dev server binding fix

`http://localhost:5173/` returned `ERR_CONNECTION_REFUSED`. Two causes:

1. The dev server was not running.
2. **A real config bug.** Even when running, Vite had bound to IPv6 `::1` only:

   ```
   http://[::1]:5173/      -> 200
   http://127.0.0.1:5173/  -> REFUSED
   ```

   `localhost` resolves to both addresses on this machine, so whether the page loaded
   depended on which one the browser reached for first. Vite's default `host` is
   `"localhost"`, which Node resolves to a **single** address - here the IPv6 one.

**Fixed** in `frontend/vite.config.js`:

| Setting | Why |
|---|---|
| `host: true` | listen on all interfaces so both IP stacks answer |
| `strictPort: true` | fail loudly instead of quietly moving to 5174 while you stare at the wrong URL |
| proxy target `127.0.0.1:8080` | was `localhost:8080`; naming the address stops the same resolution problem hitting the proxy hop |

All three now answer: `127.0.0.1`, `[::1]` and `localhost`.

> Trade-off: `host: true` also publishes the dev server on the LAN (Vite prints a
> Network URL). Set `host: false` to keep it to this machine, accepting that the
> IPv4/IPv6 refusal can return.

**Added README section 12, Troubleshooting** - this diagnosis, the Lombok annotation
processing fix, a stuck port 8080, and sign-in failing because the backend is down.

---

## 6. Co-ordinator role and sewadar fields

### New role: `COORDINATOR`

Display name "Co-ordinator". Zone-scoped alongside Zone Incharge and Supervisor.

| Can | Cannot |
|---|---|
| See sewadars, attendance and reports for its assigned zones | Reach any other zone |
| Mark and update attendance in those zones | Add, edit or delete sewadar records |
| Raise zone change requests | Approve or reject them |
| | Manage zones or login accounts |

Wired into `Role.ZONE_SCOPE`, `CurrentUserService.canMarkAttendance()`,
`AuthService.menuFor()`, the `SecurityConfig` attendance rules, the frontend
`SCREEN_ROLES` and `ZONE_SCOPED_ROLES`, and the About screen role table.

> **Assumption worth checking:** a Co-ordinator was given the same reach as a Zone
> Incharge. If the role should sit *above* zone incharges - all zones, or approval
> rights - that is a one-line change in `Role.java` plus `CurrentUserService`.

`ZONE_SCOPED_ROLES` is now exported from `AuthContext.jsx` and imported by
`Users.jsx`, so the list of zone-scoped roles is defined once on the front end.

### Password encoding and JWT

Both were already in place from change set 1 and needed no work: BCrypt via
`PasswordEncoder`, JWT via `JwtService` and `JwtAuthenticationFilter`.

### Sewadar registration form

Rebuilt around the requested field list, in this order:

| Field | Column | Notes |
|---|---|---|
| Badge Number | `badge_number` | required, unique |
| Name | `name` | required |
| F/H Name | `father_or_husband_name` | |
| Birth Date | `date_of_birth` | |
| Mobile No | `mobile` | |
| Zone | `zone_id` | required |
| Address | `address` | |
| **Aadhaar No** | `aadhar_number` | **new** - 12 digits, unique where present |
| Blood Group | `blood_group` | now a picker, 8 groups |
| **Area** | `area` | **new** |
| **Center / Point** | `center_point` | **new** |

Three new columns, one new unique constraint (`uk_sewadar_aadhar`) and one new index
(`idx_sewadar_area`).

Also added: a read-only **View** dialog for the whole record, and search now covers
F/H name, area and center as well as name, badge, mobile and department.

**Aadhaar handling.** Stored as 12 bare digits. `SewadarService.normaliseAadhar`
strips spaces and dashes on save, so `1234 5678 9012`, `9999-8888-7777` and
`123456789012` are all handled and the uniqueness check cannot be fooled by
formatting. Blank becomes `NULL`, which the nullable unique constraint allows for
many sewadars whose number has not been collected.

### Fix found while testing

The bean-validation `@Pattern` on `aadharNumber` rejected `1234 5678 9012` **before**
the service's normaliser could strip the spaces - so the API refused a value it was
designed to accept. The pattern now allows the grouping characters and the service
enforces the 12-digit rule.

### Two judgement calls

- **Badge Number was kept** on the form. It is the unique id every attendance and
  report row points at; dropping it would break both.
- **Department, Gender, Email, City, Pincode and Joining date were kept**, behind an
  "Additional details" toggle rather than deleted, because the monthly report reads
  `department`. The main form still matches the registration slip.

### Data protection note

Aadhaar numbers are personal data. They are stored as plain digits and shown only on
the form and the detail view, never in the list. If your rules require them encrypted
at rest or masked for some roles, that needs adding deliberately.

---

## 7. Accidental deletion and recovery

Every source file, both `.md` documents, `pom.xml`, `package.json` and the SQL schema
were deleted. `backend/` and `frontend/` were left as empty shells.

**Git could not help directly.** The only commit, `9fe06d4 "Initial project setup"`,
contains **just `.gitignore`**; everything else was untracked, and the working tree
reported clean. A `git clean -fdx` or similar would produce exactly this.

**What did work:** `git fsck --lost-found` turned up a **dangling tree object**
(`c4b28984`) - a complete `git add .` snapshot that was staged but never committed.
The blobs were still in `.git/objects`, so the project was restored from them with
`git read-tree` followed by `git checkout-index`.

This was an **exact** recovery, not a reconstruction from memory. The proof: the
rebuilt front-end bundle hash `index-BW9aHrE3.js` was byte-for-byte identical to the
pre-deletion build.

| Restored | Count |
|---|---|
| Java (main + test) | 82 |
| Front-end modules | 24 |
| yml / md / xml / sql | 3 / 2 / 13 / 1 |

Two deliberate deviations:

- **`backend/.metadata/` was skipped** - 300+ files of Eclipse workspace cache
  including browser profile data. Regenerable, and already in `.gitignore`.
- **The committed `.gitignore` was kept**, not the snapshot's older copy. The
  committed one is better: it covers `**/target/`, `**/node_modules/`, `**/.vite/`,
  `.metadata/` and `*.log`.

`node_modules` and `target` were gitignored and therefore not in the snapshot; both
were reinstalled.

> **Still not committed.** The restored source remains untracked. Until
> `git add . && git commit` runs there is no second safety net, and next time there
> may be no dangling object to rescue.

---

## 8. IntelliJ SDK fix and React 19

### `java: JDK isn't specified for module 'user-management'`

The deletion took `backend/.idea` with it, since it is gitignored and so was not in
the recovered snapshot. IntelliJ recreated the folder on reopen but only wrote
`compiler.xml`, `vcs.xml` and `workspace.xml` - **`misc.xml` was missing**, and that
is the file holding the project SDK. No SDK, no module JDK, hence the error.

Fixed by writing `backend/.idea/misc.xml` with:

| Component | Value |
|---|---|
| `ProjectRootManager` | `project-jdk-name="21"`, `languageLevel="JDK_21"` |
| `MavenProjectsManager` | `$PROJECT_DIR$/pom.xml`, so the module is imported from Maven |

The name `21` was not guessed: IntelliJ's `jdk.table.xml` registers Oracle OpenJDK
21.0.7 at `C:/Program Files/Java/jdk-21` under exactly that name. Modern IntelliJ
stores Maven module files outside the project, which is why there is no `.iml` to
restore - a Maven reload regenerates the module.

`pom.xml` was already unambiguous (`java.version=21` plus `<release>21</release>`) and
javac confirms `[debug parameters release 21]`.

### Front end on the latest React

| Package | Was | Now | Why this version |
|---|---|---|---|
| react | 18.3.1 | **19.2.8** | latest |
| react-dom | 18.3.1 | **19.2.8** | latest |
| react-router-dom | 6.26.2 | **7.18.3** | latest, peer `react >=18` |
| axios | 1.7.7 | **1.20.0** | latest |
| vite | 5.4.8 | **6.4.3** | see the Node ceiling below |
| @vitejs/plugin-react | 4.3.2 | **4.7.0** | latest that supports this Node |

**Why not Vite 8.** Vite 7 and 8 require Node `^20.19.0 || >=22.12.0`; this machine
runs **Node 20.10.0**. Vite 6 declares `^18 || ^20 || >=22` and works. Installing
`@vitejs/plugin-react@5` produced an `EBADENGINE` warning for the same reason - it
functioned, but outside its supported range - so it was pinned back to 4.7.0, which
officially covers this Node and peers with Vite 6. The result installs with **no
warnings and 0 vulnerabilities**.

Upgrading Node to 20.19+ or 22 LTS unlocks Vite 8 and plugin-react 6 with no
application code changes.

**No app code needed changing for React 19.** Audited for every API React 19 removed
-`ReactDOM.render`, `hydrate`, `unmountComponentAtNode`, `findDOMNode`,
`createFactory`, `propTypes`, `defaultProps`, legacy context - none were used. The
entry point was already on `createRoot`. All ten `react-router-dom` imports
(`BrowserRouter`, `Routes`, `Route`, `NavLink`, `Link`, `Navigate`, `Outlet`,
`useNavigate`, `useLocation`, `useSearchParams`) still exist in v7, and the rollup
build validates named exports, so a removal would have failed the build.

### Also fixed

`frontend/package.json` carried a stray `"user-management": "file:.."` dependency -
the front end depending on the repository root, added by an accidental `npm install`.
Removed.

---

## Files at a glance

### Added in change set 6

```
backend/src/main/java/com/user/management/entity/Sewadar.java        (3 new fields)
backend/src/main/java/com/user/management/entity/Role.java           (COORDINATOR)
backend/src/main/java/com/user/management/model/SewadarRequest.java  (rewritten)
backend/src/main/java/com/user/management/model/SewadarResponse.java (rewritten)
frontend/src/pages/Sewadars.jsx                                      (rewritten)
```

### Project layout

```
UserManagement/
├── backend/          Spring Boot REST API  -> run in IntelliJ IDEA
│   └── .run/         checked-in run configuration
├── frontend/         React single page app -> run in VS Code
├── db/               reference SQL schema and the ALTER for existing databases
├── package.json      front end scripts only
├── README.md         setup, roles, API, troubleshooting
└── CHANGELOG.md      this file
```

---

## Verification record

Everything below was run against a live server, not inferred.

| Checked | Result |
|---|---|
| Backend compile | 81 sources, clean |
| `RoleScopeTest` | **7/7 pass** (H2), one case per role |
| Frontend build | clean, 301 kB bundle |
| JWT | no token 401, garbage 401, tampered 401, valid 200 |
| Authorization | Zone Incharge on `/api/users` → 403, on `/api/sewadars` → 200 |
| CORS | preflight from `localhost:5173` allowed, `evil.example.com` rejected |
| Role isolation | Admin 4 sewadars, Zone Incharge 3 (own zone), Sewadar 1 (own record) |
| Attendance | bulk sheet, hours derived (06:00→12:30 = 6.5), re-save updated not duplicated, future date rejected |
| Reports | monthly / roster / construction / range, plus valid `.xlsx` with totals row and CSV |
| Zone change | sewadar raised, blocked from approving; admin approved and the sewadar moved; past attendance kept its original zone |
| Co-ordinator | own zone only, marked attendance, raised a request, 403 on approve / add sewadar / users / zones |
| Sewadar fields | all 11 created and updated; Aadhaar normalised; duplicate and short values rejected; search hit area, center and F/H name |
| Protections | last enabled admin cannot be disabled or deleted; sewadar with history deactivated not deleted |

---

## Known limitations

1. `spring.jpa.hibernate.ddl-auto=update` generates the schema. Production should use
   `validate` plus Flyway or Liquibase.
2. Email and WhatsApp are **unverified against real providers** - no SMTP or Meta
   credentials were available, so only the disabled-channel path was exercised.
3. The JWT lives in `localStorage`. Fine for an internal tool; move to an HttpOnly
   refresh cookie if the threat model needs it.
4. Aadhaar numbers are stored unencrypted (see the note above).
5. WhatsApp free-form text only reaches numbers that messaged your business in the
   last 24 hours. Outside that window Meta requires an approved template, which
   `WhatsAppService.sendTemplate(...)` covers but no template has been registered.
