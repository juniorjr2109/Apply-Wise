'use strict';
const http = require('node:http');
const { loadConfig } = require('./config');
const { createLogger } = require('./lib/logger');
const { openDatabase } = require('./db/connection');
const { seedCatalogue } = require('./db/seed');
const { createApp } = require('./app');

async function main() {
  const config = loadConfig();
  const logger = createLogger(config.logLevel);

  const db = openDatabase(config.dbFile);
  const courseCount = seedCatalogue(db, config.seedFile);
  const app = createApp({ db, config, logger });

  const generated = await app.services.authService.ensureDefaultAdmin(config.admin);
  if (generated) {
    const line = '─'.repeat(58);
    console.log(`\n${line}\n  First run: an admin account was created\n  Username: ${generated.username}`);
    console.log(generated.password
      ? `  Password: ${generated.password}\n  (shown once — change it with: node server/scripts/create-admin.js)`
      : '  Password: the ADMIN_PASSWORD value from your .env file');
    console.log(`${line}\n`);
  }

  // Remove expired sessions hourly; unref() so it never blocks shutdown.
  const sweeper = setInterval(() => {
    const removed = app.services.authService.purgeExpiredSessions();
    if (removed) logger.info('expired sessions purged', { removed });
  }, 60 * 60 * 1000);
  sweeper.unref();

  const server = http.createServer(app.handle);
  server.requestTimeout = 15_000;   // slow-loris protection
  server.headersTimeout = 10_000;
  server.keepAliveTimeout = 5_000;

  server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
      logger.error(`Port ${config.port} is already in use. Close the other server or set PORT in .env.`);
    } else logger.error('server error', { error: err.message });
    process.exit(1);
  });

  server.listen(config.port, config.host, () => {
    logger.info('Apply-Wise is running', { url: `http://localhost:${config.port}`, courses: courseCount, env: config.env });
    console.log(`\n  ➜  Open http://localhost:${config.port}   (admin: http://localhost:${config.port}/admin)\n`);
  });

  let shuttingDown = false;
  const shutdown = (signal) => {
    if (shuttingDown) return;
    shuttingDown = true;
    logger.info('shutting down', { signal });
    server.close(() => {
      app.close();
      db.close();
      process.exit(0);
    });
    setTimeout(() => process.exit(1), 5000).unref(); // force-exit if connections hang
  };
  process.on('SIGINT', () => shutdown('SIGINT'));
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('unhandledRejection', (err) => logger.error('unhandled rejection', { error: String(err && err.stack || err) }));
}

main().catch((err) => {
  console.error('Failed to start Apply-Wise:', err.message);
  if (/node:sqlite/.test(err.message)) console.error('Node.js 22.13 or newer is required (you have ' + process.version + ').');
  process.exit(1);
});
