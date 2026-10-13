/**
 * core/app.js — shared browser utilities (window.AW).
 * Classic script (not an ES module) so pages need no build step.
 */
(function (AW) {
  'use strict';

  /* ── DOM ── */
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

  const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
  /** Every user-supplied value goes through this before reaching innerHTML. */
  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ESCAPES[c]);

  /** Tagged template that escapes interpolations: html`<b>${name}</b>`. Use raw() to opt out. */
  function html(strings, ...values) {
    return strings.reduce((out, str, i) => {
      if (i === 0) return str;
      const v = values[i - 1];
      const piece = v && v.__raw !== undefined ? v.__raw
        : Array.isArray(v) ? v.map((x) => (x && x.__raw !== undefined ? x.__raw : escapeHtml(x))).join('')
        : escapeHtml(v);
      return out + piece + str;
    }, '');
  }
  const raw = (s) => ({ __raw: String(s) });

  /* ── API client ── */
  class ApiError extends Error {
    constructor(status, message, details) {
      super(message);
      this.status = status;
      this.details = details;
    }
  }

  async function api(path, { method = 'GET', body, headers = {}, signal } = {}) {
    const options = { method, headers: { Accept: 'application/json', ...headers }, credentials: 'same-origin', signal };
    if (body !== undefined) {
      options.headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }
    if (method !== 'GET') options.headers['X-AW-CSRF'] = '1';

    let response;
    try {
      response = await fetch(path, options);
    } catch (err) {
      if (err.name === 'AbortError') throw err;
      throw new ApiError(0, 'Cannot reach the Apply-Wise server. Make sure it is running, then try again.');
    }
    const data = await response.json().catch(() => null);
    if (!response.ok) {
      const error = (data && data.error) || {};
      throw new ApiError(response.status, error.message || `Request failed (${response.status}).`, error.details);
    }
    return data;
  }

  /* ── Small utilities ── */
  function debounce(fn, ms) {
    let timer;
    return (...args) => { clearTimeout(timer); timer = setTimeout(() => fn(...args), ms); };
  }

  function uuid() {
    if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
    const bytes = crypto.getRandomValues(new Uint8Array(16));
    return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('').replace(/^(.{8})(.{4})(.{4})(.{4})/, '$1-$2-$3-$4-');
  }

  const dateFormatter = new Intl.DateTimeFormat('en-ZA', { dateStyle: 'medium', timeStyle: 'short' });
  const formatDate = (iso) => { const d = new Date(iso); return Number.isNaN(d.getTime()) ? '' : dateFormatter.format(d); };

  /** sessionStorage wrapper that never throws (private mode, quota, corrupt JSON). */
  const store = {
    get(key, fallback = null) {
      try { const v = sessionStorage.getItem(key); return v === null ? fallback : JSON.parse(v); } catch { return fallback; }
    },
    set(key, value) { try { sessionStorage.setItem(key, JSON.stringify(value)); } catch { /* ignore */ } },
    remove(key) { try { sessionStorage.removeItem(key); } catch { /* ignore */ } }
  };

  function setLoading(button, loading, label) {
    if (!button) return;
    if (loading) { button.dataset.label = button.textContent; if (label) button.textContent = label; }
    else if (button.dataset.label) button.textContent = button.dataset.label;
    button.disabled = loading;
    button.classList.toggle('is-loading', loading);
  }

  function showAlert(el, message, items) {
    if (!el) return;
    el.innerHTML = items && items.length
      ? html`<div><strong>${message}</strong><ul>${items.map((i) => raw(html`<li>${i}</li>`))}</ul></div>`
      : html`<div>${message}</div>`;
    el.hidden = false;
  }
  const hideAlert = (el) => { if (el) { el.hidden = true; el.textContent = ''; } };

  /* ── Navigation (replaces inline onclick handlers, which CSP blocks) ── */
  function initNav() {
    const burger = $('#hamburger');
    const overlay = $('#mobileNav');
    if (!burger || !overlay) return;
    const setOpen = (open) => {
      burger.classList.toggle('open', open);
      overlay.classList.toggle('open', open);
      burger.setAttribute('aria-expanded', String(open));
      document.body.style.overflow = open ? 'hidden' : '';
    };
    burger.addEventListener('click', () => setOpen(!overlay.classList.contains('open')));
    overlay.addEventListener('click', (e) => { if (e.target.closest('a')) setOpen(false); });
    document.addEventListener('keydown', (e) => { if (e.key === 'Escape') setOpen(false); });
  }

  /** Opening the HTML files directly (file://) cannot reach the API — say so clearly. */
  function warnIfFileProtocol() {
    if (location.protocol !== 'file:') return;
    const banner = document.createElement('div');
    banner.className = 'server-banner';
    banner.innerHTML = 'Apply-Wise needs its server. In a terminal run <code>node server/index.js</code>, then open <code>http://localhost:3000</code>.';
    document.body.appendChild(banner);
  }

  document.addEventListener('DOMContentLoaded', () => { initNav(); warnIfFileProtocol(); });

  Object.assign(AW, { $, $$, escapeHtml, html, raw, api, ApiError, debounce, uuid, formatDate, store, setLoading, showAlert, hideAlert });
})(window.AW = window.AW || {});
