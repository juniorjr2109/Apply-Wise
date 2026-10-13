'use strict';
const http = require('node:http');
const { loadConfig } = require('../server/config');
const { openDatabase } = require('../server/db/connection');
const { seedCatalogue } = require('../server/db/seed');
const { createApp } = require('../server/app');
const { createLogger } = require('../server/lib/logger');

const ADMIN = { username: 'admin', password: 'correct-horse-battery' };

/** Boots a fully wired server on a random port with an in-memory database. */
async function startTestServer() {
  const config = loadConfig({ dbFile: ':memory:', sessionTtlMs: 60 * 60 * 1000, cookieSecure: false });
  const db = openDatabase(':memory:');
  seedCatalogue(db, config.seedFile);
  const app = createApp({ db, config, logger: createLogger('silent') });
  await app.services.authService.upsertAdmin(ADMIN.username, ADMIN.password);
  const server = http.createServer(app.handle);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;

  async function request(path, { method = 'GET', body, headers = {}, cookie } = {}) {
    const res = await fetch(base + path, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(method !== 'GET' ? { 'X-AW-CSRF': '1' } : {}),
        ...(cookie ? { Cookie: cookie } : {}),
        ...headers
      },
      body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body),
      redirect: 'manual'
    });
    const text = await res.text();
    let json = null;
    try { json = JSON.parse(text); } catch { /* not JSON */ }
    return { status: res.status, headers: res.headers, json, text };
  }

  async function login() {
    const res = await request('/api/admin/login', { method: 'POST', body: ADMIN });
    return res.headers.get('set-cookie').split(';')[0];
  }

  async function close() {
    await new Promise((resolve) => server.close(resolve));
    app.close();
    db.close();
  }

  return { base, request, login, close, app, db };
}

const validApplication = (overrides = {}) => ({
  firstName: 'Thabo',
  lastName: 'Dlamini',
  province: 'Limpopo',
  matricYear: new Date().getFullYear() - 1,
  subjects: [
    { name: 'English Home Language', grade: 72 },
    { name: 'Mathematics', grade: 81 },
    { name: 'Physical Sciences', grade: 75 },
    { name: 'Life Sciences', grade: 68 },
    { name: 'Geography', grade: 70 },
    { name: 'History', grade: 66 },
    { name: 'Life Orientation', grade: 90 }
  ],
  interests: ['Engineering & Technology'],
  careerGoal: 'Software engineer',
  studyLocation: '',
  ...overrides
});

module.exports = { startTestServer, validApplication, ADMIN };
