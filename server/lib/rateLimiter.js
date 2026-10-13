'use strict';

/** Fixed-window, in-memory rate limiter keyed by an arbitrary string. */
class RateLimiter {
  constructor({ windowMs, max }) {
    this.windowMs = windowMs;
    this.max = max;
    this.buckets = new Map();
    // Periodic sweep so the map cannot grow without bound. unref() lets the
    // process exit cleanly (tests, shutdown) while the timer is pending.
    this.sweeper = setInterval(() => this.sweep(), windowMs);
    this.sweeper.unref();
  }

  bucket(key) {
    const now = Date.now();
    let b = this.buckets.get(key);
    if (!b || b.resetAt <= now) {
      b = { count: 0, resetAt: now + this.windowMs };
      this.buckets.set(key, b);
    }
    return b;
  }

  /** Records a hit and reports whether it is within the limit. */
  hit(key) {
    const b = this.bucket(key);
    b.count++;
    return this.status(b);
  }

  /** Checks the limit without recording a hit. */
  peek(key) { return this.status(this.bucket(key)); }

  status(b) {
    return {
      allowed: b.count <= this.max,
      blocked: b.count >= this.max,
      remaining: Math.max(0, this.max - b.count),
      retryAfterSec: Math.max(1, Math.ceil((b.resetAt - Date.now()) / 1000))
    };
  }

  reset(key) { this.buckets.delete(key); }

  sweep() {
    const now = Date.now();
    for (const [key, b] of this.buckets) if (b.resetAt <= now) this.buckets.delete(key);
  }

  stop() { clearInterval(this.sweeper); }
}

module.exports = { RateLimiter };
