# Reminders and update notifications (Android only)

The plan agreed on 2026-10-25, and where the work stands. The web version gets no notifications.
Work is done in small stages; each one is committed, built and tested on GitHub before the next.
**All stages done; released in 0.3.0.** Next: docs/plans/getting-on-track.md.

## Progress

- [x] **Stage 0**: this plan.
- [x] **Stage 1**: update check when the app comes to the front (at most hourly), not just on a fresh start.
- [x] **Stage 2**: notification setup: channels (Reminders, Updates), asking for permission at a sensible moment, a "notifications are off" card on Today.
- [x] **Stage 3a**: background update check (WorkManager, every 12 hours, needs internet) and the
  "LoonieMate X is available" notification (once per version) with a **Download** button. For now Download opens the
  app on Settings, where the existing Updates card has the download and install buttons.
- [x] **Stage 3b**: a dedicated update screen (version, what's new, size, **Start update**, or **Install** if already
  downloaded) opened by the notification instead of Settings; the download keeps going if the person leaves the app,
  then posts "Ready to install" with an **Install** button; ask Android to skip its own confirmation where allowed.
- [x] **Stage 4**: reminder rules in the shared engine (what to send, when) with tests.
- [x] **Stage 5a**: reminder scheduling: alarms at the configured times (AlarmManager `setAndAllowWhileIdle`, no exact-alarm
  permission), rescheduled after BOOT_COMPLETED and MY_PACKAGE_REPLACED; when one fires, build `Reminders.digest` and post
  one plain notification (summary + lines, tap opens Today). Default config until Stage 6 adds settings.
- [x] **Stage 5b**: the bundled notification (summary + one silent row per item) with **Paid / Moved it / Got it /
  Remind me later** and Undo, sharing the tick code with Today; lock-screen privacy; unlock required for the buttons.
- [x] **Stage 6**: Reminders settings card (times, how often, weekend times, heads-up days, test notification).
- [x] **Stage 7**: late-reminder detection and the battery-settings help card.
- [x] **Stage 8**: ride-along reminders (report income, tax filing, backup, weekly balance check), then release.

## Notes for picking up

- Done so far: `Updater.checkIfDue()` runs from `MainActivity.onResume` with a 1-hour gap; `notify/Notifs.kt` has the
  two channels, the permission check and the settings link; Today shows `NotificationsCard` (ask, or "notifications are
  off"); `Prefs` has `notifAsked` and `notifSnoozeUntil`; `MainViewModel.notifOk` is refreshed on resume.
- Stage 3a added WorkManager (`androidx-work` in `gradle/libs.versions.toml`) and `notify/UpdateCheck.kt`
  (`UpdateCheckWorker`, scheduled in `LoonieApp`, notification id 2, `Prefs.notifiedVersion`). The notification opens
  `MainActivity` with extra `open=update` (handled in `onCreate` and `onNewIntent`), which sets `vm.openUpdate`;
  `LoonieMateApp` then switches to Settings. For 3b, open the new update sheet there instead. The existing download, signature check and install code is in
  `update/Updater.kt`; the in-app update UI is `UpdatePanel` in `ui/screens/MoreScreen.kt`.
- Stage 3b: `Sheet.Update` shows `UpdatePanel(inSheet = true)` (now public, in `ui/screens/MoreScreen.kt`);
  `vm.startUpdate` downloads in `LoonieApp.scope`, then installs if `LoonieApp.foreground`, otherwise
  `UpdateCheck.notifyReady` posts "Ready to install" (extra `open=install`, which installs on opening).
  The manifest declares `UPDATE_PACKAGES_WITHOUT_USER_ACTION`; the session already asks `USER_ACTION_NOT_REQUIRED`.
- Stage 4: `engine/.../Reminders.kt` (`Config`, `wakingDay`, `headsUpDay`, `digest`, `summary`, `padLine`) with
  `RemindersTest`. Item keys are the same `key`/`ym` that Today's ticks use (`months/<ym>.paid[key]`, `.moves[key]`).
- Stage 5a: `notify/ReminderAlarm.kt` (`next`, `schedule`, `fire`, `refreshShown`, notification id 1) and
  `ReminderReceiver` (alarm, boot, app update, clock change). Settings live in `Prefs` (`remindersOn`, `reminderTimes`
  as minutes "510,1140", `reminderWeekendTimes`, `padDays`, `movesEveningBefore`, `reminderConfig()`), plus
  `reminderDueAt` / `reminderFiredAt` for Stage 7 and `reminderShown` ("day|slot|slots"). `MainViewModel.change` and
  `undo` call `refreshShown`. Tests: `app/src/test/.../ReminderAlarmTest.kt`. For 5b, replace the single InboxStyle
  notification in `ReminderAlarm.post` with a group (summary id 1 + child per item) and action receivers.
- Stage 5b: the bundle is built in `ReminderAlarm.post` (summary id 1 alerts, rows are silent, ids from `childId(key)`);
  buttons go to `ReminderReceiver` → `ReminderAlarm.onAction` (DONE, UNDO, LATER, LATER_FIRE, GOTIT, FORGET), all
  `setAuthenticationRequired(true)`. Remind me later is **In 1 hour / In 3 hours** (Android allows three buttons, so
  "a chosen time" was left out). State in `Prefs`: `reminderGotIt`, `reminderSnoozed`, `reminderUndo`,
  `reminderChildren`. Ticks are shared through `data/Ticks.kt`.
- **Released in 0.2.1**: stages 1 to 3b (update checks and notifications). Reminders are in the app but switched off
  by default (`Prefs.remindersOn` defaults to false) and the Today card talks about updates only. In Stage 6, flip the
  default to true and reword `NotificationsCard` in `ui/screens/TodayScreen.kt` to mention reminders again. The next release goes out after Stage 8 (or sooner if you ask).

## Update notifications

- The background check runs about twice a day when there's internet, plus when the app comes to the front (at most hourly).
  Both obey the "Check for updates automatically" switch; off means no background checks and no update notifications.
- Notification: "LoonieMate X is available", first line of the release notes, a **Download** button. Sent once per version;
  a newer version replaces it; skipped versions never notify. Its own **Updates** channel.
- Tapping Download (or the notification) opens the app to an update screen: version, what's new, about 2 MB,
  and a **Start update** button. If it's already downloaded, the button says **Install**.
- If the person leaves during the download it keeps going, then posts "Ready to install" with an **Install** button
  (Android only shows its install prompt while the app is on screen).
- Ask Android to skip its own install confirmation for self-updates where it allows it (newer Android, LoonieMate as
  the installer of record); keep the prompt as the fallback. Confirm the exact rules when building it.

## Reminders

**Never spammy**
- Nothing is sent when nothing needs doing. Follow-ups only fire if something from that day is still not done.
- One alert per reminder time: a bundle with a summary ("3 things today") that expands into one row per item.
  Rows arrive silently. Updates replace the same notification silently.
- Ticking something off (in the app or on the notification) removes it and cancels its follow-ups.
- Do Not Disturb is respected.

**Timing (this phone only, not in the budget data)**
- How often: once a day, twice a day (default: the list plus one follow-up), up to three times, or off.
- Each time is picked by the person. Defaults 8:30 a.m. and 7:00 p.m. Follow-ups at least 2 hours apart.
- Optional different times for Saturday and Sunday.
- Each reminder belongs to the *waking day* it started in: a 1:00 a.m. follow-up after a 3:00 p.m. first reminder still
  covers the previous day's items, not tomorrow's. Test this in the engine.
- "Remind me later" on the notification: 1 hour, 3 hours or a chosen time; doesn't change the saved schedule.
- Optional evening-before heads-up for money moves (off by default).

**Automatic payments (bills paid by "Pre-authorized debit (PAD)")**
- Heads-up 1 or 2 days ahead (setting, default 2), grouped by account with a total:
  "Coming out of Main Chequing by Wednesday: Bell $95.00 and Hydro $84.00, $179.00 in all."
- If the heads-up would land on a weekend or bank holiday, move it earlier to the last business day.
- Information, not a to-do: shown in the first reminder of the day, no follow-ups, nothing on the day itself.
  If no other reminder goes out that day, it gets its own notification at the first reminder time.
- Mention a planned money move into that account before the debit ("after you move $332.53 to it on Tuesday").

**Buttons**
- Bill: **Paid** · Remind me later. Money move: **Moved it** · Remind me later. PAD heads-up: **Got it**.
- After Paid / Moved it, the row shows "Marked paid" with **Undo** until swiped away.
- Same code path as ticking it on Today (including savings totals for savings moves).
- Lock screen: names and amounts hidden ("3 things to do today"); the buttons ask to unlock first.
- Locked month: only an Open button.

**Getting through**
- Ask for notification permission after the first bill is added, with a reason, not on first launch.
- Card on Today when notifications are blocked or the channel is muted.
- Reschedule after a restart (BOOT_COMPLETED) and after an app update (MY_PACKAGE_REPLACED).
- Alarms that fire while the phone is idle, without the exact-alarm permission (a few minutes late is fine).
- Record when a reminder should have fired and when it did; if it's over an hour late twice, show a card with
  the steps to stop the phone putting LoonieMate to sleep.
- Work out what's due at the moment the reminder fires, from the data on the phone.

**Ride-along reminders** (report income by its due day gets its own notification on that day; the rest join that
day's list): tax filing, backup older than two weeks, weekly balance check.

**Settings card**: on/off, how often, times, weekend times, heads-up days for automatic payments, evening-before
heads-up for money moves, weekly balance-check reminder, "Send a test notification".

**Later, with Google Drive sync**: two phones keep their own reminder settings; ticking something off on one clears
the other's notification at the next sync.
