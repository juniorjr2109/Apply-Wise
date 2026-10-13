'use strict';
const aps = require('../../shared/aps');
const { HttpError } = require('../lib/http');
const { newRef } = require('../lib/security');

const NAME_RE = /^[\p{L}][\p{L}\p{M}' ’.-]{0,49}$/u;
const REF_RE = /^[A-Za-z0-9_-]{16}$/;
const IDEMPOTENCY_RE = /^[A-Za-z0-9-]{16,64}$/;
const CONTROL_CHARS = /[\u0000-\u001F\u007F]/g;

const cleanText = (value, max) => String(value ?? '').replace(CONTROL_CHARS, '').replace(/\s+/g, ' ').trim().slice(0, max);

/** Validates and normalises a submission. Throws 422 with per-field messages. */
function validateSubmission(body, now = new Date()) {
  const errors = {};
  const firstName = cleanText(body.firstName, 60);
  const lastName = cleanText(body.lastName, 60);
  if (!NAME_RE.test(firstName)) errors.firstName = 'Enter a valid first name (letters, spaces, hyphens, apostrophes).';
  if (!NAME_RE.test(lastName)) errors.lastName = 'Enter a valid last name (letters, spaces, hyphens, apostrophes).';

  const province = cleanText(body.province, 40);
  if (!aps.isProvince(province)) errors.province = 'Choose a South African province.';

  const matricYear = Number(body.matricYear);
  if (!aps.matricYears(now).includes(matricYear)) errors.matricYear = 'Choose a valid matric year.';

  const subjectCheck = aps.validateSubjects(body.subjects);
  if (!subjectCheck.ok) errors.subjects = subjectCheck.errors;

  const interestsRaw = Array.isArray(body.interests) ? body.interests : [];
  const interests = [...new Set(interestsRaw.map((i) => cleanText(i, 60)))];
  if (interests.length > aps.RULES.MAX_INTERESTS || interests.some((i) => !aps.isInterest(i))) {
    errors.interests = 'Choose interests from the list provided.';
  }

  const careerGoal = cleanText(body.careerGoal, 100);
  const studyLocation = cleanText(body.studyLocation, 40);
  if (studyLocation && !aps.isProvince(studyLocation)) errors.studyLocation = 'Choose a province or leave as "No preference".';

  if (Object.keys(errors).length) throw new HttpError(422, 'Please correct the highlighted fields.', errors);
  return {
    student: { firstName, lastName, province, matricYear, careerGoal, studyLocation },
    subjects: subjectCheck.subjects,
    interests
  };
}

function createApplicationService({ applicationRepository, matchingService, onChange = () => {} }) {
  return {
    /**
     * Saves a submission and returns its public ref. Re-sending the same
     * Idempotency-Key returns the original result instead of a duplicate row.
     */
    submit(body, idempotencyKey, userId = null) {
      const key = idempotencyKey && IDEMPOTENCY_RE.test(idempotencyKey) ? idempotencyKey : null;
      if (key) {
        const existing = applicationRepository.findByIdempotencyKey(key);
        if (existing) return { ...existing, replayed: true };
      }
      const input = validateSubmission(body);
      const result = matchingService.match({ ...input, studyLocation: input.student.studyLocation });
      const ref = newRef();
      try {
        applicationRepository.create({ ref, idempotencyKey: key, ...input, aps: result.aps, matchCount: result.matches.length, userId });
      } catch (err) {
        // Two concurrent requests with the same key: the loser reads the winner's row.
        if (key && /UNIQUE constraint failed: applications.idempotency_key/.test(err.message)) {
          return { ...applicationRepository.findByIdempotencyKey(key), replayed: true };
        }
        throw err;
      }
      onChange();
      return { ref, aps: result.aps, replayed: false };
    },

    /** Public results for a ref. Matches are recomputed from the live catalogue. */
    getResults(ref) {
      if (!REF_RE.test(String(ref))) throw new HttpError(400, 'Invalid results link.');
      const app = applicationRepository.findByRef(ref);
      if (!app) throw new HttpError(404, 'We could not find those results. The link may be wrong or the record was removed.');
      const result = matchingService.match({ subjects: app.subjects, interests: app.interests, studyLocation: app.studyLocation });
      return {
        student: { firstName: app.firstName, province: app.province, matricYear: app.matricYear },
        aps: app.aps,
        subjectCount: app.subjects.length,
        matches: result.matches,
        totalEligible: result.totalEligible
      };
    },

    list(query) {
      const page = Math.max(1, Number.parseInt(query.page, 10) || 1);
      const pageSize = Math.min(100, Math.max(5, Number.parseInt(query.pageSize, 10) || 10));
      const q = cleanText(query.q, 60);
      const province = aps.isProvince(query.province) ? query.province : '';
      const { items, total } = applicationRepository.list({ page, pageSize, q, province });
      return { items, total, page, pageSize, pages: Math.max(1, Math.ceil(total / pageSize)) };
    },

    /** A signed-in user's own applications (newest first, max 50). */
    listForUser: (userId) => applicationRepository.listByUser(userId),

    getDetail(id) {
      const app = applicationRepository.findById(parseId(id));
      if (!app) throw new HttpError(404, 'Application not found.');
      const { matches } = matchingService.match({ subjects: app.subjects, interests: app.interests, studyLocation: app.studyLocation });
      return { ...app, matches };
    },

    remove(id) {
      if (!applicationRepository.remove(parseId(id))) throw new HttpError(404, 'Application not found.');
      onChange();
    },

    iterateForExport: () => applicationRepository.iterateForExport()
  };
}

function parseId(id) {
  const n = Number(id);
  if (!Number.isSafeInteger(n) || n < 1) throw new HttpError(400, 'Invalid application id.');
  return n;
}

module.exports = { createApplicationService, validateSubmission };
