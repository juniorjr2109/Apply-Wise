'use strict';
const { once } = require('node:events');
const { serializeCookie } = require('../lib/http');
const { requireAdmin, requireSameOrigin, SESSION_COOKIE } = require('../middleware');
const { publicUser } = require('../services/authService');

/** Quotes a CSV cell and neutralises spreadsheet formula injection (=, +, -, @). */
function csvCell(value) {
  let s = value === null || value === undefined ? '' : String(value);
  if (/^[=+\-@\t\r]/.test(s)) s = `'${s}`;
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

function registerAdminRoutes(router, { authService, applicationService, statsService, auditRepository, config }) {
  const auth = requireAdmin(authService);
  const cookie = (value, maxAgeSec) => serializeCookie(SESSION_COOKIE, value, { maxAgeSec, secure: config.cookieSecure });

  router.post('/api/admin/login', requireSameOrigin, async (ctx) => {
    const body = await ctx.body();
    const session = await authService.login(body, { ip: ctx.ip, userAgent: ctx.req.headers['user-agent'] }, { role: 'admin' });
    ctx.json(200, { user: session.user, expiresAt: session.expiresAt }, {
      'Set-Cookie': cookie(session.token, (session.expiresAt - Date.now()) / 1000)
    });
  });

  router.post('/api/admin/logout', requireSameOrigin, auth, (ctx) => {
    authService.logout(ctx.sessionToken, { userId: ctx.user.id, ip: ctx.ip });
    ctx.json(200, { ok: true }, { 'Set-Cookie': cookie('', 0) });
  });

  router.get('/api/admin/me', auth, (ctx) => {
    ctx.json(200, { user: publicUser(ctx.user), expiresAt: ctx.user.expiresAt });
  });

  router.get('/api/admin/stats', auth, (ctx) => ctx.json(200, statsService.getDashboard()));

  router.get('/api/admin/applications', auth, (ctx) => ctx.json(200, applicationService.list(ctx.query)));

  // Registered before "/:id" so "export.csv" is not treated as an id.
  router.get('/api/admin/applications/export.csv', auth, async (ctx) => {
    const date = new Date().toISOString().slice(0, 10);
    ctx.res.writeHead(200, {
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': `attachment; filename="applywise-applications-${date}.csv"`,
      'Cache-Control': 'no-store'
    });
    const header = ['Ref', 'First name', 'Last name', 'Province', 'Matric year', 'APS', 'Matches', 'Interests', 'Career goal', 'Preferred location', 'Submitted (UTC)'];
    ctx.res.write('\uFEFF' + header.join(',') + '\n'); // BOM so Excel reads UTF-8 names correctly
    for (const r of applicationService.iterateForExport()) {
      const line = [r.ref, r.firstName, r.lastName, r.province, r.matricYear, r.aps, r.matchCount,
        r.interests, r.careerGoal, r.studyLocation, r.createdAt].map(csvCell).join(',') + '\n';
      // Respect backpressure: pause when the socket buffer is full.
      if (!ctx.res.write(line)) await once(ctx.res, 'drain');
    }
    auditRepository.record({ userId: ctx.user.id, action: 'export_csv', ip: ctx.ip });
    ctx.res.end();
  });

  router.get('/api/admin/applications/:id', auth, (ctx) => ctx.json(200, applicationService.getDetail(ctx.params.id)));

  router.delete('/api/admin/applications/:id', requireSameOrigin, auth, (ctx) => {
    applicationService.remove(ctx.params.id);
    auditRepository.record({ userId: ctx.user.id, action: 'delete_application', target: String(ctx.params.id), ip: ctx.ip });
    ctx.json(200, { ok: true });
  });
}

module.exports = { registerAdminRoutes, csvCell };
