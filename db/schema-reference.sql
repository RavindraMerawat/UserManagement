-- Reference schema. The application generates these tables itself through Hibernate
-- (spring.jpa.hibernate.ddl-auto=update); this file documents the shape and is the
-- starting point if you switch to Flyway or Liquibase.

CREATE DATABASE IF NOT EXISTS sewa_ums
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE sewa_ums;

CREATE TABLE IF NOT EXISTS zones (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  code         VARCHAR(40)  NOT NULL,
  name         VARCHAR(120) NOT NULL,
  description  VARCHAR(255),
  centre       VARCHAR(120),
  active       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at   DATETIME(6),
  updated_at   DATETIME(6),
  created_by   VARCHAR(60),
  updated_by   VARCHAR(60),
  CONSTRAINT uk_zone_code UNIQUE (code)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
  id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
  username             VARCHAR(60)  NOT NULL,
  password_hash        VARCHAR(100) NOT NULL,
  full_name            VARCHAR(150) NOT NULL,
  email                VARCHAR(150),
  mobile               VARCHAR(20),
  role                 VARCHAR(30)  NOT NULL,
  enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
  must_change_password BOOLEAN      NOT NULL DEFAULT FALSE,
  last_login_at        DATETIME(6),
  created_at           DATETIME(6),
  updated_at           DATETIME(6),
  created_by           VARCHAR(60),
  updated_by           VARCHAR(60),
  CONSTRAINT uk_user_username UNIQUE (username)
) ENGINE=InnoDB;

-- Zones a ZONE_INCHARGE or SUPERVISOR account may reach.
-- Empty for ADMIN, OFFICE_ADMIN, OFFICE_USER and SEWADAR, whose scope is derived
-- from the role itself or from the linked sewadar record.
CREATE TABLE IF NOT EXISTS user_zones (
  user_id BIGINT NOT NULL,
  zone_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, zone_id),
  CONSTRAINT fk_uz_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_uz_zone FOREIGN KEY (zone_id) REFERENCES zones (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sewadars (
  id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
  badge_number           VARCHAR(40)  NOT NULL,
  name                   VARCHAR(150) NOT NULL,
  father_or_husband_name VARCHAR(150),
  gender                 VARCHAR(10),
  date_of_birth          DATE,
  mobile                 VARCHAR(20),
  email                  VARCHAR(150),
  address                VARCHAR(400),
  aadhar_number          VARCHAR(12),
  city                   VARCHAR(80),
  pincode                VARCHAR(10),
  blood_group            VARCHAR(10),
  zone_id                BIGINT       NOT NULL,
  area                   VARCHAR(120),
  center_point           VARCHAR(120),
  department             VARCHAR(120),
  primary_sewa_type      VARCHAR(30),
  joining_date           DATE,
  active                 BOOLEAN      NOT NULL DEFAULT TRUE,
  user_id                BIGINT,
  created_at             DATETIME(6),
  updated_at             DATETIME(6),
  created_by             VARCHAR(60),
  updated_by             VARCHAR(60),
  CONSTRAINT uk_sewadar_badge  UNIQUE (badge_number),
  CONSTRAINT uk_sewadar_aadhar UNIQUE (aadhar_number),
  CONSTRAINT uk_sewadar_user   UNIQUE (user_id),
  CONSTRAINT fk_sewadar_zone FOREIGN KEY (zone_id) REFERENCES zones (id),
  CONSTRAINT fk_sewadar_user FOREIGN KEY (user_id) REFERENCES users (id),
  INDEX idx_sewadar_zone (zone_id),
  INDEX idx_sewadar_name (name),
  INDEX idx_sewadar_area (area)
) ENGINE=InnoDB;

-- One row per sewadar, per date, per sewa type. The unique key is what makes
-- re-saving a sewa sheet an update rather than a duplicate.
-- zone_id is denormalised from the sewadar so historic rows keep the zone the sewa
-- was actually performed in, even after a zone change is approved.
CREATE TABLE IF NOT EXISTS attendance (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  sewadar_id      BIGINT      NOT NULL,
  zone_id         BIGINT      NOT NULL,
  attendance_date DATE        NOT NULL,
  sewa_type       VARCHAR(30) NOT NULL,
  status          VARCHAR(20) NOT NULL,
  in_time         TIME(6),
  out_time        TIME(6),
  hours           DOUBLE,
  remarks         VARCHAR(400),
  marked_by       VARCHAR(60),
  created_at      DATETIME(6),
  updated_at      DATETIME(6),
  created_by      VARCHAR(60),
  updated_by      VARCHAR(60),
  CONSTRAINT uk_attendance_sewadar_date_type UNIQUE (sewadar_id, attendance_date, sewa_type),
  CONSTRAINT fk_att_sewadar FOREIGN KEY (sewadar_id) REFERENCES sewadars (id),
  CONSTRAINT fk_att_zone    FOREIGN KEY (zone_id)    REFERENCES zones (id),
  INDEX idx_attendance_date (attendance_date),
  INDEX idx_attendance_zone_date (zone_id, attendance_date)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS zone_change_requests (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  sewadar_id     BIGINT      NOT NULL,
  from_zone_id   BIGINT      NOT NULL,
  to_zone_id     BIGINT      NOT NULL,
  reason         VARCHAR(500),
  status         VARCHAR(20) NOT NULL,
  requested_by   VARCHAR(60),
  reviewed_by    VARCHAR(60),
  reviewed_at    DATETIME(6),
  review_remarks VARCHAR(500),
  created_at     DATETIME(6),
  updated_at     DATETIME(6),
  created_by     VARCHAR(60),
  updated_by     VARCHAR(60),
  CONSTRAINT fk_zcr_sewadar FOREIGN KEY (sewadar_id)   REFERENCES sewadars (id),
  CONSTRAINT fk_zcr_from    FOREIGN KEY (from_zone_id) REFERENCES zones (id),
  CONSTRAINT fk_zcr_to      FOREIGN KEY (to_zone_id)   REFERENCES zones (id),
  INDEX idx_zcr_status (status),
  INDEX idx_zcr_sewadar (sewadar_id)
) ENGINE=InnoDB;

-- Enumerated values stored as strings:
--   users.role                 ADMIN | OFFICE_ADMIN | COORDINATOR | ZONE_INCHARGE | SUPERVISOR
--                              | OFFICE_USER | SEWADAR
--   sewadars.gender            MALE | FEMALE | OTHER
--   sewadars.primary_sewa_type ROSTER_SEWA | CONSTRUCTION_SEWA | OFFICE_SEWA | OTHER
--   attendance.sewa_type       ROSTER_SEWA | CONSTRUCTION_SEWA | OFFICE_SEWA | OTHER
--   attendance.status          PRESENT | HALF_DAY | LEAVE | ABSENT
--   zone_change_requests.status PENDING | APPROVED | REJECTED | CANCELLED

-- If the sewadars table already exists from an earlier run, add the new columns
-- rather than recreating it:
--
--   ALTER TABLE sewadars
--     ADD COLUMN aadhar_number VARCHAR(12) AFTER address,
--     ADD COLUMN area          VARCHAR(120) AFTER zone_id,
--     ADD COLUMN center_point  VARCHAR(120) AFTER area,
--     ADD CONSTRAINT uk_sewadar_aadhar UNIQUE (aadhar_number),
--     ADD INDEX idx_sewadar_area (area);
--
-- With ddl-auto=update Hibernate adds the columns itself on the next start, but it
-- does not add the unique constraint, so run that part by hand in an existing
-- database.
