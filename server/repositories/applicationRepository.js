'use strict';
const { transaction } = require('../db/connection');

const COLUMNS = `
  a.id, a.ref, a.first_name AS firstName, a.last_name AS lastName, a.province,
  a.matric_year AS matricYear, a.aps, a.career_goal AS careerGoal,
  a.study_location AS studyLocation, a.match_count AS matchCount, a.created_at AS createdAt`;

const escapeLike = (s) => s.replace(/[\\%_]/g, (c) => '\\' + c);
const plain = (row) => (row ? { ...row } : null);

function createApplicationRepository(db) {
  // Listing SQL varies only by which filters are present (4 variants), so
  // prepared statements are cached by SQL text instead of re-parsed per call.
  const statementCache = new Map();
  const prepared = (sql) => {
    let stmt = statementCache.get(sql);
    if (!stmt) { stmt = db.prepare(sql); statementCache.set(sql, stmt); }
    return stmt;
  };

  const s = {
    insert: db.prepare(`
      INSERT INTO applications (ref, idempotency_key, first_name, last_name, province, matric_year,
                                aps, career_goal, study_location, match_count, user_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id`),
    byUser: db.prepare(`SELECT ${COLUMNS} FROM applications a WHERE a.user_id = ? ORDER BY a.created_at DESC, a.id DESC LIMIT 50`),
    insertSubject: db.prepare('INSERT INTO application_subjects (application_id, subject, grade, points) VALUES (?, ?, ?, ?)'),
    insertInterest: db.prepare('INSERT INTO application_interests (application_id, interest) VALUES (?, ?)'),
    byRef: db.prepare(`SELECT ${COLUMNS} FROM applications a WHERE a.ref = ?`),
    byId: db.prepare(`SELECT ${COLUMNS} FROM applications a WHERE a.id = ?`),
    byIdempotencyKey: db.prepare('SELECT ref, aps FROM applications WHERE idempotency_key = ?'),
    subjects: db.prepare('SELECT subject AS name, grade, points FROM application_subjects WHERE application_id = ? ORDER BY points DESC, subject'),
    interests: db.prepare('SELECT interest FROM application_interests WHERE application_id = ? ORDER BY interest'),
    remove: db.prepare('DELETE FROM applications WHERE id = ?'),
    exportAll: db.prepare(`
      SELECT ${COLUMNS},
             (SELECT group_concat(interest, '; ') FROM application_interests WHERE application_id = a.id) AS interests
      FROM applications a ORDER BY a.created_at DESC`),
    totals: db.prepare(`
      SELECT COUNT(*) AS total,
             ROUND(AVG(aps), 1) AS avgAps,
             ROUND(AVG(match_count), 1) AS avgMatches,
             SUM(created_at >= strftime('%Y-%m-%dT00:00:00Z','now')) AS today
      FROM applications`),
    byProvince: db.prepare('SELECT province AS label, COUNT(*) AS value FROM applications GROUP BY province ORDER BY value DESC'),
    apsBands: db.prepare(`
      SELECT CASE WHEN aps < 20 THEN 'Under 20' WHEN aps < 25 THEN '20–24' WHEN aps < 30 THEN '25–29'
                  WHEN aps < 35 THEN '30–34' ELSE '35+' END AS label,
             MIN(aps) AS sortKey, COUNT(*) AS value
      FROM applications GROUP BY label ORDER BY sortKey`),
    topInterests: db.prepare(`
      SELECT interest AS label, COUNT(*) AS value FROM application_interests
      GROUP BY interest ORDER BY value DESC, interest LIMIT 8`),
    daily: db.prepare(`
      SELECT substr(created_at, 1, 10) AS day, COUNT(*) AS value FROM applications
      WHERE created_at >= strftime('%Y-%m-%dT00:00:00Z', 'now', '-13 days')
      GROUP BY day ORDER BY day`)
  };

  function withDetails(row) {
    if (!row) return null;
    return {
      ...row,
      subjects: s.subjects.all(row.id).map(plain),
      interests: s.interests.all(row.id).map((r) => r.interest)
    };
  }

  return {
    /** Inserts an application and its child rows atomically. */
    create({ ref, idempotencyKey, student, subjects, interests, aps, matchCount, userId = null }) {
      return transaction(db, () => {
        const { id } = s.insert.get(ref, idempotencyKey, student.firstName, student.lastName, student.province,
          student.matricYear, aps, student.careerGoal, student.studyLocation, matchCount, userId);
        for (const sub of subjects) s.insertSubject.run(id, sub.name, sub.grade, sub.points);
        for (const interest of interests) s.insertInterest.run(id, interest);
        return id;
      });
    },

    findByIdempotencyKey: (key) => plain(s.byIdempotencyKey.get(key)),
    listByUser: (userId) => s.byUser.all(userId).map(plain),
    findByRef: (ref) => withDetails(plain(s.byRef.get(ref))),
    findById: (id) => withDetails(plain(s.byId.get(id))),
    remove: (id) => Number(s.remove.run(id).changes) > 0,

    /** Paginated, filterable listing. Search uses parameterised LIKE (no injection). */
    list({ page, pageSize, q, province }) {
      const where = [];
      const params = [];
      if (q) {
        const term = `%${escapeLike(q)}%`;
        where.push(`(a.first_name LIKE ? ESCAPE '\\' OR a.last_name LIKE ? ESCAPE '\\' OR (a.first_name || ' ' || a.last_name) LIKE ? ESCAPE '\\')`);
        params.push(term, term, term);
      }
      if (province) { where.push('a.province = ?'); params.push(province); }
      const clause = where.length ? `WHERE ${where.join(' AND ')}` : '';
      const total = prepared(`SELECT COUNT(*) AS n FROM applications a ${clause}`).get(...params).n;
      const items = prepared(`SELECT ${COLUMNS} FROM applications a ${clause}
                                ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?`)
        .all(...params, pageSize, (page - 1) * pageSize).map(plain);
      return { items, total };
    },

    /** Streams rows for CSV export without loading the whole table into memory. */
    iterateForExport: () => s.exportAll.iterate(),

    aggregates() {
      const totals = plain(s.totals.get());
      return {
        total: totals.total,
        today: totals.today || 0,
        avgAps: totals.avgAps,
        avgMatches: totals.avgMatches,
        byProvince: s.byProvince.all().map(plain),
        apsBands: s.apsBands.all().map(({ label, value }) => ({ label, value })),
        topInterests: s.topInterests.all().map(plain),
        daily: s.daily.all().map(plain)
      };
    }
  };
}

module.exports = { createApplicationRepository };
