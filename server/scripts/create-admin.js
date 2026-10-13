'use strict';
/**
 * Create an admin or reset an admin password.
 *   node server/scripts/create-admin.js <username> <password>
 * Resetting a password signs that admin out of every device.
 */
const { loadConfig } = require('../config');
const { openDatabase } = require('../db/connection');
const { createUserRepository, createSessionRepository, createAuditRepository } = require('../repositories/userRepository');
const { createAuthService, MIN_PASSWORD_LENGTH } = require('../services/authService');
const { createLogger } = require('../lib/logger');

async function main() {
  const [username, password] = process.argv.slice(2);
  if (!username || !password) {
    console.log(`Usage: node server/scripts/create-admin.js <username> <password>\nPassword must be at least ${MIN_PASSWORD_LENGTH} characters.`);
    process.exit(1);
  }
  const db = openDatabase(loadConfig().dbFile);
  const auth = createAuthService({
    userRepository: createUserRepository(db),
    sessionRepository: createSessionRepository(db),
    auditRepository: createAuditRepository(db),
    sessionTtlMs: 0,
    logger: createLogger('silent')
  });
  try {
    const { created } = await auth.upsertAdmin(username, password);
    console.log(created ? `Admin "${username}" created.` : `Password updated for "${username}". Existing sessions were signed out.`);
  } catch (err) {
    console.error(`Error: ${err.message}`);
    process.exitCode = 1;
  } finally {
    auth.stop();
    db.close();
  }
}

main();
