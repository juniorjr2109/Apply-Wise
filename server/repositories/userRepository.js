'use strict';

const plain = (row) => (row ? { ...row } : null);

function createUserRepository(db) {
  const s = {
    byUsername: db.prepare(`SELECT id, username, password_hash AS passwordHash, role, first_name AS firstName, last_name AS lastName
                            FROM users WHERE username = ?`),
    insert: db.prepare('INSERT INTO users (username, password_hash, role, first_name, last_name) VALUES (?, ?, ?, ?, ?) RETURNING id'),
    updatePassword: db.prepare('UPDATE users SET password_hash = ? WHERE id = ?'),
    touchLogin: db.prepare("UPDATE users SET last_login_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') WHERE id = ?"),
    count: db.prepare('SELECT COUNT(*) AS n FROM users'),
    countByRole: db.prepare('SELECT COUNT(*) AS n FROM users WHERE role = ?')
  };
  return {
    findByUsername: (username) => plain(s.byUsername.get(username)),
    create: ({ username, passwordHash, role = 'admin', firstName = '', lastName = '' }) =>
      s.insert.get(username, passwordHash, role, firstName, lastName).id,
    updatePassword: (id, passwordHash) => s.updatePassword.run(passwordHash, id),
    touchLogin: (id) => s.touchLogin.run(id),
    count: () => s.count.get().n,
    countByRole: (role) => s.countByRole.get(role).n
  };
}

function createSessionRepository(db) {
  const s = {
    insert: db.prepare('INSERT INTO sessions (token_hash, user_id, expires_at, ip, user_agent) VALUES (?, ?, ?, ?, ?)'),
    findValid: db.prepare(`
      SELECT u.id, u.username, u.role, u.first_name AS firstName, u.last_name AS lastName, s.expires_at AS expiresAt
      FROM sessions s JOIN users u ON u.id = s.user_id
      WHERE s.token_hash = ? AND s.expires_at > ?`),
    remove: db.prepare('DELETE FROM sessions WHERE token_hash = ?'),
    removeForUser: db.prepare('DELETE FROM sessions WHERE user_id = ?'),
    removeExpired: db.prepare('DELETE FROM sessions WHERE expires_at <= ?')
  };
  return {
    create: ({ tokenHash, userId, expiresAt, ip, userAgent }) =>
      s.insert.run(tokenHash, userId, expiresAt, ip, String(userAgent || '').slice(0, 200)),
    findValid: (tokenHash, now = Date.now()) => plain(s.findValid.get(tokenHash, now)),
    remove: (tokenHash) => s.remove.run(tokenHash),
    removeForUser: (userId) => s.removeForUser.run(userId),
    removeExpired: (now = Date.now()) => Number(s.removeExpired.run(now).changes)
  };
}

function createAuditRepository(db) {
  const insert = db.prepare('INSERT INTO audit_log (user_id, action, target, ip) VALUES (?, ?, ?, ?)');
  return {
    record: ({ userId = null, action, target = null, ip = null }) => insert.run(userId, action, target, ip)
  };
}

module.exports = { createUserRepository, createSessionRepository, createAuditRepository };
