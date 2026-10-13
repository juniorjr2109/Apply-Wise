'use strict';
const crypto = require('node:crypto');
const { Router } = require('./lib/router');
const { HttpError, sendJson, readJson, clientIp } = require('./lib/http');
const { createStaticHandler } = require('./lib/static');
const { createCourseRepository } = require('./repositories/courseRepository');
const { createApplicationRepository } = require('./repositories/applicationRepository');
const { createUserRepository, createSessionRepository, createAuditRepository } = require('./repositories/userRepository');
const { createMatchingService } = require('./services/matchingService');
const { createApplicationService } = require('./services/applicationService');
const { createAuthService } = require('./services/authService');
const { createStatsService } = require('./services/statsService');
const { registerPublicRoutes } = require('./routes/publicRoutes');
const { registerAdminRoutes } = require('./routes/adminRoutes');
const { registerAuthRoutes } = require('./routes/authRoutes');

const SECURITY_HEADERS = {
  'Content-Security-Policy': [
    "default-src 'self'", "script-src 'self'", "style-src 'self' https://fonts.googleapis.com",
    "font-src 'self' https://fonts.gstatic.com", "img-src 'self' data:", "connect-src 'self'",
    "frame-ancestors 'none'", "base-uri 'self'", "form-action 'self'", "object-src 'none'"
  ].join('; '),
  'X-Content-Type-Options': 'nosniff',
  'X-Frame-Options': 'DENY',
  'Referrer-Policy': 'same-origin',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  'Cross-Origin-Opener-Policy': 'same-origin'
};

/** Builds every dependency explicitly — no globals, easy to test with an in-memory DB. */
function createApp({ db, config, logger }) {
  const repositories = {
    courseRepository: createCourseRepository(db),
    applicationRepository: createApplicationRepository(db),
    userRepository: createUserRepository(db),
    sessionRepository: createSessionRepository(db),
    auditRepository: createAuditRepository(db)
  };
  const matchingService = createMatchingService(repositories);
  const statsService = createStatsService(repositories);
  const applicationService = createApplicationService({
    ...repositories, matchingService, onChange: () => statsService.invalidate()
  });
  const authService = createAuthService({ ...repositories, sessionTtlMs: config.sessionTtlMs, logger });
  const services = { matchingService, statsService, applicationService, authService };

  const router = new Router();
  const publicRoutes = registerPublicRoutes(router, { ...repositories, ...services, db });
  registerAdminRoutes(router, { ...repositories, ...services, config });
  const authRoutes = registerAuthRoutes(router, { ...services, config });

  const serveStatic = createStaticHandler({
    mounts: [{ prefix: '/shared/', dir: config.sharedDir }, { prefix: '/', dir: config.publicDir }]
  });

  async function handle(req, res) {
    const started = process.hrtime.bigint();
    const requestId = crypto.randomUUID();
    res.setHeader('X-Request-Id', requestId);
    for (const [k, v] of Object.entries(SECURITY_HEADERS)) res.setHeader(k, v);

    let url;
    try {
      url = new URL(req.url, 'http://localhost');
      if (url.pathname === '/admin' || url.pathname === '/admin/') {
        res.writeHead(302, { Location: '/admin/login.html' });
        return res.end();
      }
      if (url.pathname.startsWith('/api/')) {
        const { handlers, params } = router.resolve(req.method, url.pathname);
        const ctx = {
          req, res, params, ip: clientIp(req),
          query: Object.fromEntries(url.searchParams),
          body: () => readJson(req, config.maxBodyBytes),
          json: (status, body, headers) => sendJson(res, status, body, headers)
        };
        for (const handler of handlers) {
          await handler(ctx);
          if (res.writableEnded || res.headersSent) break;
        }
        if (!res.headersSent) throw new Error(`No response sent for ${req.method} ${url.pathname}`);
      } else {
        await serveStatic(req, res, url.pathname);
      }
    } catch (err) {
      handleError(err, req, res, requestId, url);
    } finally {
      const ms = Number(process.hrtime.bigint() - started) / 1e6;
      res.once('finish', () => logger.debug('request', { id: requestId, method: req.method, path: url ? url.pathname : req.url, status: res.statusCode, ms: +ms.toFixed(1) }));
    }
  }

  function handleError(err, req, res, requestId, url) {
    if (res.headersSent) { res.destroy(err); return; }
    const isApi = !url || url.pathname.startsWith('/api/');
    if (err instanceof HttpError) {
      if (isApi) return sendJson(res, err.status, { error: { message: err.message, details: err.details } });
      res.writeHead(err.status, { 'Content-Type': 'text/plain; charset=utf-8' });
      return res.end(err.status === 404 ? 'Page not found' : err.message);
    }
    logger.error('unhandled error', { id: requestId, method: req.method, path: url && url.pathname, error: err.message, stack: err.stack });
    sendJson(res, 500, { error: { message: 'Something went wrong on our side. Please try again.', requestId } });
  }

  function close() {
    publicRoutes.stop();
    authRoutes.stop();
    authService.stop();
  }

  return { handle, services, repositories, close };
}

module.exports = { createApp };
