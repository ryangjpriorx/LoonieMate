# Personal Expenses (Android first, web understands the data)

The plan agreed on 2026-10-26. Everyone spends a little on themselves: a coffee, smokes, a game, a night out. Ignoring it
doesn't make it go away; it just turns up later as "unlogged spending" and a plan that never matches reality. So the
budget plans for it openly, as **Personal Expenses**, without ever asking what it's for.
**Queued after docs/plans/pages-and-blanks.md.** Work in small stages; each is committed, built and tested on GitHub
before the next.

## Decisions

- **Name:** "Personal Expenses".
- **Never asks what it's for.** One amount per adult (or one for the household), per week, per paycheque or per month.
  Blank means $0; $0 turns the whole thing off.
- **Setup:** a step after bills in first-time setup, worded without judgement: "Most people spend a little on
  themselves: a coffee, smokes, a game, a night out. Whatever yours is, you don't need to say. Planning $20 a week beats
  planning $0 and spending $80." Changeable any time in Settings.
- **In the plan:** taken out after bills and food (like food money), before what goes to savings. Shown as its own line
  on the Month screen.
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

- [ ] **Stage 0**: this plan.
- [ ] **Stage 1**: the maths in both engines (web first, then Kotlin), checked by the parity tests: the planned amount in
  `monthCalc`, the `self` spending category, going over, a held-back amount for a month (`months/<ym>`), `runway`.
- [ ] **Stage 2**: Android: the setup step, a Settings card, the "Personal Expenses" choice in Log spending, the Month line.
- [ ] **Stage 3**: Android: the "hold back" fix in Where you stand and the status note. Web shows the line. Then release.
