'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { DatabaseSync } = require('node:sqlite');
const { startTestServer, validApplication, ADMIN } = require('./helpers');
const { MIGRATIONS } = require('../server/db/migrations');
const { openDatabase } = require('../server/db/connection');

const STUDENT = { firstName: 'Naledi', lastName: 'Khumalo', email: 'Naledi@Example.com', password: 'matric2025' };
const cookieOf = (res) => res.headers.get('set-cookie').split(';')[0];

test('student accounts', async (t) => {
  const srv = await startTestServer();
  t.after(() => srv.close());

  let cookie;
  await t.test('register creates a student, signs in and never returns the hash', async () => {
    const res = await srv.request('/api/auth/register', { method: 'POST', body: STUDENT });
    assert.equal(res.status, 201);
    assert.equal(res.json.user.role, 'student');
    assert.equal(res.json.user.username, 'naledi@example.com', 'email stored lower-case');
    assert.equal(res.json.user.firstName, 'Naledi');
    assert.equal(res.json.user.passwordHash, undefined);
    assert.match(res.headers.get('set-cookie'), /HttpOnly/);
    cookie = cookieOf(res);
  });

  await t.test('duplicate email (any case) is rejected with 409', async () => {
    const res = await srv.request('/api/auth/register', { method: 'POST', body: { ...STUDENT, email: 'NALEDI@example.com' } });
    assert.equal(res.status, 409);
  });

  await t.test('weak or invalid input gives per-field errors', async () => {
    const res = await srv.request('/api/auth/register', {
      method: 'POST', body: { firstName: '<b>', lastName: '', email: 'not-an-email', password: 'short' }
    });
    assert.equal(res.status, 422);
    for (const f of ['firstName', 'lastName', 'email', 'password']) assert.ok(res.json.error.details[f], f);
    const noDigit = await srv.request('/api/auth/register', { method: 'POST', body: { ...STUDENT, email: 'x@y.com', password: 'onlyletters' } });
    assert.equal(noDigit.status, 422);
  });

  await t.test('me returns the signed-in user', async () => {
    const res = await srv.request('/api/auth/me', { cookie });
    assert.equal(res.status, 200);
    assert.equal(res.json.user.lastName, 'Khumalo');
  });

  await t.test('applications submitted while signed in appear under My applications', async () => {
    const sub = await srv.request('/api/applications', { method: 'POST', body: validApplication({ firstName: 'Naledi' }), cookie });
    assert.equal(sub.status, 201);
    await srv.request('/api/applications', { method: 'POST', body: validApplication({ firstName: 'Anonymous' }) });
    const mine = await srv.request('/api/me/applications', { cookie });
    assert.equal(mine.status, 200);
    assert.equal(mine.json.items.length, 1);
    assert.equal(mine.json.items[0].ref, sub.json.ref);
  });

  await t.test('students cannot open admin endpoints or the admin web login', async () => {
    assert.equal((await srv.request('/api/admin/stats', { cookie })).status, 403);
    const webAdmin = await srv.request('/api/admin/login', { method: 'POST', body: { username: STUDENT.email, password: STUDENT.password } });
    assert.equal(webAdmin.status, 401);
  });

  await t.test('login by email is case-insensitive; logout revokes the session', async () => {
    const res = await srv.request('/api/auth/login', { method: 'POST', body: { email: 'NALEDI@EXAMPLE.COM', password: STUDENT.password } });
    assert.equal(res.status, 200);
    const c = cookieOf(res);
    assert.equal((await srv.request('/api/auth/logout', { method: 'POST', cookie: c })).status, 200);
    assert.equal((await srv.request('/api/auth/me', { cookie: c })).status, 401);
    assert.equal((await srv.request('/api/me/applications', { cookie: c })).status, 401);
  });

  await t.test('admins sign in through the same endpoint and keep admin access', async () => {
    const res = await srv.request('/api/auth/login', { method: 'POST', body: { username: ADMIN.username, password: ADMIN.password } });
    assert.equal(res.status, 200);
    assert.equal(res.json.user.role, 'admin');
    assert.equal((await srv.request('/api/admin/stats', { cookie: cookieOf(res) })).status, 200);
  });

  await t.test('wrong password gives the generic 401', async () => {
    const res = await srv.request('/api/auth/login', { method: 'POST', body: { email: STUDENT.email, password: 'nope-nope-1' } });
    assert.equal(res.status, 401);
    assert.equal(res.json.error.message, 'Invalid username or password.');
  });
});

test('upgrading a v1 database keeps admins, sessions and applications', () => {
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'aw-')), 'old.db');
  const old = new DatabaseSync(file);
  old.exec(MIGRATIONS[0]);
  old.exec('PRAGMA user_version = 1');
  old.exec(`INSERT INTO users (id, username, password_hash, role) VALUES (1, 'admin', 'scrypt$x', 'admin');
            INSERT INTO sessions (token_hash, user_id, expires_at) VALUES ('abc', 1, 9999999999999);
            INSERT INTO audit_log (user_id, action) VALUES (1, 'login');
            INSERT INTO applications (ref, first_name, last_name, province, matric_year, aps) VALUES ('r1', 'A', 'B', 'Gauteng', 2025, 30);`);
  old.close();

  const db = openDatabase(file);
  assert.equal(db.prepare('PRAGMA user_version').get().user_version, MIGRATIONS.length);
  assert.equal(db.prepare("SELECT role FROM users WHERE username = 'admin'").get().role, 'admin');
  assert.equal(db.prepare('SELECT COUNT(*) n FROM sessions').get().n, 1, 'sessions survive the users rebuild');
  assert.equal(db.prepare('SELECT user_id FROM audit_log').get().user_id, 1);
  assert.equal(db.prepare("SELECT user_id FROM applications WHERE ref = 'r1'").get().user_id, null);
  assert.equal(db.prepare('PRAGMA foreign_keys').get().foreign_keys, 1, 'foreign keys back on');
  db.close();
});
