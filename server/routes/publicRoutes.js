'use strict';
const { RateLimiter } = require('../lib/rateLimiter');
const { rateLimit, requireSameOrigin, optionalUser } = require('../middleware');

function registerPublicRoutes(router, { applicationService, courseRepository, authService, db }) {
  const withUser = optionalUser(authService);
  const submitLimiter = new RateLimiter({ windowMs: 60_000, max: 20 });
  const startedAt = Date.now();

  router.get('/api/health', (ctx) => {
    db.prepare('SELECT 1').get(); // throws (→ 500) if the database is unavailable
    ctx.json(200, { status: 'ok', uptimeSec: Math.round((Date.now() - startedAt) / 1000), catalogue: courseRepository.counts() });
  });

  router.post('/api/applications',
    requireSameOrigin,
    rateLimit(submitLimiter, (ctx) => ctx.ip, 'Too many submissions from this device. Please wait a minute.'),
    withUser, // signed-in students get the application linked to their account
    async (ctx) => {
      const body = await ctx.body();
      const result = applicationService.submit(body, ctx.req.headers['idempotency-key'], ctx.user ? ctx.user.id : null);
      ctx.json(result.replayed ? 200 : 201, { ref: result.ref, aps: result.aps });
    });

  router.get('/api/applications/:ref/results', (ctx) => {
    ctx.json(200, applicationService.getResults(ctx.params.ref));
  });

  return { stop: () => submitLimiter.stop() };
}

module.exports = { registerPublicRoutes };
