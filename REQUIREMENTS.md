# Requirements — Pandal Office Management

Every requirement asked for on this project, with what was built against it and where
it lives. Each has an ID so it can be referenced in a review or a bug report.

**Status key**

| | Meaning |
|---|---|
| Done | Built and verified against a running server |
| Done* | Built, but with a decision or limit noted in the Notes column |
| Partial | Built for the requested case, with a stated gap |
| Not built | Deliberately out of scope — see [Section 12](#12-not-built-and-why) |

Related documents: [README.md](README.md) for setup, [CHANGELOG.md](CHANGELOG.md) for
the history of how it was built.

---

## 1. Scope and stack

| ID | Requirement | Status | Notes |
|---|---|---|---|
| ST-1 | Project lives under the `UserManagement` folder | Done | `backend/`, `frontend/`, `db/` |
| ST-2 | Backend on **Spring Boot** | Done | 3.3.4 |
| ST-3 | **Java 21** | Done | `pom.xml` `java.version=21`, javac reports `release 21` |
| ST-4 | Frontend on **React**, latest version comfortable with Java 21 | Done* | React 19.2.8. Vite pinned to 6 because Node 20.10 is below Vite 7's floor — see [NF-7](#7-non-functional-requirements) |
| ST-5 | **MySQL** database | Done | Schema created by [db/schema.sql](backend/src/main/resources/db/schema.sql) at startup, with Hibernate at `ddl-auto=validate` |
| ST-6 | **Swagger** API documentation | Done | springdoc 2.6.0 at `/swagger-ui.html`, JWT authorise button wired |
| ST-7 | Base package **`com.user.management`** | Done | 82 classes |
| ST-8 | Packages `controller`, `entity`, `model`, `config` | Done | Plus `service`, `repository`, `security`, `integration`, `report`, `exception`, `bootstrap` |
| ST-9 | **`AuthorizationController`** in the controller package | Done | Serves `/api/auth/**` |
| ST-10 | Backend runs **only in IntelliJ**, never from VS Code | Done | Run config at [backend/.run/](backend/.run/UserManagementApplication.run.xml); no Maven call in any npm script |
| ST-11 | Frontend runs from VS Code | Done | `npm start` at the repo root |

---

## 2. Roles

**RL-1 — Seven roles.** Status: **Done**

| Role | Enum | Data reach |
|---|---|---|
| Admin | `ADMIN` | Every zone |
| Office Admin | `OFFICE_ADMIN` | Every zone |
| Co-ordinator | `COORDINATOR` | Assigned zones only |
| Zone Incharge | `ZONE_INCHARGE` | Assigned zones only |
| Supervisor | `SUPERVISOR` | Assigned zones only |
| Office User | `OFFICE_USER` | Every zone, read only |
| Sewadar | `SEWADAR` | Own records only |

**RL-2 — Access matrix.** Status: **Done**

| Capability | Admin | Office Admin | Co-ordinator | Zone Incharge | Supervisor | Office User | Sewadar |
|---|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| See all sewadars | ✓ | ✓ | zone | zone | zone | ✓ | own |
| See all attendance | ✓ | ✓ | zone | zone | zone | ✓ | own |
| Add / edit / delete sewadar | ✓ | ✓ | | | | | |
| Mark and update attendance | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | |
| Correct or delete a marked entry | ✓ | ✓ | | | | Incharge | |
| Open the Monthly Report | ✓ | ✓ | | | | Incharge, Sewadar | own |
| Raise a zone change request | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | own |
| Approve / reject a zone change | ✓ | ✓ | | | | | |
| Manage zones, areas and points | ✓ | ✓ | | | | | |
| Issue / collect a badge | ✓ | ✓ | view | view | view | view | |
| Manage login accounts | ✓ | ✓\* | | | | | |
| Open Setup | ✓ | ✓ | | | | | |
| Reports | ✓ | ✓ | zone | zone | zone | ✓ | own |

> **Office User and attendance.** An Office User marks attendance from the desk;
> the designation matrix has granted it since designations were introduced, and the
> URL rule that still refused it was corrected in change set 30. What an Office User
> may do beyond that depends on their designation - Office Sewadar also carries add,
> edit and delete on the register, an Office User with no designation carries
> neither.

**RL-3 — A Sewadar must never see another sewadar's data.** Status: **Done**

Enforced on the server, not the UI. `CurrentUserService.scope()` turns the role into
a [`DataScope`](backend/src/main/java/com/user/management/security/DataScope.java) that
every repository query takes as a parameter. `RoleScopeTest` asserts it per role
(7 tests). Verified over HTTP that a Sewadar asking for another sewadar's attendance
gets `403 You can only view your own attendance`.

**RL-4 — Zone-scoped roles see only their zone.** Status: **Done**

Reaching outside returns `403 You do not have access to zone N` rather than silently
returning nothing, so a mistake is visible instead of looking like missing data.

> **\* On managing login accounts.** Office Admin was added in
> [change set 13](CHANGELOG.md). Only an **Admin** may create, edit, delete or
> photograph an **Admin** account, or give any account the Admin role — otherwise
> opening account administration to Office Admin would let an Office Admin promote
> itself and hold every permission in the application.

> **On "SuperAdmin".** There is no such role. The seven above are the whole set, and
> **Admin** is the super admin. The roles named in that request as seeing every zone
> are exactly the roles that already have global scope.

> **Assumption on RL-1.** Co-ordinator was given the same reach as Zone Incharge:
> its own zones, may mark attendance and raise requests, may not approve them or edit
> the sewadar register. If a Co-ordinator should sit *above* zone incharges, that is a
> one-line change in `Role.java` plus `CurrentUserService`.

---

## 3. Sewadar module

**SW-1 — Add, edit, update, delete sewadars.** Status: **Done** — Admin and Office Admin only.

**SW-2 — Registration fields, in this order.** Status: **Done**

| # | Field | Column | Notes |
|---|---|---|---|
| — | Badge No | `badge_number` | Required, unique. Kept because every attendance and report row keys off it |
| 1 | Name | `name` | Required |
| 2 | F/H Name | `father_or_husband_name` | |
| 3 | Birth Date | `date_of_birth` | |
| 4 | Mobile No | `mobile` | Exactly 10 digits, on the form and at the server |
| 5 | Zone | `zone_id` | Required |
| 6 | Address | `address` | |
| 7 | Aadhaar No | `aadhar_number` | 12 digits, unique where present |
| 8 | Blood Group | `blood_group` | Picker of 8 groups |
| 9 | Area | `area` | |
| 10 | Center / Point | `center_point` | |

Behind an **Additional details** toggle: Department, Primary sewa type, Gender, Email,
City, Pincode, Joining date. Kept because the monthly report reads `department`.

**SW-3 — Aadhaar handling.** Status: **Done**

Normalised to 12 bare digits on the way in. Spaces and dashes are stripped on save, so
`1234 5678 9012`, `9999-8888-7777` and `123456789012` are all accepted and the
uniqueness check cannot be fooled by formatting. Blank becomes `NULL`, which the
nullable unique constraint permits for sewadars whose number has not been collected.

The digits are stored as typed, and **masked for the roles that have no reason to read
them** — see [NF-9](#7-non-functional-requirements).

**SW-4 — Photo on sewadar creation and edit.** Status: **Done**

JPEG, PNG or WebP, 3 MB maximum. Magic bytes are checked, so a text file renamed
`.png` is rejected. Bytes live in a separate `photos` table so grid queries never
load them; the row carries only `hasPhoto` and `photoUpdatedAt`.

Editing replaces the image: the row is looked up by `(owner_type, owner_id)` and
updated in place, so a sewadar never accumulates a second photo row. The 3 MB limit
and the `mediumblob` column it has to fit are the same constant,
`Photo.MAX_DATA_BYTES` — see [change set 10](CHANGELOG.md), where a `tinyblob` column
made every real upload fail.

**SW-5 — Grid column order.** Status: **Done**

`Badge No | Photo | Name | F/H Name | Mobile No | Zone | Area / Center | Aadhaar Card | Status | Actions`

**SW-6 — View a sewadar with all data.** Status: **Done** — read-only dialog with the
photo and every field in three sections: Registration details, Sewa and contact, Login.

**SW-7 — Search.** Status: **Done** — free text over name, F/H name, badge, mobile,
area, center and department, plus zone and active filters.

**SW-10 — Search by designation.** Status: **Done** — a Designation picker beside Zone
on the sewadar register, and `designationId` on `GET /api/sewadars`. It narrows the
text search rather than replacing it, so "asha" + Supervisor is a question the screen
can ask. A sewadar whose designation has never been filled in still appears when no
designation is chosen — the designation is a left join for exactly that reason. Change
set 29.

**SW-8 — Deleting a sewadar with history.** Status: **Done** — deactivated rather than
removed, so past attendance stays valid.

---

**SW-9 — A dashboard tile opens the people it counted.** Status: **Done**

Total Sewadars, Present Today, On Leave and Absent Today are links. Each opens a grid
of exactly those people:

`Photo | Badge No | Name | F/H Name | Mobile No | Age | Zone | Area | Point | Blood Group`

Age is derived from the date of birth; no column was added to store it.

The count and the list are the **same query** (`SewadarRepository.findForDashboard` /
`countForDashboard`), so the grid can never hold a different number of rows than the
tile just showed. That also corrected the tiles, which used to count attendance
*rows*: a sewadar marked present for two sewa types on one day was counted twice.

Scope applies exactly as everywhere else — global roles get every zone, zone-scoped
roles only their own, a Sewadar only their own record, including when the URL is typed
by hand.

---

## 4. Attendance module

**AT-1 — Mark attendance.** Status: **Done**

**AT-2 — Update attendance.** Status: **Done** — edit date, sewa type, status, in/out
time and remarks. Delete is Admin / Office Admin only.

**AT-3 — Mark Attendance screen per the supplied design.** Status: **Done\***

Live clock header, single search box, person card with photo, green *Checked In* /
red *Not Yet Checked Out* state pair, Check In and Check Out buttons, and the log
table with Work Hours and Location.

> **One deliberate departure from the supplied design.** The state pair was drawn as
> two full-width panels, which on a real screen came out larger than the Check In and
> Check Out buttons that do the work. They are now pills at exactly the button height
> (38px), only as wide as their own text. Requested, and recorded in
> [change set 12](CHANGELOG.md).

**AT-3b — Enter a day that was missed.** Status: **Done**

A separate **Manage Past Attendance** tab, deliberately not a date field on the live
Mark Attendance screen - that would invite recording today's arrival against last
Tuesday. Person card, one row of Attendance Date / Check In Time / Check Out Time, a
Remarks box, and a single **Save Attendance** that writes whichever halves are
missing.

| Rule | Where it is enforced |
|---|---|
| The current month's past days only - the 1st through yesterday | picker `min`/`max`, an immediate message on a typed date, and again inside save |
| Times already recorded arrive filled in and locked | the day is read before the form is drawn |
| No future date, no second check in, check out after check in | the server, in `CheckInOutService` |

The month window is a **screen policy**, not an invariant: the server accepts any
non-future date, so a direct API call is not bound by it. Adding it to
`CheckInOutService` would not affect Mark Attendance or the zone sheet, since today
always falls inside the current month.

**AT-4 — Search a sewadar by Badge No, Name, Mobile No or Aadhaar Card.** Status: **Done**

| Key | Match | Verified |
|---|---|---|
| Badge No | exact | `B00123` → Amit Sharma |
| Name | contains | `Amit` → Amit Sharma |
| Mobile No | digits, contains | `9876543210` → Amit Sharma |
| Aadhaar Card | exact, digits | `123456789012` → Amit Sharma |

Several matches produce a pick list. Minimum 2 characters. Always narrowed to the
caller's zones.

**AT-5 — Individual Check In and Check Out.** Status: **Done**

`POST /api/attendance/check-in`, `POST /api/attendance/check-out`. Check in sets the
in time and marks the day present; check out sets the out time and derives the hours.
Guards, all verified:

- Double check in refused — *"already checked in at 09:12. Check out instead."*
- Check out with no check in refused
- Double check out refused
- Check out earlier than check in refused
- Future dates refused
- Inactive sewadar refused

**AT-6 — Zone-wise search and bulk Check In / Check Out.** Status: **Done**

Second tab: pick zone, sewa type and date, tick sewadars, then check the whole
selection in or out. Shortcuts select "not-checked-in" or "awaiting check-out". A row
that cannot be marked is reported as a skipped row with a reason rather than failing
the batch.

**AT-7 — Attendance record uniqueness.** Status: **Done**

One row per **(sewadar, date, sewa type)**. Re-saving a sheet updates rather than
duplicating. Sewa hours derive from in/out time.

**AT-8 — Roster Sewa and Construction Sewa.** Status: **Done** — `SewaType` covers
`ROSTER_SEWA`, `CONSTRUCTION_SEWA`, `OFFICE_SEWA`, `OTHER`.

> **Note on AT-3.** The design's *Location* column shows the zone name. There is no
> separate location field; if check-in location should be captured per check-in
> (gate vs office, or GPS), that is a schema addition — see
> [Section 13](#13-open-questions).

---

## 5. Reports module

**RP-1 — Attendance data report.** Status: **Done**
**RP-2 — Monthly attendance report.** Status: **Done** — `GET /api/reports/monthly?year=&month=`
**RP-3 — Roster Sewa report.** Status: **Done** — `/api/reports/roster-sewa`
**RP-4 — Construction Sewa report.** Status: **Done** — `/api/reports/construction-sewa`
**RP-5 — Custom date range report.** Status: **Done** — `/api/reports/range`, capped at 400 days

Every report returns per-sewadar rows (present, half day, leave, absent, roster days,
construction days, hours, effective days, attendance %) plus period totals and
status / sewa-type breakdowns.

**RP-6 — Reports respect the caller's scope.** Status: **Done** — a Sewadar's monthly
report contains exactly their own row, labelled "My Records".

**RP-7 — Export.** Status: **Done** — Excel (Apache POI, with a styled header and a
TOTAL row) and CSV, for both monthly and range.

**RP-9 — Monthly report by locality.** Status: **Done** — a Locality picker on the
Monthly Report filters, opening on **Local** because that is the register the office
prints almost every time; Outstation and All localities are the other two choices.
`locality` on `/api/reports/monthly` and its Excel, PDF, CSV and share endpoints, and
the chosen locality is appended to the title and so to the downloaded file name. The
**default lives in the screen, not the server**: a request that says nothing about
locality still covers everyone, so older links and other callers are unchanged. A
sewadar with no locality recorded falls under All localities and under neither of the
other two. Change set 31.

**RP-8 — Monthly report by designation.** Status: **Done** — a Designation picker on
the Monthly Report filters, applied when **Generate report** is pressed, and
`designationId` on `/api/reports/monthly` with its Excel, PDF, CSV and share
endpoints. The chosen designation is appended to the report title, so the sheet and
the downloaded file say what they hold rather than looking like the whole register
with most of it missing. Change set 29.

---

## 6. Requests, zones, accounts

**RQ-1 — Zone change request.** Status: **Done**

The form states the sewadar's current zone as soon as one is picked, so the move reads
as "from here to there"; the target list is every active zone except that one, and says
why it is empty when it is. **A reason is required** — the reviewer has nothing else to
decide on. Both from [change set 19](CHANGELOG.md).

A Sewadar raises one for themselves; a Co-ordinator, Zone Incharge or Supervisor for
a sewadar in their zone; Admin and Office Admin approve or reject. Approval moves the
sewadar. Verified: a Sewadar is blocked from raising one for someone else and from
approving their own; **past attendance keeps its original zone** after a move.

**RQ-2 — One pending request per sewadar.** Status: **Done**
**RQ-3 — Cancel a pending request.** Status: **Done** — by the raiser or an admin.

**ZN-1 — Zone master data.** Status: **Done** — CRUD for Admin / Office Admin; a zone
with active sewadars is deactivated rather than deleted.

**ZN-2 — Setup: Zone, Area, Satsang Point.** Status: **Done** — one screen with three
tabs for the office's three levels of geography. Areas belong to a zone, satsang points
to an area, each with a unique name within its parent. The sewadar form picks Area and
Point from these lists instead of offering a free text box. Reading follows zone scope;
writing is Admin and Office Admin. Anything still in use is deactivated rather than
deleted, and `SetupBackfill` seeds the lists on first start from the names already
written on sewadar records. See [change set 19](CHANGELOG.md).

**UM-1 — Login accounts.** Status: **Done** — **Admin and Office Admin**. Zone-scoped
roles require at least one zone; a Sewadar account must link to a sewadar record.
Only an Admin may touch an ADMIN account or assign the ADMIN role — see
[RL-2](#2-roles).
**UM-2 — Photo on user creation and edit.** Status: **Done** — JPEG, PNG or WebP,
3 MB. The size and type are checked in the browser before the file is sent and again
on the server, which is what enforces them. A file past the servlet limit returns a
clear 413 rather than the bare 500 it used to; see change set 17.
**UM-3 — View a user with all data.** Status: **Done** — dialog with photo, Account and Access sections.
**UM-4 — Last enabled Admin cannot be disabled or deleted.** Status: **Done**

---

## 7. Non-functional requirements

| ID | Requirement | Status | Notes |
|---|---|---|---|
| NF-1 | **Spring Security** | Done | Stateless chain, per-endpoint role rules, `@EnableMethodSecurity` |
| NF-2 | **Password encoding** | Done | BCrypt everywhere a password is stored |
| NF-3 | **JWT** for authenticating users | Done | HS512, 8 hour default expiry; no token / garbage / tampered all return 401 |
| NF-16 | **Messages are written for the office** | Done | A failure says what happened and what to do - "The service is unavailable at the moment - maintenance may be in progress" - and never a port number, a status code or a class name; the diagnostic detail goes to the browser console. Field errors read as the label above the box. The server's own messages are passed through, because they were already written for the office. Change set 34 |
| NF-15 | **An account reads its own gender's register** | Done | Gender and GR. No live on the login (`users.gender`, `users.badgeNo`), set on the User Account form. `DataScope` carries the gender into every scoped query, for every role except Admin, on top of the zone reach. An account with no gender set sees both, so turning this on takes nothing away until the field is filled in. `GenderScopeTest`. Change set 33 |
| NF-14 | **The clock is India Standard Time** | Done | The application sets its own default zone to `Asia/Kolkata` (`app.time-zone`), in `main()` before Spring starts and again from the property once it is up. Attendance is stored zone-less, so the JVM's zone decides what gets written; the production server keeps UTC and was recording every check in 5h30m early. `IndiaTimeTest` holds it, and fails if the application is left on the machine's zone. Change set 32 |
| NF-17 | **Twenty-five rows to a page, everywhere** | Done | One constant each side: `PAGE_SIZE` in `frontend/src/pageSize.js`, imported by all eleven paging screens, and `PageSizeTest`, which walks every `@RestController` by reflection and fails on any `size` defaulting to anything else - including one added later. Three deliberate exceptions (two dropdowns and one lookup) say so in a comment. Change sets 40, 41 |
| UA-7 | **The signed-in account shows a face** | Done | The profile carries `photoSewadarId` and `sewadarPhotoUpdatedAt`: an account with its own photo keeps it, otherwise the header and My Profile read the photo on the person's sewadar record, found by the account link or by GR. No. A pointer, never a copy, and kept separate from `sewadarId` so borrowing a face does not narrow what the account may see. `ProfilePhotoTest`. Change set 41 |
| SW-11 | **The three badge lists are three lists** | Done | Issued, Received and Pending are the sewadar search with `badgeIssued`/`badgeReceived` set; before change set 40 the flags stopped at the screen and all three tiles opened the whole register. Searching inside an open list stays inside it. `BadgeFilterTest`. Change set 40 |
| UA-5 | **The username can be corrected** | Done | The edit dialog carries the username as well as the create form. A rename checks the new name is free and refuses in words rather than with a constraint error, and is written to the log with who did it and the name it replaced. Change set 40 |
| UA-6 | **An account shows its sewadar's photo** | Done | On the grid and now in the edit dialog too: an account with no picture of its own displays the photo of the sewadar it is linked to, read by id - the bytes are never copied. Uploading still sets a photo on the account itself. Change sets 39, 40 |
| NF-13 | **One session per browser tab** | Done | The token and profile live in `sessionStorage`, not `localStorage`: two people can be signed in side by side in one browser and neither reaches the other's screens. A token left by an older build is cleared on load. Change set 28 |
| NF-4 | **CORS** | Done | Driven by `app.cors.allowed-origins`; a foreign origin is rejected |
| NF-5 | Authorization failures return 403, not 401 | Done | `RestAuthEntryPoints` gives both the same JSON envelope |
| NF-6 | Photos must not be publicly readable | Done | Endpoint stays authenticated; the UI fetches blobs with the bearer token |
| NF-7 | Latest React on this machine | Done* | React 19.2.8 + Router 7.18.3 + Vite 6.4.3 + plugin-react 4.7.0. Vite 7/8 need Node `^20.19` and this machine has 20.10, so Vite 6 is the newest that installs with no warnings |
| NF-8 | Dev server reachable on `localhost` | Done | `host: true` so both IPv4 and IPv6 answer; `strictPort` so a busy port fails loudly |
| NF-12 | **Dark mode** | Done | Two blocks of token overrides; system / light / dark, chosen under Profile > Appearance and remembered per browser. The explicit choice beats the OS setting in both directions |
| NF-11 | **One design system** | Done | `app.css` rewritten in change set 15 as ten numbered sections - tokens, base, controls, data, overlays, shell, login, dashboard, screens, responsive. Colour is reserved for the primary action and for row status |
| NF-10 | **Usable on a phone, Android and iOS** | Done | Drawer navigation with a backdrop, 16px form fields so iOS does not zoom, safe-area insets for the notch, `dvh` heights, 44px touch targets, single-column layouts and bottom-sheet dialogs below 640px. Verified by rendering at 390, 360 and 320px |
| NF-9 | **Aadhaar masked by role** | Done* | `XXXX XXXX 9012` for every role below Admin / Office Admin, applied on the server. Encryption at rest was built and then dropped by decision — detail below |

**NF-9 — Aadhaar masked by role.** Status: **Done\***

This was [open question 3](#13-open-questions). It was answered in two halves, and
only one of them was kept.

*On screen — built and kept.*

| Role | Sees |
|---|---|
| Admin, Office Admin | the whole number — they are the roles that register and correct it |
| Sewadar | their own number in full |
| Co-ordinator, Zone Incharge, Supervisor, Office User | `XXXX XXXX 9012` |

The decision is `CurrentUserService.canViewFullAadhar(sewadarId)`, next to every other
role rule, and it is applied on the **server**: the masked responses never carry the
other eight digits, so no UI mistake can expose them. It covers the grid, the detail
view and the Mark Attendance card. The response also carries `aadharMasked`, which is
what stops the UI reformatting a masked value into its last four digits and what hides
the attendance card's show/hide toggle when there is nothing to reveal. A masked value
submitted back to the server is refused rather than overwriting the real number.

*At rest — built, then removed on request.* AES-256-GCM in the column with a keyed
fingerprint column carrying uniqueness. It worked and was verified end to end, but it
made a secret load-bearing: the key derived from `AADHAR_SECRET` became part of the
backup, a dump restored without it was unreadable, and changing it on a live database
destroyed every number. That was judged too much operational weight for this
application, so it was reverted whole — see [CHANGELOG](CHANGELOG.md) change set 11,
which records the design in enough detail to rebuild it if the rules change.

> **What this means in practice.** Masking is access control inside the application.
> Anyone who can read the table directly — a DBA, a backup file, a stolen dump — sees
> every Aadhaar number. If a rule requires otherwise, encryption is the answer and it
> is a deliberate, separate piece of work.

---

## 8. Screens and navigation

**UI-1 — Login screen.** Status: **Done**

Rebuilt in change set 23 from a supplied mock-up and its source: a blue sky hero
carrying the wordmark, *Together We Serve*, the promise, a cloud-and-orbit
illustration, the four modules and the quote; a white panel on the right with the
two fields, remember me, the Login button and **Google** beneath it.

| Sub-requirement | Status |
|---|---|
| Blue background | Done — layered gradient, drawn artwork, no image download |
| Remove "Sign in to manage attendance, sewa records and reports." | Done |
| Remove the `ADMIN_PASSWORD` hint block | Done |
| Field labelled **Login**, not Username | Done |
| Google single sign-on only, no Microsoft | Done — Microsoft removed on request |

> Google, *Forgot password* and *Sign up* are drawn because the design carries them.
> None is wired: single sign-on is not configured, and accounts and password resets
> belong to an administrator. Each says so when used.

**UI-2 — Left-side navigation behind the login.** Status: **Done**

Dashboard, Sewadar, Attendance, Badge Detail, Report, Request, User Account, Setup,
Contact — in that order, as requested in change set 20. User Account and Setup are
Admin and Office Admin. Built from the `menu` array in the login response, so the
**server** decides what appears; route guards repeat the check but never enforce it
alone.

Attendance carries four tabs: **Mark Attendance** (now, one press), **Zone
Attendance** (a whole zone at once), **Manage Past Attendance** (a missed day, typed
times) and **Records**.

**UI-3 — Branding.** Status: **Done\*** — sidebar, login, browser tab, footer, About
page, Swagger title and the report email footer.

> The name is **Pandal Office Management**. Change set 15 renamed it to the wordmark
> on a supplied mockup; [change set 16](CHANGELOG.md) reverted that - a mockup shows
> what a screen should look like, not what the product is called. The tagline
> **"People · Service · Community"** from the design is kept, as a design element
> under the wordmark.
>
> The front-end strings live in one place, `frontend/src/brand.js`; the backend keeps
> its own copy for the Swagger title (`OpenApiConfig`) and the report footer
> (`ReportExporter`).

**UI-4 — Home page per the supplied design.** Status: **Done\***

Welcome block with long date and quote card, four stat tiles with coloured icon
squares, Quick Actions row, and two panels (Recent Activities, This Month). Topbar
search, notification bell and user dropdown; white sidebar with blue active state;
footer bar.

> Two decisions on UI-4:
> 1. **No invented trend percentages.** The design shows "+5% from last month". The
>    API has no previous-period figures and printing made-up numbers on a dashboard
>    would be worse than omitting them. Each tile shows a real sub-label instead.
> 2. **The sidebar keeps this application's modules.** See
>    [Section 12](#12-not-built-and-why).

**UI-5 — Contact screen.** Status: **Done** — message delivered to the office admins.
**UI-6 — About screen.** Status: **Done** — role matrix with the signed-in role highlighted.
**UI-7 — Profile screen with password change.** Status: **Done**

---

## 9. Integrations

**IN-1 — Email integration.** Status: **Partial**

Built with `spring-boot-starter-mail`. A shared report goes out as an HTML table
(first 100 rows) with the full Excel workbook attached. Zone change events notify the
office admins.

**Not verified against a real SMTP server** — no credentials were available. Only the
disabled-channel path was exercised. Configure `EMAIL_ENABLED` and the `MAIL_*`
variables in the IntelliJ run configuration to switch it on.

**IN-2 — WhatsApp integration.** Status: **Partial**

Built against the Meta WhatsApp Cloud API. A share sends a text summary with the top
ten sewadars. Ten-digit numbers get the country code prefixed.

**Not verified against Meta** — no phone number id or access token was available.
Also: Cloud API free-form text only reaches numbers that messaged your business in
the last 24 hours; outside that window Meta requires an approved template, which
`WhatsAppService.sendTemplate(...)` supports but **no template has been registered**.

**IN-3 — Share a report.** Status: **Done** — email and/or WhatsApp, per report. While
a channel is off the request is accepted, the composed message is logged, and the
response reports `emailSent: false` with the reason, so the flow is testable without
credentials.

---

## 10. Data model

| Table | Holds |
|---|---|
| `zones` | Zone master data |
| `users` | Login accounts, BCrypt hash, role, photo stamp |
| `user_zones` | Zones a zone-scoped account may reach |
| `sewadars` | Sewadar register including Aadhaar, Area, Center/Point, photo stamp |
| `attendance` | One row per sewadar / date / sewa type, with in and out time |
| `zone_change_requests` | Requests with an approval trail |
| `photos` | Image bytes for sewadars and accounts, one row per owner, `mediumblob` |

The DDL lives in
[backend/src/main/resources/db/schema.sql](backend/src/main/resources/db/schema.sql),
which is the file the application runs at startup rather than a second copy that can
drift from it. Hibernate is set to `validate`, so the entities and that script cannot
disagree without the application refusing to start.

**Column order and names** were specified in change set 14: every table reads
identity, then the record, then flags and links, then the four audit columns last;
names are camelCase matching the Java field (`badgeNo`, `mobileNo`, `emailId`,
`aadharNo`, `createdAt`, `createdBy`). The sewadar table leads with the registration
form in its own order: `badgeNo, name, fatherOrHusbandName, gender, birthDate,
mobileNo, emailId, aadharNo, zoneId, area, point`.

---

## 11. API surface

| Group | Base | Notable |
|---|---|---|
| Authorization | `/api/auth` | `login`, `me`, `dashboard`, `change-password` |
| Sewadars | `/api/sewadars` | CRUD, `me`, `for-attendance`, `{id}/photo` |
| Attendance | `/api/attendance` | `lookup`, `status/{id}`, `check-in`, `check-out`, `bulk-check-in`, `bulk-check-out`, `bulk`, CRUD |
| Reports | `/api/reports` | `monthly`, `roster-sewa`, `construction-sewa`, `range`, each with `/excel`, `/csv`, `/share` |
| Requests | `/api/requests/zone-change` | raise, `review`, `cancel` |
| Zones | `/api/zones` | CRUD |
| Users | `/api/users` | CRUD, `{id}/photo` (Admin and Office Admin; only an Admin may touch an Admin account) |
| Reference | `/api/meta` | `options`, `channels`, `contact` |

Two things a caller cannot guess, stated in the OpenAPI description itself:

1. **Every read is scoped to the token.** The same endpoint returns different rows
   for different callers - a Zone Incharge sees their zones, a Sewadar sees their own
   record, an Admin sees everything.
2. **Dates are calendar dates in the server's timezone**, sent as `yyyy-MM-dd`, never
   derived from a UTC instant. East of Greenwich the UTC date is still yesterday for
   part of every morning, which silently writes attendance to the wrong day
   ([change set 27](CHANGELOG.md)).

`LoginResponse` carries `hasPhoto` and `photoUpdatedAt` so a client can draw the
signed-in account's picture without a second call; `photoUpdatedAt` is the cache key,
so a new upload replaces the old image.

Full parameter and response detail is in Swagger at `/swagger-ui.html`.

---

## 12. Not built, and why

**NB-1 — Employees, Leave Management, Visitors, Meetings & Rooms, Assets & Inventory,
Helpdesk, Settings.**

These appear in the supplied design's sidebar, but they belong to a different
application. None exists in this backend. Adding the menu items would have produced
navigation leading to dead screens, so the design's *visual language* was applied to
this application's real modules instead. Building them is a substantial separate
piece of work — say so if it is wanted and it can be scoped.

~~**NB-2 — Dashboard trend percentages.**~~ **Built in
[change set 15](CHANGELOG.md).** The dashboard endpoint now measures the period
before - yesterday for the day tiles, the end of last month for the register - and
returns present and absent days per month for the overview chart. Both are scoped by
role. A trend is shown only when there is a real comparison to make: a previous value
of zero has no percentage, and an unchanged figure has no trend, so the tile falls
back to its plain sub-label rather than printing a number nobody can act on.

**NB-3 — A separate check-in location field.** *Location* currently shows the zone
name.

---

## 13. Open questions

1. **Co-ordinator scope** — same as Zone Incharge, or above it with approval rights?
2. **Check-in location** — should it be its own field, or captured per check-in?
3. **Aadhaar at rest** — **masking answered, encryption declined.** Masked for every
   role below Admin / Office Admin, see [NF-9](#7-non-functional-requirements).
   Encryption at rest was built and then removed on request, because it made a secret
   load-bearing for the whole database. **Still open:** if a rule ever requires the
   numbers unreadable to whoever holds a database dump, masking does not provide that
   and the encryption has to come back — [CHANGELOG](CHANGELOG.md) change set 11 keeps
   the design. One smaller decision was taken rather than asked: a **Sewadar sees
   their own number in full**, on the grounds that it is theirs.
4. **Email and WhatsApp credentials** — needed to verify IN-1 and IN-2 end to end.
5. **Node upgrade** — moving to Node 20.19+ or 22 LTS would allow Vite 8; no
   application code would change.
6. **Which of NB-1** matters, if any.

---

## 14. Verification summary

Everything below was run against a live server, not inferred.

| Area | Result |
|---|---|
| Backend build | clean |
| `RoleScopeTest` | **7/7 pass** (H2), one case per role |
| `AadharPrivacyTest` | **9/9 pass** (H2) — masking per role across grid, detail and attendance card; masked value refused on save |
| `PhotoStorageTest` | **7/7 pass** (H2) — round trip, replace in place, both owner types, the guards |
| Frontend build | clean |
| JWT | no token / garbage / tampered → 401; valid → 200 |
| Authorization | Zone Incharge on `/api/users` → 403; on `/api/sewadars` → 200 |
| CORS | `localhost:5173` allowed, foreign origin rejected |
| Role isolation | Admin 4 sewadars, Zone Incharge 3 (own zone), Sewadar 1 (own record) |
| Attendance lookup | all four keys resolve to the right person |
| Check in / out | hours derived (08:00→16:45 = 8.75); every guard refuses correctly |
| Bulk check in / out | 3 marked; one bad id skipped without aborting the batch |
| Photos | round-trip byte-identical; renamed text file rejected; unauthenticated fetch 401 |
| Photo replace | 480 kB uploaded then replaced with 750 kB: one row, updated in place, served bytes identical |
| Photo column | `mediumblob` on a new database; an existing `tinyblob` widened at startup and verified with `ddl-auto=none` |
| Photo limits | over 3 MB refused before the database is touched; a refused upload leaves the stored photo unchanged |
| Photo, oversized | a file past the servlet limit used to return a bare 500; now 400 with the exact limit, checked in the browser first too |
| Photo, owner deleted | the image is removed with its owner - it used to be orphaned, and ids are reused |
| Account editing | every field the API accepts is on the Edit dialog and was saved end to end: name, role, email, mobile, zones, enabled, password reset |
| Status pills | `.state` 38px tall and `.act-grid .btn` 38px tall — measured identical; pill width is its own content, not the row |
| Mobile layout | rendered at 390, 360 and 320px: no horizontal overflow at any width, filters stack, tiles two-up to 340px then one, tables scroll inside their own box |
| Mobile navigation | drawer opens with a dimming backdrop at 390 and 360, page behind locked; sidebar docked and no backdrop at 1100 |
| Tile drill-down | all four tiles match the rows behind them for all six roles (5/2/1/2 global, 3/1/1/1 Zone 1, 2/1/0/1 Zone 2) |
| Tile counts people | a sewadar present for two sewa types on one day counts once, not twice |
| Drill-down scope | zone roles get only their zones; a Sewadar typing the URL gets only their own record |
| Badge / Attendance / Report scope | measured per role: badges 5/3/2, attendance 6/4/2, report rows 5/3/2 by scope |
| User Accounts | Admin and Office Admin 200 and in the menu; Office User, Co-ordinator, Zone Incharge, Supervisor 403 and absent |
| Privilege escalation | Office Admin refused on creating an Admin, editing an Admin, promoting to Admin and photographing an Admin |
| Account photo | uploaded, replaced and served back byte-identical, by Admin and by Office Admin |
| Reports | monthly / roster / construction / range; valid `.xlsx` with TOTAL row; CSV |
| Zone change | raised, approval blocked for non-admins, admin approved, sewadar moved, history kept its zone |
| Sewadar fields | all 11 created and updated; Aadhaar normalised; duplicates and short values rejected |
| Aadhaar masking | Zone Incharge and Office User get `XXXX XXXX 9012` in the grid, the detail view and the attendance card; Admin and Office Admin get all 12 |
| Aadhaar uniqueness | `1234-5678-9012` refused against a stored `123456789012` |
| Aadhaar card scan | full 12 digits and `1234 5678 9012` both resolve to B00123; `12345678` matches nothing |
| Aadhaar write-back | submitting `XXXX XXXX 9012` is refused and the stored number is unchanged |
| Tab counts | three scoped endpoints; Sewadar, User Account and Request open on the design's tab strip with real numbers |
| Dark mode | rendered light and dark side by side at 1180px; both legible |
| Phone tab bar | rendered at 390px in both themes, active item marked |
| Design match | login and dashboard rendered and checked against the supplied design: copy, the four tiles, the Settings group, and the palette |
| Redesign, desktop | login rendered from the built application; dashboard, shell and screens rendered at 1440px and checked against the supplied design |
| Redesign, phone | dashboard and login rendered at 390 and 360px; two wrapping faults found by looking at them and fixed |
| Chart palette | validated: CVD separation ΔE 24.7, normal-vision 33.6, contrast above 3:1 - all checks pass |
| Zones and About | both were finished pages with no route and no menu entry, so neither could be opened; now routed, and ZONES added to the Admin and Office Admin menus |
| Schema from the script | fresh MySQL: tables created by `db/schema.sql`, `validate` passed, zones and admin seeded |
| Column order | last four columns of every audited table are `createdAt, createdBy, updatedAt, updatedBy`; sewadars leads with the registration form in the requested order |
| Restart | second start is a no-op and the data survives; nothing in the application drops a table |
| After the rename | sewadar create, photo upload, attendance with derived hours, card scan, dashboard, tile drill-down, monthly report and zone change request all verified against the new schema |
| Schema after the revert | generated `aadhar_number VARCHAR(12)` with `uk_sewadar_aadhar`, identical to the existing database — no migration needed |
| Protections | last enabled admin protected; sewadar with history deactivated not deleted |
