# Getting on track (Android first)

The plan agreed on 2026-10-26. Most people start using LoonieMate partway through a month: some money is already
spent, some bills are paid, some paycheques are in, and they may be behind on things. This finds where they actually
stand today so the plan matches reality from day one.
**Queued after docs/plans/reminders-and-updates.md.** Work in small stages; each is committed, built and tested on
GitHub before the next. **All stages done; released in 0.4.0.** Next: docs/plans/accessibility.md.

## Decisions

- **Android first.** The web version only needs to *understand* the data (no step-by-step screens there).
- **Starting month:** the current month is a starting month with no savings target and no "behind on savings"
  warnings; regular planning starts on the 1st of next month.
- **"Get back on track"** (everyone): re-runs the catch-up steps (2 to 6) from today. Placed low in Settings, in a
  small "Need a fresh start?" card near the bottom, not prominent.
- **"Redo first-time setup"** (debug): forces the whole onboarding from step 1. At the very bottom of Settings,
  below About. Not hidden behind taps, just out of the way.

## The idea: start from today

Before today is history. Three facts about right now set the starting point:
1. What's in each account (saved as the first balance check, which already works as a starting point).
2. What's still coming in this month: paydays from today on. Earlier ones are already in the balances.
3. What's still owed: bills due from today to the month's end, plus anything the person says they're behind on.
Tracking starts today (`settings.trackFrom`), so nothing earlier shows as overdue unless they said so.

## The steps

1. **Accounts, income and bills**: the existing setup, needed first.
2. **What's in your accounts right now**: one field per account; saved as the starting balance check.
3. **This month so far**: tick the bills dated 1st–today that are already paid; see which paydays already came in.
4. **Anything you're behind on**: who, how much, and how to handle it:
   - *Pay it all on a date* (e.g. next payday) → a one-time expense on that date.
   - *Pay it off bit by bit* (payment arrangement) → a bill using the existing debt feature.
   - *Not sure yet* → an overdue item that keeps showing until it's handled.
5. **Food so far**: one rough number since the 1st (or the 16th), logged so this half's food money starts right. Optional.
6. **Where you stand**: a day-by-day look from today to the month's end (money now, plus each payday, minus each
   bill), finding the first day money runs short. Example: "Rent ($1,450) is due Nov 1. You'll have $980 then; the
   next paycheque comes Nov 6. Short $470 for 5 days." Then the fixes the app already has, rent first: pay part now
   and the rest on payday ("pay later" with an amount and date), push back a deferrable bill, give up a non-essential,
   cover from Emergency Savings (Child Reserve last), and if it still doesn't add up, a plain note to call the landlord
   or company before the due date.
7. **Starting month**: this month is marked as a starting month; its status card is about getting to the month's end.

## Stages

- [x] **Stage 0**: this plan.
- [x] **Stage 1**: web engine first, then the Kotlin port, checked by the parity tests:
  - the day-by-day look from today with the first short day (`Core` function, e.g. `runway(st, today, balances)`);
  - the starting-month marker on `months/<ym>` (no savings target that month in `monthCalc` / `targetsThrough`),
    understood by the web version's status card too.
- [x] **Stage 2**: how catch-up items are saved (one-time expense, debt bill, or "not sure yet"), using data the web
  version already reads; mark them so both versions can show them as catch-up items.
- [x] **Stage 3**: Android screens for steps 2 to 5 (balances, this month so far, behind on, food so far).
- [x] **Stage 4**: Android "Where you stand" (step 6) with the fixes.
- [x] **Stage 5**: onboarding wiring: runs after first-time setup, sets `trackFrom` to today and marks the starting
  month; the starting-month status card.
- [x] **Stage 6**: Settings: "Need a fresh start?" card with **Get back on track** (low in Settings), and
  **Redo first-time setup** at the very bottom. Then release.

## Notes for picking up

- Stage 1 (done): `startingMonth(st, ym)` in both engines (web `Core.startingMonth`, Kotlin `Core.startingMonth`):
  `months/<ym>.starting` gives that month no emergency target (`targetsThrough`, `projection`) and no extra-payday,
  "what's left" or "cover the shortfall" moves; Child Benefit moves and targets are unchanged. `runway(st, today, bal)`
  in both engines: days from today to the month's end (or next month's first main payday if later) with items
  (in / overdue / bill / oneoff / food), running balance, `low`, `lowDate`, `short {date, amount, nextIn}`.
  Parity household `ontario-starting` covers both.
- Stages 3 to 6 (done): `ui/sheets/CatchUpSheet.kt` (five steps; `first` marks first-time setup). `MainViewModel`:
  `startCatchUp` (first-time: tracking from the 1st), `catchUpFrom` (the shared look-back window), `catchUpSplit` /
  `catchUpPush` (marks the bill deferrable, then defers), `finishCatchUp` (starting month, `Prefs.onboarded`),
  `skipOnboarding`, `startingStatus` (the starting-month status on Today and Month). Today's `StartHereCard` shows once
  accounts, income and bills exist. Settings: "Need a fresh start?" card (Get back on track) and "Redo first-time
  setup" at the very bottom.
- Stage 2 (done): `engine/.../CatchUp.kt` (`oneTime`, `plan` with the payoff date as `end`, `unsure`, `undecided`);
  `settings.catchUp` entries count as owed today in `runway`. App: `MainViewModel.catchUpOnDate`, `catchUpPlan`,
  `catchUpUnsure`, `catchUpResolve`. Test: `CatchUpTest`.

- Existing pieces to reuse: balance checks (`engine/.../Checkins.kt`, `ui/sheets/CheckinSheet.kt`), paid ticks
  (`months/<ym>.paid[key]`), one-time expenses (`months/<ym>/expenses/<id>` with `oneTime`), debts (`bills/<id>.debt`,
  `Views.readDebt`), "pay later" and "give up" (`MainViewModel.defer` / `skip`, `ui/sheets/Sheets.kt` AdjustSheet),
  transfers out of savings (`MainViewModel.transfer`).
- Any new field must be harmless to older copies of the web version and the app (they ignore what they don't know).
