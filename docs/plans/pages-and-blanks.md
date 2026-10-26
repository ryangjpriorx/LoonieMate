# Pages instead of slide-up sheets, and blank means $0 (Android first, then web)

The plan agreed on 2026-10-26. Every form in the app opened in a slide-up sheet (Material `ModalBottomSheet`). A swipe
down, or a tap above it, closed it and threw away everything typed, which happened often during testing and is worst for
people with shaky hands or who struggle with phones. Separately, steps that ask for several amounts (like the account
balances in getting on track) made people type 0 into every empty field.
**Comes before docs/plans/personal-expenses.md.** Work in small stages; each is committed, built and tested on GitHub
before the next.

## Decisions

- **Every sheet becomes a full-screen page**, quick Log spending included: one way of working everywhere is easiest.
  - Nothing closes it by accident: no swipe-to-close, no tap-outside.
  - A top bar with a close (✕) button and the title; multi-step flows show "Step 2 of 5" under it.
  - The main buttons (Save / Next) stay where they are in the form; the page scrolls and makes room for the keyboard.
- **Leaving takes a deliberate choice.**
  - Android's back gesture or ✕: in a multi-step flow, back goes to the previous step first.
  - If anything has been typed or changed on the page, it asks **"Leave without saving?"** with **Keep going** and
    **Leave**. Nothing typed → it just closes.
  - What's typed survives switching apps or rotating the phone (`rememberSaveable` for the page and its fields);
    getting on track reopens on the step it was on.
- **Blank means $0** wherever several amounts are asked for at once and $0 is a normal answer (account balances in
  getting on track and the balance check, food so far, and similar). If any were left blank, **one** box for all of
  them: "You left 3 amounts blank. Count them as $0.00?" → **Count as $0.00** / **Go back**. Never one box per field.
  Fields where $0 makes no sense (a bill's amount, an income's amount) still need a number.
- **Web version:** dialogs no longer close when you click outside them (the close button and Esc still work, and Esc
  asks first if something was typed).

## Stages

- [x] **Stage 0**: this plan.
- [x] **Stage 1**: the page container (`ui/sheets/Sheets.kt`): full-screen, top bar with ✕, back handling, the
  "Leave without saving?" check, saved state across rotation; every sheet moved onto it.
- [ ] **Stage 2**: blank-means-$0 with the single confirmation box (getting on track balances, balance check, food so
  far); getting on track remembers its step.
- [ ] **Stage 3**: web dialogs stop closing on an outside click. Then release.

## Notes for picking up

- Sheets are routed in `ui/sheets/Sheets.kt` (`Sheets(sheet, vm, onDismiss, open)`), opened from `ui/AppUi.kt`
  (`sealed class Sheet`). Each sheet composable gets `close`.
- Stage 1 (done): `ui/sheets/Page.kt`: `Page(onClose)` (full-screen Scaffold drawn over the app inside the same window,
  top bar with ✕, `BackHandler`), `PageState` (`title`, `edited`, `mark()`, `saved()`) through `LocalPage`,
  `LeaveDialog`, `SheetTitle` (now sets the top bar title), `marking(onChange)`. The shared fields in `Fields.kt`
  (`MoneyField`, `TextFieldRow`, `DateField`, `SelectField`) and the raw fields in `Sheets.kt` mark the page as edited.
  `Sheets()` keys each page by its `Sheet` so chained forms start fresh. `CatchUpSheet` calls `saved()` on each step
  and handles back as "previous step". `AppUi` hides the app from screen readers while a page is open and fades the
  page in (instant with Less motion). Rotation: the activity handles orientation and size changes itself
  (`android:configChanges`), so nothing is rebuilt. `ScreensTest` checks the "Leave without saving?" flow and that
  back steps back in getting on track.
