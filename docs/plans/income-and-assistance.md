# Employment Insurance, and a cleanup of the assistance forms (Android and web)

The plan agreed on 2026-10-26. EI was missing as a kind of income, and the ODSP / Ontario Works forms asked questions
people shouldn't have to answer, mainly "Which payment a month's pay reduces". Work in small stages; each is
committed, built and tested on GitHub before the next. **All stages done; released in 0.13.0.**
Next: docs/plans/getting-on-track-fixes.md (balances are the truth), then bank bill payment, amount changes over time,
and pet prescriptions.

## Decisions

- **Each program carries its own rule** (`Core.Program.lag`, `from16`): ODSP, a month's pay lowers the payment two
  months later; Ontario Works, pay from the 16th to the 15th lowers the next payment (ontario.ca, "Working and earning
  while on Ontario Works"); Quebec the next month; Nova Scotia unchanged (two months; confirm). Shown as a sentence
  with real months (`IncomeSetup.ruleText`), never asked.
- **Income kinds** (`kind`: `pay`, `ei`, `other`) decide how assistance counts each income (`Core.countsAs`): EI in
  full; pay as work pay, `odsp: "dis"` for someone on ODSP for a disability; other income in full (`odsp: "full"`) or
  not at all. Incomes saved without a kind keep their old `odsp` choice. The deduction switches are gone; only couple,
  dependants, supported employment (Nova Scotia) and Quebec's custom rule remain in `ded`.
- **EI**: every 2 weeks, `end` (last payment; `Core.inRange`), report reminder every 2 weeks
  (`report: {on, every: 14, anchor}`, default 3 days before a payday; `Views.eiReportDay`, ticks saved as
  `months/<ym>.reported["<id>@<date>"]`).
- Not done: CPP / CPP Disability as kinds of their own (add as "Other income", counted in full).

## Stages

- [x] **Stage 0**: this plan.
- [x] **Stage 1**: engine, web first then Kotlin: per-program lag and the OW window, `countsAs`, `inRange`, EI
  reports. Parity households `ontario-works` and `odsp-ei`; `EiTest`.
- [x] **Stage 2**: Android: `IncomeSheet` asks the kind, EI fields, the plain rule; "+ Add Employment Insurance" in
  Settings; EI report rows in `ReminderAlarm`. `ScreensTest.assistanceAndEi`.
- [x] **Stage 3**: web: the same forms in the Income tab (`INCOME_KINDS`, `kindOf`, `ruleText`, "+ Add EI"). Then
  release.

## Notes for picking up

- Assistance still works its payment out from the income's saved usual amount, not a carried-forward Month tab
  amount; docs/plans/amount-changes.md fixes that with dated changes.
