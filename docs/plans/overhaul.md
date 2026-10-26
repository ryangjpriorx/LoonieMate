# The overhaul: easier, calmer, sturdier (Android first, then web)

The plan agreed on 2026-10-26. LoonieMate does a lot now, and it shows: Today and Month are long
scrolls of cards and numbers, Settings is fifteen cards in a row, some things live in odd places, and nothing can be
long-pressed. This phase makes every screen do one job well, puts things where people look for them, makes the
everyday actions (tick it off, change it, put it off) quick in more than one way, and finishes the four plans still
waiting, so the app ends up stable with nothing pending. **1.0.0 is the developer's seal of approval after testing
it himself: never release 1.0.0 until he explicitly says so.** This phase ends on a 0.x release.
Work in small stages; each is committed, built and tested on GitHub before the next, with screenshots checked in
light, dark and the largest text size.

## The order, and why

1. **Getting on track fixes first** (docs/plans/getting-on-track-fixes.md): it's a wrong-numbers bug ("$300 short"
   for someone who has simply spent this half's food money already), and wrong numbers come before looks.
2. **The money side of the new To do** (cash and ATM withdrawals, one list of everything to do) in both engines, so
   the screens are built on it rather than patched later.
3. **The overhaul itself**: shared pieces and navigation, then Today, Month, the other tabs, the forms and
   first-time setup.
4. **Bank bill payment, amounts that change, pet prescriptions** (their plans as written), built straight into the
   new bill and income pages instead of building their screens twice.
5. **A stability pass**, ending on a 0.x release for testing (1.0.0 only when the developer says so).

The web version keeps up stage by stage instead of in one catch-up at the end (see Web version below).

## What's wrong now (read from the code and the screenshots)

**Getting around**
- Food is a form page reached from three places (Today, Month, Settings) with its settings mixed in. It's a big part
  of the budget, not a form.
- Income only lives in Settings, between Accounts and Food budget. People look for it next to bills and the month's plan.
- The month arrows sit in the top bar of Month, Bills, Spending and Savings, and the chosen month quietly carries
  between them (go to December on Month, open Bills, and Bills is on December too).
- "Log spending" floats over Month, where nothing is logged.
- **Android has no guided first-time setup.** A new person gets the accessibility page, then a Welcome card with
  Import / Add an account / Add a bill, and has to find income in Settings themselves. The Welcome card still says
  setting up income "is coming next; for now do that in the web version", which hasn't been true for a while.

**Today**
- Up to eleven cards: notices (notifications, late reminders, accessibility, update, storage), getting started, To do,
  Food money, Personal Expenses, Balances, Log spending, Debt payoff, Next few days.
- To do rows are tall (name, then "Was due Oct 20 · Adults · Main Chequing · Debit card", then a pill button), so seven
  items fill two screens. Overdue names are all red.
- Tapping a row does nothing. The only thing a row can do is its small "Mark paid" / "Mark done" pill; there's no way
  from Today to change the bill, pay part of it later, or give it up.
- Things to do live in three places: the To do card, reminder cards above it (report income, tax season, backup) and
  the Balances card. Taking out cash isn't really there at all (see below).
- Food money says "the line shows where you'd be at $X a day", which needs explaining.
- The subtitle "Saved on this phone · backed up today" is always there, even when there's nothing to say.

**Month**
- Six bullet points of status (most repeat Today's To do), then the shortfall card, four number tiles, Coming up,
  where the money goes, five more sum tiles, spending by category, debts and a savings line.
- Coming up is Today's list again, not this month's: on November's page it shows October's past-due bills.
- Tiles like "Bills due · next 10 days" and "Child Reserve" repeat other screens without helping plan the month.
- "Change November income" / "Income and locking" is hard to guess the meaning of.

**Lists and forms**
- Nothing can be long-pressed, and there are no row menus: every change goes through a full form.
- A logged expense **can't be changed**, only deleted with the bin button on every row (easy to hit by accident).
- Quick buttons are removed in Settings, far from where they're used.
- Adding a program, benefit or ready-made fund is a drop-down plus a separate Add button inside a card.
- The bill form shows everything at once: savings fund, non-essential and deferrable come before the basics are done.
- Family income for savings programs is a form field in the middle of the Savings tab.

**Motion**
- Every card fades and lifts in one after another (about 0.4 s each, 40 ms apart) every time a tab is opened, numbers
  count up for 0.75 s, bars grow for 0.9 s and debt rings for 1.1 s. Each looks nice once; together, on every tab
  switch, the app feels slow.
- The colour line under the top bar shifts forever and the status badge pulses forever.

**Cash and the ATM**
- A bill paid in cash only makes a "withdraw" move when there's a "Cash on hand" account. Without one, nothing tells
  the person to take the cash out at all.
- With one, it reads "Move $200.00 to Cash", dated the day the bill is due, one row per bill.
- Food money going to a cash "account" is also shown as a move, never as "take out cash".

## Decisions

### Navigation
- **Five tabs stay**: Today · Month · Bills · Spending · Savings, with Settings behind the gear. They're already
  learned, and five is the most a phone bar holds well.
- **Spending takes in Food**: Food money (both halves, per day, what's logged) at the top of Spending, then Personal
  Expenses, then everything logged. The food *budget settings* (share and minimum) move to Settings → Food budget.
  The Food form page goes away.
- **Income has two doors to one page**: a "Money in" section at the top of Month (each payday and benefit, tap to
  open), and Settings → Income and benefits.
- **The month switcher moves into the page** on Month and Spending (the only tabs about one month). Bills and Savings
  are always about now. The chosen month is still shared between Month and Spending, and a "Back to this month"
  chip shows whenever another month is open.
- **The floating button**: "Log spending" on Today and Spending, "Add a bill" on Bills, nothing on Month and Savings.
  It shrinks to just the + while scrolling down so it doesn't sit on top of rows.
- Android back always goes back one step: page → tab → Today → leave.

### Today: what to do, and am I okay
In this order, and nothing else:
1. **The date and one status chip** ("On track", "Heads up", "Needs attention"). Tap it for the why (Month's points).
2. **One notice at most**, the most important of: couldn't save, a damaged budget, update ready, notifications off,
   reminders arriving late, the accessibility offer. The rest wait their turn. Getting started (first time) is
   bigger and comes first.
3. **To do**, the main thing on the screen. One list of every real-world action due today or overdue:
   - bills to pay (later: pay in your bank app by a pay-by date, call in a prescription),
   - money moves between accounts, **cash to take out**, money into savings,
   - report income (EI, assistance), the weekly balance check when it's due, lock last month when it's over.
   Each row is one line plus a short second line ("Main Chequing · was due Oct 20"), the amount on the right and a
   round tick on the left (a 48 dp target, 56 with Bigger buttons). Overdue rows get an "Overdue" label and a red dot,
   not a red name. **Tap** a row to open it (the bill, the move and why, the fund); **long-press** for its menu (below).
   Ticking a row fades it into a folded "Done today · 3" line at the bottom (tap to see, tap again to untick), with
   Undo as now. Nothing to do: "All done for today", and the next thing coming.
   Money arriving today sits above the list as one quiet line: "+$1,263.33 Sam's paycheque lands today".
4. **Spending money**: one card, two lines. "Food · $470 left · about $78 a day for 6 days" with a bar, and
   "Personal Expenses · $53 left this month" (calm colour, never red, as agreed). Tap either for Spending.
5. **Log it**: the quick buttons in one row that scrolls sideways, then "Something else". Long-press a quick button to
   log a different amount, change it or remove it.
6. **Coming up**: the next 7 days, one line each (day · name · amount), tap to open.

Moved off Today: the Balances card (becomes a To do item when a check is due, and lives in Settings → Accounts), Debt
payoff (Bills), the Welcome card (replaced by first-time setup), the always-on backup subtitle (shown only when a
backup is over two weeks old or sync needs attention).

### Month: the plan, and how it's going
1. **Month switcher and status**: one sentence ("$754 short. Cover it from Emergency Savings first.") with "Why?" to
   unfold the points.
2. **Where the money goes**: one bar split into Bills, Food, Personal Expenses and Savings (or Short, in the problem
   colour, plus its icon and word), and under it five lines: Money in, Bills, Food, Personal Expenses, Left for
   savings. That's the numbers that matter for a month; the tiles go.
3. **Make it fit** (only when short, or when something was given up or put off): the same give-up and pay-later rows
   as now, made shorter, with the same long-press menu.
4. **Money in**: each payday and benefit with what it pays for (today's per-payday breakdown, folded to one line
   each: "Nov 6 · Sam's paycheque $1,263 → bills $640, food $200, savings $50"), tap to unfold, long-press to change
   the amount for this month or open the income.
5. **Spent so far**: the top five categories and "See all" (Spending).
6. **Past months**: a "Lock this month" card when a month is over; "Change this month's income" lives in Money in.

Gone from Month: Coming up (Today has it), the Bills-due, Child Reserve and Food tiles, Debts, the savings line.

### Bills, Spending, Savings
- **Bills**: one list sorted by the next due date, filter chips All · Adults · Kids · Pets · Debts, group colours kept
  as dots with the group's name. Each row: name, amount, "next Nov 6 · Main Chequing", pills only when they matter
  (paused, given up this month, put off, example). Debts get their rings and payoff dates under the Debts chip and in
  a small summary at the top. Tap opens the bill; long-press for the menu.
- **Spending**: month switcher, Food money, Personal Expenses, everything logged (tap to **change** an entry, which
  is new; the bin buttons go, delete is in the entry and its menu), one-time expenses, last 6 months, spreadsheet.
- **Savings**: Emergency Savings, the Child Reserve and the other funds as before; the 6-month look and the savings
  programs fold up by default; "+ Add a fund" opens a page of choices (a blank fund or a ready-made one with its
  yearly cost); family income moves to Settings → Household.

### Long-press, where it makes sense
Tap always does the obvious thing (open it, or log it for a quick button). Long-press gives a short menu of the
other things people do with *that* item, with a little buzz. The same actions are always reachable another way
(the ⋮ button on its page, and TalkBack's actions menu), so long-press is a shortcut, never the only way.

| Long-press on | Menu |
|---|---|
| A bill in To do, Coming up or Bills | Paid (or Not paid) · Pay part later… · Give up this month… (non-essential) · Change the bill · Pause it |
| A money move or cash to take out | Done · Why this move? · Change the accounts it uses |
| A logged expense | Change · Log it again today · Make it a quick button · Delete |
| A quick button | Log a different amount… · Change · Remove |
| A Personal Expenses item | Log a different amount… · Change the list |
| A payday on Month | Change this month's amount… · Open the income |
| An account | Change · Check its balance |
| A savings fund | Record a transfer · Use money from it · Change |

Not used for: tabs, the month switcher, buttons, or anything where a menu would just repeat the tap.
No swipe gestures on money rows: a swipe is too easy to do by accident on a list of payments.

### Forms
- **First-time setup** (new): after "Make LoonieMate easier to use?", one guided flow, each step a page with Skip:
  your household (name, people, province) → your accounts → money in (assistance, paycheques, EI, benefits, each
  picked from a list of choices) → your bills → food → Personal Expenses → getting on track (where you stand today).
  "Import a backup" is offered on the first step. Progress shows as "Step 3 of 7"; leaving and coming back resumes.
- **Log spending**: amount, then category chips (the last one used first), then Save; date, note, who paid and
  "make a quick button" fold under "More". The same page changes an existing entry.
- **Bill form**: name, amount, how often and when, paid from and how, then Save. "More options" holds non-essential,
  deferrable, savings fund, start/stop/pause, and debt. New bills get the account and method of the last bill
  added to that group. (Bank bill payment, amount changes and prescriptions add to this form in their stages.)
- **Adding income, a benefit or a fund**: "+ Add" opens a page listing the choices with one line about each, instead
  of a drop-down plus an Add button.
- Every form keeps what was agreed in docs/plans/pages-and-blanks.md (no accidental closing, "Leave without
  saving?", blank means $0 where it should).

### Settings: a short list of pages
One row per page, each with an icon, its name and a one-line summary ("Ontario · Alex, Sam", "5 accounts", "Backed up
today"):
- **Your budget**: Household and province · Accounts · Income and benefits · Food budget · Quick buttons
- **This phone**: Reminders · Look · Accessibility
- **Keeping it safe**: Backup · Sync between phones · Updates
- **Help**: Get back on track · About LoonieMate
- At the bottom, as now: Redo first-time setup (for testing) and Reset LoonieMate.

Pages that only show settings open like forms but without "Leave without saving?" when nothing is typed.

### Cash and the ATM (both engines, parity tested)
- **Any bill paid by Cash gets a "Take out cash" item**, with or without a "Cash on hand" account (it's taken from the
  bill's account; a cash account, if there is one, is where it goes).
- **When**: the day before it's due (ATMs work on weekends, so no business-day shuffle), but never before the payday
  that pays for it. Rent due Sunday Nov 1 paid in cash, ODSP on Friday Oct 30 → take it out Saturday Oct 31.
- **One trip a day**: everything to take out on the same day from the same account is one item:
  "Take out $240.00 cash from Main Chequing · Rent $200.00, babysitter $40.00".
- **Food money in cash**: when the everyday-spending account is "Cash on hand", the food move becomes
  "Take out $200.00 cash for food (16th–end)".
- Shown with an ATM icon; ticking it is the same as a money move (same keys, so older copies keep their ticks).
  Older copies that don't know the new item kind show it as an ordinary money move.

**What the bank and its ATM can do** (new, optional, on each chequing account's page):
- **Daily ATM limit** (blank = no limit). Hint: "Check your banking app; it's often $500 or $1,000."
- **Bills the ATM gives out**: $20s (the default), $10s and $20s, or $5s and up.
- **Branches with tellers**: yes (the default) or "No, it's an online bank (like Simplii or Tangerine)".

**More than the daily limit**: the first time it happens, the Take out cash item asks how, and the answer is saved on
that account (changeable on its page):
- **Go back each day**: spread over the days before, starting early enough, each day's part its own item
  ("Take out $1,000 today and $450 tomorrow for rent"), never before the money is there.
- **Use another account too**: take the rest from a second account's ATM card the same day (with its own limit),
  plus a money move into that account first if it doesn't hold enough.
If neither is enough in time, the item says so plainly and suggests a teller (or, for an online bank, starting a day
earlier or an e-Transfer to whoever is being paid if they take one).

**Amounts the ATM can't give out exactly** (anything that isn't a whole number of its bills):
- The item rounds the ATM amount up to its bills: "Take out $1,460 at the ATM (it gives $20s). Rent is $1,453.00;
  the extra $7.00 stays as cash on hand."
- **With tellers**: "Or get exactly $1,453.00 from a teller."
- **Online bank, coins needed** (the bill can't be paid with what the ATM gives without change, like an envelope for
  the landlord): "Your bank has no tellers. Get $3.00 in coins as change when you buy something, or ask a store to
  break a bill." Cash amounts with cents count to the nearest 5¢, as Canadian stores do.
- **A dollar or two from a paper bill** ($5, $10, $20, $50, $100): when a cash bill's amount is within $2.00 of the
  next $5 (for example $38 or $1,453), LoonieMate asks once, on the bill form and on its first Take out cash item:
  "Round up to $40.00 to avoid coins?" **Round it up** / **Keep it exact**. Rounding up plans the rounded amount for that
  bill whenever it's paid in cash, so the budget matches what actually leaves. The answer is saved on the bill.
- Any extra taken out (rounding up to the ATM's bills) is noted as cash on hand, not as spending.

### Motion: quick but smooth
- Page and tab changes: a short fade (150 ms tabs, about 220 ms pages, standard Material easing). No sideways slide
  between tabs.
- Cards fade in **once**, the first time a tab is shown in a session (about 180 ms, 20 ms apart, at most 5 staggered),
  not every time the tab is opened.
- Numbers and bars animate only when the value **changes** (a tick, a log), about 300 ms; no count-up on first view.
- Ticked rows fold away (about 200 ms) and the list closes the gap smoothly; the long-press menu grows out of the row.
- The top line stays a gradient but stops moving; the status badge no longer pulses (its icon and word already say it).
- Less motion turns all of it off, as now. A test checks that no animation in the app runs longer than 400 ms
  (except progress while downloading).

### Web version: the same where it makes sense
- Every stage that changes the Android app also brings the web version along in the same stage, so both stay
  functionally the same: the same To do (cash included), Today and Month layout, Food inside Spending, editing an
  expense, first-time setup, Settings grouped the same way, the new bill and income options.
- Long-press becomes a ⋯ button on each row (and right-click on a computer), opening the same menu.
- Things the web version also gets now, since they work fine in a browser: getting on track (all six steps and the
  fixes), the Personal Expenses list editor.
- Things that don't work well in a browser get a short, friendly stub where the feature would be, instead of being
  missing: reminders and notifications, sync between phones, self-updates. The stub says what it does and that
  "LoonieMate works better on Android", with the download link. On iPhone it says the web version is the way to use
  LoonieMate there (no Android link nagging).

### Stability: "works as intended" means checked
- **Journey tests** (Compose screen tests, the same ones that draw the screenshots): first-time setup start to finish;
  log, change and delete an expense; tick a bill paid, Undo, untick; pay part later and give up from the long-press
  menu; a cash bill showing up as Take out cash and ticking it; a shortfall made to fit; backup out and back in;
  getting on track with a $0 balance. The fixed waits in screen tests are replaced with waiting for the screen to
  settle, so they're faster and can't pass by luck.
- **Every plan's "not tested yet" is closed**: sync through real Google Drive on two phones, reminders over a few days
  on a phone that saves battery hard, the self-update, notifications after a restart. Done on a real phone with a
  short written checklist (docs/testing.md), results noted in that file.
- **Nothing pending**: every plan in docs/plans is marked done, the README and screenshots match the app, and the
  release notes list what changed since 0.13.0.
- The last release of this phase is a 0.x version for the developer to test; **1.0.0 waits for his explicit word.**

## Stages

Each stage from 2 on does the Android app first, then the web version, unless it says otherwise.

- [x] **Stage 0**: this plan.
- [x] **Stage 1**: getting-on-track-fixes.md, both stages (engine, then the Android steps). Released in 0.14.0.
- [x] **Stage 2**: engine, web first then Kotlin, parity tested: Take out cash (dates, one trip a day, food in cash,
  ATM limits with days or a second account, rounding to the ATM's bills, tellers, round-up to avoid coins), and
  `Views.todo(st, today)`: one list of every To do item with its kind, so both versions build To do the same way.
- [x] **Stage 3**: shared pieces: the row (tap, long-press or ⋯ menu, TalkBack actions, tick), the action menu, the
  motion rules, settings pages; navigation (tabs, the floating button, the month switcher in the page); Settings as a
  list; the account page's ATM settings.
- [ ] **Stage 4**: Today.
- [ ] **Stage 5**: Month.
- [ ] **Stage 6**: Bills, Spending (with Food and changing an entry) and Savings.
- [ ] **Stage 7**: forms: first-time setup, Log spending, the bill form (with the round-up question), the add-choices
  pages. Release 0.15.0.
- [ ] **Stage 8**: bank-bill-payment.md (engine, Android on the new bill form and To do, web).
- [ ] **Stage 9**: amount-changes.md.
- [ ] **Stage 10**: pet-prescriptions.md. Release 0.16.0.
- [ ] **Stage 11**: web-only gaps: getting on track and the Personal Expenses list editor on the web, and the
  "works better on Android" stubs for reminders, sync and updates.
- [ ] **Stage 12**: stability pass: journey tests, the real-phone checklist, README and screenshots. Release 0.17.0
  for the developer to test. **Not 1.0.0** until he says so.

## Decided (2026-10-26)

1. **Web**: functionally the same as Android wherever that makes sense, built in the same stages; a "works better on
   Android" stub where something doesn't work well in a browser.
2. **Ticked items** fold into "Done today" until tomorrow.
3. **Cash** is taken out the day before it's due.
4. **ATM limits** on chequing accounts, with the person's choice of going back each day or using another account;
   rounding to the ATM's bills, tellers or not, and an offer to round up a dollar or two to avoid coins.
5. **1.0.0** only on the developer's explicit say-so, after his own testing.

## Notes for picking up

- Stage 3 (done): `ui/Menu.kt` (`MenuItem`, `MenuRow`: tap, long-press menu with a buzz, the same items as TalkBack
  actions; `TickButton`). `Motion` timings and `LocalScreen` / `SeenScreens` in Components.kt (cards fade in once per
  screen per session; `rememberCountUp` and `GrowBar` / `ProgressRing` glide only on change; no pulse; still top line).
  AppUi: pages are a stack (`StackSaver`, `rememberSaveableStateHolder`, `open` / `close` / `replace`), tab fade,
  floating button by tab and collapsed once scrolled (`lists` of LazyListState), `MonthSwitcher` inside Month and
  Spending; Bills and Savings build for the current month. Settings: `MoreScreen` is the grouped list
  (`SettingsRow`, `GroupHeading`), `Sheet.Settings(section)` → `SettingsPage`; cards double as pages via `CardOrBare`.
  Family income is on the Household page. Rows: `EventRow` (tick, tap to open, `billMenu`-style menus, `MoveDialog`,
  `cashNotes`), `ExpenseRow` (menu, no bin), `QuickButton` + `quickMenu`, accounts and funds rows. Accounts:
  `MainViewModel.AtmSettings` on `saveAccount`. Web: `atmFields` on its Accounts tab (row menus come with Stage 4).

- Stage 2 (done): `Core.moneyMoves` (both engines) gives cash taken out `cash: true` with `src`, `items` ([{name, amount, key,
  ym}] of the bills it's for), `arrive`, `latest`, `atm` (rounded to the ATM's bills), `extra`, `fromHand` (cash on hand
  from an earlier trip that month), `notes`, `teller`, `coins` (online banks), and over the limit `part` / `parts` / `ask`
  / `overBy`, or `split` (the second account's name) with a move into that account and its own cash item. Cash for bills
  is done once those bills are ticked paid. `Core.bAmt` rounds a cash bill up when `cashRound` is true. `Core.atmOf`,
  `Core.atmParts`. `Core.todo(st, today, lastCheck)` → `{items, incoming}`; items are upcoming bills and moves plus
  `report` (ei, key, ym, forYm), `tax` (year), `lock` (ym) and `check` (days). The web's `reportCfg` / `eiReportDay`
  moved into its engine. Parity: households `cash-online-bank`, `cash-tellers`; `todo` and `todoChecked` per household.
  Both versions' rows say "Take out $X cash" for now; the real To do comes in Stage 4.

- Today's To do is built in `MainViewModel.buildToday` from `Core.upcoming`; reminder cards come from `Views.reminders`
  and EI report days from `Views.eiReportDay`. Stage 2 moves the list into `Views.todo` (both engines).
- Cash today: `Core.moneyMoves` (Engine.kt, "cash bills: withdraw from the account that pays them"; web index.html
  the same block). Account types in `Accounts.TYPES` (`cash` = "Cash on hand"). Bill methods in `Labels.METHODS`.
- Rows: `EventRow` / `spokenRow` (ui/screens/Rows.kt), `ExpenseRow` (SpendingScreen.kt), `DoneButton`, `enter()`,
  `rememberCountUp`, `GrowBar`, `ProgressRing`, `StatusBadge` pulse, `AccentLine` (ui/AppUi.kt, ui/Components.kt).
- Navigation: `Tab`, `Sheet`, the top bar and floating button in `LoonieMateApp` (ui/AppUi.kt); Settings cards in
  ui/screens/MoreScreen.kt; Food in ui/sheets/FoodSheet.kt.
- New data, all ignored by older copies: on accounts `atmLimit`, `atmNotes` (20, 10 or 5), `tellers` (false for an
  online bank), `atmOver` ("days" or "split") and `atmAlso` (the second account); on bills `cashRound` (true / false,
  absent = not asked yet). An expense's edits use the same fields and path. Everything else is screens.
