'use strict';
const { HttpError, parseCookies } = require('../lib/http');

const SESSION_COOKIE = 'aw_session';

/** Resolves the session cookie (if any) to ctx.user without rejecting anonymous requests. */
function optionalUser(authService) {
  return (ctx) => {
    const token = parseCookies(ctx.req.headers.cookie)[SESSION_COOKIE];
    const user = token ? authService.authenticate(token) : null;
    ctx.user = user || null;
    ctx.sessionToken = user ? token : null;
  };
}

/** Rejects requests without a valid session of any role; exposes ctx.user. */
function requireUser(authService) {
  const resolve = optionalUser(authService);
  return (ctx) => {
    resolve(ctx);
    if (!ctx.user) throw new HttpError(401, 'Your session has expired. Please sign in again.');
  };
}

/** Signed-in admins only. Students get 403 (signed in, but not allowed). */
function requireAdmin(authService) {
  const user = requireUser(authService);
  return (ctx) => {
    user(ctx);
    if (ctx.user.role !== 'admin') throw new HttpError(403, 'This area is for administrators only.');
  };
}

/**
 * CSRF defence for state-changing requests. The session cookie is
 * SameSite=Strict, and we additionally require a custom header that a
 * cross-site HTML form cannot send, plus a same-origin Origin header if present.
 */
function requireSameOrigin(ctx) {
  if (ctx.req.headers['x-aw-csrf'] !== '1') throw new HttpError(403, 'Missing CSRF header.');
  const origin = ctx.req.headers.origin;
  if (origin) {
    let host;
    try { host = new URL(origin).host; } catch { throw new HttpError(403, 'Bad origin.'); }
    if (host !== ctx.req.headers.host) throw new HttpError(403, 'Cross-origin request blocked.');
  }
}

/** Generic per-key request limit (e.g. submissions per IP). */
function rateLimit(limiter, keyFn = (ctx) => ctx.ip, message = 'Too many requests. Please slow down.') {
  return (ctx) => {
    const status = limiter.hit(keyFn(ctx));
    if (!status.allowed) {
      ctx.res.setHeader('Retry-After', String(status.retryAfterSec));
      throw new HttpError(429, message, { retryAfterSec: status.retryAfterSec });
    }
  };
}

module.exports = { optionalUser, requireUser, requireAdmin, requireSameOrigin, rateLimit, SESSION_COOKIE };
