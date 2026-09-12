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
  mobileNo            VARCHAR(20),
  emailId             VARCHAR(150),
  aadharNo            VARCHAR(12),
  zoneId              BIGINT       NOT NULL,
  area                VARCHAR(120),
  point               VARCHAR(120),

  -- additional details
  address             VARCHAR(400),
  bloodGroup          VARCHAR(10),
  city                VARCHAR(80),
  pincode             VARCHAR(10),
  department          VARCHAR(120),
  primarySewaType     VARCHAR(30),
  joiningDate         DATE,

  -- state and links
  badgeIssued         BIT(1)       NOT NULL DEFAULT b'0',
  badgeReceived       BIT(1)       NOT NULL DEFAULT b'0',
  active              BIT(1)       NOT NULL DEFAULT b'1',
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
  zoneId    BIGINT       NOT NULL,
  active    BIT(1)       NOT NULL DEFAULT b'1',
  createdAt DATETIME(6),
  createdBy VARCHAR(60),
  updatedAt DATETIME(6),
  updatedBy VARCHAR(60),
  PRIMARY KEY (id),
  CONSTRAINT uk_area_zone_name UNIQUE (zoneId, name),
  CONSTRAINT fk_area_zone FOREIGN KEY (zoneId) REFERENCES zones (id),
  INDEX idx_area_zone (zoneId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ------------------------------------------------------ satsang_points -----
-- The place a sewadar actually reports to, inside an area.
CREATE TABLE IF NOT EXISTS satsang_points (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  name      VARCHAR(120) NOT NULL,
  areaId    BIGINT       NOT NULL,
  active    BIT(1)       NOT NULL DEFAULT b'1',
  createdAt DATETIME(6),
  createdBy VARCHAR(60),
  updatedAt DATETIME(6),
  updatedBy VARCHAR(60),
  PRIMARY KEY (id),
  CONSTRAINT uk_point_area_name UNIQUE (areaId, name),
  CONSTRAINT fk_point_area FOREIGN KEY (areaId) REFERENCES areas (id),
  INDEX idx_point_area (areaId)
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
