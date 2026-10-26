# Accessibility (Android first, then web)

The plan agreed on 2026-10-26. Make LoonieMate comfortable for people who use large text, a screen reader, colour-blind
vision, or have trouble with small buttons or quick pop-ups. Some fixes are simply always on; the rest are switches the
person is offered during first-time setup and can change any time in Settings.
**Queued after docs/plans/getting-on-track.md** (that plan creates the first-time setup this one adds a step to).
Work in small stages; each is committed, built and tested on GitHub before the next.
**To pick up again: find the first unticked stage below and start there.**

## Decisions

- **Two kinds of work.** Things nobody should have to switch on are always on (screen-reader labels, tap target sizes,
  status shown with more than colour). Everything else is a switch.
- **Switches:** Bigger text, Bold text, Easier-to-read font, Stronger contrast, Colour-blind friendly colours, Less motion,
  More time, Bigger buttons. (A "Simple view" was considered and left out for now.)
- **Settings:** a new **Accessibility** card right below Appearance, marked "Only this phone".
- **Setup:** the first screen of first-time setup, before accounts. Never asks *why* someone needs it; stores only the
  switches.
- **This phone only:** saved in `Prefs` like Appearance, not in the budget, not in backups, not synced later by Google
  Drive (each phone or browser keeps its own).
- **Web version:** the same switches, saved in that browser only, and it also follows the browser's own reduced-motion
  and contrast settings.

## Always on (no switch)

- **Screen readers (TalkBack):** each row is read as one sentence ("Bell, $95.00, due Wednesday, not paid"); Paid, Moved
  it, Pay later and Undo are offered as TalkBack actions, so nothing needs a swipe; section titles are headings; paid
  ticks say "paid" or "not paid"; ticking something off is announced. Icons that do something have a label; decorative
  ones are skipped.
- **Not just colour:** good / heads-up / problem always show an icon and a word as well as the colour; bill groups keep
  their names next to the colour dot.
- **Tap targets** at least 48 dp everywhere, including the small Paid pills and month arrows.
- **Largest font size:** every screen still works at the phone's biggest text setting (money amounts wrap instead of
  cutting off, the Today numbers stack, sheets scroll).
- **Contrast checked by a test:** fails the build if any colour theme, light or dark, has text below 4.5:1 against its
  background (status colours on their tinted backgrounds included).
- **Undo when TalkBack is on:** messages stay long enough to reach (check what Material's snackbar already does with
  the accessibility timeout; if not enough, keep it until dismissed).

## The switches

- **Bigger text**: Normal, Large, Larger, Largest (about 100 / 115 / 130 / 150 %), on top of the phone's own text size.
- **Bold text**: heavier weights throughout.
- **Easier-to-read font**: Atkinson Hyperlegible Next (free, OFL licence, made by the Braille Institute for low vision).
  Bundled, trimmed to the letters English and French need, so the app stays small.
- **Stronger contrast**: darker text and muted text, outlined cards, solid status colours instead of pale tints;
  at least 7:1 for text (the same test checks it).
- **Colour-blind friendly colours**: blue for good and orange for problems instead of green and red, amber kept for
  heads-up, bill group colours that stay distinct for red-green and blue-yellow colour blindness. Works with every theme
  and with Stronger contrast.
- **Less motion**: the current **Visual effects** switch moves here (same stored setting, `effects`, shown the other
  way round). If the phone's "remove animations" is on, motion stays off whatever the switch says, with a note saying so.
  The "suggested for this phone" note about low-end phones stays.
- **More time**: Undo and other messages stay until dismissed; nothing on screen times out on its own.
- **Bigger buttons**: tap targets grow to about 56 dp with more space between rows and buttons (shaky hands, small
  screens, using the phone one-handed).

## The setup screen

- Title "Make LoonieMate easier to use?", the switches in a list, and a live preview above them (a sample bill row with
  its status) that changes as switches are flipped.
- **Matched to the phone in advance**: large font size → Bigger text; bold text → Bold text; high-contrast text or the
  phone's contrast setting → Stronger contrast; colour correction → Colour-blind friendly colours; animations off →
  Less motion; TalkBack on → More time. Each one switched on this way says why ("Your phone uses large text").
  Confirm the exact Android settings that can be read when building (some are public APIs, some only on newer Android).
- Buttons: **Continue**, and **Skip** (keeps what was matched to the phone). Footnote: "Change these any time in
  Settings → Accessibility."
- Shown once (`Prefs.a11yAsked`); "Redo first-time setup" shows it again.
- **People already using the app** don't go through setup again: if the phone's settings suggest it would help, a
  one-time card on Today offers the same choices; otherwise the release notes mention the new card.

## Stages

- [x] **Stage 0**: this plan.
- [x] **Stage 1**: the always-on fixes: TalkBack sentences, actions and headings on Today, Month, Bills, Spending,
  Savings and the sheets; status icons and words; 48 dp targets; layouts at the largest font size; the contrast test
  for the current themes (fix anything it catches). Compose UI tests at font scale 2.0 with accessibility checks on.
- [x] **Stage 2**: storage and the Accessibility card: `Prefs` keys, an `Access` settings object passed down like
  `LocalFx` (e.g. `LocalAccess`), the card below Appearance with all switches; Less motion moved in and following the
  phone's animation setting live.
- [x] **Stage 3**: Bigger text, Bold text and the Easier-to-read font.
- [ ] **Stage 4**: Stronger contrast and Colour-blind friendly colours for every theme, light and dark, with the
  contrast test extended to 7:1 for Stronger contrast.
- [ ] **Stage 5**: More time and Bigger buttons.
- [ ] **Stage 6**: reading the phone's settings, the setup screen as the first step of first-time setup, and the
  one-time Today card for people already using the app.
- [ ] **Stage 7**: web version: the always-on fixes (labels, focus outlines, keyboard use, status words), an
  Accessibility card in its settings with the same switches, and following `prefers-reduced-motion`,
  `prefers-contrast: more` and `forced-colors`. Then release.

## Notes for picking up

- Stage 3 (done): `res/font/atkinson_{regular,medium,semibold,bold,extrabold}.ttf` (subset with fontTools to Latin,
  Latin-1, Œœ Ÿ, punctuation, €, arrows, −, •, ✓; from github.com/googlefonts/atkinson-hyperlegible-next),
  licence in `assets/licenses/AtkinsonHyperlegibleNext-OFL.txt`. `Theme.kt`: `Atkinson`, `TEXT_SCALES`
  (1, 1.15, 1.3, 1.5 × the phone's own scale via `LocalDensity`), `accessType` (font family; Bold adds 200 to every
  style's weight). Explicit `fontWeight =` in a few `Text` calls stay as written. Test: `ScreensTest.readableText`.
- Stage 2 (done): `Prefs.Access` (textSize 0–3, bold, font, contrast, colorBlind, moreTime, bigButtons; keys `a11y*`),
  `Prefs.access` flow, `Prefs.a11yAsked`, `Prefs.phoneNoMotion` (set in `MainActivity.onResume` from
  ANIMATOR_DURATION_SCALE). `LocalAccess` in `Theme.kt`; `LoonieTheme(..., access)`. `AccessibilityCard` in
  `MoreScreen.kt` below Appearance; Less motion = `!Look.effects`, forced on when the phone's animations are off.
- Stage 1 (done): `ContrastTest` (app unit test) checks every theme, light and dark, at 4.5:1 (text on all surfaces,
  status and bill-group colours on surfaces, tints and card tops, accents, button text). Light-mode colours were
  darkened to pass: in `Theme.kt` LightExtra good 1C7240, warn 8C5900, bad B33924, adults 2E65A5, baby AB3A6A,
  pets 0D7067, sky 1A6999; second accents iris 873EC4, ocean primary 0B6B94 / second 0F7068, forest 2C7136 / 516C1B,
  sunset B23C0B / AF3465, slate second 566578. **The web version's matching colours still need the same change
  (Stage 7).** TalkBack: `spokenRow` + custom actions on `EventRow` (Rows.kt) and `ExpenseRow`; headings on
  `SectionCard`, `DayHeader`, `SheetTitle`; status icons in `StatusBadge`. Large text: `bigText()` in Components.kt
  (font scale ≥ 1.5) stacks card trailing content, give-up/pay-later buttons, icon-only bottom bar, one-line top bar.
  `ScreensTest.largestText` draws every tab at font scale 2.0. Material buttons already give 48 dp touch targets; the
  M3 snackbar already lengthens itself for accessibility services. Compose's built-in accessibility checks need a newer
  ui-test than BOM 2024.12, so they weren't turned on.

- Starting point (2026-10-26): all text comes from `MaterialTheme.typography` (no fixed `sp` sizes), so it already
  scales with the phone. Colours live in `ui/theme/Theme.kt` (`PALETTES`, `LightExtra` / `DarkExtra` for status and
  bill-group colours, `LoonieTheme`). Motion is `Look.effects` / `LocalFx`, defaulted by `data/DeviceClass.kt`
  (`effectsByDefault`, `describe`). The Appearance card and `SwitchRow` are in `ui/screens/MoreScreen.kt`.
  The Undo snackbar is in `ui/AppUi.kt` (`SnackbarDuration.Short`). Only `AccountSheet` uses semantics so far.
- Bigger text: multiply `LocalDensity.fontScale` inside `LoonieTheme`. Bold: bump weights in `appTypography()`.
  Bigger buttons: `LocalMinimumInteractiveComponentSize` plus row padding. Stronger contrast and colour-blind colours:
  extra variants of the colour scheme and of `Extra`.
- Web: `web/index.html`; switches as classes on `<html>` (e.g. `a-big`, `a-bold`, `a-font`, `a-contrast`, `a-cb`,
  `a-still`, `a-time`, `a-btn`) driving the existing CSS variables; saved in `localStorage`; load the font only when
  it's switched on.
- None of this touches the budget data, so there's nothing for the parity tests or older copies to understand.
