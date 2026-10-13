'use strict';
const fs = require('node:fs/promises');
const path = require('node:path');
const zlib = require('node:zlib');
const { HttpError } = require('./http');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.txt': 'text/plain; charset=utf-8'
};
const COMPRESSIBLE = new Set(['.html', '.css', '.js', '.json', '.svg', '.txt']);

/**
 * mounts: [{ prefix: '/shared/', dir }, { prefix: '/', dir }] — first match wins.
 * Files are read once and kept in memory (with a gzip variant) until their
 * mtime changes, so repeat requests never touch the disk contents again.
 */
function createStaticHandler({ mounts }) {
  const resolvedMounts = mounts.map((m) => ({ prefix: m.prefix, dir: path.resolve(m.dir) }));
  const fileCache = new Map();

  async function load(filePath, stat) {
    const cached = fileCache.get(filePath);
    if (cached && cached.mtimeMs === stat.mtimeMs && cached.size === stat.size) return cached;
    const body = await fs.readFile(filePath);
    const ext = path.extname(filePath).toLowerCase();
    const entry = {
      mtimeMs: stat.mtimeMs,
      size: stat.size,
      body,
      gzip: COMPRESSIBLE.has(ext) && body.length > 1024 ? zlib.gzipSync(body) : null,
      etag: `W/"${stat.size.toString(16)}-${Math.floor(stat.mtimeMs).toString(16)}"`,
      type: MIME[ext] || 'application/octet-stream',
      cacheControl: ext === '.html' ? 'no-cache' : 'public, max-age=3600'
    };
    fileCache.set(filePath, entry);
    return entry;
  }

  return async function serveStatic(req, res, pathname) {
    if (req.method !== 'GET' && req.method !== 'HEAD') throw new HttpError(405, 'Method not allowed');

    let decoded;
    try { decoded = decodeURIComponent(pathname); } catch { throw new HttpError(400, 'Malformed path'); }
    if (decoded.includes('\0')) throw new HttpError(400, 'Malformed path');

    const mount = resolvedMounts.find((m) => decoded.startsWith(m.prefix));
    if (!mount) throw new HttpError(404, 'Not found');
    let relative = decoded.slice(mount.prefix.length);
    if (relative === '' || relative.endsWith('/')) relative += 'index.html';

    const filePath = path.resolve(mount.dir, '.' + path.sep + relative);
    if (filePath !== mount.dir && !filePath.startsWith(mount.dir + path.sep)) {
      throw new HttpError(403, 'Forbidden');
    }

    let stat;
    try { stat = await fs.stat(filePath); } catch { throw new HttpError(404, 'Page not found'); }
    if (!stat.isFile()) throw new HttpError(404, 'Page not found');

    const file = await load(filePath, stat);
    const headers = { 'Content-Type': file.type, 'Cache-Control': file.cacheControl, ETag: file.etag, Vary: 'Accept-Encoding' };

    if (req.headers['if-none-match'] === file.etag) {
      res.writeHead(304, headers);
      return res.end();
    }
    const useGzip = file.gzip && /\bgzip\b/.test(String(req.headers['accept-encoding'] || ''));
    const body = useGzip ? file.gzip : file.body;
    if (useGzip) headers['Content-Encoding'] = 'gzip';
    headers['Content-Length'] = body.length;
    res.writeHead(200, headers);
    res.end(req.method === 'HEAD' ? undefined : body);
  };
}

module.exports = { createStaticHandler };
