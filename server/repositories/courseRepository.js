'use strict';

const split = (value) => (value ? value.split('|') : []);

/** Read access to the course catalogue (one query, no N+1). */
function createCourseRepository(db) {
  const findAllStmt = db.prepare(`
    SELECT c.id, c.name AS course, c.faculty, c.min_aps AS minAps, c.math_min AS mathMin,
           i.name AS institution, i.province,
           (SELECT group_concat(subject, '|')  FROM course_subjects  WHERE course_id = c.id) AS subjects,
           (SELECT group_concat(interest, '|') FROM course_interests WHERE course_id = c.id) AS interests
    FROM courses c
    JOIN institutions i ON i.id = c.institution_id
    ORDER BY c.id`);
  const countStmt = db.prepare(`
    SELECT (SELECT COUNT(*) FROM courses) AS courses, (SELECT COUNT(*) FROM institutions) AS institutions`);

  return {
    findAll() {
      return findAllStmt.all().map((row) => ({
        id: row.id,
        course: row.course,
        faculty: row.faculty,
        institution: row.institution,
        province: row.province,
        minAps: row.minAps,
        mathMin: row.mathMin,
        requiredSubjects: split(row.subjects),
        interests: split(row.interests)
      }));
    },
    counts() { return { ...countStmt.get() }; }
  };
}

module.exports = { createCourseRepository };
