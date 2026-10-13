/**
 * shared/aps.js — single source of truth for Apply-Wise domain rules.
 *
 * Loaded by the browser as a classic <script> (exposes window.AW.aps) and by
 * Node via require(). Keeping one copy removes the duplicated APS logic that
 * previously lived in apply.html, results.html and server.js.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else { root.AW = root.AW || {}; root.AW.aps = factory(); }
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  const SUBJECTS = Object.freeze([
    'English Home Language', 'English First Additional Language',
    'Afrikaans Home Language', 'Afrikaans First Additional Language',
    'IsiZulu Home Language', 'IsiXhosa Home Language', 'Sesotho Home Language',
    'Setswana Home Language', 'Sepedi Home Language', 'Xitsonga Home Language',
    'Siswati Home Language', 'Tshivenda Home Language', 'IsiNdebele Home Language',
    'Mathematics', 'Mathematical Literacy', 'Technical Mathematics',
    'Physical Sciences', 'Technical Sciences', 'Life Sciences',
    'Geography', 'History', 'Accounting', 'Business Studies', 'Economics',
    'Life Orientation', 'Computer Applications Technology', 'Information Technology',
    'Tourism', 'Consumer Studies', 'Hospitality Studies', 'Agricultural Sciences',
    'Visual Arts', 'Dramatic Arts', 'Music', 'Civil Technology',
    'Mechanical Technology', 'Electrical Technology', 'Engineering Graphics & Design'
  ]);

  const INTERESTS = Object.freeze([
    'Engineering & Technology', 'Health Sciences', 'Business & Commerce', 'Law',
    'Education & Teaching', 'Arts & Humanities', 'Natural Sciences', 'Social Sciences',
    'Agriculture & Environment', 'Architecture & Design', 'Media & Communication',
    'Finance & Accounting'
  ]);

  const PROVINCES = Object.freeze([
    'Gauteng', 'Western Cape', 'KwaZulu-Natal', 'Eastern Cape', 'Limpopo',
    'Mpumalanga', 'North West', 'Free State', 'Northern Cape'
  ]);

  const RULES = Object.freeze({
    EXCLUDED_SUBJECT: 'Life Orientation',
    MATHS: 'Mathematics',
    MATHS_VARIANTS: Object.freeze(['Mathematics', 'Mathematical Literacy', 'Technical Mathematics']),
    COUNTED_SUBJECTS: 6,      // APS = best 6 subjects excluding Life Orientation
    MIN_COUNTED_SUBJECTS: 6,  // a valid NSC profile has at least 6 non-LO subjects
    MAX_SUBJECTS: 10,
    MAX_APS: 42,
    MAX_INTERESTS: 12
  });

  const SUBJECT_SET = new Set(SUBJECTS);
  const INTEREST_SET = new Set(INTERESTS);
  const PROVINCE_SET = new Set(PROVINCES);

  /** NSC achievement level (1–7) for a percentage. 80–100 = 7 … 0–29 = 1. */
  function gradeToPoints(percentage) {
    const pct = Number(percentage);
    if (!Number.isFinite(pct) || pct < 0) return 0;
    if (pct >= 80) return 7;
    if (pct >= 70) return 6;
    if (pct >= 60) return 5;
    if (pct >= 50) return 4;
    if (pct >= 40) return 3;
    if (pct >= 30) return 2;
    return 1;
  }

  /**
   * APS = sum of the best six subject levels, excluding Life Orientation.
   * Duplicate subject names are counted once (first occurrence wins), so a
   * student cannot inflate the score by repeating or adding extra subjects.
   */
  function calculateAps(subjects) {
    if (!Array.isArray(subjects)) return 0;
    const seen = new Set();
    const points = [];
    for (const s of subjects) {
      if (!s || !s.name || s.name === RULES.EXCLUDED_SUBJECT || seen.has(s.name)) continue;
      if (s.grade === '' || s.grade === null || s.grade === undefined) continue;
      seen.add(s.name);
      points.push(gradeToPoints(s.grade));
    }
    points.sort((a, b) => b - a);
    let total = 0;
    for (let i = 0; i < Math.min(points.length, RULES.COUNTED_SUBJECTS); i++) total += points[i];
    return total;
  }

  /**
   * Validates a subject list. Returns { ok, errors: string[], subjects }
   * where subjects is the normalised list ({ name, grade:int, points }).
   */
  function validateSubjects(input) {
    const errors = [];
    if (!Array.isArray(input) || input.length === 0) {
      return { ok: false, errors: ['Add your subjects and grades.'], subjects: [] };
    }
    if (input.length > RULES.MAX_SUBJECTS) {
      errors.push(`You can enter at most ${RULES.MAX_SUBJECTS} subjects.`);
    }
    const seen = new Set();
    const subjects = [];
    input.forEach((raw, i) => {
      const row = i + 1;
      const name = raw && typeof raw.name === 'string' ? raw.name.trim() : '';
      const gradeRaw = raw ? raw.grade : undefined;
      if (!name) { errors.push(`Row ${row}: choose a subject.`); return; }
      if (!SUBJECT_SET.has(name)) { errors.push(`Row ${row}: "${name}" is not a recognised NSC subject.`); return; }
      if (seen.has(name)) { errors.push(`${name} is listed more than once.`); return; }
      const grade = gradeRaw === '' || gradeRaw === null ? NaN : Number(gradeRaw);
      if (!Number.isFinite(grade) || grade < 0 || grade > 100) {
        errors.push(`${name}: enter a percentage between 0 and 100.`); return;
      }
      seen.add(name);
      const rounded = Math.round(grade);
      subjects.push({ name, grade: rounded, points: gradeToPoints(rounded) });
    });

    const mathsTaken = RULES.MATHS_VARIANTS.filter((m) => seen.has(m));
    if (mathsTaken.length > 1) errors.push(`Choose only one of: ${mathsTaken.join(', ')}.`);
    if (seen.size && ![...seen].some((n) => n.endsWith('Home Language'))) {
      errors.push('Include your Home Language subject.');
    }
    const counted = subjects.filter((s) => s.name !== RULES.EXCLUDED_SUBJECT).length;
    if (counted < RULES.MIN_COUNTED_SUBJECTS && errors.length === 0) {
      errors.push(`Enter at least ${RULES.MIN_COUNTED_SUBJECTS} subjects besides Life Orientation.`);
    }
    return { ok: errors.length === 0, errors, subjects };
  }

  /** Matric years accepted: ten years back up to next year's cohort. */
  function matricYears(now) {
    const year = (now || new Date()).getFullYear();
    const years = [];
    for (let y = year + 1; y >= year - 10; y--) years.push(y);
    return years;
  }

  return Object.freeze({
    SUBJECTS, INTERESTS, PROVINCES, RULES,
    isSubject: (s) => SUBJECT_SET.has(s),
    isInterest: (s) => INTEREST_SET.has(s),
    isProvince: (s) => PROVINCE_SET.has(s),
    gradeToPoints, calculateAps, validateSubjects, matricYears
  });
});
