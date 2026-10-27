# Pet prescriptions and refills (Android and web)

The plan agreed on 2026-10-26. **Queued as Stage 10 of docs/plans/overhaul.md**, built straight into the new bill form
(Stage 7) so its screens are only made once. A prescription doesn't follow a pay cycle: 100 pills at 2 a day lasts
50 days, so the refill drifts through the calendar. LoonieMate works out when the supply runs out, reminds the person to
call it in a few days before, and budgets the cost on that date. Food and litter stay as they are (set amounts every
pay cycle). Work in small stages; each is committed, built and tested on GitHub before the next.

## Decisions

- **A prescription is a bill with a new kind of schedule, "when the supply runs out"** (`per: "refill"`), not a
  separate feature. That way it gets everything bills already have: the Pets group, Month and Bills rows, To do, Paid
  ticks (`months/<ym>.paid[key]`), Undo, Where you stand, reminders, sync and backups. Defaults to the Pets group; any
  group works (people's prescriptions run the same way).
- **What the form asks:** what it is ("Apoquel 16 mg"), who it's for (pet's name, optional), how much comes in a fill
  (100), how much is given and how often (2 a day; also "every N days" for monthly chews, and fractions for half
  pills; a unit word: pills, mL, chews), the cost of a fill, when it was last filled, where it comes from (vet or
  pharmacy name and phone, optional, tap to call), how many days ahead to call it in (default 5), and refills left on
  the prescription (optional).
- **The maths:** days it lasts = fill ÷ daily amount, rounded down (100 ÷ 2 = 50). Filled Oct 26 with the first dose
  that day → doses Oct 26 to Dec 14, runs out Dec 15, call in Dec 10 (45 days after filling). If the call-in day falls
  on a weekend or bank holiday it moves earlier to the last business day (a per-prescription switch if the vet is
  open weekends).
- **Budgeting:** the cost is a bill due on the call-in day, so the money is ready when it's needed. It lands in
  whichever month and pay period that day falls in; a short supply can land twice in one month and a long one can skip
  months, and the plan, food money, savings targets and Where you stand all see that. Not deferrable by default.
- **Future fills are projected** forward from the last fill, assuming each refill happens on its call-in day.
- **Paid = refilled.** Ticking it Paid opens a small "Refilled" page prefilled with today, the usual amount and the
  usual cost; any can be changed (vets don't always give the same count). That re-anchors the chain.
- **When reality drifts:** "Count what's left" (how many are left today; the run-out date moves) and "Change the dose"
  (from today). Both re-anchor without a refill. Both are in the bill's long-press menu too.
- **Refills left:** each refill counts one down. When none are left, the call-in item says the vet will probably need
  to see the pet for a new prescription, so book that first. No count entered → never mentioned.
- **Stopped medication:** "Stop this prescription" ends the chain (an end date, like other bills).

## To do and reminders

- A To do item on the call-in day: "Call in Max's Apoquel · runs out Sat Dec 15" with **Called it**; the bill itself
  then waits for Paid at pick-up (that's when the Refilled page asks for the real count and cost).
- The same in the day's reminder bundle, with Remind me later; if still not called in the day before it runs out,
  the follow-up says "runs out tomorrow".

## Data (harmless to older copies)

- On `bills/<id>`: `per: "refill"`, `supply`, `dose` (amount a day, may be a fraction), `unit`, `filled` (date),
  `lead` (days), `refillsLeft` (optional), `forName`, `source`, `phone`, `weekends` (vet open weekends). Re-anchors
  keep a short `fills` history (`[{date, amount, cost}]`) so past paid occurrences keep their own dates and amounts.
- Occurrence keys must stay stable when the chain is re-anchored: key by fill number, not by date.
- Older copies that don't know `per: "refill"` must ignore the bill rather than treat it as monthly; check before
  Stage 1.

## Stages

- [x] **Stage 0**: this plan.
- [ ] **Stage 1**: engine, web first then Kotlin, parity: refill dates (supply ÷ dose, lead days, weekend / holiday
  move, fractions, every N days), re-anchoring from `fills`, count-what's-left and dose changes, stable keys, the end
  date; refills in `monthCalc`, `runway`, `Views.todo` (the call-in item) and the 6-month look. Parity: a 50-day
  supply across a month end, a 30-day supply landing twice in a month, a call-in day on a holiday.
- [ ] **Stage 2**: Android: "Add a prescription" on the new bill form, the Refilled page from the Paid tick, Count
  what's left, Change the dose, Stop; rows show "runs out Dec 15 · call in Dec 10"; the call-in reminder.
- [ ] **Stage 3**: web: the same form and actions (functionally the same, per the overhaul). Then release.
