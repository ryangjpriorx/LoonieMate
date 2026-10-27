# Paying bills through the bank app (Android and web)

The plan agreed on 2026-10-26. **Queued as Stage 8 of docs/plans/overhaul.md**, built into the new bill form and To do.
Bills can be marked as paid by "Pre-authorized debit (PAD)", but most Canadians pay many bills themselves as a payee in
their bank's app (online bill payment). That payment takes 1 to 3 business days to reach the company, while the money
leaves the account the moment it's sent. So these bills need paying a few business days before their due date, and the
budget should take the money out on the day it's paid. Work in small stages; each is committed, built and tested on
GitHub before the next.

## Decisions

- **A new way to pay a bill: "Bill payment in my bank app"**, next to PAD in the bill form (`Labels.METHODS`).
- **Asks for:** the account it's paid from, and how many business days it takes to arrive (default 3; banks quote
  1 to 3). Optional: the payee's name as it appears in the bank app.
- **Pay-by date** = the due date moved back that many business days (weekends and bank holidays don't count).
  Bell due Wednesday Dec 10, 3 business days → pay by Friday Dec 5.
- **Budget on the pay-by date** in `monthCalc`, `runway` and the pay-period split; money moves into that account are
  timed before the pay-by date.
- **To do** on the pay-by day: "Pay Bell in your bank app · Main Chequing · due Wed Dec 10".
- **Reminder** on the pay-by day: "Pay Bell $95.00 from Main Chequing today so it arrives by Wednesday", with
  **Paid** and Remind me later; after the pay-by date the follow-up says it may arrive late.
- **Overdue** only after the due date (between them it's "pay today, it may be a day late").
- **Interac e-Transfer** (landlords, people) is a plain method with no lead days.

## Data (harmless to older copies)

- On `bills/<id>`: the method value for bank bill payment, `payDays` (business days, default 3), `payee` (optional).
  Older copies treat the bill as due on its due date, only a few days off.
- Paid ticks keep the same `key` / `ym` (the due date's occurrence).

## Stages

- [x] **Stage 0**: this plan.
- [ ] **Stage 1**: engine, web first then Kotlin, parity: the pay-by date (business days, holidays, across a month
  end and a pay-period boundary), money counted on it, overdue only after the due date, `Views.todo`.
- [ ] **Stage 2**: Android: the choice in the bill form, To do / Month / Bills wording, reminders.
- [ ] **Stage 3**: web: the same. Then release (with Stages 9 and 10 of the overhaul).
