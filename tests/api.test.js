'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { startTestServer, validApplication, ADMIN } = require('./helpers');

test('Apply-Wise API', async (t) => {
  const srv = await startTestServer();
  t.after(() => srv.close());

  await t.test('health reports the seeded catalogue', async () => {
    const res = await srv.request('/api/health');
    assert.equal(res.status, 200);
    assert.ok(res.json.catalogue.courses > 100);
  });

  let ref;
  await t.test('submission computes APS on the server and returns a results ref', async () => {
    const res = await srv.request('/api/applications', { method: 'POST', body: validApplication({ aps: 999 }) });
    assert.equal(res.status, 201);
    assert.equal(res.json.aps, 35, 'client-supplied aps must be ignored');
    assert.match(res.json.ref, /^[A-Za-z0-9_-]{16}$/);
    ref = res.json.ref;
  });

  await t.test('results load by ref (refresh / new tab safe) and never save a duplicate', async () => {
    const a = await srv.request(`/api/applications/${ref}/results`);
    const b = await srv.request(`/api/applications/${ref}/results`);
    assert.equal(a.status, 200);
    assert.equal(a.json.student.firstName, 'Thabo');
    assert.ok(a.json.matches.length > 0 && a.json.matches.length <= 12);
    assert.deepEqual(a.json, b.json);
    const cookie = await srv.login();
    const list = await srv.request('/api/admin/applications', { cookie });
    assert.equal(list.json.total, 1);
  });

  await t.test('unknown or malformed refs give clear 404 / 400', async () => {
    assert.equal((await srv.request('/api/applications/AAAAAAAAAAAAAAAA/results')).status, 404);
    assert.equal((await srv.request('/api/applications/../../etc/results')).status, 404);
    assert.equal((await srv.request('/api/applications/short/results')).status, 400);
  });

  await t.test('same Idempotency-Key twice creates one record (double-click / retry)', async () => {
    const headers = { 'Idempotency-Key': 'test-key-0000000000000001' };
    const first = await srv.request('/api/applications', { method: 'POST', body: validApplication({ firstName: 'Lerato' }), headers });
    const second = await srv.request('/api/applications', { method: 'POST', body: validApplication({ firstName: 'Lerato' }), headers });
    assert.equal(first.status, 201);
    assert.equal(second.status, 200);
    assert.equal(first.json.ref, second.json.ref);
  });

  await t.test('invalid input is rejected with per-field messages', async () => {
    const res = await srv.request('/api/applications', {
      method: 'POST',
      body: validApplication({ firstName: '<img src=x onerror=alert(1)>', province: 'Mars', matricYear: 1990, subjects: [{ name: 'Mathematics', grade: 120 }] })
    });
    assert.equal(res.status, 422);
    assert.ok(res.json.error.details.firstName);
    assert.ok(res.json.error.details.province);
    assert.ok(res.json.error.details.matricYear);
    assert.ok(Array.isArray(res.json.error.details.subjects));
  });

  await t.test('malformed, wrong-type and oversized bodies are refused', async () => {
    assert.equal((await srv.request('/api/applications', { method: 'POST', body: '{bad json' })).status, 400);
    assert.equal((await srv.request('/api/applications', { method: 'POST', body: '[]' })).status, 400);
    assert.equal((await srv.request('/api/applications', { method: 'POST', body: 'x', headers: { 'Content-Type': 'text/plain' } })).status, 415);
    const huge = JSON.stringify({ firstName: 'a'.repeat(40 * 1024) });
    assert.equal((await srv.request('/api/applications', { method: 'POST', body: huge })).status, 413);
  });

  await t.test('state-changing requests require the CSRF header and same origin', async () => {
    const noHeader = await fetch(`${srv.base}/api/applications`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(validApplication()) });
    assert.equal(noHeader.status, 403);
    const crossSite = await srv.request('/api/applications', { method: 'POST', body: validApplication(), headers: { Origin: 'https://evil.example' } });
    assert.equal(crossSite.status, 403);
  });

  await t.test('admin endpoints refuse requests without a session', async () => {
    for (const path of ['/api/admin/me', '/api/admin/stats', '/api/admin/applications', '/api/admin/applications/export.csv']) {
      assert.equal((await srv.request(path)).status, 401, path);
    }
    assert.equal((await srv.request('/api/admin/stats', { cookie: 'aw_session=forged-token' })).status, 401);
  });

  await t.test('wrong password is rejected without revealing whether the user exists', async () => {
    const wrongPass = await srv.request('/api/admin/login', { method: 'POST', body: { username: ADMIN.username, password: 'nope-nope-nope' } });
    const noUser = await srv.request('/api/admin/login', { method: 'POST', body: { username: 'ghost', password: 'nope-nope-nope' } });
    assert.equal(wrongPass.status, 401);
    assert.equal(noUser.status, 401);
    assert.equal(wrongPass.json.error.message, noUser.json.error.message);
  });

  await t.test('successful login sets an HttpOnly, SameSite=Strict cookie', async () => {
    const res = await srv.request('/api/admin/login', { method: 'POST', body: ADMIN });
    assert.equal(res.status, 200);
    const setCookie = res.headers.get('set-cookie');
    assert.match(setCookie, /HttpOnly/);
    assert.match(setCookie, /SameSite=Strict/);
    assert.equal(res.json.user.username, 'admin');
    assert.equal(res.json.user.passwordHash, undefined);
  });

  await t.test('dashboard stats, search, pagination, detail, CSV and delete', async () => {
    const cookie = await srv.login();
    const stats = await srv.request('/api/admin/stats', { cookie });
    assert.equal(stats.status, 200);
    assert.equal(stats.json.totals.applications, 2);
    assert.equal(stats.json.daily.length, 14);

    const search = await srv.request('/api/admin/applications?q=ler', { cookie });
    assert.equal(search.json.total, 1);
    assert.equal(search.json.items[0].firstName, 'Lerato');
    const wildcard = await srv.request('/api/admin/applications?q=%25', { cookie });
    assert.equal(wildcard.json.total, 0, '% must be treated literally');

    const page = await srv.request('/api/admin/applications?page=1&pageSize=5', { cookie });
    assert.equal(page.json.pageSize, 5);

    const id = search.json.items[0].id;
    const detail = await srv.request(`/api/admin/applications/${id}`, { cookie });
    assert.equal(detail.json.subjects.length, 7);
    assert.ok(Array.isArray(detail.json.matches));

    const csv = await srv.request('/api/admin/applications/export.csv', { cookie });
    assert.equal(csv.status, 200);
    assert.match(csv.headers.get('content-type'), /text\/csv/);
    assert.equal(csv.text.trim().split('\n').length, 3);

    assert.equal((await srv.request(`/api/admin/applications/${id}`, { method: 'DELETE', cookie })).status, 200);
    assert.equal((await srv.request(`/api/admin/applications/${id}`, { method: 'DELETE', cookie })).status, 404);
    assert.equal((await srv.request('/api/admin/applications/abc', { cookie })).status, 400);
    const after = await srv.request('/api/admin/stats', { cookie });
    assert.equal(after.json.totals.applications, 1, 'stats cache is invalidated on delete');
  });

  await t.test('CSV export neutralises spreadsheet formulas', async () => {
    await srv.request('/api/applications', { method: 'POST', body: validApplication({ careerGoal: '=HYPERLINK("http://x")' }) });
    const cookie = await srv.login();
    const csv = await srv.request('/api/admin/applications/export.csv', { cookie });
    assert.ok(csv.text.includes(`"'=HYPERLINK(""http://x"")"`));
  });

  await t.test('logout revokes the session on the server', async () => {
    const cookie = await srv.login();
    assert.equal((await srv.request('/api/admin/logout', { method: 'POST', cookie })).status, 200);
    assert.equal((await srv.request('/api/admin/me', { cookie })).status, 401);
  });

  await t.test('repeated failed logins lock the account temporarily', async () => {
    let last;
    for (let i = 0; i < 6; i++) {
      last = await srv.request('/api/admin/login', { method: 'POST', body: { username: 'lockme', password: 'wrong-password-x' } });
    }
    assert.equal(last.status, 429);
  });

  await t.test('static files: pages served, traversal blocked, security headers set', async () => {
    const home = await srv.request('/');
    assert.equal(home.status, 200);
    assert.match(home.headers.get('content-security-policy'), /script-src 'self'/);
    assert.equal(home.headers.get('x-frame-options'), 'DENY');
    assert.equal((await srv.request('/shared/aps.js')).status, 200);
    assert.equal((await srv.request('/admin')).status, 302);
    const traversal = await fetch(`${srv.base}/..%2f..%2fserver%2fconfig.js`);
    assert.ok([403, 404].includes(traversal.status));
    assert.equal((await srv.request('/does-not-exist.html')).status, 404);
    assert.equal((await srv.request('/api/nope')).status, 404);
    assert.equal((await srv.request('/api/health', { method: 'DELETE' })).status, 405);
  });

  await t.test('unchanged static files return 304 with ETag', async () => {
    const first = await srv.request('/css/base.css');
    const etag = first.headers.get('etag');
    const second = await srv.request('/css/base.css', { headers: { 'If-None-Match': etag } });
    assert.equal(second.status, 304);
  });
});
