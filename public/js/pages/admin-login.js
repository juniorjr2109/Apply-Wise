/** pages/admin-login.js — admin sign-in. Session is an HttpOnly cookie set by the server. */
(function (AW) {
  'use strict';
  const { $ } = AW;
  const DASHBOARD = '/admin/dashboard.html';

  /** Only allow same-site relative redirects (prevents open-redirect via ?next=). */
  function nextUrl() {
    const next = new URLSearchParams(location.search).get('next') || '';
    return /^\/admin\/[A-Za-z0-9_\-./]*$/.test(next) && !next.includes('..') ? next : DASHBOARD;
  }

  async function redirectIfSignedIn() {
    try { await AW.api('/api/admin/me'); location.replace(nextUrl()); }
    catch { /* not signed in — stay on the form */ }
  }

  function bind() {
    const form = $('#loginForm');
    const alertBox = $('#loginAlert');
    const button = $('#loginBtn');
    const notice = $('#loginNotice');

    const reason = new URLSearchParams(location.search).get('reason');
    if (reason === 'expired') { notice.textContent = 'Your session expired. Please sign in again.'; notice.hidden = false; }
    if (reason === 'signedout') { notice.textContent = 'You have been signed out.'; notice.hidden = false; }

    $('#togglePassword').addEventListener('click', (e) => {
      const input = $('#password');
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      e.currentTarget.textContent = show ? 'Hide' : 'Show';
      e.currentTarget.setAttribute('aria-pressed', String(show));
    });

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      if (button.disabled) return; // ignore double submits
      AW.hideAlert(alertBox);
      notice.hidden = true;
      const username = form.elements.username.value.trim();
      const password = form.elements.password.value;
      if (!username || !password) return AW.showAlert(alertBox, 'Enter your username and password.');

      AW.setLoading(button, true, 'Signing in…');
      try {
        await AW.api('/api/admin/login', { method: 'POST', body: { username, password } });
        location.replace(nextUrl());
      } catch (err) {
        AW.showAlert(alertBox, err.message);
        form.elements.password.value = '';
        form.elements.password.focus();
        AW.setLoading(button, false);
      }
    });
  }

  document.addEventListener('DOMContentLoaded', () => { bind(); redirectIfSignedIn(); });
})(window.AW);
