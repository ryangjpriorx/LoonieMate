# Personal Expenses (Android first, web understands the data)

The plan agreed on 2026-10-26. Everyone spends a little on themselves: a coffee, smokes, a game, a night out. Ignoring it
doesn't make it go away; it just turns up later as "unlogged spending" and a plan that never matches reality. So the
budget plans for it openly, as **Personal Expenses**, without ever asking what it's for.
**Queued after docs/plans/pages-and-blanks.md.** Work in small stages; each is committed, built and tested on GitHub
before the next. **All stages done; released in 0.8.0.**

## Decisions

- **Name:** "Personal Expenses".
- **A list of what you get and how often** (changed 2026-10-26, was one amount per person): each item has what it is
  (optional), how much each time, and how many times a day, week, month or payday ("a $3.25 coffee, 5 times a week").
  Items without an amount are left out; an empty list turns the whole thing off.
- **Setup:** a step after bills in first-time setup, worded without judgement: "Most people spend a little on
  themselves: a coffee, smokes, a game, a night out. Whatever yours is, you don't need to say. Planning $20 a week beats
  planning $0 and spending $80." Lives on the **Spending tab** (moved from Settings 2026-10-26), where each item can
  also be logged with one tap.
- **In the plan:** taken out after bills and food (like food money), before what goes to savings. Shown as its own line
  on the Month screen.
- **On Today: what's gone on Personal Expenses this month** (added 2026-10-26). A small, plain card: "Personal
  Expenses · $34 of $87 this month", a calm bar, and what's left. Honest about the number but never shaming: no red, no
  "wasted" or "spent too much" wording; going over just says "$12 over what was planned" in the normal text colour.
  Shown only when Personal Expenses are planned or something was logged under it.
- **Logging is optional:** a "Personal Expenses" choice in Log spending (category key `self`; `personal` is already
  taken by "Personal care"). Not logging it is fine and never nagged about. Going over
  what's planned comes out of what's left for savings, the same way going over on food does.
- **When money is short: suggested, never expected.** It stays in the plan unless the person chooses otherwise. Expecting
  people to cut it would upset them and they'd stop using the app.
  - "Where you stand" offers a fix: "Hold back $40 of your Personal Expenses until Nov 6", with half or all; it only
    applies to this month.
  - Order of fixes: pay later → give up a non-essential bill → hold back Personal Expenses → Emergency Savings.
  - If bills, food and Personal Expenses add up to more than the income, the status note suggests lowering it and by
    how much. Wording stays kind: a suggestion, not a telling-off.
- **Compatible:** new settings fields that older copies ignore; the web engine gets the same maths, checked by the
  parity tests; the web version shows the line and the category but has no setup step.

## Stages

- [x] **Stage 0**: this plan.
- [x] **Stage 1**: the maths in both engines (web first, then Kotlin), checked by the parity tests: the planned amount in
  `monthCalc`, the `self` spending category, going over, a held-back amount for a month (`months/<ym>`), `runway`.
- [x] **Stage 2**: Android: the setup step, a Settings card, the "Personal Expenses" choice in Log spending, the Month line,
  and the Today card.
- [x] **Stage 3**: Android: the "hold back" fix in Where you stand and the status note. Web shows the line. Then release.

## Notes for picking up

- Stage 1 (done): both engines (web `Core.selfPlanFor` / `SELF`, Kotlin `Core.selfPlanFor` / `Core.SELF`).
  `settings.selfPlan` = `[{name, amount, per: "week" | "pay" | "month"}]` (week = 52/12 a month; pay = each regular main
  payday; anything else = per month). `months/<ym>.selfHold` holds some back for that month (capped at the plan).
  `monthCalc` adds `selfPlanned`, `selfHold`, `self` (after the hold), `perPaySelf`, `selfSpent` (logged `self`, not
  one-time), `selfOver`; `plannedSurplus` takes off `self`, `liveSurplus` takes off `selfOver`, and `self` spending is
  no longer in `otherSpent`. `runway` adds `self` items: today, what was given out on this month's paydays so far and
  not spent; then `perPaySelf` on each later regular payday (or the whole amount on the 1st with no main paydays).
  Status (`getStatus` / `Views.status`): "Bills, food and Personal Expenses add up to…" for future months, and a
  `selfTip` ("If you can, planning $X less for Personal Expenses … would help.") offering only what's still unspent.
  The per-payday breakdown shows a Personal Expenses line. Spending category "Personal Expenses" (`self`) in both lists.
  Parity: the Ontario, Quebec and Nova Scotia households have `selfPlan`; Ontario has `self` spending, a hold, and a
  hold bigger than the plan.
- Stage 2 (done): `ui/sheets/SelfSheet.kt`: `SelfSheet` (`Sheet.Self`: this month's planned / held back / logged /
  left, the logged entries, "+ Log Personal Expenses", then the plan) and `SelfPlanEditor` (one line per person: who,
  how much, per week / paycheque / month; blank = $0), also step 5 of getting on track (now six steps).
  `MainViewModel`: `SelfRow`, `selfRows`, `readSelf`, `selfMonthly`, `saveSelf`, `holdSelf`. Today: `SelfCard`
  (`TodayModel.self`, shown when planned or logged; calm "sky" colour, never red). Settings: `SelfPlanCard` under Food.
  Month: a Personal Expenses tile. Log spending already lists the category.
- Stage 3 (done): Where you stand offers "Hold back some Personal Expenses" (all of what's still unspent up to the
  shortfall, or half), after giving up a non-essential and before Emergency Savings. Web month summary shows the line.
- List and Spending tab (2026-10-26): `selfPlan` items gain `times` (default 1) and `per: "day"` (365/12 a month);
  `selfItemMonthly(p, regCount)` in both engines, `selfPlanFor` sums it; parity covers day, week, pay, month, bad and
  zero `times`. Android: `SelfPlanEditor` is the list (what, each time, how many, a day / week / month / each payday,
  "about $X a month" under each); `SelfSpendingCard` on the Spending tab (planned vs logged, held back with Put it back,
  each item with "Log $X" for today via `MainViewModel.logSelfItem`, Change the list, Log something else). The Settings
  card is gone; Today's card links to the Spending tab.
