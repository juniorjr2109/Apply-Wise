'use strict';
const { HttpError } = require('./http');

/**
 * Tiny method + path router. Patterns support named params ("/items/:id").
 * Each route has a chain of async handlers that receive a shared context;
 * a handler "finishes" the chain by sending a response.
 */
class Router {
  constructor() { this.routes = []; }

  add(method, pattern, handlers) {
    const keys = [];
    const source = pattern.replace(/\/:([A-Za-z_]+)/g, (_, key) => { keys.push(key); return '/([^/]+)'; });
    this.routes.push({ method, regex: new RegExp(`^${source}/?$`), keys, handlers });
    return this;
  }

  get(pattern, ...handlers) { return this.add('GET', pattern, handlers); }
  post(pattern, ...handlers) { return this.add('POST', pattern, handlers); }
  delete(pattern, ...handlers) { return this.add('DELETE', pattern, handlers); }

  /** Returns { handlers, params } or throws 404/405. */
  resolve(method, pathname) {
    let pathExists = false;
    const lookup = method === 'HEAD' ? 'GET' : method;
    for (const route of this.routes) {
      const m = route.regex.exec(pathname);
      if (!m) continue;
      pathExists = true;
      if (route.method !== lookup) continue;
      const params = {};
      route.keys.forEach((key, i) => {
        try { params[key] = decodeURIComponent(m[i + 1]); }
        catch { throw new HttpError(400, 'Malformed URL parameter'); }
      });
      return { handlers: route.handlers, params };
    }
    throw pathExists ? new HttpError(405, 'Method not allowed') : new HttpError(404, 'Endpoint not found');
  }
}

module.exports = { Router };
