# Apply-Wise v2 — Engineering Report

WebWizards · BICT231 · 2026

This report documents how Apply-Wise was rebuilt from a set of static pages into a production-ready web application with an admin dashboard. It is organised around the seven questions in the brief. The code it describes is in this repository. File paths are given so every claim can be checked.

---

## 1. Requirements, edge cases and plan

### 1.1 Requirements

**Students** must be able to enter their details and NSC results, see their APS calculated as they type, and receive a ranked list of courses and institutions they qualify for. Their results must survive a page refresh and be shareable as a link.

**Administrators** must be able to sign in securely and see a dashboard with totals, trends and breakdowns. They must be able to search, page through, inspect, delete and export applications.

**The assessment rubric** additionally requires original code (no templates or builders), real back-end logic, a database that stores, retrieves and deletes data, and a dashboard panel. The system must also run on the group's Windows laptops, where `npm` was blocked by PowerShell's execution policy.

### 1.2 Key design decision

The back end uses **only modules built into Node.js**: `node:http`, `node:sqlite`, `node:crypto` and `node:test`. This means there is no `npm install` step and no `node_modules` folder, which removes the setup failures the group experienced. It also guarantees that every line of server logic is the group's own work.

### 1.3 Edge cases identified

| Area | Edge case | Handling |
|---|---|---|
| APS | More than 6 subjects entered | Only the best 6 count (`shared/aps.js → calculateAps`) |
| APS | Same subject entered twice | Rejected by validation. It is also ignored by the calculator as a second line of defence |
| APS | Both Mathematics and Mathematical Literacy | Rejected |
| APS | No Home Language | Rejected (every NSC needs one) |
| APS | Grade blank, negative, above 100 or decimal | Blank or out of range is rejected. Decimals are rounded |
| Matching | Student qualifies for nothing | Results page explains alternatives (bridging, TVET, upgrading) |
| Matching | Maths minimum set but Maths not listed as required | The seeder adds Mathematics automatically |
| Submission | Double-click, slow network retry | `Idempotency-Key` header. A repeated key returns the original record |
| Submission | Two identical keys arrive at the same moment | The unique index wins and the loser reads the winner's row |
| Results | Refresh, new tab, shared link | Results load by an unguessable `ref` in the URL, not from browser storage |
| Results | Wrong or deleted link | Clear 400 or 404 message with a route back to the form |
| Form | Page refreshed mid-form | Draft saved to `sessionStorage` and restored |
| Form | Enter pressed on step 1 | Advances to the next step instead of submitting |
| Storage | Private browsing, full or corrupt storage | `AW.store` wrapper never throws |
| Auth | Password guessing | Account locks after 5 failures per IP in 15 minutes, and an IP locks after 20 failures across accounts |
| Auth | Probing for valid usernames | Same message and same response time whether or not the user exists |
| Auth | Session expires while the dashboard is open | Any 401 redirects to sign-in with "session expired". There is also a 5-minute warning |
| Auth | Open redirect through `?next=` | Only `/admin/...` paths are accepted |
| Dashboard | Fast typing in search | 300 ms debounce. Earlier requests are aborted so stale results never overwrite new ones |
| Dashboard | Deleting the last row on the last page | Steps back to the previous page |
| Dashboard | Record deleted in another tab | Delete returns 404, a toast explains, the table refreshes |
| Export | Names or goals starting with `=`, `+`, `-`, `@` | Prefixed with `'` so Excel never runs them as formulas |
| HTTP | Malformed JSON, wrong content type, huge body | 400, 415 and 413 respectively. Oversized bodies are drained, not buffered |
| HTTP | `/../../server/config.js` | Path resolved and checked against the public folder → 403 |
| Ops | Port already in use, old Node version | Clear startup message telling the user what to do |
| Ops | Opening pages from `file://` | Banner explains how to start the server |

### 1.4 Plan

1. Move all domain rules (subjects, APS, validation) into one shared module used by both browser and server.
2. Design a normalised SQLite schema with migrations.
3. Build the server in layers: routes → services → repositories → database.
4. Rebuild the pages with no inline scripts so a strict Content Security Policy can be enforced.
5. Add the admin login and dashboard.
6. Write automated tests and a benchmark, and verify in a real browser.

---

## 2. Architecture and implementation

### 2.1 Architecture

```
 Browser (HTML + CSS + classic JS, no build step)
 ┌──────────────────────────────────────────────────────────┐
 │ index · apply · results · admin/login · admin/dashboard  │
 │ js/core/app.js  ← DOM helpers, escaping, API client      │
 │ shared/aps.js   ← same file the server uses              │
 └───────────────▲──────────────────────────┬───────────────┘
                 │ static files (ETag, gzip) │ JSON over fetch
 ┌───────────────┴──────────────────────────▼───────────────┐
 │ server/app.js — composition root, security headers,      │
 │                 error handling, request ids              │
 │  lib/static.js        lib/router.js + middleware/        │
 │                        ├ routes/publicRoutes.js          │
 │                        └ routes/adminRoutes.js           │
 │  services/  matching · application · auth · stats        │
 │  repositories/  course · application · user/session/audit│
 │  db/  connection (WAL, migrations) · seed                │
 └──────────────────────────┬───────────────────────────────┘
                            ▼
                 SQLite file: data/applywise.db
```

Each layer only calls the layer directly beneath it. Routes never run SQL, and repositories never know about HTTP.

### 2.2 Folder structure

```
apply-wise/
├── package.json · README.md · .env.example · .gitignore
├── shared/aps.js                 Domain rules (browser + server)
├── server/
│   ├── index.js                  Entry: start, first admin, shutdown
│   ├── app.js                    Wiring, headers, error handling
│   ├── config.js
│   ├── lib/        http · router · static · cache · rateLimiter · security · logger
│   ├── middleware/index.js       requireAdmin · requireSameOrigin · rateLimit
│   ├── routes/     publicRoutes · adminRoutes
│   ├── services/   matching · application · auth · stats
│   ├── repositories/ course · application · user (+ session, audit)
│   ├── db/         connection · migrations · seed · seed-courses.json
│   └── scripts/create-admin.js
├── public/
│   ├── index.html · apply.html · results.html
│   ├── admin/      login.html · dashboard.html
│   ├── css/        base · home · apply · results · admin
│   └── js/         core/app.js · pages/{home,apply,results,admin-login,admin-dashboard}.js
├── tests/          aps · matching · api (28 tests) + helpers
├── bench/matching.bench.js
└── docs/ENGINEERING_REPORT.md
```

### 2.3 Database schema

Ten tables in third normal form. Subjects and interests are separate rows rather than comma-separated text, so they can be indexed and counted in SQL.

```
users ──< sessions                 institutions ──< courses ──< course_subjects
  │                                                   └──────< course_interests
  └──< audit_log
applications ──< application_subjects
             └─< application_interests
```

| Table | Purpose | Notable constraints |
|---|---|---|
| `users` | Admin accounts | `username UNIQUE COLLATE NOCASE`, scrypt hash only |
| `sessions` | Sign-in sessions | Primary key is the **SHA-256 of the token**, so a stolen database cannot be used to log in. Indexed on expiry |
| `institutions`, `courses` | Catalogue (22 institutions, 136 courses) | `UNIQUE(institution_id, name)`, `min_aps BETWEEN 0 AND 42` |
| `course_subjects`, `course_interests` | Requirements and tags | Composite primary keys, `WITHOUT ROWID` |
| `applications` | Student submissions | `ref UNIQUE` (public id), `idempotency_key UNIQUE`, indexes on `created_at` and `province` |
| `application_subjects`, `application_interests` | Submitted results | `grade BETWEEN 0 AND 100`, `ON DELETE CASCADE` |
| `audit_log` | Admin actions (login, failed login, delete, export) | Kept when a user is removed (`SET NULL`) |

Schema changes are versioned with `PRAGMA user_version` (`server/db/migrations.js`), so existing databases upgrade automatically. The seeder is idempotent and can run on every start.

### 2.4 API

| Method & path | Auth | Purpose |
|---|---|---|
| `GET /api/health` | – | Liveness check plus catalogue size |
| `POST /api/applications` | CSRF header, 20/min per IP | Validate, compute APS on the server, save, return `ref` |
| `GET /api/applications/:ref/results` | – (unguessable ref) | Matches for one student |
| `POST /api/admin/login` · `/logout` | CSRF | Start or end a session |
| `GET /api/admin/me` · `/stats` | Admin | Current user; dashboard aggregates |
| `GET /api/admin/applications` | Admin | Search, province filter, pagination |
| `GET /api/admin/applications/:id` | Admin | Full record plus current matches |
| `DELETE /api/admin/applications/:id` | Admin + CSRF | Delete (cascades to children) |
| `GET /api/admin/applications/export.csv` | Admin | Streamed CSV download |

Every error has the same shape, `{ "error": { "message", "details?" } }`, so the front end handles all failures with one code path.

### 2.5 Data flow: student submission

1. `apply.js` validates each step with `shared/aps.js` and shows inline errors.
2. On submit it sends JSON plus an `Idempotency-Key` that was created once per draft.
3. `publicRoutes` runs the CSRF and rate-limit checks.
4. `applicationService.validateSubmission` re-validates everything. The browser is never trusted, and any APS the client sends is ignored.
5. `matchingService.match` computes APS and matches against the in-memory catalogue index.
6. `applicationRepository.create` inserts the parent and child rows in **one transaction**.
7. The stats cache is invalidated and the browser receives `{ ref, aps }`.
8. The browser navigates to `results.html?ref=…`, which loads the results through the API.

### 2.6 Data flow: admin

1. `login.js` posts credentials.
2. `authService` checks the lockout, verifies the scrypt hash, stores `sha256(token)` and returns a cookie with `HttpOnly; SameSite=Strict`.
3. Dashboard requests carry the cookie automatically, and `requireAdmin` resolves it to a user on every request.
4. Logging out deletes the session row, so the cookie is dead even if it was copied.

### 2.7 UI structure

- **Landing:** hero, live catalogue numbers, problem, how it works, objectives, team.
- **Apply:** progress bar → personal → subjects (live APS, best-6 note) → interests → review (shows which subjects count).
- **Results:** greeting and APS, summary line, ranked cards (Strong, Good or Reach), disclaimer.
- **Admin login:** single card, show/hide password, lockout and expiry messages.
- **Admin dashboard:** 5 stat cards, 14-day trend, APS distribution, province and interest bar charts, searchable paginated table, detail dialog, CSV export, sign-out.

---

## 3. Audit of the previous code (v1)

### 3.1 Structural problems

| Problem | Where | Impact |
|---|---|---|
| Logic, data and markup mixed in one file | `results.html` was 43 KB with the full course list inline | Impossible to test or reuse |
| No real back end or database in the version that ran | localStorage only | Fails rubric criteria 7 and 8 (maximum 70%) |
| Authentication in the browser | `auth.js` | Provides no security (see §4) |
| Inline `onclick` and `<script>` everywhere | All pages | Blocks a Content Security Policy |
| Hard-coded option lists | Province list twice per page, 12 interest buttons, 31 subjects | Lists drift apart when edited |

### 3.2 Duplicate code

| Duplicated | Copies in v1 | v2 |
|---|---|---|
| `gradeToAPS` and APS sum | `apply.html`, `results.html`, `server.js` | One function in `shared/aps.js` |
| Course database | `results.html`, `data.js`, `server.js`, `js.html` | `seed-courses.json` → SQLite |
| Subject, province and interest lists | `apply.html`, `dashboard.html` | `shared/aps.js` constants |
| Mobile nav toggle | Pasted into every page | `initNav()` in `js/core/app.js` |
| Review and card HTML with inline styles | `apply.html`, `results.html` | CSS classes plus an escaping template helper |

### 3.3 Performance bottlenecks in v1

- The entire catalogue was parsed on every results page load.
- Matching used substring checks (`includes`) inside a nested loop.
- The dashboard re-rendered the whole table after every delete.
- 160 KB of HTML and JS shipped uncompressed with no caching headers.

### 3.4 Maintainability risks

There were no tests. Business rules were copied by hand between files, which is how the APS scale bug spread to three places. The data file had invisible formatting damage (curly quotes), and nothing alerted anyone.

### 3.5 Refactoring strategy applied

1. Extract the shared domain module.
2. Move data into the database.
3. Separate the server into routes, services and repositories with explicit dependency injection.
4. Move every page's script into its own file.
5. Replace inline styles with CSS classes.
6. Add tests before and after each change.

The improved architecture is described in §2.

---

## 4. Production bug investigation

Each bug below was reproduced, traced to its root cause, fixed, and covered by a test where possible.

### Bug 1: `js.html` throws a syntax error and nothing loads
- **Symptom:** the page script fails immediately.
- **Root cause:** the course list contains 136 typographic quotes (`“ ”`) instead of straight quotes, almost certainly from pasting through Word or a phone notes app. JavaScript treats `“` as an invalid token.
- **Fix:** data now lives in `seed-courses.json`, which is parsed with `JSON.parse` and validated by the seeder. Bad data stops the server at startup with the exact course name, instead of failing silently in a browser.

### Bug 2: anyone can open "protected" pages
- **Root cause:** `auth.js` stored users and the session in `localStorage`. `requireAuth()` only checked that a session key *exists*. Typing `localStorage.setItem('applywise_session','{}')` in the console bypasses it. Passwords used a 32-bit string hash, which has trivial collisions, and that hash was stored in plain sight.
- **Fix:** server-side sessions (`authService.js`). Passwords use scrypt with a per-user salt and constant-time comparison. Tokens are 256-bit random values stored only as SHA-256 hashes. The cookie is `HttpOnly` (JavaScript cannot read it) and `SameSite=Strict`, logout revokes on the server, and there are lockouts and an audit log. The dashboard HTML is public, but **every byte of data** comes from endpoints that return 401 without a valid session.

### Bug 3: stored XSS on the review page and dashboard
- **Root cause:** user input was inserted with `innerHTML` using template strings, for example `` `${user.firstName}` ``. A first name of `<img src=x onerror=…>` executed code in the admin's browser, which gave access to the admin session.
- **Fix:** three layers. (1) The server rejects names that are not letters (`NAME_RE`). (2) Every interpolation goes through the `html` tagged template, which escapes by default (`js/core/app.js`). (3) A Content Security Policy of `script-src 'self'` blocks injected inline scripts even if (1) and (2) ever fail.

### Bug 4: APS under-reported by about 6 points
- **Root cause:** the grade table used 90%+ = 7 and 80–89% = 6. The NSC scale is **80–100% = 7, 70–79% = 6 … 0–29% = 1**. The wrong table was copied into three files (and into the earlier requirements document), so every student appeared about one level weaker per subject.
- **Fix:** a single corrected `gradeToPoints`, with tests on every band boundary (`tests/aps.test.js`).

### Bug 5: APS inflated by adding subjects
- **Root cause:** the old code summed **all** subjects except Life Orientation. A student with 8 subjects outscored an identical student with 6.
- **Fix:** the best 6 subjects count. Duplicates are rejected, and Maths with Maths Literacy is rejected.

### Bug 6: duplicate applications in the dashboard
- **Root cause:** `results.html` saved the application *every time it loaded*, so refreshing, using back/forward, or reopening the tab created new rows. Double-clicking Submit also created two.
- **Fix:** saving happens only in `POST /api/applications`. The results page is read-only and keyed by `ref`. Submissions carry an idempotency key, backed by a unique index that also handles the simultaneous-request race.

### Bug 7: high-achieving students shown low-requirement courses first
- **Root cause:** the scoring gave the *largest* bonus to the *largest* APS surplus, so a diploma needing APS 18 outranked a degree needing 34 for a student with 35.
- **Fix:** `proximityScore` rewards courses near the student's level. A test asserts the APS-34 degree ranks above the APS-18 diploma.

### Bug 8: misleading statistics on the landing page
- **Root cause:** hard-coded "26+ universities, 1000+ programs". The catalogue has 22 institutions and 136 courses.
- **Fix:** the numbers are loaded from `/api/health`, with accurate fallback values.

### Robustness added alongside

Body size limits, slow-connection timeouts (`requestTimeout` and `headersTimeout`), graceful shutdown on Ctrl+C, hourly expired-session cleanup, request ids in error responses, and structured JSON logs.

---

## 5. Scalable system design

### 5.1 Components

| Component | Responsibility | Scales by |
|---|---|---|
| Static handler | Serve pages with ETag, gzip and memory cache | CDN in front |
| Router + middleware | Auth, CSRF, rate limits | Stateless; any number of instances |
| Matching service | Pure computation over an in-memory index | CPU; trivially parallel |
| Application, auth, stats services | Business rules | Stateless |
| Repositories | All SQL, prepared statements | Swap SQLite → PostgreSQL behind the same interface |

### 5.2 Caching strategy

| Layer | What | Policy | Invalidation |
|---|---|---|---|
| Browser | CSS and JS | `max-age=3600` + ETag → 304 | File mtime changes the ETag |
| Browser | HTML | `no-cache` + ETag | Always revalidated, so deploys are seen immediately |
| Server memory | Static file bytes and gzip | Kept until the file changes | mtime/size check |
| Server memory | Course catalogue index | Loaded once | `matchingService.invalidate()` |
| Server memory | Match results | LRU, 1000 entries, 10 min TTL | Cleared with the catalogue |
| Server memory | Dashboard stats | 30 s TTL | Cleared on every create or delete |
| Never cached | Admin API, results API | `Cache-Control: no-store` | – |

### 5.3 Growth path

| Load | Change |
|---|---|
| Today (a few hundred submissions a day) | Single Node process + SQLite in WAL mode is ample |
| Thousands of concurrent users | Run several instances behind a load balancer. Move sessions and rate-limit counters to Redis, which is the only shared state |
| Millions of rows | PostgreSQL behind the same repository interface, full-text search for names, and a materialised daily stats table |
| Large catalogue (10k+ courses) | Bucket the course index by `min_aps` so matching scans only eligible bands |

---

## 6. Performance evaluation

Measured with `node bench/matching.bench.js` on the development sandbox (Node 22, single core).

| Operation | v2 result |
|---|---|
| `calculateAps` | ≈ 1 µs per call (≈ 1,000,000 per second) |
| Catalogue reload and index build | ≈ 600 µs |
| Match, uncached (136 courses) | ≈ 5 µs |
| Match, cached | ≈ 3 µs |
| `GET /results` end to end, 50 concurrent | ≈ 1,400 requests/s, p50 ≈ 21 ms, p95 ≈ 56 ms |

**Interpretation:** the biggest gain came from loading the catalogue **once** instead of on every request, roughly **110× faster** (600 µs → 5 µs). The LRU result cache adds little at today's catalogue size, because matching 136 courses is already cheaper than building a cache key. It is kept because it bounds cost if the catalogue grows, but it is not the main win.

### Other optimisations

- **Prepared statements** are created once, and the dynamic listing SQL is cached by its text.
- **Aggregates run in SQL** (`COUNT`, `AVG`, `GROUP BY`) instead of loading every row into JavaScript.
- **CSV export streams** with an iterator and respects backpressure, so memory stays flat for large tables.
- **gzip** cuts HTML, CSS and JS transfer by about 70%, and the compressed bytes are cached.
- **Async scrypt** runs off the main thread, so a login never blocks other requests.
- **Front end:**
  - one delegated click listener per container instead of one per row or button
  - debounced search, with stale requests aborted
  - charts animated through CSS transitions
  - auto-refresh pauses while the tab is hidden
  - reveal-on-scroll elements are unobserved after appearing
  - scripts load with `defer`

### Known limits

The idempotency check and the insert are two statements; the unique index makes this safe but not lock-free. Rate limits are per process. CSV export holds an open read statement while streaming.

---

## 7. Clean architecture

### 7.1 Separation of concerns

| Concern | Lives in | Knows nothing about |
|---|---|---|
| Domain rules (APS, subjects, validation) | `shared/aps.js` | HTTP, database, DOM |
| Persistence | `server/repositories/*` | HTTP, business rules |
| Business logic | `server/services/*` | HTTP requests, SQL |
| Transport | `server/routes/*`, `middleware/` | SQL |
| Infrastructure | `server/lib/*` | Apply-Wise itself (reusable) |
| Composition | `server/app.js` | Only place where everything meets |
| Presentation | `public/js/pages/*` | Other pages |

### 7.2 Reduced coupling

Every factory receives its dependencies as arguments, for example `createApplicationService({ applicationRepository, matchingService, onChange })`. There are no global singletons. That is why the tests can start a complete server on an in-memory database in about 50 ms, and why swapping SQLite for PostgreSQL only touches `repositories/`.

The stats cache is invalidated through an `onChange` callback rather than the application service importing the stats service, so neither depends on the other.

### 7.3 Behaviour preserved

The student flow is unchanged: the same four steps, the same field order, the same card layout and the same colour palette. The changes users can see are the bug fixes listed in §4, plus the new admin area.

### 7.4 Verification

- **28 automated tests pass** (`node --test "tests/*.test.js"`). They cover APS boundaries, validation, matching rules and ranking, and the full HTTP API including auth, CSRF, lockout, idempotency, CSV injection, path traversal, caching headers and 304s.
- **Headless Chromium** completed the full journey (form → results → admin login → dashboard → detail dialog) on desktop and mobile with no JavaScript errors.

---

## Appendix: rubric mapping

| Rubric criterion | Evidence |
|---|---|
| 5. HTML & CSS | Semantic, responsive pages; 5 external stylesheets; no inline styles |
| 6. JavaScript | Live APS, multi-step validation, draft saving, dynamic rendering, debounced search |
| 7. Back-end integration | Node.js server: routing, validation, sessions, business logic |
| 8. Database integration | SQLite, 10 tables; **create** (submit), **read** (results, list, stats), **update** (password reset, last login), **delete** (admin delete, cascades) |
| 9. Dashboard panels | 5 stat cards, 4 charts, searchable table, detail view, CSV report |
| 10. Originality | No frameworks, templates or npm packages; every component written by the group |
