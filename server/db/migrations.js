'use strict';

/**
 * Ordered, append-only list of schema migrations. The index of each entry is
 * its version; PRAGMA user_version records how many have run. Never edit a
 * shipped migration — add a new one.
 */
const NOW = "(strftime('%Y-%m-%dT%H:%M:%fZ','now'))";

const MIGRATIONS = [
  /* v1 — initial schema */ `
  CREATE TABLE users (
    id             INTEGER PRIMARY KEY,
    username       TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    password_hash  TEXT    NOT NULL,
    role           TEXT    NOT NULL DEFAULT 'admin' CHECK (role IN ('admin')),
    created_at     TEXT    NOT NULL DEFAULT ${NOW},
    last_login_at  TEXT
  );

  CREATE TABLE sessions (
    token_hash  TEXT    PRIMARY KEY,                 -- sha256 of the cookie token
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  INTEGER NOT NULL,                    -- epoch milliseconds
    created_at  TEXT    NOT NULL DEFAULT ${NOW},
    ip          TEXT,
    user_agent  TEXT
  );
  CREATE INDEX idx_sessions_expires ON sessions(expires_at);
  CREATE INDEX idx_sessions_user    ON sessions(user_id);

  CREATE TABLE institutions (
    id        INTEGER PRIMARY KEY,
    name      TEXT NOT NULL UNIQUE,
    province  TEXT NOT NULL
  );

  CREATE TABLE courses (
    id              INTEGER PRIMARY KEY,
    institution_id  INTEGER NOT NULL REFERENCES institutions(id) ON DELETE CASCADE,
    name            TEXT    NOT NULL,
    faculty         TEXT    NOT NULL,
    min_aps         INTEGER NOT NULL CHECK (min_aps BETWEEN 0 AND 42),
    math_min        INTEGER NOT NULL DEFAULT 0 CHECK (math_min BETWEEN 0 AND 100),
    UNIQUE (institution_id, name)
  );

  CREATE TABLE course_subjects (
    course_id  INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    subject    TEXT    NOT NULL,
    PRIMARY KEY (course_id, subject)
  ) WITHOUT ROWID;

  CREATE TABLE course_interests (
    course_id  INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    interest   TEXT    NOT NULL,
    PRIMARY KEY (course_id, interest)
  ) WITHOUT ROWID;

  CREATE TABLE applications (
    id               INTEGER PRIMARY KEY,
    ref              TEXT    NOT NULL UNIQUE,        -- unguessable public id for results links
    idempotency_key  TEXT    UNIQUE,                 -- de-duplicates double submits / retries
    first_name       TEXT    NOT NULL,
    last_name        TEXT    NOT NULL,
    province         TEXT    NOT NULL,
    matric_year      INTEGER NOT NULL,
    aps              INTEGER NOT NULL CHECK (aps BETWEEN 0 AND 42),
    career_goal      TEXT    NOT NULL DEFAULT '',
    study_location   TEXT    NOT NULL DEFAULT '',
    match_count      INTEGER NOT NULL DEFAULT 0,
    created_at       TEXT    NOT NULL DEFAULT ${NOW}
  );
  CREATE INDEX idx_applications_created  ON applications(created_at);
  CREATE INDEX idx_applications_province ON applications(province);

  CREATE TABLE application_subjects (
    application_id  INTEGER NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    subject         TEXT    NOT NULL,
    grade           INTEGER NOT NULL CHECK (grade BETWEEN 0 AND 100),
    points          INTEGER NOT NULL CHECK (points BETWEEN 0 AND 7),
    PRIMARY KEY (application_id, subject)
  ) WITHOUT ROWID;

  CREATE TABLE application_interests (
    application_id  INTEGER NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    interest        TEXT    NOT NULL,
    PRIMARY KEY (application_id, interest)
  ) WITHOUT ROWID;
  CREATE INDEX idx_application_interests_interest ON application_interests(interest);

  CREATE TABLE audit_log (
    id          INTEGER PRIMARY KEY,
    user_id     INTEGER REFERENCES users(id) ON DELETE SET NULL,
    action      TEXT    NOT NULL,
    target      TEXT,
    ip          TEXT,
    created_at  TEXT    NOT NULL DEFAULT ${NOW}
  );
  CREATE INDEX idx_audit_created ON audit_log(created_at);
  `,

  /* v2 — student accounts: users gain a 'student' role and names;
     applications can belong to a signed-in user. */ `
  CREATE TABLE users_v2 (
    id             INTEGER PRIMARY KEY,
    username       TEXT    NOT NULL UNIQUE COLLATE NOCASE,   -- admins: username, students: email
    password_hash  TEXT    NOT NULL,
    role           TEXT    NOT NULL DEFAULT 'student' CHECK (role IN ('admin', 'student')),
    first_name     TEXT    NOT NULL DEFAULT '',
    last_name      TEXT    NOT NULL DEFAULT '',
    created_at     TEXT    NOT NULL DEFAULT ${NOW},
    last_login_at  TEXT
  );
  INSERT INTO users_v2 (id, username, password_hash, role, created_at, last_login_at)
    SELECT id, username, password_hash, role, created_at, last_login_at FROM users;
  DROP TABLE users;
  ALTER TABLE users_v2 RENAME TO users;
  CREATE INDEX idx_users_role ON users(role);

  ALTER TABLE applications ADD COLUMN user_id INTEGER REFERENCES users(id) ON DELETE SET NULL;
  CREATE INDEX idx_applications_user ON applications(user_id, created_at);
  `
];

module.exports = { MIGRATIONS };
