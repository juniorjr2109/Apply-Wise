/** pages/home.js — landing page: reveal-on-scroll and live catalogue numbers. */
(function (AW) {
  'use strict';

  function revealOnScroll() {
    const items = AW.$$('.pain-card, .step, .obj-card, .goal-item, .team-card, .stat-item');
    if (!('IntersectionObserver' in window) || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    const observer = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target); // reveal once, then stop observing
      }
    }, { threshold: 0.12 });
    items.forEach((el) => { el.classList.add('reveal'); observer.observe(el); });
  }

  async function loadCatalogueNumbers() {
    try {
      const { catalogue } = await AW.api('/api/health');
      AW.$$('[data-stat]').forEach((el) => {
        const value = catalogue[el.dataset.stat];
        if (Number.isFinite(value)) el.textContent = String(value);
      });
    } catch { /* keep the static fallback numbers */ }
  }

  document.addEventListener('DOMContentLoaded', () => { revealOnScroll(); loadCatalogueNumbers(); });
})(window.AW);
