'use strict';
const { serializeCookie } = require('../lib/http');
const { RateLimiter } = require('../lib/rateLimiter');
const { requireUser, requireSameOrigin, rateLimit, SESSION_COOKIE } = require('../middleware');
const { publicUser } = require('../services/authService');

/**
 * Accounts for everyone (students and admins), used by the Android app.
 *   POST /api/auth/register   create a student account and sign in
 *   POST /api/auth/login      sign in with email (students) or username (admins)
 *   POST /api/auth/logout     end the current session
 *   GET  /api/auth/me         who is signed in
 *   GET  /api/me/applications the signed-in user's own applications
 */
function registerAuthRoutes(router, { authService, applicationService, config }) {
  const user = requireUser(authService);
  const registerLimiter = new RateLimiter({ windowMs: 60 * 60_000, max: 10 });
  const cookie = (value, maxAgeSec) => serializeCookie(SESSION_COOKIE, value, { maxAgeSec, secure: config.cookieSecure });
  const meta = (ctx) => ({ ip: ctx.ip, userAgent: ctx.req.headers['user-agent'] });
  const sendSession = (ctx, status, session) => ctx.json(status, { user: session.user, expiresAt: session.expiresAt }, {
    'Set-Cookie': cookie(session.token, (session.expiresAt - Date.now()) / 1000)
  });

  router.post('/api/auth/register', requireSameOrigin,
    rateLimit(registerLimiter, (ctx) => ctx.ip, 'Too many accounts created from this network. Try again later.'),
    async (ctx) => sendSession(ctx, 201, await authService.register(await ctx.body(), meta(ctx))));

  router.post('/api/auth/login', requireSameOrigin, async (ctx) => {
    const body = await ctx.body();
    // Students type their email; normalise case so "Thabo@Mail.com" works.
    const username = String(body.username ?? body.email ?? '').trim();
    sendSession(ctx, 200, await authService.login({ username, password: body.password }, meta(ctx)));
  });

  router.post('/api/auth/logout', requireSameOrigin, user, (ctx) => {
    authService.logout(ctx.sessionToken, { userId: ctx.user.id, ip: ctx.ip });
    ctx.json(200, { ok: true }, { 'Set-Cookie': cookie('', 0) });
  });

  router.get('/api/auth/me', user, (ctx) => ctx.json(200, { user: publicUser(ctx.user), expiresAt: ctx.user.expiresAt }));

  router.get('/api/me/applications', user, (ctx) => ctx.json(200, { items: applicationService.listForUser(ctx.user.id) }));

  return { stop: () => registerLimiter.stop() };
}

module.exports = { registerAuthRoutes };
