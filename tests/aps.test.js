'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const aps = require('../shared/aps');

test('NSC achievement levels: 80+ is 7, below 30 is 1', () => {
  assert.equal(aps.gradeToPoints(100), 7);
  assert.equal(aps.gradeToPoints(80), 7);
  assert.equal(aps.gradeToPoints(79.9), 6);
  assert.equal(aps.gradeToPoints(50), 4);
  assert.equal(aps.gradeToPoints(30), 2);
  assert.equal(aps.gradeToPoints(29), 1);
  assert.equal(aps.gradeToPoints(0), 1);
  assert.equal(aps.gradeToPoints(-5), 0);
  assert.equal(aps.gradeToPoints('abc'), 0);
});

test('APS excludes Life Orientation and counts only the best six subjects', () => {
  const subjects = ['A', 'B', 'C', 'D', 'E', 'F', 'G'].map((n, i) => ({ name: n, grade: 85 - i * 10 }));
  // levels: 85→7, 75→6, 65→5, 55→4, 45→3, 35→2, 25→1 ; best six = 27
  assert.equal(aps.calculateAps(subjects), 27);
  assert.equal(aps.calculateAps([...subjects, { name: 'Life Orientation', grade: 99 }]), 27);
});

test('APS ignores duplicates and blank grades, and never exceeds 42', () => {
  const dupes = [{ name: 'Mathematics', grade: 90 }, { name: 'Mathematics', grade: 90 }];
  assert.equal(aps.calculateAps(dupes), 7);
  assert.equal(aps.calculateAps([{ name: 'History', grade: '' }]), 0);
  const many = aps.SUBJECTS.map((name) => ({ name, grade: 100 }));
  assert.equal(aps.calculateAps(many), 42);
  assert.equal(aps.calculateAps(null), 0);
});

test('validateSubjects catches every invalid profile', () => {
  const base = [
    { name: 'English Home Language', grade: 60 }, { name: 'Mathematics', grade: 60 },
    { name: 'Physical Sciences', grade: 60 }, { name: 'Life Sciences', grade: 60 },
    { name: 'Geography', grade: 60 }, { name: 'History', grade: 60 }
  ];
  assert.equal(aps.validateSubjects(base).ok, true);
  assert.equal(aps.validateSubjects([]).ok, false);
  assert.equal(aps.validateSubjects(base.slice(0, 5)).ok, false, 'needs 6 non-LO subjects');
  assert.equal(aps.validateSubjects([...base, { name: 'Mathematics', grade: 70 }]).ok, false, 'duplicate');
  assert.equal(aps.validateSubjects([...base, { name: 'Mathematical Literacy', grade: 70 }]).ok, false, 'maths + maths lit');
  assert.equal(aps.validateSubjects([...base.slice(1), { name: 'Economics', grade: 60 }]).ok, false, 'no home language');
  assert.equal(aps.validateSubjects([...base.slice(0, 5), { name: 'History', grade: 101 }]).ok, false, 'grade > 100');
  assert.equal(aps.validateSubjects([...base.slice(0, 5), { name: 'Basket Weaving', grade: 60 }]).ok, false, 'unknown subject');
  assert.equal(aps.validateSubjects(base).subjects[0].grade, 60);
});
