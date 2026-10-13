# Apply-Wise v2

Apply-Wise matches South African matric students with university courses they qualify for, based on their APS and subject results. Version 2 adds a real back end, a SQLite database and a protected **admin dashboard**.

**Built by WebWizards — BICT231 Introduction to Web Development, University of Mpumalanga, 2026.**
Original code only: HTML, CSS, JavaScript and Node.js. No templates, frameworks or npm packages.

## Requirements

Node.js **22.13 or newer** (Node 24 LTS recommended). Nothing else: there is no `npm install` step, because the server only uses modules built into Node (`node:http`, `node:sqlite`, `node:crypto`).

Check your version:

```
node -v
```

## Run it

Open a terminal in this folder and run:

```
node server/index.js
```

Then open **http://localhost:3000** in Edge or Chrome.

On the very first start the terminal prints an admin username and a one-time password. Copy it.

```
──────────────────────────────────────────────────────────
  First run: an admin account was created
  Username: admin
  Password: kYfO_SJKeSik92qT
──────────────────────────────────────────────────────────
```

To choose your own password instead, create or reset an admin at any time:

```
node server/scripts/create-admin.js admin "MyStrongPassword2026"
```

Passwords must be at least 10 characters. Resetting a password signs that admin out everywhere.

Do **not** open the HTML files by double-clicking them. Pages opened from `file://` cannot reach the server, and the site shows a banner explaining this.

## Pages

| URL | Who | Purpose |
|---|---|---|
| `/` | Everyone | Landing page |
| `/apply.html` | Students | 4-step form: details, subjects, interests, review |
| `/results.html?ref=…` | Students | Personal matches. The link can be bookmarked or shared |
| `/admin` | Admins | Sign in |
| `/admin/dashboard.html` | Admins | Statistics, charts, search, view, delete, CSV export |

## Accounts API (used by the Android app)

| Endpoint | Purpose |
|---|---|
| `POST /api/auth/register` | Create a student account (`firstName`, `lastName`, `email`, `password`) and sign in |
| `POST /api/auth/login` | Sign in: students with their email, admins with their username |
| `POST /api/auth/logout` | Sign out |
| `GET /api/auth/me` | Who is signed in |
| `GET /api/me/applications` | The signed-in user's own applications |

Applications submitted while signed in are linked to the account. Students cannot open `/api/admin/*` (403), and the web admin page only accepts admin accounts. Existing databases are upgraded automatically on the next start; admins, sessions and applications are kept.

## Other commands

| Command | What it does |
|---|---|
| `node --test "tests/*.test.js"` | Runs the 39 automated tests |
| `node bench/matching.bench.js` | Performance benchmark |
| `node --watch server/index.js` | Restarts automatically when you edit server files |

If PowerShell allows npm scripts on your machine, `npm start`, `npm test` and `npm run bench` do the same things.

## Configuration (optional)

Copy `.env.example` to `.env` to change the port, set the first admin's password, or change the session length. The database is stored in `data/applywise.db`. Delete that file to start again from an empty database.

## Troubleshooting

| Message | Fix |
|---|---|
| `Cannot find module 'node:sqlite'` | Update Node.js to 22.13 or newer |
| `Port 3000 is already in use` | Close the other server, or set `PORT=3001` in `.env` |
| `ExperimentalWarning: SQLite…` | Harmless. Use `npm start` or add `--disable-warning=ExperimentalWarning` to hide it |
| Forgot the admin password | `node server/scripts/create-admin.js admin "NewPassword123"` |

See `docs/ENGINEERING_REPORT.md` for the architecture, database design, edge cases, bug investigation and performance results.
