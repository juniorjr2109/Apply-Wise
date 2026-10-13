# Apply-Wise for Android

Native Android client for Apply-Wise, built with **Kotlin, Jetpack Compose and Material Design 3**. It uses the same Node.js server as the website, so applications from the web and the app appear together on one admin dashboard.

WebWizards — BICT231, University of Mpumalanga, 2026.

## What's in the app

| Screen | What it does |
|---|---|
| Home | **Menu button (top left) opens the navigation drawer.** Account button (top right): *Sign in* when signed out, or your initials with *My applications* and *Sign out* |
| Navigation drawer | Your name and email, Home, Find my courses, My applications, Admin dashboard (admins only), Server settings, and Sign in / Create account / Sign out |
| Sign in / Create account | One screen with a switch at the top. Students sign in with email, admins with their username |
| My applications | Every search submitted while signed in, newest first; tap one to see its results |
| Apply (4 steps) | Personal details, subjects with live APS, interests, review. The draft is saved automatically |
| Results | Ranked course cards (Strong, Good, Reach), pull to refresh, share link |
| Dashboard | Stat cards, 14-day chart, APS, province and interest charts, search, province filters, infinite scroll, detail sheet, delete with confirmation, sign out |
| Settings | Server address with a connection test, plus help for using a real phone |

Light and dark themes use the Apply-Wise brand colours. Material You wallpaper colours can be switched on with `dynamicColor = true` in `ApplyWiseTheme`.

## Requirements

- Android Studio Ladybug (2024.2) or newer
- JDK 17 (bundled with Android Studio)
- An emulator or phone running Android 8.0 (API 26) or later
- The Apply-Wise v2 server from the web project (**with student accounts**, included in the latest zip), running on your computer

## Run it

1. **Start the server** in the web project folder:
   ```
   node server/index.js
   ```
2. In Android Studio, choose **File → Open** and select this `ApplyWiseAndroid` folder. Let Gradle sync; the first sync downloads Gradle and the libraries and needs internet.
3. Pick an emulator and press **Run ▶**.

The emulator reaches your computer at `http://10.0.2.2:3000`, which is already the default. Nothing else to configure.

### Using a real phone

1. Put the phone and the computer on the same Wi-Fi network.
2. In the web project's `.env`, add `HOST=0.0.0.0` and restart the server. By default it only accepts connections from the computer itself.
3. Find the computer's IPv4 address (Windows: `ipconfig`).
4. In the app, open **Settings**, enter `http://<IPv4>:3000`, and tap **Test & save**.
5. If it still fails, allow Node.js through Windows Firewall for private networks.

### Accounts

- **Students** tap *Create account* (drawer or Home), enter name, email and a password of 8+ characters that includes a number. Applications submitted while signed in are saved to the account.
- **Admins** sign in on the same screen with the admin username the server printed on its first start. The drawer then shows *Admin dashboard*.

To create or reset an admin:
```
node server/scripts/create-admin.js admin "YourPassword2026"
```

## Tests

Right-click `app/src/test` and choose **Run tests**. They check the APS and validation rules, the same cases as the web project's `tests/aps.test.js`.

## Notes

- Plain HTTP (needed for a local server) is allowed only in **debug** builds. Release builds require HTTPS.
- You stay signed in after closing the app until you choose **Sign out** or the session expires (8 hours by default). Signing out always clears the session on the phone, even when offline.
- If Android Studio says the Gradle wrapper is missing, accept its offer to use the wrapper settings (`gradle/wrapper/gradle-wrapper.properties`). It downloads Gradle 8.11.1 automatically.
- Android Studio may suggest newer library versions. Updating is optional; versions are pinned in `gradle/libs.versions.toml`.

See `docs/ARCHITECTURE.md` for the design.
