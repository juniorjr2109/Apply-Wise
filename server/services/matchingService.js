'use strict';
const aps = require('../../shared/aps');
const { LruCache } = require('../lib/cache');

const MAX_RESULTS = 12;
const MAX_PER_INSTITUTION = 2;
const REACH_WINDOW = 3; // show courses up to 3 APS points above the student's score

/**
 * Relevance of a course's APS requirement to the student's APS. Courses near
 * the student's level score highest; far-below courses (safe but unambitious)
 * and reach courses score lower. Fixes the old rule that ranked a diploma
 * 17 points below the student's APS above a degree they comfortably qualify for.
 */
function proximityScore(diff) {
  if (diff < 0) return 24 + 2 * diff;   // reach: -1 → 22, -3 → 18
  if (diff <= 3) return 40;             // right at the student's level
  if (diff <= 7) return 32;
  if (diff <= 11) return 22;
  return 12;
}

function classify(diff) {
  if (diff >= 4) return 'Strong Match';
  if (diff >= 0) return 'Good Match';
  return 'Reach';
}

function explain(course, studentAps, diff, matchedInterest) {
  const apsPart = diff >= 4
    ? `Your APS of ${studentAps} comfortably exceeds the minimum of ${course.minAps}.`
    : diff >= 0
      ? `Your APS of ${studentAps} meets the minimum of ${course.minAps}.`
      : `Your APS of ${studentAps} is ${-diff} point${diff === -1 ? '' : 's'} below the usual minimum of ${course.minAps} — a reach option worth applying to.`;
  const fitPart = matchedInterest
    ? ` It aligns with your interest in ${matchedInterest}.`
    : ` It offers strong prospects in ${course.faculty}.`;
  return apsPart + fitPart;
}

/**
 * Matches a student profile against the catalogue.
 * - The catalogue is loaded once and pre-indexed (Sets for O(1) lookups).
 * - Results are memoised per profile in a bounded LRU cache.
 */
function createMatchingService({ courseRepository, cacheSize = 1000, cacheTtlMs = 10 * 60_000 }) {
  let index = null;
  const cache = new LruCache({ maxEntries: cacheSize, ttlMs: cacheTtlMs });

  function getIndex() {
    index ??= courseRepository.findAll().map((c) => ({ ...c, interestSet: new Set(c.interests) }));
    return index;
  }

  function cacheKey(subjects, interests, studyLocation) {
    const subj = subjects.map((s) => `${s.name}:${s.grade}`).sort().join(',');
    return `${subj}|${[...interests].sort().join(',')}|${studyLocation || ''}`;
  }

  function compute(subjects, interests, studyLocation) {
    const grades = new Map(subjects.map((s) => [s.name, s.grade]));
    const studentAps = aps.calculateAps(subjects);
    const wanted = new Set(interests);
    const candidates = [];

    for (const course of getIndex()) {
      if (!course.requiredSubjects.every((subject) => grades.has(subject))) continue;
      if (course.mathMin > 0 && !(grades.get(aps.RULES.MATHS) >= course.mathMin)) continue;
      const diff = studentAps - course.minAps;
      if (diff < -REACH_WINDOW) continue;

      const matchedInterest = course.interests.find((i) => wanted.has(i)) || null;
      let score = proximityScore(diff);
      for (const i of course.interests) if (wanted.has(i)) score += 20;
      if (studyLocation && course.province === studyLocation) score += 10;

      candidates.push({
        courseId: course.id,
        course: course.course,
        institution: course.institution,
        faculty: course.faculty,
        province: course.province,
        minAps: course.minAps,
        matchType: classify(diff),
        score,
        reason: explain(course, studentAps, diff, matchedInterest),
        requirements: course.requiredSubjects.length
          ? `Requires ${course.requiredSubjects.join(', ')}${course.mathMin ? ` (Mathematics ≥ ${course.mathMin}%)` : ''}`
          : ''
      });
    }

    candidates.sort((a, b) => b.score - a.score || b.minAps - a.minAps || a.courseId - b.courseId);

    // Prefer variety (max 2 per institution), then top up with the best remaining.
    const picked = [];
    const perInstitution = new Map();
    const leftovers = [];
    for (const c of candidates) {
      const n = perInstitution.get(c.institution) || 0;
      if (picked.length < MAX_RESULTS && n < MAX_PER_INSTITUTION) {
        picked.push(c);
        perInstitution.set(c.institution, n + 1);
      } else leftovers.push(c);
    }
    for (const c of leftovers) { if (picked.length >= MAX_RESULTS) break; picked.push(c); }
    picked.sort((a, b) => b.score - a.score || b.minAps - a.minAps || a.courseId - b.courseId);

    return Object.freeze({ aps: studentAps, matches: Object.freeze(picked), totalEligible: candidates.length });
  }

  return {
    /** profile: { subjects:[{name,grade}], interests:[], studyLocation } */
    match({ subjects, interests = [], studyLocation = '' }) {
      return cache.wrap(cacheKey(subjects, interests, studyLocation), () => compute(subjects, interests, studyLocation));
    },
    invalidate() { index = null; cache.clear(); },
    stats: () => ({ size: cache.size, hits: cache.hits, misses: cache.misses })
  };
}

module.exports = { createMatchingService, MAX_RESULTS };
