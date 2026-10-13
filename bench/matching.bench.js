'use strict';
/**
 * Micro-benchmark: matching throughput (cold vs cached), APS calculation,
 * and end-to-end HTTP latency for the results endpoint.
 *   node bench/matching.bench.js
 */
const { performance } = require('node:perf_hooks');
const http = require('node:http');
const { loadConfig } = require('../server/config');
const { openDatabase } = require('../server/db/connection');
const { seedCatalogue } = require('../server/db/seed');
const { createApp } = require('../server/app');
const { createLogger } = require('../server/lib/logger');
const aps = require('../shared/aps');

const config = loadConfig({ dbFile: ':memory:' });
const db = openDatabase(':memory:');
seedCatalogue(db, config.seedFile);
const app = createApp({ db, config, logger: createLogger('silent') });
const { matchingService } = app.services;

const pool = ['Mathematics', 'Physical Sciences', 'Life Sciences', 'Geography', 'History', 'Accounting', 'Economics', 'Business Studies'];
function randomProfile(seed) {
  const subjects = [{ name: 'English Home Language', grade: 50 + (seed % 45) }, { name: 'Life Orientation', grade: 80 }];
  for (let i = 0; i < 6; i++) subjects.push({ name: pool[(seed + i) % pool.length], grade: 40 + ((seed * 7 + i * 13) % 60) });
  return { subjects, interests: [aps.INTERESTS[seed % aps.INTERESTS.length]], studyLocation: '' };
}

function time(label, n, fn) {
  fn(0); // warm-up
  const t0 = performance.now();
  for (let i = 0; i < n; i++) fn(i);
  const ms = performance.now() - t0;
  console.log(`${label.padEnd(36)} ${n.toLocaleString().padStart(8)} ops  ${(ms / n * 1000).toFixed(1).padStart(8)} µs/op  ${Math.round(n / (ms / 1000)).toLocaleString().padStart(10)} ops/s`);
}

const profiles = Array.from({ length: 20000 }, (_, i) => randomProfile(i));
console.log(`Catalogue: ${db.prepare('SELECT COUNT(*) n FROM courses').get().n} courses\n`);
time('calculateAps', 200000, (i) => aps.calculateAps(profiles[i % profiles.length].subjects));
time('catalogue reload + index build', 2000, () => { matchingService.invalidate(); matchingService.match(profiles[0]); });
time('match — cache miss (unique profiles)', 20000, (i) => matchingService.match(profiles[i]));
const before = process.memoryUsage().heapUsed;
time('match — cache hit (repeat profile)', 200000, () => matchingService.match(profiles[1]));
console.log(`\nHeap delta after cached run: ${((process.memoryUsage().heapUsed - before) / 1024 / 1024).toFixed(2)} MB`);

(async () => {
  const server = http.createServer(app.handle);
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const post = await fetch(`${base}/api/applications`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-AW-CSRF': '1' },
    body: JSON.stringify({ firstName: 'Bench', lastName: 'User', province: 'Gauteng', matricYear: new Date().getFullYear(), ...randomProfile(3) })
  });
  const { ref } = await post.json();
  const N = 2000;
  const t0 = performance.now();
  const latencies = [];
  for (let i = 0; i < N; i += 50) {
    await Promise.all(Array.from({ length: 50 }, async () => {
      const s = performance.now();
      const r = await fetch(`${base}/api/applications/${ref}/results`);
      await r.arrayBuffer();
      latencies.push(performance.now() - s);
    }));
  }
  const total = performance.now() - t0;
  latencies.sort((a, b) => a - b);
  const pct = (p) => latencies[Math.floor(p * latencies.length)].toFixed(2);
  console.log(`\nHTTP GET /results  ${N} requests, 50 concurrent: ${Math.round(N / (total / 1000))} req/s  p50 ${pct(0.5)} ms  p95 ${pct(0.95)} ms  p99 ${pct(0.99)} ms`);
  server.close(); app.close(); db.close();
})();
