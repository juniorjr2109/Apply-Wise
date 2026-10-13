/**
 * pages/admin-dashboard.js — protected admin dashboard.
 * The page itself is public HTML; every piece of data comes from /api/admin/*,
 * which the server refuses without a valid session cookie. The client-side
 * guard below is only for user experience, not security.
 */
(function (AW) {
  'use strict';
  const { $, $$, html, raw } = AW;
  const REFRESH_MS = 60_000;
  const state = { page: 1, pageSize: 10, q: '', province: '', pages: 1, listController: null };

  /* ── Session handling ── */
  function toLogin(reason) {
    const next = encodeURIComponent(location.pathname);
    location.replace(`/admin/login.html?next=${next}${reason ? `&reason=${reason}` : ''}`);
  }

  /** Wraps AW.api: any 401 means the session ended → back to sign in. */
  async function adminApi(path, options) {
    try {
      return await AW.api(path, options);
    } catch (err) {
      if (err.status === 401) { toLogin('expired'); throw err; }
      throw err;
    }
  }

  let toastTimer;
  function toast(message) {
    const el = $('#toast');
    el.textContent = message;
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, 3000);
  }

  /* ── Stats & charts ── */
  function barList(container, rows) {
    if (!rows.length) { container.innerHTML = '<p class="empty">No data yet.</p>'; return; }
    const max = Math.max(...rows.map((r) => r.value), 1);
    container.innerHTML = rows.map((r) => html`
      <div class="bar-row">
        <span class="bar-label" title="${r.label}">${r.label}</span>
        <div class="bar-track"><div class="bar-fill" data-pct="${(r.value / max * 100).toFixed(1)}"></div></div>
        <span class="bar-value">${r.value}</span>
      </div>`).join('');
    // Widths are set via CSSOM after insertion (inline style attributes are blocked by CSP),
    // inside rAF so the CSS transition animates from 0.
    requestAnimationFrame(() => $$('.bar-fill', container).forEach((el) => { el.style.width = `${el.dataset.pct}%`; }));
  }

  function dailyChart(container, days) {
    const max = Math.max(...days.map((d) => d.value), 1);
    container.innerHTML = days.map((d, i) => {
      const date = new Date(`${d.label}T00:00:00Z`);
      const label = i % 2 === 0 ? `${date.getUTCDate()}/${date.getUTCMonth() + 1}` : '';
      return html`<div class="spark-col" title="${d.label}: ${d.value}">
        <div class="spark-bar" data-pct="${(d.value / max * 100).toFixed(1)}"></div>
        <span class="spark-label">${label}</span></div>`;
    }).join('');
    requestAnimationFrame(() => $$('.spark-bar', container).forEach((el) => { el.style.height = `${el.dataset.pct}%`; }));
  }

  function renderStats(s) {
    const set = (key, value) => { const el = $(`[data-stat="${key}"]`); if (el) el.textContent = value ?? '–'; };
    set('applications', s.totals.applications);
    set('avgAps', s.totals.avgAps ?? '–');
    set('avgMatches', s.totals.avgMatches ?? '–');
    set('topProvince', s.totals.topProvince || '–');
    set('courses', s.catalogue.courses);
    $('[data-stat-sub="today"]').textContent = `${s.totals.today} today`;
    $('[data-stat-sub="institutions"]').textContent = `courses · ${s.catalogue.institutions} institutions`;
    dailyChart($('#dailyChart'), s.daily);
    barList($('#apsChart'), s.apsBands);
    barList($('#provinceChart'), s.byProvince);
    barList($('#interestChart'), s.topInterests);
    $('#updatedAt').textContent = `Updated ${AW.formatDate(s.generatedAt)}`;
  }

  async function loadStats() {
    renderStats(await adminApi('/api/admin/stats'));
  }

  /* ── Table ── */
  function rowHtml(a) {
    return html`<tr data-id="${a.id}">
      <td><strong>${a.firstName} ${a.lastName}</strong></td>
      <td>${a.province}</td>
      <td class="hide-sm">${a.matricYear}</td>
      <td><span class="aps-pill-sm">${a.aps}</span></td>
      <td class="hide-sm">${a.matchCount}</td>
      <td class="hide-sm">${AW.formatDate(a.createdAt)}</td>
      <td><div class="row-actions">
        <button type="button" class="icon-btn" data-action="view" aria-label="View ${a.firstName} ${a.lastName}">View</button>
        <button type="button" class="icon-btn danger" data-action="delete" aria-label="Delete ${a.firstName} ${a.lastName}">Delete</button>
      </div></td></tr>`;
  }

  async function loadTable() {
    // Cancel any in-flight request so a slow, older search can never overwrite newer results.
    if (state.listController) state.listController.abort();
    const controller = new AbortController();
    state.listController = controller;
    const params = new URLSearchParams({ page: state.page, pageSize: state.pageSize });
    if (state.q) params.set('q', state.q);
    if (state.province) params.set('province', state.province);

    const body = $('#tableBody');
    try {
      const data = await adminApi(`/api/admin/applications?${params}`, { signal: controller.signal });
      // Deleting the last row on the last page leaves an empty page: step back.
      if (!data.items.length && data.total > 0 && state.page > 1) { state.page = data.pages; return loadTable(); }
      state.pages = data.pages;
      body.innerHTML = data.items.length
        ? data.items.map(rowHtml).join('')
        : html`<tr><td colspan="7" class="table-state">${state.q || state.province ? 'No applications match your filters.' : 'No applications yet. They will appear here as students submit the form.'}</td></tr>`;
      const from = data.total ? (data.page - 1) * data.pageSize + 1 : 0;
      const to = Math.min(data.page * data.pageSize, data.total);
      $('#pageInfo').textContent = `${from}–${to} of ${data.total}`;
      $('#prevPage').disabled = data.page <= 1;
      $('#nextPage').disabled = data.page >= data.pages;
    } catch (err) {
      if (err.name === 'AbortError' || err.status === 401) return;
      body.innerHTML = html`<tr><td colspan="7" class="table-state">${err.message}</td></tr>`;
    } finally {
      if (state.listController === controller) state.listController = null;
    }
  }

  /* ── Detail dialog ── */
  async function openDetail(id) {
    const dialog = $('#detailDialog');
    $('#detailTitle').textContent = 'Loading…';
    $('#detailMeta').textContent = '';
    $('#detailBody').innerHTML = '<p class="empty">Loading application…</p>';
    if (!dialog.open) dialog.showModal();
    try {
      const a = await adminApi(`/api/admin/applications/${encodeURIComponent(id)}`);
      $('#detailTitle').textContent = `${a.firstName} ${a.lastName}`;
      $('#detailMeta').textContent = `${a.province} · Matric ${a.matricYear} · APS ${a.aps} · ${AW.formatDate(a.createdAt)}`;
      $('#detailBody').innerHTML = html`
        <div class="detail-section"><h3>Subjects</h3>
          ${a.subjects.map((s) => raw(html`<div class="detail-row"><span>${s.name}</span><strong>${s.grade}% · ${s.points} pts</strong></div>`))}
        </div>
        <div class="detail-section"><h3>Interests &amp; goals</h3>
          ${a.interests.length ? raw(html`<div class="chip-group">${a.interests.map((i) => raw(html`<span class="chip">${i}</span>`))}</div>`) : raw('<p class="empty">No interests selected.</p>')}
          ${a.careerGoal ? raw(html`<div class="detail-row"><span>Career goal</span><strong>${a.careerGoal}</strong></div>`) : ''}
          ${a.studyLocation ? raw(html`<div class="detail-row"><span>Preferred location</span><strong>${a.studyLocation}</strong></div>`) : ''}
        </div>
        <div class="detail-section"><h3>Current top matches (${a.matches.length})</h3>
          ${a.matches.length ? a.matches.map((m) => raw(html`<div class="detail-row"><span>${m.course} — ${m.institution}</span><strong>${m.matchType}</strong></div>`)) : raw('<p class="empty">No matching courses.</p>')}
        </div>
        <p class="empty">Student results link: <a href="/results.html?ref=${a.ref}" target="_blank" rel="noopener">open ↗</a></p>`;
    } catch (err) {
      if (err.status !== 401) $('#detailBody').innerHTML = html`<p class="empty">${err.message}</p>`;
    }
  }

  async function deleteApplication(id, name) {
    if (!confirm(`Delete the application from ${name}? This cannot be undone.`)) return;
    try {
      await adminApi(`/api/admin/applications/${encodeURIComponent(id)}`, { method: 'DELETE' });
      toast(`Deleted application from ${name}.`);
      await Promise.all([loadTable(), loadStats()]);
    } catch (err) {
      if (err.status === 404) { toast('That application was already removed.'); loadTable(); }
      else if (err.status !== 401) toast(err.message);
    }
  }

  /* ── Wiring ── */
  function bind() {
    $('#provinceFilter').insertAdjacentHTML('beforeend', AW.aps.PROVINCES.map((p) => html`<option value="${p}">${p}</option>`).join(''));

    const search = AW.debounce((value) => { state.q = value.trim(); state.page = 1; loadTable(); }, 300);
    $('#searchInput').addEventListener('input', (e) => search(e.target.value));
    $('#provinceFilter').addEventListener('change', (e) => { state.province = e.target.value; state.page = 1; loadTable(); });
    $('#prevPage').addEventListener('click', () => { if (state.page > 1) { state.page--; loadTable(); } });
    $('#nextPage').addEventListener('click', () => { if (state.page < state.pages) { state.page++; loadTable(); } });

    // One delegated listener for every row button (rows are re-rendered often).
    $('#tableBody').addEventListener('click', (e) => {
      const button = e.target.closest('[data-action]');
      if (!button) return;
      const row = button.closest('tr');
      const name = row.querySelector('strong').textContent;
      if (button.dataset.action === 'view') openDetail(row.dataset.id);
      if (button.dataset.action === 'delete') deleteApplication(row.dataset.id, name);
    });

    const dialog = $('#detailDialog');
    $('#closeDetail').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', (e) => { if (e.target === dialog) dialog.close(); }); // click backdrop

    $('#refreshBtn').addEventListener('click', async (e) => {
      AW.setLoading(e.currentTarget, true);
      try { await Promise.all([loadStats(), loadTable()]); toast('Dashboard refreshed.'); }
      catch (err) { if (err.status !== 401) toast(err.message); }
      finally { AW.setLoading(e.currentTarget, false); }
    });

    $('#logoutBtn').addEventListener('click', async () => {
      try { await AW.api('/api/admin/logout', { method: 'POST' }); } catch { /* session may already be gone */ }
      location.replace('/admin/login.html?reason=signedout');
    });

    // Auto-refresh stats only while the tab is visible (no wasted requests in background tabs).
    let timer = null;
    const schedule = () => {
      clearInterval(timer);
      if (document.visibilityState === 'visible') timer = setInterval(() => loadStats().catch(() => {}), REFRESH_MS);
    };
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') loadStats().catch(() => {});
      schedule();
    });
    schedule();
  }

  async function init() {
    let me;
    try { me = await adminApi('/api/admin/me'); }
    catch (err) {
      if (err.status !== 401) { document.body.textContent = err.message; }
      return;
    }
    $('#userName').textContent = me.user.username;
    $('#userAvatar').textContent = me.user.username.slice(0, 2);
    $('#dash').hidden = false;
    bind();
    const results = await Promise.allSettled([loadStats(), loadTable()]);
    const failed = results.find((r) => r.status === 'rejected' && r.reason.status !== 401);
    if (failed) AW.showAlert($('#dashAlert'), failed.reason.message);

    // Warn shortly before the session expires instead of failing silently mid-task.
    const msLeft = me.expiresAt - Date.now();
    if (msLeft > 5 * 60_000) setTimeout(() => toast('Your session ends in 5 minutes — save your work.'), msLeft - 5 * 60_000);
  }

  document.addEventListener('DOMContentLoaded', init);
})(window.AW);
