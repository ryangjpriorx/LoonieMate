# LoonieMate

A budget planner for Canadian families on a tight budget: social assistance (ODSP, Ontario Works,
Québec and Nova Scotia programs), government benefits, bi-weekly paychecks, bills for adults, kids
and pets, and the money moves that tie them together. Built for people who want the app to do the
thinking.

- **Android app** (this repository): a native app built with Kotlin, Jetpack Compose and Material 3.
  It follows your wallpaper colours on Android 12 and later, and turns on its extra visual effects
  only on phones with the power for them.
- **Web version** ([`web/index.html`](web/index.html)): one page that runs in any browser, including on
  iPhone. It has the same features without the extra visuals.

Both use the same encrypted backup file, so a budget can move between them, or between phones,
at any time.

## Installing on Android

1. Open the [latest release](../../releases/latest) on your phone and download `LoonieMate-x.y.z.apk`.
2. Open the file. Android will ask you to allow installs from your browser or Files app; allow it.
3. From then on LoonieMate checks this page for new versions and installs them itself (you tap
   **Install** once per update).

LoonieMate needs Android 8.0 or later.

## Privacy

There is no account and no server. The budget is saved only on your phone. Backups are locked
with a password you choose (AES-256-GCM, with the key made by PBKDF2-SHA256 at 600,000 rounds) and
can't be opened without it, by anyone. Android's own cloud backup is turned off for the same reason.
The only network use is checking this repository for updates.

## How it's built

| Folder | What's in it |
|---|---|
| `engine/` | The budget maths in plain Kotlin: pay dates and bank holidays, benefit schedules, assistance deductions, bills, food, savings, money moves, debt payoff, backups. |
| `parity/` | Runs the web version's maths in Node and saves every answer to `fixtures.json`. The Kotlin engine is tested against it on every build, so the two always agree to the cent. |
| `app/` | The Android app. |
| `web/` | The web version. |
| `signing/` | The release signing key, encrypted. |

Every push builds a test copy of the app (see **Actions**). Pushing a tag like `v0.2.0` publishes a
signed release; release notes come from `docs/releases/v0.2.0.md`.

```
node parity/gen.js          # the web version's answers
./gradlew :engine:test      # the app engine must match them
./gradlew :app:assembleDebug
```
