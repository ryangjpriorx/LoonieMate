# Getting on track: the balance is the truth (Android first, web understands the data)

The plan agreed on 2026-10-26 (part of docs/plans/overhaul.md, Stage 1). Fixes to the getting-on-track steps
(`ui/sheets/CatchUpSheet.kt`) and the day-by-day look (`Core.runway`, both engines). Work in small stages; each is
committed, built and tested on GitHub before the next. **All stages done.**

## What went wrong (read from the code)

- **The balance was treated as extra money on top of the plan.** `runway` started from the balances, then took off
  "Food money left" for this half and "Personal Expenses left" as if that money were still waiting to be spent
  (the plan for the half minus whatever was logged). Someone who just started has logged nothing, so the whole
  half's food and Personal Expenses got taken off again. Example: balance $0, food money $300 for the 16th–31st,
  nothing logged → Where you stand said they were $300 short today. In reality the $0 means that money is already
  gone: what came in since the last payday has been spent. Money doesn't come from nowhere.
- **"Food so far" asked for a number the balance already answers.** It was a guess typed in on top of the balance, so
  the two could disagree.
- **A payday dated today was always assumed to be in the balance** (`runway` only counted paydays after today). Usually
  right, but deposits can land later in the day, so someone checking their bank in the morning got a balance
  without it and LoonieMate never counted it.

## Decisions

### Start from what's actually there
- At the starting point, the accounts hold everything left from the paydays that already came in. That money has to
  cover, in order: bills still owed before the next money comes in (including anything behind on), then this pay
  period's food money, then Personal Expenses. Whatever the plan expected for food and Personal Expenses that the
  balance can't cover was already spent before LoonieMate.
- `runway` caps "Food money left" and "Personal Expenses left" at what the balance can cover after those bills (food
  first). Paydays still to come work as before.
- The spent-before part is saved once, as an entry for the starting month ("Spent before LoonieMate", or "Spent before
  getting back on track"; groceries for food, Personal Expenses for the rest), so Month and Spending match reality
  instead of showing a whole unspent half.
- **Food so far became a check, not a question:** "After the bills due before Nov 27 ($135.00), about $75.40 is left
  for food until Nov 30 and $0.00 for Personal Expenses. Sound right?" with **Yes, that's right** and **Change it**
  (type what's really left; blank counts as $0 after one question). A $0 balance gets a kind line: "That's okay. It
  means what came in this month has gone on bills and spending. LoonieMate starts from here." A balance that covers
  only part of it gets: "The rest of what the plan set aside was already spent, and that's okay."
- The steps are now: balances → this month so far → anything behind on → Personal Expenses (the list) → what's left
  for food (and Personal Expenses) → where you stand. The check comes after the list so it knows the plan.
- Same logic for **Get back on track**.

### A payday that falls on the starting day
- If any payday or benefit is dated today, the balances step asks under the fields:
  "Your ODSP ($1,368.00) is due today. Is it already in these balances?" **Yes, it's in** (default) / **Not yet**.
- Not yet → it counts as coming in today in Where you stand, and pays the bills and food money from today.
- Only today's paydays are asked about; earlier ones stay as "already in your balances".

## Data (harmless to older copies)

- `months/<ym>.start = {date, notIn: [income or credit ids not in the balances yet], foodLeft, selfLeft}`: what the
  person confirmed. `runway` in both engines reads it only on that date; older copies ignore it.
- The spent-before entries are ordinary expenses with a marker (`startSpent: true`); doing the step again replaces
  that day's entries. They're exactly the gap between the plan and what's left, so they never count as overspending.

## Stages

- [x] **Stage 0**: this plan.
- [x] **Stage 1**: engine, web first then Kotlin, parity tested: `runway` caps food and Personal Expenses by the
  balance after bills, reads `months/<ym>.start`, and returns `now`. Parity: `out.runways` in parity/gen.js (a plain
  household: $0 mid-half and on the 16th, some money with bills first, more than the plan, a payday today in or not
  yet, `notIn` from another day ignored, confirmed amounts, blank confirmed amounts, the first half).
- [x] **Stage 2**: Android steps: today's-payday question on balances, the food and Personal Expenses check, the kind
  lines, Where you stand saying "plus $X coming in today".

## Notes for picking up

- Engine: `runway` returns `now = {nextIn, owed, room, foodPlan, food, selfPlan, self}` (`selfPlan` / `self` only
  with Personal Expenses this month). `owed` is overdue + bills + one-time expenses dated before `nextIn` (or up to
  the end when there's no more money coming). With `start.foodLeft` / `selfLeft` given, those are used as they are.
- App: `MainViewModel.paydaysToday`, `startToday`, `saveStartNotIn` (the balances step; clears earlier food/self
  answers), `startRunway` (today's balance check), `saveStartLeft` (replaces the day's `startSpent` entries, then saves
  `foodLeft` / `selfLeft`). UI: `BalancesStep`, `LeftStep` in `ui/sheets/CatchUpSheet.kt`.
- The web version computes the same `runway`; its own getting-on-track steps come in docs/plans/overhaul.md Stage 11.
