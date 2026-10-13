'use strict';
const path = require('node:path');
const fs = require('node:fs');

const ROOT = path.resolve(__dirname, '..');

// Optional .env support without a dependency (Node >= 21.7).
const envFile = path.join(ROOT, '.env');
if (fs.existsSync(envFile) && typeof process.loadEnvFile === 'function') process.loadEnvFile(envFile);

const int = (value, fallback) => {
  const n = Number.parseInt(value, 10);
  return Number.isFinite(n) ? n : fallback;
};

function loadConfig(overrides = {}) {
  return Object.freeze({
    env: process.env.NODE_ENV || 'development',
    host: process.env.HOST || '127.0.0.1',
    port: int(process.env.PORT, 3000),
    dbFile: process.env.DB_FILE || path.join(ROOT, 'data', 'applywise.db'),
    publicDir: path.join(ROOT, 'public'),
    sharedDir: path.join(ROOT, 'shared'),
    seedFile: path.join(__dirname, 'db', 'seed-courses.json'),
    sessionTtlMs: int(process.env.SESSION_TTL_HOURS, 8) * 60 * 60 * 1000,
    cookieSecure: process.env.COOKIE_SECURE === 'true',
    maxBodyBytes: 32 * 1024,
    logLevel: process.env.LOG_LEVEL || 'info',
    admin: {
      username: process.env.ADMIN_USERNAME || 'admin',
      password: process.env.ADMIN_PASSWORD || ''
    },
    ...overrides
  });
}

module.exports = { loadConfig, ROOT };
