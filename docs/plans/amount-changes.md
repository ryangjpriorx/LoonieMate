# Amounts that change over time (Android and web)

The plan agreed on 2026-10-26. **Queued as Stage 9 of docs/plans/overhaul.md.** Money changes: ODSP goes up every
year, an overpayment is taken back a bit at a time, rent goes up, a job's pay changes. Today LoonieMate can only hold
one amount for each income and bill. Work in small stages; each is committed, built and tested on GitHub before the
next.

## What happens now (read from the code)

- An income's "usual amount" is one number. Changing it in Settings changes every month that isn't locked, including
  months already over.
- The Month tab's income page keeps per-month amounts (`months/<ym>.income.amt[id]`) that carry forward to later
  months (`Core.resolve`), but it reads as "this month only" and is hard to find.
- **Bug:** assistance works its payment out from the saved usual amount (`base = srcA.amount`), not the carried-
  forward one. Typing January's new ODSP amount on the Month tab works for January, then February onward goes back.
- Bills have one amount and no history: a rent increase changes every unlocked month back to the start.
- Nothing models an overpayment being paid back.

## Decisions

### "Changes from…" on every income, benefit and bill
- Each income, benefit and bill page gets **Amount changes**: a short list of "from <month> → $amount", plus
  **+ It changes from…**. Editing the usual amount directly asks: "Change it from this month on, or fix it for every
  month?" (from this month is the default).
- Saved on the item as `changes: [{from: "2027-01", amount: 1450}]`; the engine picks the latest change on or
  before each month, before the Month tab's per-month typed amounts (which still win for that month and carry on).
- Assistance uses that month's amount as its full payment before income is taken off (fixes the bug above).
- The ODSP page reminds people to add the yearly increase from the month it starts.

### Overpayments being paid back
- On assistance, EI and benefits: **Being paid back**: how much comes off each payment (a set amount or a
  percentage), from which payment, and the total owed (or "until further notice"). Taken off each payment until the
  total is reached, shown as "$310 of $600 paid back", and stops by itself.
- Saved as `recover: [{from: "2026-12", each: 50, pct: null, total: 600}]` on the income or benefit.

### Rent and other bills
- Same "changes from…" list on bills. Past months keep the old amount; locked months were already safe.

## Data (harmless to older copies)

- `changes` and `recover` are new fields; older copies ignore them and keep using the usual amount.
- Saving a change also sets the usual amount to the newest amount, so older copies are right from now on.

## Stages

- [x] **Stage 0**: this plan.
- [ ] **Stage 1**: engine, web first then Kotlin, parity: `changes` for incomes, credits and bills; assistance using
  that month's amount; `recover` with a total and a percentage. Parity: an ODSP increase in January with deductions,
  an overpayment that ends mid-year, a rent increase.
- [ ] **Stage 2**: Android: Amount changes on income, benefit and bill pages (and the payday long-press menu);
  "from this month or every month?"; Being paid back with progress.
- [ ] **Stage 3**: web: the same.
