'use strict';
const { LruCache } = require('../lib/cache');

/**
 * Dashboard aggregates. Computed in SQL (never by loading every row) and
 * cached briefly; any write to applications invalidates the cache, so admins
 * still see new submissions immediately.
 */
function createStatsService({ applicationRepository, courseRepository, ttlMs = 30_000 }) {
  const cache = new LruCache({ maxEntries: 1, ttlMs });

  function fillDays(daily, days = 14) {
    const counts = new Map(daily.map((d) => [d.day, d.value]));
    const out = [];
    const today = new Date();
    for (let i = days - 1; i >= 0; i--) {
      const d = new Date(Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate() - i));
      const key = d.toISOString().slice(0, 10);
      out.push({ label: key, value: counts.get(key) || 0 });
    }
    return out;
  }

  return {
    getDashboard() {
      return cache.wrap('dashboard', () => {
        const a = applicationRepository.aggregates();
        return {
          generatedAt: new Date().toISOString(),
          totals: {
            applications: a.total,
            today: a.today,
            avgAps: a.avgAps,
            avgMatches: a.avgMatches,
            topProvince: a.byProvince[0] ? a.byProvince[0].label : null
          },
          catalogue: courseRepository.counts(),
          byProvince: a.byProvince,
          apsBands: a.apsBands,
          topInterests: a.topInterests,
          daily: fillDays(a.daily)
        };
      });
    },
    invalidate: () => cache.clear()
  };
}

module.exports = { createStatsService };
