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
- **Code health:** ~~the old family names still used as field names in the engine~~ (done: renamed to
  `rentIncome`, `rentDate`, `rentLeft`, `mainPer`, `mainRegular`; old data support dropped, agreed 2026-10-26), "Sheet" names left over now that forms are pages,
  31 catch-all `catch (e: Exception)` blocks that may hide real problems, the 585-line view model, duplicated logic
  between the web UI and the Kotlin views, unused code and imports.
- **Web version:** keeping up with Android features, the 2,650-line single page, and dialogs and forms on a phone
  browser.
- **Tests and build:** screen tests that wait with fixed sleeps, what isn't tested at all, and making failures easy
  to read (test failures now show in the build summary).

## Stages

- [x] **Stage 0**: this plan.
- [x] **Stage 1**: the audit; findings written below (2026-10-26).
- [x] **Stage 2**: must-fix batch(es). M1, M2 done; released in 0.9.0.
- [ ] **Stage 3**: should-fix batches. S1–S6 done; released in 0.9.0. Nice-to-haves still open.
- [ ] **Stage 4**: polish pass and fresh screenshots. Then release.

## Findings

Read-only audit of 2026-10-26 (engine, data, sync, backups, updater, reminders, Android screens, web). Each finding has
an id for the fix commits. Already done during the audit: **the first family's names** are gone from the engine
(`ryan` → `rentIncome`, `ryanDate` → `rentDate`, `ryanLeft` → `rentLeft`, `staceyPer` → `mainPer`,
`staceyRegular` → `mainRegular`). Agreed afterwards: old data doesn't need to keep working (only the developer used the
early versions), so the legacy keys and old snapshot names aren't read any more.

**Status (0.9.0):** M1, M2 and S1–S6 are fixed. N2 no longer applies. The rest of the nice-to-haves are open.

### Must fix

- **M1 (fixed in 0.9.0). A weekly or bi-weekly payday entered as a future date wipes out the earlier paydays.** `schedDates` (both
  engines) never goes back before the anchor. The weekday picker (`IncomeSetup.withWeekday` / `schedFor`) always stores
  the *next* matching day, and the bi-weekly field says "Any real payday works", so the natural answer is the next
  payday. Example: a weekly Friday paycheque set up on Monday Oct 26 keeps only Oct 30; Oct 2, 9, 16 and 23 vanish, so
  October looks short, food money shrinks, and getting on track's "money that already came in" is empty. Fix: run the
  cycle backwards too (incomes already have `start` for a new job), add a parity case, and keep the hint honest.
- **M2 (fixed in 0.9.0). Sync can delete or undo recent changes when the sync file is stale.** `Sync.mergeDoc`: when this phone's copy
  equals the base and the file's copy differs, the file wins, including when the file simply *lacks* the item. Cloud
  apps (Drive especially) can hand a phone an older cached copy, or the other phone can write over the file from an
  older copy. Then a bill added on phone A after its last sync is deleted on A, and an edit is reverted. Fix: deletions
  only through tombstones (missing without a tombstone keeps this phone's copy), and a file copy older than the base
  (`_u`) doesn't overwrite this phone; then the file is rewritten. Add SyncTest cases for a stale file.

### Should fix

- **S1 (fixed in 0.9.0). A form is lost if Android closes LoonieMate in the background.** Pages and their fields live in `remember`,
  not saved state, and `Sheet` isn't saveable. "Check my balances" tells people to open their banking apps, which is
  exactly when a low-memory phone closes LoonieMate; coming back, the typed balances are gone. Fix: save the open page
  and its fields (rememberSaveable with a Sheet saver, or the view model's SavedStateHandle).
- **S2 (fixed in 0.9.0). A save that fails is silent.** `Repository.persist` catches the error and carries on (a full phone, for
  example); the person thinks it's saved and it's gone after a restart. Fix: say "Couldn't save on this phone" with a
  way to make a backup.
- **S3 (fixed in 0.9.0). A damaged budget file starts an empty budget without a word.** `Repository.load` keeps a copy
  (`budget-damaged-…json`) but the app opens empty and the next change writes over `budget.json`. Fix: tell the
  person, and offer to restore from that copy or a backup.
- **S4 (fixed in 0.9.0). Undo of a delete can be undone by sync.** `Repository.restore` puts back the old document with its old `_u`;
  if a sync ran between the delete and the Undo, the tombstone on the file is newer and deletes it again. Fix: restore
  with a fresh `_u` and drop only that item's tombstone.
- **S5 (fixed in 0.9.0). The sync file could be left half-written.** `SyncManager.writeText` falls back to mode "w", which some
  providers don't truncate; a shorter file then keeps old bytes on the end and stops opening. Fix: read back and
  check after writing (and retry with a fresh write).
- **S6 (fixed in 0.9.0). The web version can lose everything on iPhone.** It keeps the budget only in `localStorage`, which Safari
  clears after 7 days without a visit (unless it's added to the Home Screen), and no `navigator.storage.persist()` is
  requested. Fix: request persistence, and nudge toward a backup or Add to Home Screen on Safari. Done as a notice on
  iPhone and iPad: add it to the Home Screen (Chrome on iPhone has the same 7-day limit, since all iOS browsers use
  WebKit, so recommending Chrome wouldn't have helped).

### Nice to have

- **N1.** A backup file states its own key-stretching rounds (`iterations`) and nothing caps it; a crafted file could
  hang the app on import. Cap it (for example 10 million).
- **N2.** Months locked by 0.9 save the new field names; a phone still on 0.8 or older shows $0 for rent income on
  those locked months until it updates (sync makes mixed versions possible). Both update themselves, so just note it
  in the release notes.
- **N3.** The engines accept negative amounts from imported or synced data (a −$500 bill adds $500). The forms
  already refuse them; the engine could ignore them too.
- **N4.** Reminder buttons (Paid, Moved it) change the budget but don't start a sync; it waits for the app to open or
  the 6-hour background sync. Sync after a notification action.
- **N5.** The reminder alarm does the month maths inside the broadcast receiver on the main thread; fine at today's
  speed (a month takes under 1 ms on the web engine) but `goAsync()` would be safer on slow phones.
- **N6.** Changing dark mode or the phone's font size while a form is open still rebuilds the screen and loses typing
  (only orientation and size are handled). Covered by S1's fix.
- **N7.** Closing "Make LoonieMate easier to use?" with ✕ during first-time setup also skips getting on track.
- **N8.** Code tidying: "Sheet"/"Sheets" names now that forms are pages; unused imports left by the page change; the
  month maths recalculated inside screens (9 places; fast enough, but tidier in the view model); the per-payday
  breakdown is written twice (web and Kotlin) with no parity check.
- **N9.** Screen tests wait with fixed sleeps (`settle()`), which makes them slow; and there are no tests for S2–S5.
- **N10.** `android:allowBackup="false"` stops cloud backup, but phone-to-phone transfer on Android 12+ still copies the
  budget unless `dataExtractionRules` say otherwise. Probably wanted (moving to a new phone); decide and write it down.

### Checked and fine

- **Updater:** the download is checked against the SHA-256 in `update.json` *and* must be signed with the same key as
  the installed app before Android's installer sees it; the install receiver isn't exported.
- **Backups:** AES-256-GCM with PBKDF2-SHA256 at 600,000 rounds, a fresh salt and nonce each time; wrong passwords and
  damaged files give clear messages.
- **Sync password:** sealed with a key in Android's Keystore; a lost key just asks for the password again.
- **Reminders:** lock-screen versions hide names and amounts; Paid / Moved it need the phone unlocked; alarms are set
  again after a restart, an update or a clock change.
- **Web version:** every name and note written into the page goes through `esc()`; error messages use `textContent`.
- **Most of the 31 catch-all error handlers** are at the edge with Android or the network and show a clear message;
  the ones that matter are S2, S3 and S5.
- **Speed:** a month's maths takes about 0.7 ms, the day-by-day look about 2 ms and the 6-month projection about
  5 ms (web engine); not worth restructuring for.
- **Forms** already refuse zero or negative amounts where they make no sense.

## Proposed order

1. M1 and M2 (with parity and sync tests), then a release.
2. S1–S5 (Android data safety), then a release.
3. S6 (web storage).
4. Nice-to-haves and the polish pass.

## Notes for picking up

- M1: `schedDates` weekly / bi-weekly no longer clamp to the anchor (both engines); parity schedules with a future anchor.
  Bills still treat a weekly / bi-weekly anchor as the first due date (that's their "next due date" field).
- M2 and the emptied-phone case: `Sync.mergeDoc` keeps whichever side still has an item when the other lacks it and the
  base has it (deletions are settled by tombstones in `reconcile`), and keeps this phone's copy when the file's copy is
  older (`_u`) than the base. `SyncTest`: stale file, stale edit, deletions with the guard, an emptied phone.
- S1: `Sheet` subclasses are data classes / data objects; `SheetSaver` in `ui/AppUi.kt`; form fields use
  `rememberSaveable`; `stateMapSaver`, `stringListSaver` (Page.kt) and `selfRowsSaver`; `PageState.Saver` keeps the
  "typed something" flag. `ScreensTest.aFormSurvivesBeingRebuilt`. Not saved: BillSheet's extra payments list, the
  balance-check result and an opened backup (they're re-made in a tap).
- S2/S3: `Repository.problem` ("save" / "damaged"), `damagedCopy`, `dismissProblem()`; `StorageProblemCard` on Today.
- S4: `Repository.restoreAll` (fresh `_u`, per-item tombstones; an undone add gets a tombstone);
  `replaceAll(data, recordRemoved = true)` for "Replace this phone's budget". `RepositoryTest`.
- S5: `SyncManager.writeText` reads the file back and requires `Backup.envelope` to succeed, trying "wt" then "w".
- S6: web `iosNotice()` (iPhone/iPad, not when opened from the Home Screen, dismissed with `rs-ios-ok`), shown on
  the welcome view, the month overview and Today; `navigator.storage.persist()`; pages.yml adds
  `apple-touch-icon.png` (docs/assets) and web-app meta tags.
