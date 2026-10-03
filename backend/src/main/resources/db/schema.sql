-- =============================================================================
--  Pandal Office Management - schema
--
--  This file, not Hibernate, is what creates the tables. It runs on every start
--  (spring.sql.init.mode=always) and every statement is CREATE ... IF NOT EXISTS,
--  so the first start builds the schema and every start after it does nothing.
--  Hibernate is then set to ddl-auto=validate: it checks the entities against
--  what is here and refuses to start if they have drifted apart, rather than
--  silently altering the database.
--
--  WHY A SCRIPT AT ALL
--  Hibernate cannot control the order of columns - it appends each new one to the
--  end of the table. The order below is deliberate and is the reason this file
--  exists:
--
--    1. identity first      - id, then the business key
--    2. the record itself   - the fields someone reading a row wants to see
--    3. flags and links     - state, and foreign keys to other tables
--    4. audit last          - createdAt, createdBy, updatedAt, updatedBy, always
--                             the final four columns of every table
--
--  Column order has no effect on queries or performance. It affects SELECT * and
--  what the table looks like in Workbench, which is the point.
--
--  NAMING
--  Columns are camelCase, matching the Java field on the entity, so there is one
--  name to remember rather than two: badgeNo, createdAt, mobileNo, aadharNo.
--  MySQL matches column names case-insensitively, so this is safe here. It is not
--  portable to PostgreSQL, which folds unquoted identifiers to lower case - that
--  move would mean quoting every identifier.
--
--  TO START OVER
--  This script never drops anything. Dropping is a decision, not a side effect of
--  a restart, so it is a command you run deliberately:
--
--    DROP DATABASE office_management;
--
--  The next start recreates the schema and DataBootstrap seeds the zones and the
--  first admin account.
-- =============================================================================


-- --------------------------------------------------------------- zones -----
CREATE TABLE IF NOT EXISTS zones (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  code          VARCHAR(40)  NOT NULL,
  name          VARCHAR(120) NOT NULL,
  description   VARCHAR(255),
  centre        VARCHAR(120),
  active        BIT(1)       NOT NULL DEFAULT b'1',
  createdAt     DATETIME(6),
  createdBy     VARCHAR(60),
  updatedAt     DATETIME(6),
  updatedBy     VARCHAR(60),
  PRIMARY KEY (id),
  CONSTRAINT uk_zone_code UNIQUE (code),
  -- Two zones sharing a name is as confusing as two sharing a code.
  CONSTRAINT uk_zone_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- --------------------------------------------------------------- users -----
CREATE TABLE IF NOT EXISTS users (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  username           VARCHAR(60)  NOT NULL,
  passwordHash       VARCHAR(100) NOT NULL,
  fullName           VARCHAR(150) NOT NULL,
  emailId            VARCHAR(150),
  mobileNo           VARCHAR(20),
  role               VARCHAR(30)  NOT NULL,
  roleId             BIGINT,
  -- The sewadar this login belongs to, and their gender. The gender is what
  -- narrows everything the account sees: a male login reads the male register,
  -- a female login the female one. Nullable, because an account made before this
  -- existed has neither until somebody fills them in.
  badgeNo            VARCHAR(40),
  gender             VARCHAR(10),
  enabled            BIT(1)       NOT NULL DEFAULT b'1',
  mustChangePassword BIT(1)       NOT NULL DEFAULT b'0',
  lastLoginAt        DATETIME(6),
  photoUpdatedAt     DATETIME(6),
  createdAt          DATETIME(6),
  createdBy          VARCHAR(60),
  updatedAt          DATETIME(6),
  updatedBy          VARCHAR(60),
  PRIMARY KEY (id),
  CONSTRAINT uk_user_username UNIQUE (username),
  -- Nullable, so as many accounts as you like may have no email; where one is
  -- given it belongs to a single account.
  CONSTRAINT uk_user_email UNIQUE (emailId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Which zones a zone-scoped account may reach. A join table, so no audit columns.
CREATE TABLE IF NOT EXISTS user_zones (
  userId BIGINT NOT NULL,
  zoneId BIGINT NOT NULL,
  PRIMARY KEY (userId, zoneId),
  CONSTRAINT fk_user_zones_user FOREIGN KEY (userId) REFERENCES users (id),
  CONSTRAINT fk_user_zones_zone FOREIGN KEY (zoneId) REFERENCES zones (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ------------------------------------------------------------ sewadars -----
-- The registration form's own order, field for field, then the details behind
-- the "Additional details" toggle, then flags and links, then audit.
CREATE TABLE IF NOT EXISTS sewadars (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,

  -- the registration form, in the order it is filled in
  badgeNo             VARCHAR(40)  NOT NULL,
  name                VARCHAR(150) NOT NULL,
  fatherOrHusbandName VARCHAR(150),
  gender              VARCHAR(10),
  birthDate           DATE,
  age                 INT,
  mobileNo            VARCHAR(20),
  emailId             VARCHAR(150),
  aadharNo            VARCHAR(12),
  zoneId              BIGINT       NOT NULL,
  area                VARCHAR(120),
  point               VARCHAR(120),

  -- additional details
  address             VARCHAR(400),
  bloodGroup          VARCHAR(10),
  department          VARCHAR(120),
  status              VARCHAR(20),
  roleId              BIGINT,
  sewaPointId         BIGINT,
  joiningDate         DATE,

  -- state and links
  badgeIssued         BIT(1)       NOT NULL DEFAULT b'0',
  badgeReceived       BIT(1)       NOT NULL DEFAULT b'0',
  exempted            BIT(1)       NOT NULL DEFAULT b'0',
  photoUpdatedAt      DATETIME(6),
  userId              BIGINT,

  -- audit
  createdAt           DATETIME(6),
  createdBy           VARCHAR(60),
  updatedAt           DATETIME(6),
  updatedBy           VARCHAR(60),

  PRIMARY KEY (id),
  CONSTRAINT uk_sewadar_badge  UNIQUE (badgeNo),
  CONSTRAINT uk_sewadar_aadhar UNIQUE (aadharNo),
  CONSTRAINT uk_sewadar_email  UNIQUE (emailId),
  CONSTRAINT uk_sewadar_user   UNIQUE (userId),
  CONSTRAINT fk_sewadar_zone FOREIGN KEY (zoneId) REFERENCES zones (id),
  CONSTRAINT fk_sewadar_user FOREIGN KEY (userId) REFERENCES users (id),
  INDEX idx_sewadar_zone (zoneId),
  INDEX idx_sewadar_name (name),
  INDEX idx_sewadar_area (area)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- --------------------------------------------------------------- areas -----
-- The middle level of the office's geography: zone > area > satsang point.
-- Set up on the Setup screen so the sewadar form offers a list rather than a
-- free text box.
CREATE TABLE IF NOT EXISTS areas (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  name      VARCHAR(120) NOT NULL,
  active    BIT(1)       NOT NULL DEFAULT b'1',
  createdAt DATETIME(6),
  createdBy VARCHAR(60),
  updatedAt DATETIME(6),
  updatedBy VARCHAR(60),
  PRIMARY KEY (id),
  CONSTRAINT uk_area_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ------------------------------------------------------ satsang_points -----
-- The place a sewadar actually reports to, inside an area.
CREATE TABLE IF NOT EXISTS satsang_points (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  name      VARCHAR(120) NOT NULL,
  active    BIT(1)       NOT NULL DEFAULT b'1',
  createdAt DATETIME(6),
  createdBy VARCHAR(60),
  updatedAt DATETIME(6),
  updatedBy VARCHAR(60),
  PRIMARY KEY (id),
  CONSTRAINT uk_point_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------- attendance -----
-- One row per sewadar, per date, per sewa type. The unique key is what makes
-- re-saving a sewa sheet an update instead of a duplicate.
CREATE TABLE IF NOT EXISTS attendance (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  sewadarId      BIGINT      NOT NULL,
  -- Denormalised from the sewadar so a record keeps the zone the sewa was
  -- actually performed in, even after an approved zone change.
  zoneId         BIGINT      NOT NULL,
  attendanceDate DATE        NOT NULL,
  sewaType       VARCHAR(30) NOT NULL,
  status         VARCHAR(20) NOT NULL,
  inTime         TIME(6),
  outTime        TIME(6),
  hours          DOUBLE,
  remarks        VARCHAR(400),
  markedBy       VARCHAR(60),
  createdAt      DATETIME(6),
  createdBy      VARCHAR(60),
  updatedAt      DATETIME(6),
  updatedBy      VARCHAR(60),
  PRIMARY KEY (id),
  CONSTRAINT uk_attendance_sewadar_date_type UNIQUE (sewadarId, attendanceDate, sewaType),
  CONSTRAINT fk_attendance_sewadar FOREIGN KEY (sewadarId) REFERENCES sewadars (id),
  CONSTRAINT fk_attendance_zone    FOREIGN KEY (zoneId)    REFERENCES zones (id),
  INDEX idx_attendance_date (attendanceDate),
  INDEX idx_attendance_zone_date (zoneId, attendanceDate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ------------------------------------------------ zone_change_requests -----
CREATE TABLE IF NOT EXISTS zone_change_requests (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  sewadarId     BIGINT      NOT NULL,
  fromZoneId    BIGINT      NOT NULL,
  toZoneId      BIGINT      NOT NULL,
  reason        VARCHAR(500),
  status        VARCHAR(20) NOT NULL,
  requestedBy   VARCHAR(60),
  reviewedBy    VARCHAR(60),
  reviewedAt    DATETIME(6),
  reviewRemarks VARCHAR(500),
  createdAt     DATETIME(6),
  createdBy     VARCHAR(60),
  updatedAt     DATETIME(6),
  updatedBy     VARCHAR(60),
  PRIMARY KEY (id),
  CONSTRAINT fk_zcr_sewadar FOREIGN KEY (sewadarId)  REFERENCES sewadars (id),
  CONSTRAINT fk_zcr_from    FOREIGN KEY (fromZoneId) REFERENCES zones (id),
  CONSTRAINT fk_zcr_to      FOREIGN KEY (toZoneId)   REFERENCES zones (id),
  INDEX idx_zcr_status (status),
  INDEX idx_zcr_sewadar (sewadarId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- -------------------------------------------------------------- photos -----
-- Image bytes live here rather than on sewadars or users: a LOB on the owning
-- row is loaded by every list query, so a 200 row page would drag 200 photos
-- into memory. The owner keeps only a photoUpdatedAt stamp.
--
-- MEDIUMBLOB, not BLOB: 3 MB is the limit the application enforces, and a
-- TINYBLOB here once made every upload fail with "Data too long for column".
CREATE TABLE IF NOT EXISTS photos (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  ownerType   VARCHAR(20)  NOT NULL,
  ownerId     BIGINT       NOT NULL,
  contentType VARCHAR(100) NOT NULL,
  sizeBytes   BIGINT       NOT NULL,
  data        MEDIUMBLOB   NOT NULL,
  createdAt   DATETIME(6),
  createdBy   VARCHAR(60),
  updatedAt   DATETIME(6),
  updatedBy   VARCHAR(60),
  PRIMARY KEY (id),
  -- Owner type is part of the key: user 1 and sewadar 1 can both have a photo.
  CONSTRAINT uk_photo_owner UNIQUE (ownerType, ownerId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- Enumerated values, all stored as strings:
--   users.role                 ADMIN | OFFICE_ADMIN | COORDINATOR | ZONE_INCHARGE
--                              | SUPERVISOR | OFFICE_USER | SEWADAR
--   sewadars.gender            MALE | FEMALE | OTHER
--   sewadars.primarySewaType   ROSTER_SEWA | CONSTRUCTION_SEWA | OFFICE_SEWA | OTHER
--   attendance.sewaType        ROSTER_SEWA | CONSTRUCTION_SEWA | OFFICE_SEWA | OTHER
--   attendance.status          PRESENT | HALF_DAY | LEAVE | ABSENT
--   zone_change_requests.status PENDING | APPROVED | REJECTED | CANCELLED
--   photos.ownerType           SEWADAR | USER

-- The roles a sewadar can hold. Shown on the screens as "Designation".
CREATE TABLE IF NOT EXISTS roles (
  id        BIGINT      NOT NULL AUTO_INCREMENT,
  name      VARCHAR(80) NOT NULL,
  active    BIT(1)      NOT NULL DEFAULT b'1',

  createdAt DATETIME(6),
  createdBy VARCHAR(60),
  updatedAt DATETIME(6),
  updatedBy VARCHAR(60),

  PRIMARY KEY (id),
  CONSTRAINT uk_role_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Weekly seating sewa: who sat on which Sunday or Thursday, and the token they
-- were given. The token is per day, not per person - the screens call it Badge No.
CREATE TABLE IF NOT EXISTS weeklySeatingSewa (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  sewadarId  BIGINT      NOT NULL,
  sewaDate   DATE        NOT NULL,
  weekDay    VARCHAR(10) NOT NULL,
  tokenNo    VARCHAR(40) NOT NULL,
  badgeIssued    BIT(1)  NOT NULL DEFAULT b'0',
  badgeReceived  BIT(1)  NOT NULL DEFAULT b'0',
  issuedAt       TIME,
  receivedAt     TIME,

  createdAt  DATETIME(6),
  createdBy  VARCHAR(60),
  updatedAt  DATETIME(6),
  updatedBy  VARCHAR(60),

  PRIMARY KEY (id),
  CONSTRAINT uk_weeklySeating_sewadar_date UNIQUE (sewadarId, sewaDate),
  CONSTRAINT uk_weeklySeating_token_date UNIQUE (sewaDate, tokenNo),
  CONSTRAINT fk_weeklySeating_sewadar FOREIGN KEY (sewadarId) REFERENCES sewadars(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Construction sewa, one row per sewadar per day. The count is how many rows a
-- sewadar has, which is why there is no count column to keep in step with them.
CREATE TABLE IF NOT EXISTS constructionSewa (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  sewadarId  BIGINT       NOT NULL,
  sewaDate   DATE         NOT NULL,
  remarks    VARCHAR(300),

  createdAt  DATETIME(6),
  createdBy  VARCHAR(60),
  updatedAt  DATETIME(6),
  updatedBy  VARCHAR(60),

  PRIMARY KEY (id),
  CONSTRAINT uk_constructionSewa_sewadar_date UNIQUE (sewadarId, sewaDate),
  CONSTRAINT fk_constructionSewa_sewadar FOREIGN KEY (sewadarId) REFERENCES sewadars(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sewaPoints (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  name      VARCHAR(120) NOT NULL,
  active    BIT(1)       NOT NULL DEFAULT b'1',

  createdAt DATETIME(6),
  createdBy VARCHAR(60),
  updatedAt DATETIME(6),
  updatedBy VARCHAR(60),

  PRIMARY KEY (id),
  CONSTRAINT uk_sewaPoint_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- Columns added after the first release.
--
-- Every CREATE above is IF NOT EXISTS, which does nothing to a table that is
-- already there, so a new column on an existing table has to be added here.
-- MySQL 8 has no ADD COLUMN IF NOT EXISTS, and this file runs on every start
-- with continue-on-error false, so the add is made conditional by hand: look in
-- INFORMATION_SCHEMA, build the statement only when the column is missing, and
-- execute a harmless SELECT otherwise.
--
-- This runs during spring.sql.init, which is before Hibernate validates the
-- entities - which is the only window in which it is any use.
-- ---------------------------------------------------------------------------

SET @hasExempted := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_SCHEMA = DATABASE()
                       AND TABLE_NAME = 'sewadars'
                       AND COLUMN_NAME = 'exempted');

SET @addExempted := IF(@hasExempted = 0,
    'ALTER TABLE sewadars ADD COLUMN exempted BIT(1) NOT NULL DEFAULT b''0'' AFTER badgeReceived',
    'SELECT 1');

PREPARE addExemptedStmt FROM @addExempted;
EXECUTE addExemptedStmt;
DEALLOCATE PREPARE addExemptedStmt;

-- ---------------------------------------------------------------------------
-- Sewadar: status, designation and sewa point in; city, pincode and the old
-- primary sewa type out.
--
-- Same conditional dance as above: MySQL 8 has no ADD/DROP COLUMN IF EXISTS, and
-- this file runs on every start with continue-on-error false, so each change is
-- checked against INFORMATION_SCHEMA first.
--
-- The three DROPs delete data. That was asked for deliberately - city, pincode
-- and primary sewa type are being retired, not hidden.
-- ---------------------------------------------------------------------------

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'status');
SET @s := IF(@c = 0, 'ALTER TABLE sewadars ADD COLUMN status VARCHAR(20) AFTER department', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'roleId');
SET @s := IF(@c = 0, 'ALTER TABLE sewadars ADD COLUMN roleId BIGINT AFTER status', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'sewaPointId');
SET @s := IF(@c = 0, 'ALTER TABLE sewadars ADD COLUMN sewaPointId BIGINT AFTER roleId', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'city');
SET @s := IF(@c > 0, 'ALTER TABLE sewadars DROP COLUMN city', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'pincode');
SET @s := IF(@c > 0, 'ALTER TABLE sewadars DROP COLUMN pincode', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'primarySewaType');
SET @s := IF(@c > 0, 'ALTER TABLE sewadars DROP COLUMN primarySewaType', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- The foreign keys, added only once the columns exist.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND CONSTRAINT_NAME = 'fk_sewadar_role');
SET @s := IF(@c = 0, 'ALTER TABLE sewadars ADD CONSTRAINT fk_sewadar_role
             FOREIGN KEY (roleId) REFERENCES roles(id)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND CONSTRAINT_NAME = 'fk_sewadar_sewaPoint');
SET @s := IF(@c = 0, 'ALTER TABLE sewadars ADD CONSTRAINT fk_sewadar_sewaPoint
             FOREIGN KEY (sewaPointId) REFERENCES sewaPoints(id)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- The sewadar `active` flag is retired: every record on file simply counts.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'active');
SET @s := IF(@c > 0, 'ALTER TABLE sewadars DROP COLUMN active', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ---------------------------------------------------------------------------
-- Retire the short-lived `designations` table.
--
-- An earlier build put the sewadar's designation in its own table. It is a role,
-- so it now lives in `roles` and the foreign key is `sewadars.roleId`. This
-- carries any value already recorded across, then removes the old column and
-- table. Idempotent, like everything else in this file.
-- ---------------------------------------------------------------------------

-- 1. copy what the old column holds into the new one, matching by name
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'designationId');
SET @s := IF(@c > 0,
    'UPDATE sewadars s
       JOIN designations d ON d.id = s.designationId
       JOIN roles r ON r.name = d.designation
       SET s.roleId = r.id
     WHERE s.roleId IS NULL',
    'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2. drop the old foreign key, if it is still there
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND CONSTRAINT_NAME = 'fk_sewadar_designation');
SET @s := IF(@c > 0, 'ALTER TABLE sewadars DROP FOREIGN KEY fk_sewadar_designation', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3. drop the old column
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'designationId');
SET @s := IF(@c > 0, 'ALTER TABLE sewadars DROP COLUMN designationId', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 4. and the table itself
DROP TABLE IF EXISTS designations;

-- A login account carries a role from the roles table as well.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'users' AND COLUMN_NAME = 'roleId');
SET @s := IF(@c = 0, 'ALTER TABLE users ADD COLUMN roleId BIGINT AFTER role', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'users' AND CONSTRAINT_NAME = 'fk_user_role');
SET @s := IF(@c = 0, 'ALTER TABLE users ADD CONSTRAINT fk_user_role
             FOREIGN KEY (roleId) REFERENCES roles(id)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- Age as written on the slip, for the records that have no birth date.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'age');
SET @s := IF(@c = 0, 'ALTER TABLE sewadars ADD COLUMN age INT AFTER birthDate', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- The day the construction sewa was done, which is not the day it was typed in.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'constructionSewa' AND COLUMN_NAME = 'sewaDate');
SET @s := IF(@c = 0, 'ALTER TABLE constructionSewa ADD COLUMN sewaDate DATE AFTER sewaCount',
             'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- Construction sewa moved from a typed count to a row per day. Existing rows keep
-- their date - a count of five collapses to the one day it was recorded against,
-- because the other four days were never written down and cannot be invented.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'constructionSewa' AND COLUMN_NAME = 'sewaDate'
           AND IS_NULLABLE = 'YES');
SET @s := IF(@c = 1,
             'UPDATE constructionSewa SET sewaDate = COALESCE(sewaDate, DATE(createdAt), CURRENT_DATE) WHERE sewaDate IS NULL',
             'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @s := IF(@c = 1, 'ALTER TABLE constructionSewa MODIFY sewaDate DATE NOT NULL', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- One row per sewadar becomes one row per sewadar per day.
--
-- The new key goes on BEFORE the old one comes off, and the order is the whole
-- point: the foreign key on sewadarId needs an index over that column, and MySQL
-- refuses to drop the last one - "needed in a foreign key constraint". With
-- (sewadarId, sewaDate) in place its leftmost column covers the foreign key, and
-- the old single-column key can then go.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'constructionSewa' AND CONSTRAINT_NAME = 'uk_constructionSewa_sewadar_date');
SET @s := IF(@c = 0,
             'ALTER TABLE constructionSewa ADD CONSTRAINT uk_constructionSewa_sewadar_date UNIQUE (sewadarId, sewaDate)',
             'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'constructionSewa' AND CONSTRAINT_NAME = 'uk_constructionSewa_sewadar');
SET @s := IF(@c = 1, 'ALTER TABLE constructionSewa DROP INDEX uk_constructionSewa_sewadar', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'constructionSewa' AND COLUMN_NAME = 'sewaCount');
SET @s := IF(@c = 1, 'ALTER TABLE constructionSewa DROP COLUMN sewaCount', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- A seating row now records what happened to the badge that day, so the day's
-- issued and received counts come from the day's own rows.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'weeklySeatingSewa' AND COLUMN_NAME = 'badgeIssued');
SET @s := IF(@c = 0,
             'ALTER TABLE weeklySeatingSewa ADD COLUMN badgeIssued BIT(1) NOT NULL DEFAULT b''0'' AFTER tokenNo',
             'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'weeklySeatingSewa' AND COLUMN_NAME = 'badgeReceived');
SET @s := IF(@c = 0,
             'ALTER TABLE weeklySeatingSewa ADD COLUMN badgeReceived BIT(1) NOT NULL DEFAULT b''0'' AFTER badgeIssued',
             'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- When the badge went out and when it came back, to the minute.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'weeklySeatingSewa' AND COLUMN_NAME = 'issuedAt');
SET @s := IF(@c = 0, 'ALTER TABLE weeklySeatingSewa ADD COLUMN issuedAt TIME AFTER badgeReceived',
             'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'weeklySeatingSewa' AND COLUMN_NAME = 'receivedAt');
SET @s := IF(@c = 0, 'ALTER TABLE weeklySeatingSewa ADD COLUMN receivedAt TIME AFTER issuedAt',
             'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- One spelling for the designation, matching the Role enum's own display name.
-- The permission rules ignore the hyphen either way; this is so the screens and the
-- reports do not show the office two different words for one role.
UPDATE roles SET name = 'Co-ordinator' WHERE name = 'Coordinator';

-- The roster sewa type is now called Daily, which is the word the office uses.
-- Existing attendance rows carry the enum's name, so they are renamed with it.
UPDATE attendance SET sewaType = 'DAILY_SEWA' WHERE sewaType = 'ROSTER_SEWA';

-- Office Sewa and Other are no longer sewa types; anything marked against them
-- becomes Daily Sewa, which is what the office would call it now.
UPDATE attendance SET sewaType = 'DAILY_SEWA' WHERE sewaType IN ('OFFICE_SEWA', 'OTHER');

-- The grouping inside an area. Only Indore's areas use it today.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'grouping');
SET @s := IF(@c = 0, 'ALTER TABLE sewadars ADD COLUMN `grouping` VARCHAR(120) AFTER area',
             'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- The other zones a co-ordinator covers. Their own zone stays on sewadars.zoneId.
CREATE TABLE IF NOT EXISTS sewadarZones (
  sewadarId BIGINT NOT NULL,
  zoneId    BIGINT NOT NULL,

  PRIMARY KEY (sewadarId, zoneId),
  CONSTRAINT fk_sewadarZones_sewadar FOREIGN KEY (sewadarId) REFERENCES sewadars(id),
  CONSTRAINT fk_sewadarZones_zone FOREIGN KEY (zoneId) REFERENCES zones(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Local or Outstation.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'sewadars' AND COLUMN_NAME = 'locality');
SET @s := IF(@c = 0, 'ALTER TABLE sewadars ADD COLUMN locality VARCHAR(20) AFTER `grouping`',
             'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;


-- ---------------------------------------------------------------------------
-- Areas and satsang points become lists of their own.
--
-- An area used to belong to a zone and a point to an area, which is what made the
-- Add Sewadar pickers narrow one by the other. The office keeps them as separate
-- lists, so the parent columns go.
--
-- The order below is the whole difficulty. Both foreign keys come off first -
-- including the points' key into areas, because duplicate areas cannot be deleted
-- while a child row still references them. Then the indexes, which MySQL will not
-- drop while they are the last one covering a foreign key. Then the duplicates,
-- because the same area name was allowed once per zone and the new key is on the
-- name alone. Only then the columns, and last the new unique keys.
--
-- Sewadars carry the area and point as text, so nothing on them needs moving;
-- only the lists themselves are rebuilt.
-- ---------------------------------------------------------------------------

-- 1. the foreign keys, children first
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'satsang_points'
             AND CONSTRAINT_NAME = 'fk_point_area');
SET @s := IF(@c = 1, 'ALTER TABLE satsang_points DROP FOREIGN KEY fk_point_area', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'areas'
             AND CONSTRAINT_NAME = 'fk_area_zone');
SET @s := IF(@c = 1, 'ALTER TABLE areas DROP FOREIGN KEY fk_area_zone', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2. the indexes those keys needed
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'satsang_points'
             AND INDEX_NAME = 'uk_point_area_name');
SET @s := IF(@c > 0, 'ALTER TABLE satsang_points DROP INDEX uk_point_area_name', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'satsang_points'
             AND INDEX_NAME = 'idx_point_area');
SET @s := IF(@c > 0, 'ALTER TABLE satsang_points DROP INDEX idx_point_area', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'areas'
             AND INDEX_NAME = 'uk_area_zone_name');
SET @s := IF(@c > 0, 'ALTER TABLE areas DROP INDEX uk_area_zone_name', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'areas'
             AND INDEX_NAME = 'idx_area_zone');
SET @s := IF(@c > 0, 'ALTER TABLE areas DROP INDEX idx_area_zone', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3. the duplicates a per-parent key used to allow; the lowest id of each wins
DELETE a FROM areas a
  JOIN areas b ON LOWER(a.name) = LOWER(b.name) AND a.id > b.id;

DELETE p FROM satsang_points p
  JOIN satsang_points q ON LOWER(p.name) = LOWER(q.name) AND p.id > q.id;

-- 4. the parent columns
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'areas'
             AND COLUMN_NAME = 'zoneId');
SET @s := IF(@c = 1, 'ALTER TABLE areas DROP COLUMN zoneId', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'satsang_points'
             AND COLUMN_NAME = 'areaId');
SET @s := IF(@c = 1, 'ALTER TABLE satsang_points DROP COLUMN areaId', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 5. the new keys, on the name alone
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'areas'
             AND INDEX_NAME = 'uk_area_name');
SET @s := IF(@c = 0, 'ALTER TABLE areas ADD CONSTRAINT uk_area_name UNIQUE (name)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'satsang_points'
             AND INDEX_NAME = 'uk_point_name');
SET @s := IF(@c = 0, 'ALTER TABLE satsang_points ADD CONSTRAINT uk_point_name UNIQUE (name)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;


-- ------------------------------------------------- GR. No and gender on a login
-- Added when the office asked for each account to see its own gender's register.
SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'users' AND COLUMN_NAME = 'badgeNo');
SET @s := IF(@c = 0, 'ALTER TABLE users ADD COLUMN badgeNo VARCHAR(40) AFTER roleId', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

SET @c := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'users' AND COLUMN_NAME = 'gender');
SET @s := IF(@c = 0, 'ALTER TABLE users ADD COLUMN gender VARCHAR(10) AFTER badgeNo', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
