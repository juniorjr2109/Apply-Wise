'use strict';

/**
 * Bounded LRU cache with per-entry TTL. Map preserves insertion order, so the
 * first key is always the least recently used one.
 */
class LruCache {
  constructor({ maxEntries = 500, ttlMs = 60_000 } = {}) {
    this.maxEntries = maxEntries;
    this.ttlMs = ttlMs;
    this.map = new Map();
    this.hits = 0;
    this.misses = 0;
  }

  get(key) {
    const entry = this.map.get(key);
    if (!entry || entry.expiresAt <= Date.now()) {
      if (entry) this.map.delete(key);
      this.misses++;
      return undefined;
    }
    this.map.delete(key);
    this.map.set(key, entry); // refresh recency
    this.hits++;
    return entry.value;
  }

  set(key, value, ttlMs = this.ttlMs) {
    if (this.map.has(key)) this.map.delete(key);
    this.map.set(key, { value, expiresAt: Date.now() + ttlMs });
    if (this.map.size > this.maxEntries) this.map.delete(this.map.keys().next().value);
    return value;
  }

  /** Returns the cached value or computes, stores and returns it. */
  wrap(key, compute, ttlMs) {
    const cached = this.get(key);
    return cached !== undefined ? cached : this.set(key, compute(), ttlMs);
  }

  delete(key) { this.map.delete(key); }
  clear() { this.map.clear(); }
  get size() { return this.map.size; }
}

module.exports = { LruCache };
