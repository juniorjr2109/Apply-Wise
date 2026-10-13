'use strict';
const { HttpError } = require('../lib/http');
const { RateLimiter } = require('../lib/rateLimiter');
const security = require('../lib/security');

const USERNAME_RE = /^[A-Za-z0-9._-]{3,32}$/;
const EMAIL_RE = /^[^\s@]{1,64}@[^\s@]{1,190}\.[A-Za-z]{2,24}$/;
const NAME_RE = /^[\p{L}][\p{L}\p{M}' ’.-]{0,49}$/u;
const MIN_PASSWORD_LENGTH = 10;          // admins
const MIN_STUDENT_PASSWORD_LENGTH = 8;   // students

/** Public shape of a user — never includes the password hash. */
const publicUser = (u) => ({ id: u.id, username: u.username, role: u.role, firstName: u.firstName || '', lastName: u.lastName || '' });

function createAuthService({ userRepository, sessionRepository, auditRepository, sessionTtlMs, logger }) {
  // Failed logins: 5 per account+IP and 20 per IP in 15 minutes.
  const accountLimiter = new RateLimiter({ windowMs: 15 * 60_000, max: 5 });
  const ipLimiter = new RateLimiter({ windowMs: 15 * 60_000, max: 20 });

  function assertNotLocked(accountKey, ip) {
    for (const [limiter, key] of [[accountLimiter, accountKey], [ipLimiter, ip]]) {
      const status = limiter.peek(key);
      if (status.blocked) {
        throw new HttpError(429, `Too many failed attempts. Try again in ${Math.ceil(status.retryAfterSec / 60)} minute(s).`,
          { retryAfterSec: status.retryAfterSec });
      }
    }
  }

  function startSession(user, { ip, userAgent }) {
    const token = security.newToken();
    const expiresAt = Date.now() + sessionTtlMs;
    sessionRepository.create({ tokenHash: security.sha256(token), userId: user.id, expiresAt, ip, userAgent });
    userRepository.touchLogin(user.id);
    auditRepository.record({ userId: user.id, action: 'login', ip });
    return { token, expiresAt, user: publicUser(user) };
  }

  return {
    /**
     * Signs a user in. `role` restricts who may use a given endpoint (the web
     * admin page passes 'admin'); a wrong role gets the same 401 as a wrong
     * password so the endpoint does not reveal which accounts exist.
     */
    async login({ username, password }, { ip, userAgent }, { role } = {}) {
      const name = String(username || '').trim();
      const pass = String(password || '');
      if (!name || !pass) throw new HttpError(400, 'Enter your username and password.');
      if (name.length > 64 || pass.length > 256) throw new HttpError(401, 'Invalid username or password.');

      const accountKey = `${ip}|${name.toLowerCase()}`;
      assertNotLocked(accountKey, ip);

      const user = userRepository.findByUsername(name);
      // Always run one scrypt verification so response time does not reveal
      // whether the username exists.
      const valid = await security.verifyPassword(pass, user ? user.passwordHash : await security.dummyHash());

      if (!user || !valid || (role && user.role !== role)) {
        accountLimiter.hit(accountKey);
        ipLimiter.hit(ip);
        auditRepository.record({ userId: user ? user.id : null, action: 'login_failed', target: name.slice(0, 64), ip });
        logger.warn('admin login failed', { username: name.slice(0, 64), ip });
        throw new HttpError(401, 'Invalid username or password.');
      }

      accountLimiter.reset(accountKey);
      return startSession(user, { ip, userAgent });
    },

    /** Creates a student account and signs it in. Throws 409 if the email is taken. */
    async register({ firstName, lastName, email, password }, { ip, userAgent }) {
      const errors = {};
      const first = String(firstName || '').trim().replace(/\s+/g, ' ');
      const last = String(lastName || '').trim().replace(/\s+/g, ' ');
      const mail = String(email || '').trim().toLowerCase();
      const pass = String(password || '');
      if (!NAME_RE.test(first)) errors.firstName = 'Enter a valid first name.';
      if (!NAME_RE.test(last)) errors.lastName = 'Enter a valid last name.';
      if (!EMAIL_RE.test(mail) || mail.length > 254) errors.email = 'Enter a valid email address.';
      if (pass.length < MIN_STUDENT_PASSWORD_LENGTH) errors.password = `Use at least ${MIN_STUDENT_PASSWORD_LENGTH} characters.`;
      else if (pass.length > 256) errors.password = 'Password is too long.';
      else if (!/[A-Za-z]/.test(pass) || !/[0-9]/.test(pass)) errors.password = 'Use letters and at least one number.';
      if (Object.keys(errors).length) throw new HttpError(422, 'Please correct the highlighted fields.', errors);

      if (userRepository.findByUsername(mail)) {
        throw new HttpError(409, 'An account with this email already exists. Sign in instead.', { email: 'Already registered.' });
      }
      const passwordHash = await security.hashPassword(pass);
      let id;
      try {
        id = userRepository.create({ username: mail, passwordHash, role: 'student', firstName: first, lastName: last });
      } catch (err) {
        // Two sign-ups with the same email at the same moment: the unique index decides.
        if (/UNIQUE constraint failed: users.username/.test(err.message)) {
          throw new HttpError(409, 'An account with this email already exists. Sign in instead.', { email: 'Already registered.' });
        }
        throw err;
      }
      auditRepository.record({ userId: id, action: 'register', ip });
      return startSession({ id, username: mail, role: 'student', firstName: first, lastName: last }, { ip, userAgent });
    },

    /** Resolves a cookie token to a user, or null if missing/expired/revoked. */
    authenticate(token) {
      if (!token || token.length > 100) return null;
      return sessionRepository.findValid(security.sha256(token));
    },

    logout(token, { userId, ip } = {}) {
      if (token) sessionRepository.remove(security.sha256(token));
      if (userId) auditRepository.record({ userId, action: 'logout', ip });
    },

    purgeExpiredSessions: () => sessionRepository.removeExpired(),

    /** Creates an admin, or resets the password of an existing one. */
    async upsertAdmin(username, password) {
      if (!USERNAME_RE.test(username)) throw new Error('Username must be 3–32 characters: letters, digits, . _ -');
      if (String(password).length < MIN_PASSWORD_LENGTH) throw new Error(`Password must be at least ${MIN_PASSWORD_LENGTH} characters.`);
      const passwordHash = await security.hashPassword(password);
      const existing = userRepository.findByUsername(username);
      if (existing) {
        if (existing.role !== 'admin') throw new Error(`"${username}" is a student account, not an admin.`);
        userRepository.updatePassword(existing.id, passwordHash);
        sessionRepository.removeForUser(existing.id); // force re-login everywhere
        return { created: false };
      }
      userRepository.create({ username, passwordHash, role: 'admin' });
      return { created: true };
    },

    /** First run only: guarantees one admin exists. Returns generated credentials, if any. */
    async ensureDefaultAdmin({ username, password }) {
      if (userRepository.countByRole('admin') > 0) return null;
      const finalPassword = password || security.randomPassword();
      await this.upsertAdmin(username, finalPassword);
      return { username, password: password ? null : finalPassword };
    },

    stop() { accountLimiter.stop(); ipLimiter.stop(); }
  };
}

module.exports = { createAuthService, publicUser, MIN_PASSWORD_LENGTH, MIN_STUDENT_PASSWORD_LENGTH };
