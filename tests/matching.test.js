'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const { createMatchingService } = require('../server/services/matchingService');

const course = (id, overrides) => ({
  id, course: `Course ${id}`, faculty: 'Science', institution: `Uni ${id}`, province: 'Gauteng',
  minAps: 30, mathMin: 0, requiredSubjects: [], interests: [], ...overrides
});
const subjects = (mathGrade) => [
  { name: 'English Home Language', grade: 70 }, { name: 'Mathematics', grade: mathGrade },
  { name: 'Physical Sciences', grade: 70 }, { name: 'Life Sciences', grade: 70 },
  { name: 'Geography', grade: 70 }, { name: 'History', grade: 70 }
]; // APS = 36 when maths is 70–79

function service(courses) {
  let loads = 0;
  const svc = createMatchingService({ courseRepository: { findAll: () => { loads++; return courses; } } });
  return { svc, loads: () => loads };
}

test('excludes courses whose subject or Maths minimum is not met', () => {
  const { svc } = service([
    course(1, { requiredSubjects: ['Accounting'] }),
    course(2, { requiredSubjects: ['Mathematics'], mathMin: 80 }),
    course(3, { requiredSubjects: ['Mathematics'], mathMin: 70 })
  ]);
  const ids = svc.match({ subjects: subjects(72) }).matches.map((m) => m.courseId);
  assert.deepEqual(ids, [3]);
});

test('classifies Strong / Good / Reach and drops courses more than 3 points out of reach', () => {
  const { svc } = service([course(1, { minAps: 30 }), course(2, { minAps: 35 }), course(3, { minAps: 38 }), course(4, { minAps: 40 })]);
  const byId = Object.fromEntries(svc.match({ subjects: subjects(72) }).matches.map((m) => [m.courseId, m.matchType]));
  assert.equal(byId[1], 'Strong Match');
  assert.equal(byId[2], 'Good Match');
  assert.equal(byId[3], 'Reach');
  assert.equal(byId[4], undefined);
});

test('ranks courses near the student\'s APS above far-below "safe" courses', () => {
  const { svc } = service([course(1, { minAps: 18 }), course(2, { minAps: 34 })]);
  assert.equal(svc.match({ subjects: subjects(72) }).matches[0].courseId, 2);
});

test('interests and preferred province raise the ranking', () => {
  const { svc } = service([course(1, { minAps: 34 }), course(2, { minAps: 34, interests: ['Law'], province: 'Limpopo' })]);
  const top = svc.match({ subjects: subjects(72), interests: ['Law'], studyLocation: 'Limpopo' }).matches[0];
  assert.equal(top.courseId, 2);
});

test('limits to 12 results, preferring at most 2 per institution', () => {
  const courses = Array.from({ length: 30 }, (_, i) => course(i + 1, { institution: `Uni ${i % 3}`, minAps: 34 }));
  const { svc } = service(courses);
  const { matches } = svc.match({ subjects: subjects(72) });
  assert.equal(matches.length, 12);
});

test('loads the catalogue once and caches results per profile', () => {
  const { svc, loads } = service([course(1)]);
  const first = svc.match({ subjects: subjects(72) });
  const second = svc.match({ subjects: [...subjects(72)].reverse() }); // same profile, different order
  assert.equal(first, second);
  assert.equal(loads(), 1);
  svc.invalidate();
  svc.match({ subjects: subjects(72) });
  assert.equal(loads(), 2);
});
