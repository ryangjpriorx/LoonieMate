# Polish and code audit (Android and web)

Proposed 2026-10-26, after Personal Expenses (0.8.0) finished the features that were in progress. No new features in this
phase: find what's wrong, risky or rough, then fix it in small batches. Each batch is committed, built and tested on
GitHub before the next, and released when a group of fixes is worth updating for.

## How it works

1. **Audit first, read-only.** Go through the code area by area and write every finding into this file, each with
   where it is, what goes wrong (a real example where possible), and how bad it is:
   - **Must fix**: wrong numbers, lost or corrupted data, a crash, a security hole.
   - **Should fix**: confusing behaviour, slow screens, fragile code that will break the next change.
   - **Nice to have**: tidying, naming, small visual polish.
2. **Agree the list** before changing anything.
3. **Fix in batches**: must-fix first, then should-fix grouped by screen or area. Every money fix gets a parity
   household or test that would have caught it.
4. **Polish pass** last: wording, spacing, empty states, the first-run flow end to end, fresh README screenshots.

## Audit areas

- **Money maths (both engines):** rounding, months with 5 paydays, a payday on a holiday or weekend, leap years,
  month ends, negative or blank amounts, very large amounts, locked months made by older versions, and parity cases
  that are still missing.
- **Data safety:** saving, Undo, backups (encrypt / decrypt / import), sync merging and deletions, what happens if
  the app is killed mid-save, and old data from earlier versions (every saved field must keep reading).
- **Security:** backup encryption, the stored sync password, the in-app updater (is the downloaded APK checked
  before it's installed?), exported Android components and intents, the web version's handling of imported files.
- **Android behaviour:** the app being closed by Android in the background, notifications and alarms after a
  restart, time zone and date changes, large text and TalkBack, small and large screens.
- **Speed:** the month calculation is re-run inside screens on every redraw (9 places in the UI); the Today,
  Month and Settings screens with a full year of data.
- **Code health:** the old family names still used as field names in the engine (`ryan`, `staceyPer`, …; some are
  stored in locked-month snapshots, so old data must keep working), "Sheet" names left over now that forms are pages,
  31 catch-all `catch (e: Exception)` blocks that may hide real problems, the 585-line view model, duplicated logic
  between the web UI and the Kotlin views, unused code and imports.
- **Web version:** keeping up with Android features, the 2,650-line single page, and dialogs and forms on a phone
  browser.
- **Tests and build:** screen tests that wait with fixed sleeps, what isn't tested at all, and making failures easy
  to read (test failures now show in the build summary).

## Stages

- [ ] **Stage 0**: this plan.
- [ ] **Stage 1**: the audit; findings written below.
- [ ] **Stage 2**: must-fix batch(es).
- [ ] **Stage 3**: should-fix batches.
- [ ] **Stage 4**: polish pass and fresh screenshots. Then release.

## Findings

(filled in by stage 1)
