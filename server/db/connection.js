'use strict';
const fs = require('node:fs');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const { MIGRATIONS } = require('./migrations');

/** Opens the database, applies pragmas and runs pending migrations. */
function openDatabase(file) {
  if (file !== ':memory:') fs.mkdirSync(path.dirname(file), { recursive: true });
  const db = new DatabaseSync(file);
  db.exec(`
    PRAGMA journal_mode = WAL;      -- readers never block the writer
    PRAGMA synchronous = NORMAL;    -- safe with WAL, far fewer fsyncs
    PRAGMA foreign_keys = ON;
    PRAGMA busy_timeout = 5000;
  `);
  migrate(db);
  return db;
}

function migrate(db) {
  const current = db.prepare('PRAGMA user_version').get().user_version;
  if (current >= MIGRATIONS.length) return;
  // Table rebuilds (SQLite's 12-step ALTER procedure) need foreign keys off,
  // otherwise dropping a parent table would cascade-delete child rows.
  // The pragma cannot change inside a transaction, so it wraps the loop.
  db.exec('PRAGMA foreign_keys = OFF');
  try {
    MIGRATIONS.slice(current).forEach((sql, i) => {
      transaction(db, () => {
        db.exec(sql);
        const broken = db.prepare('PRAGMA foreign_key_check').all();
        if (broken.length) throw new Error(`Migration ${current + i + 1} broke ${broken.length} foreign key(s)`);
        db.exec(`PRAGMA user_version = ${current + i + 1}`);
      });
    });
  } finally {
    db.exec('PRAGMA foreign_keys = ON');
  }
}

/** Runs fn inside BEGIN/COMMIT, rolling back on any error. Not re-entrant. */
function transaction(db, fn) {
  db.exec('BEGIN IMMEDIATE');
  try {
    const result = fn();
    db.exec('COMMIT');
    return result;
  } catch (err) {
    db.exec('ROLLBACK');
    throw err;
  }
}

module.exports = { openDatabase, transaction };
