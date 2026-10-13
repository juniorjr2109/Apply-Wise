/**
 * pages/apply.js — the 4-step application wizard.
 * Domain rules (subjects, APS, validation) come from shared/aps.js, the same
 * module the server uses, so the browser and API can never disagree.
 */
(function (AW) {
  'use strict';
  const { aps, $, $$, html, raw } = AW;
  const DRAFT_KEY = 'aw:draft';
  const DEFAULT_SUBJECTS = ['English Home Language', 'Mathematics', 'Physical Sciences', 'Life Sciences', 'Geography', 'History', 'Life Orientation'];
  const TOTAL_STEPS = 4;

  const form = $('#wizard');
  const subjectList = $('#subjectList');
  const alertBox = $('#formAlert');
  let currentStep = 1;

  /* ── Building the form from data (no duplicated option lists in HTML) ── */
  function fillSelects() {
    const sources = { provinces: aps.PROVINCES, years: aps.matricYears() };
    $$('select[data-options]', form).forEach((select) => {
      const options = sources[select.dataset.options].map((v) => html`<option value="${v}">${v}</option>`);
      select.insertAdjacentHTML('beforeend', options.join(''));
    });
    $('#interestTags').innerHTML = aps.INTERESTS
      .map((i) => html`<button type="button" class="tag-btn" aria-pressed="false" data-interest="${i}">${i}</button>`).join('');
  }

  const subjectOptions = aps.SUBJECTS.map((s) => html`<option value="${s}">${s}</option>`).join('');

  function addSubjectRow(name = '', grade = '') {
    if ($$('.subject-row', subjectList).length >= aps.RULES.MAX_SUBJECTS) {
      AW.showAlert(alertBox, `You can add at most ${aps.RULES.MAX_SUBJECTS} subjects.`);
      return;
    }
    const row = document.createElement('div');
    row.className = 'subject-row';
    row.innerHTML = html`
      <label>Subject
        <select class="sub-name" aria-label="Subject"><option value="">— Select subject —</option>${raw(subjectOptions)}</select>
      </label>
      <label>% Achieved
        <input type="number" class="sub-grade" min="0" max="100" step="1" inputmode="numeric" placeholder="e.g. 75" aria-label="Percentage"/>
      </label>
      <button type="button" class="remove-btn" aria-label="Remove subject">✕</button>`;
    row.querySelector('.sub-name').value = name;
    row.querySelector('.sub-grade').value = grade;
    subjectList.appendChild(row);
  }

  /* ── Reading state ── */
  function readSubjects() {
    return $$('.subject-row', subjectList).map((row) => ({
      name: row.querySelector('.sub-name').value,
      grade: row.querySelector('.sub-grade').value
    }));
  }

  function readForm() {
    const data = Object.fromEntries(new FormData(form));
    return {
      firstName: (data.firstName || '').trim(),
      lastName: (data.lastName || '').trim(),
      province: data.province || '',
      matricYear: data.matricYear ? Number(data.matricYear) : '',
      subjects: readSubjects(),
      interests: $$('.tag-btn[aria-pressed="true"]', form).map((b) => b.dataset.interest),
      careerGoal: (data.careerGoal || '').trim(),
      studyLocation: data.studyLocation || ''
    };
  }

  /* ── Live APS ── */
  function updateAps() {
    const subjects = readSubjects().filter((s) => s.name && s.grade !== '');
    $('#apsDisplay').textContent = aps.calculateAps(subjects);
    const counted = subjects.filter((s) => s.name !== aps.RULES.EXCLUDED_SUBJECT).length;
    $('#apsNote').textContent = counted > aps.RULES.COUNTED_SUBJECTS
      ? `Best ${aps.RULES.COUNTED_SUBJECTS} of your ${counted} subjects counted`
      : 'Updates as you enter grades';
  }

  /* ── Validation (mirrors the server; the server remains the authority) ── */
  function setFieldError(name, message) {
    const slot = $(`[data-error-for="${name}"]`, form);
    if (slot) slot.textContent = message || '';
    const input = form.elements[name];
    if (input && input.setAttribute) input.setAttribute('aria-invalid', message ? 'true' : 'false');
  }

  function validateStep(step) {
    const data = readForm();
    const errors = {};
    if (step === 1) {
      const nameRe = /^[\p{L}][\p{L}\p{M}' ’.-]{0,49}$/u;
      if (!nameRe.test(data.firstName)) errors.firstName = 'Enter your first name (letters only).';
      if (!nameRe.test(data.lastName)) errors.lastName = 'Enter your last name (letters only).';
      if (!aps.isProvince(data.province)) errors.province = 'Choose your province.';
      if (!aps.matricYears().includes(data.matricYear)) errors.matricYear = 'Choose your matric year.';
    }
    if (step === 2) {
      const result = aps.validateSubjects(data.subjects);
      if (!result.ok) errors.subjects = result.errors.join(' ');
      $$('.subject-row', subjectList).forEach((row) => {
        const grade = row.querySelector('.sub-grade').value;
        const bad = grade === '' || Number(grade) < 0 || Number(grade) > 100;
        row.querySelector('.sub-grade').setAttribute('aria-invalid', String(bad));
        row.querySelector('.sub-name').setAttribute('aria-invalid', String(!row.querySelector('.sub-name').value));
      });
    }
    const fields = step === 1 ? ['firstName', 'lastName', 'province', 'matricYear'] : step === 2 ? ['subjects'] : [];
    fields.forEach((f) => setFieldError(f, errors[f]));
    return Object.keys(errors).length === 0;
  }

  /* ── Navigation ── */
  function goTo(step) {
    if (step > currentStep) {
      for (let s = currentStep; s < step; s++) if (!validateStep(s)) return;
    }
    if (step === 4) renderReview();
    currentStep = step;
    $$('.wizard-step', form).forEach((el) => el.classList.toggle('active', Number(el.dataset.step) === step));
    $$('.progress-step').forEach((el) => {
      const n = Number(el.dataset.step);
      el.classList.toggle('active', n === step);
      el.classList.toggle('done', n < step);
      if (n === step) el.setAttribute('aria-current', 'step'); else el.removeAttribute('aria-current');
    });
    AW.hideAlert(alertBox);
    window.scrollTo({ top: 0, behavior: 'smooth' });
    const heading = $(`.wizard-step[data-step="${step}"] h2`, form);
    if (heading) heading.focus({ preventScroll: true });
  }

  /* ── Review (all values escaped) ── */
  function renderReview() {
    const d = readForm();
    const { subjects } = aps.validateSubjects(d.subjects);
    const counted = subjects.filter((s) => s.name !== aps.RULES.EXCLUDED_SUBJECT)
      .sort((a, b) => b.points - a.points).slice(0, aps.RULES.COUNTED_SUBJECTS).map((s) => s.name);
    const rows = subjects.map((s) => {
      const dropped = !counted.includes(s.name);
      const note = s.name === aps.RULES.EXCLUDED_SUBJECT ? 'not counted' : dropped ? 'not in best 6' : `${s.points} pts`;
      return raw(html`<div class="review-row${dropped ? ' is-dropped' : ''}"><span>${s.name}</span><strong>${s.grade}% <span class="muted">(${note})</span></strong></div>`);
    });
    $('#reviewContent').innerHTML = html`
      <div class="review-list">
        <div class="review-card">
          <div class="review-title">Personal</div>
          <div class="review-name">${d.firstName} ${d.lastName}</div>
          <div class="review-meta">${d.province} · Matric ${d.matricYear}</div>
        </div>
        <div class="review-card">
          <div class="review-title">Subjects &amp; grades</div>
          ${rows}
          <div class="review-total"><span>Total APS</span><span>${aps.calculateAps(subjects)}</span></div>
        </div>
        <div class="review-card">
          <div class="review-title">Interests</div>
          ${d.interests.length ? raw(html`<div class="chip-group">${d.interests.map((i) => raw(html`<span class="chip">${i}</span>`))}</div>`) : raw('<div class="review-meta">None selected — results will be ranked by APS only.</div>')}
          ${d.careerGoal ? raw(html`<div class="review-meta">Career goal: ${d.careerGoal}</div>`) : ''}
          ${d.studyLocation ? raw(html`<div class="review-meta">Preferred location: ${d.studyLocation}</div>`) : ''}
        </div>
      </div>`;
  }

  /* ── Draft persistence: a refresh never loses the student's input ── */
  function saveDraft() {
    const draft = AW.store.get(DRAFT_KEY, {});
    AW.store.set(DRAFT_KEY, { ...readForm(), idempotencyKey: draft.idempotencyKey || AW.uuid() });
  }

  function restoreDraft() {
    const draft = AW.store.get(DRAFT_KEY);
    const subjects = draft && Array.isArray(draft.subjects) && draft.subjects.length ? draft.subjects : DEFAULT_SUBJECTS.map((name) => ({ name, grade: '' }));
    subjects.slice(0, aps.RULES.MAX_SUBJECTS).forEach((s) => addSubjectRow(aps.isSubject(s.name) ? s.name : '', s.grade ?? ''));
    if (!draft) return;
    ['firstName', 'lastName', 'province', 'matricYear', 'careerGoal', 'studyLocation'].forEach((k) => {
      if (draft[k] !== undefined && form.elements[k]) form.elements[k].value = draft[k];
    });
    const interests = new Set(Array.isArray(draft.interests) ? draft.interests : []);
    $$('.tag-btn', form).forEach((b) => b.setAttribute('aria-pressed', String(interests.has(b.dataset.interest))));
    $$('.tag-btn', form).forEach((b) => b.classList.toggle('selected', b.getAttribute('aria-pressed') === 'true'));
  }

  /* ── Submit ── */
  let submitting = false;
  async function submit(event) {
    event.preventDefault();
    if (submitting) return;
    if (currentStep < TOTAL_STEPS) return goTo(currentStep + 1); // Enter key advances instead of submitting
    if (!validateStep(1)) return goTo(1);
    if (!validateStep(2)) return goTo(2);

    submitting = true;
    const button = $('#submitBtn');
    AW.setLoading(button, true, 'Finding matches…');
    AW.hideAlert(alertBox);
    saveDraft();
    const { idempotencyKey } = AW.store.get(DRAFT_KEY, {});
    try {
      const result = await AW.api('/api/applications', { method: 'POST', body: readForm(), headers: { 'Idempotency-Key': idempotencyKey } });
      AW.store.remove(DRAFT_KEY);
      window.location.assign(`results.html?ref=${encodeURIComponent(result.ref)}`);
    } catch (err) {
      const details = err.details || {};
      const items = Object.values(details).flat();
      AW.showAlert(alertBox, err.message, items);
      if (details.firstName || details.lastName || details.province || details.matricYear) goTo(1);
      else if (details.subjects) goTo(2);
      AW.setLoading(button, false);
      submitting = false;
    }
  }

  /* ── Events (delegated: one listener per container, not per element) ── */
  function bindEvents() {
    form.addEventListener('click', (e) => {
      const t = e.target;
      if (t.closest('[data-next]')) goTo(currentStep + 1);
      else if (t.closest('[data-back]')) goTo(currentStep - 1);
      else if (t.closest('.remove-btn')) { t.closest('.subject-row').remove(); updateAps(); saveDraft(); }
      else if (t.closest('.tag-btn')) {
        const btn = t.closest('.tag-btn');
        const on = btn.getAttribute('aria-pressed') !== 'true';
        btn.setAttribute('aria-pressed', String(on));
        btn.classList.toggle('selected', on);
        saveDraft();
      }
    });
    $('#addSubject').addEventListener('click', () => { addSubjectRow(); saveDraft(); });
    const debouncedSave = AW.debounce(saveDraft, 300);
    form.addEventListener('input', (e) => {
      if (e.target.matches('.sub-grade, .sub-name')) updateAps();
      if (e.target.name) setFieldError(e.target.name, '');
      debouncedSave();
    });
    form.addEventListener('change', (e) => { if (e.target.matches('.sub-name')) updateAps(); debouncedSave(); });
    form.addEventListener('submit', submit);
    // Clicking "Back" after a submit shows a fresh, enabled form.
    window.addEventListener('pageshow', () => { submitting = false; AW.setLoading($('#submitBtn'), false); });
  }

  document.addEventListener('DOMContentLoaded', () => {
    fillSelects();
    restoreDraft();
    bindEvents();
    updateAps();
  });
})(window.AW);
