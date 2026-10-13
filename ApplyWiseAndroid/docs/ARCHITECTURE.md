# Apply-Wise Android — Architecture

## Overview

```
 UI (Jetpack Compose + Material 3)
 ┌───────────────────────────────────────────────────────────────┐
 │ HomeScreen  ApplyScreen  ResultsScreen  AdminLogin  Dashboard  │
 │      │           │            │              │          │      │
 │  HomeVM     ApplyVM      ResultsVM       LoginVM   DashboardVM │  StateFlow<UiState>
 └──────┼───────────┼────────────┼──────────────┼──────────┼──────┘
        ▼           ▼            ▼              ▼          ▼
 Data   StudentRepository                    AdminRepository
        │  LocalStore (DataStore)            │
        └──────────────┬──────────────────────┘
                       ▼
        Retrofit ApplyWiseApi ─ OkHttp (host rewrite, CSRF header, cookie jar)
                       ▼
             Apply-Wise v2 Node.js server  ←  same server as the website
 Domain  Aps (APS rules, validation) · AppException · MatchType
```

**Pattern:** MVVM with unidirectional data flow.
- Each screen observes one immutable `UiState` through `collectAsStateWithLifecycle`.
- User actions call ViewModel functions; ViewModels update state and call repositories.
- Composables hold no business logic.

**Dependency injection:** manual, through `AppContainer`, which is created once in `ApplyWiseApp`. There's no annotation processing, so builds are faster and there are fewer moving parts for a student team. Moving to Hilt later only touches `AppContainer` and the ViewModel factories.

## Folder structure

```
app/src/main/java/com/webwizards/applywise/
├── ApplyWiseApp.kt · MainActivity.kt
├── di/AppContainer.kt
├── domain/        Aps.kt (port of shared/aps.js) · Models.kt
├── data/
│   ├── remote/    Dto.kt · ApplyWiseApi.kt · Network.kt (OkHttp, errors)
│   ├── local/     LocalStore.kt (DataStore: server URL, draft, recent results)
│   └── repository/Repositories.kt (StudentRepository, AdminRepository)
└── ui/
    ├── theme/     Theme.kt (brand light/dark schemes, typography, shapes)
    ├── navigation/AppNavHost.kt (type-safe routes)
    ├── components/Components.kt · DropdownField.kt
    ├── home/ apply/ results/ admin/ settings/
app/src/test/.../domain/ApsTest.kt
```

## Data flow: submitting an application

1. `ApplyViewModel` validates each step with `Aps`, the same rules as the server.
2. Every edit is saved to DataStore after a 400 ms pause, so process death loses nothing.
3. On submit, `StudentRepository.submit` posts JSON with an `Idempotency-Key` that was generated once per draft. A retry after a timeout returns the same record instead of creating a duplicate.
4. The server returns `ref`. The draft is cleared and navigation replaces the form with `ResultsRoute(ref)`.
5. `ResultsViewModel` loads `/api/applications/{ref}/results` and remembers the ref on the phone so it appears under "Your recent results".

## Accounts and navigation

**Navigation drawer.** Home hosts a `ModalNavigationDrawer` opened by the menu button at the top left. Items depend on who is signed in: *Admin dashboard* appears only for admins, and the footer switches between *Sign in / Create account* and *Sign out*. System back closes the drawer first.

**One source of truth for "who is signed in".** `AuthRepository.user` is a `StateFlow<UserDto?>`. The drawer, top-bar account menu, Apply form (name pre-fill and "saved to your account" note), My applications and the dashboard all observe it, so signing in or out anywhere updates every screen at once.

**Sign in.**
1. `POST /api/auth/login` or `/api/auth/register`.
2. The server sets an `HttpOnly` session cookie.
3. `SessionCookieJar` reports every cookie change, and the cookie plus the last known user are saved to DataStore. This keeps the user signed in across app restarts, and the drawer shows their name even offline.

**Start-up.** `ApplyWiseApp` reads the saved server, cookie and user. `AuthRepository.verifySavedSession()` then calls `/api/auth/me`. A `401` signs the user out; a network error keeps the cached user until the server is reachable.

**Session loss anywhere.**
- An OkHttp interceptor watches for `401` on any request except sign-in itself, and calls `AuthRepository.onSessionLost()`. That clears the cookie and user, so every screen reacts.
- Changing the server address does the same, so a session is never sent to a different server.

**Sign out.** A confirmation dialog appears, then `POST /api/auth/logout`. Local session data is always cleared, even offline.

## Data flow: admin

1. Admins sign in on the shared sign-in screen. The dashboard checks `user.isAdmin` before loading.
2. A `401` sends the admin to sign-in with "session expired". A `403` (a student opening it) shows "administrators only".
3. Search is debounced by 300 ms. `collectLatest` cancels in-flight requests when filters change, so stale results never appear.

## Edge cases handled

| Case | Handling |
|---|---|
| Server not running or unreachable | Clear message plus a "Server settings" shortcut |
| Wrong server address | Settings tests `/api/health` before saving. On failure the previous address is restored |
| Switching servers | Session cookies are cleared, so a session is never sent to another server |
| Double tap on Submit or Sign in | Buttons disable while busy, and ViewModels ignore re-entry |
| Network timeout, then retry | Idempotency key prevents duplicate applications |
| Rotation, process death | ViewModels keep state across rotation, DataStore keeps the draft, `rememberSaveable` keeps login fields |
| Corrupt local data | DataStore read errors fall back to defaults, and draft values are re-validated on load |
| Back button mid-form | Goes to the previous step first, then leaves the form |
| Back from results | Returns home, not to a form that was already submitted |
| Results deleted by admin | 404 message, and the ref is removed from "recent results" |
| Grade entry | Digits only, 3 characters maximum. Above 100 is flagged inline and on validation |
| More than 6 subjects | APS card says "Best 6 of your N subjects counted". Review marks the dropped subjects |
| Session expires mid-task | Global 401 handler signs the user out everywhere. The dashboard redirects to sign-in with "session expired" |
| Student opens the admin dashboard | Server returns 403. The app explains it's for administrators, and the drawer never shows it to students |
| Email already registered | 409 shown on the email field, with a prompt to sign in instead |
| Double tap on Sign in or Create account | Ignored while a request is running |
| Offline at start-up with a saved session | The cached user stays shown and is re-verified when the server is reachable |
| Signed out while on My applications | Redirected to sign-in, which returns to the list afterwards |
| Record already deleted | Snackbar explains and the list updates |
| Rows shifting between pages after deletes | Appended pages are de-duplicated by id |
| Too many login attempts | The server's 429 message is shown as-is |

## Performance

- Immutable `@Immutable` UI states and stable keys (`key = { it.id }`) let Compose skip unchanged rows.
- `animateItem()` animates insertions and removals without recomposing the whole list.
- The 14-day chart is drawn in one `Canvas` pass instead of one layout per bar.
- Infinite scroll uses a `derivedStateOf` threshold, so scrolling does not recompose on every frame.
- Network calls run on `Dispatchers.IO`. OkHttp connection pooling and a 20-second call timeout bound the slowest case.
- Release builds enable R8 minification and resource shrinking.

## Security

- Cleartext HTTP is allowed only in debug builds, through a manifest placeholder.
- The session cookie is stored in app-private DataStore (excluded from cloud backup and device transfer) so students stay signed in. Cookie headers are redacted from debug logs, and the server stores only a SHA-256 hash of the session token.
- Cloud backup is disabled (`data_extraction_rules.xml`), so drafts don't leave the device.
- The server remains the authority: it re-validates input, recalculates APS, and enforces auth, CSRF and lockouts.
