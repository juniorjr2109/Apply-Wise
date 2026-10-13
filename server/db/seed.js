'use strict';
const fs = require('node:fs');
const { transaction } = require('./connection');
const aps = require('../../shared/aps');

/**
 * Upserts the course catalogue from seed-courses.json. Safe to run on every
 * start: existing rows are updated in place, nothing is duplicated.
 * Returns the number of courses in the catalogue.
 */
function seedCatalogue(db, seedFile) {
  const rows = JSON.parse(fs.readFileSync(seedFile, 'utf8'));
  for (const r of rows) {
    const bad = [...r.requiredSubjects.filter((s) => !aps.isSubject(s)), ...r.interests.filter((i) => !aps.isInterest(i))];
    if (bad.length || !aps.isProvince(r.province)) {
      throw new Error(`Invalid seed row "${r.course}" at ${r.institution}: ${bad.join(', ') || r.province}`);
    }
  }

  const upsertInstitution = db.prepare(`
    INSERT INTO institutions (name, province) VALUES (?, ?)
    ON CONFLICT(name) DO UPDATE SET province = excluded.province
    RETURNING id`);
  const upsertCourse = db.prepare(`
    INSERT INTO courses (institution_id, name, faculty, min_aps, math_min) VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(institution_id, name) DO UPDATE SET
      faculty = excluded.faculty, min_aps = excluded.min_aps, math_min = excluded.math_min
    RETURNING id`);
  const clearSubjects = db.prepare('DELETE FROM course_subjects WHERE course_id = ?');
  const clearInterests = db.prepare('DELETE FROM course_interests WHERE course_id = ?');
  const addSubject = db.prepare('INSERT INTO course_subjects (course_id, subject) VALUES (?, ?)');
  const addInterest = db.prepare('INSERT INTO course_interests (course_id, interest) VALUES (?, ?)');

  transaction(db, () => {
    const institutionIds = new Map();
    for (const r of rows) {
      if (!institutionIds.has(r.institution)) {
        institutionIds.set(r.institution, upsertInstitution.get(r.institution, r.province).id);
      }
      const courseId = upsertCourse.get(institutionIds.get(r.institution), r.course, r.faculty, r.minAps, r.mathMin).id;
      clearSubjects.run(courseId);
      clearInterests.run(courseId);
      // A Maths percentage requirement implies Mathematics is required.
      const subjects = new Set(r.requiredSubjects);
      if (r.mathMin > 0) subjects.add(aps.RULES.MATHS);
      subjects.forEach((s) => addSubject.run(courseId, s));
      new Set(r.interests).forEach((i) => addInterest.run(courseId, i));
    }
  });
  return db.prepare('SELECT COUNT(*) AS n FROM courses').get().n;
}

module.exports = { seedCatalogue };
