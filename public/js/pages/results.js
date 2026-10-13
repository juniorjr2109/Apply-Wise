/** pages/results.js — renders matches for ?ref=… (works after refresh, in new tabs and when shared). */
(function (AW) {
  'use strict';
  const { $, html } = AW;
  const VARIANT = { 'Strong Match': 'match-strong', 'Good Match': 'match-good', Reach: 'match-reach' };
  const LABEL = { 'Strong Match': 'Strong', 'Good Match': 'Good', Reach: 'Reach' };

  function card(m, index) {
    const variant = VARIANT[m.matchType] || 'match-good';
    return html`
      <article class="result-card" data-index="${index}">
        <div class="card-top">
          <div>
            <h3 class="card-course">${m.course}</h3>
            <div class="card-institution">🏛 ${m.institution}</div>
          </div>
          <div class="match-score ${variant}" aria-label="${m.matchType}">✓<span>${LABEL[m.matchType] || ''}</span></div>
        </div>
        <div class="card-meta">
          <span class="meta-tag aps">APS min: ${m.minAps}</span>
          <span class="meta-tag province">📍 ${m.province}</span>
          <span class="meta-tag ${variant}">${m.matchType}</span>
          <span class="meta-tag">${m.faculty}</span>
        </div>
        <div class="card-body">
          <p>${m.reason}</p>
          ${m.requirements ? AW.raw(html`<p class="card-requirements">📋 ${m.requirements}</p>`) : ''}
        </div>
      </article>`;
  }

  function showError(title, message) {
    $('#loadingState').hidden = true;
    $('#errorTitle').textContent = title;
    $('#errorMessage').textContent = message;
    $('#errorState').hidden = false;
    $('#results').setAttribute('aria-busy', 'false');
  }

  async function init() {
    const ref = new URLSearchParams(location.search).get('ref');
    if (!ref) return showError('No results to show', 'Complete the application form first and we will match you with courses.');
    try {
      const data = await AW.api(`/api/applications/${encodeURIComponent(ref)}/results`);
      $('#heroGreeting').textContent = `Hi ${data.student.firstName}, here are your matches`;
      $('#heroSub').textContent = `${data.subjectCount} subjects · APS ${data.aps} · ${data.student.province}`;
      $('#heroAps').textContent = data.aps;
      document.title = `Apply-Wise — ${data.matches.length} matches for ${data.student.firstName}`;
      $('#summary').textContent = data.matches.length
        ? `Showing your top ${data.matches.length} of ${data.totalEligible} courses you are eligible or nearly eligible for.`
        : '';

      const grid = $('#resultsGrid');
      if (!data.matches.length) {
        grid.innerHTML = html`<div class="error-state"><h3>No matches yet</h3>
          <p>Your APS or subject combination doesn't meet the requirements in our catalogue. Consider a bridging programme, TVET college or upgrading your results — and speak to a career counsellor.</p>
          <a href="apply.html" class="btn-primary">Try different results</a></div>`;
      } else {
        grid.innerHTML = data.matches.map(card).join('');
        grid.querySelectorAll('.result-card').forEach((el) => el.style.setProperty('--i', el.dataset.index));
      }
      $('#loadingState').hidden = true;
      $('#resultsView').hidden = false;
      $('#results').setAttribute('aria-busy', 'false');
    } catch (err) {
      if (err.status === 404 || err.status === 400) showError('Results not found', err.message);
      else showError('We could not load your results', err.message);
    }
  }

  document.addEventListener('DOMContentLoaded', init);
})(window.AW);
