package looniemate.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithText
import looniemate.app.data.Prefs
import looniemate.engine.J
import looniemate.engine.Json
import looniemate.engine.arr
import looniemate.engine.s
import looniemate.engine.strList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws every screen with a made-up household (the same one the engine checks use) and saves
 * a picture of each, so the layout can be reviewed without a phone. Any crash fails the build.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h2000dp-xhdpi")
class ScreensTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val out = File(System.getProperty("screens") ?: "build/screens").apply { mkdirs() }
    private val problems = mutableListOf<String>()

    private fun settle() { repeat(12) { Thread.sleep(60); rule.waitForIdle() } }

    /** Every window on screen (the app, plus a dialog when one is open). */
    private fun windows(): List<android.view.View> {
        val g = Class.forName("android.view.WindowManagerGlobal"); val inst = g.getMethod("getInstance").invoke(null)
        val f = g.getDeclaredField("mViews"); f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return (f.get(inst) as List<android.view.View>).filter { it.isShown && it.width > 0 }
    }

    /** [dialog]: draw the pop-up window opened on top of the app instead (forms are pages inside the app's own window). */
    private fun shot(name: String, sheet: Boolean = false, dialog: Boolean = false) {
        settle()
        try {
            if (sheet && rule.onAllNodesWithTag("page").fetchSemanticsNodes().isEmpty()) error("no page open")
            /* this test's own window, or the dialog window opened on top of it (earlier tests' windows can linger) */
            val decor = rule.activity.window.decorView; val all = windows(); val mine = all.indexOf(decor)
            val v = if (dialog) all.drop(mine + 1).lastOrNull() ?: error("no dialog window") else decor
            val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
            rule.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
            File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } catch (e: Throwable) { problems.add("$name: ${e.javaClass.simpleName}: ${e.message?.take(300)}") }
    }

    private fun seed(dark: Boolean, effects: Boolean) {
        val fx = Json.parse(File(System.getProperty("fixtures")!!).readText())
        val docs = (fx["scenarios"].arr()[0]["docs"] as J.O).map
        /* go through the screen's own view model: in this simulated phone the view model can outlive
           a test's application object, so the app's repo and prefs may not be the ones on screen */
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[MainViewModel::class.java]
        rule.runOnUiThread {
            vm.repo.replaceAll(LinkedHashMap(docs))
            vm.prefs.setLook(Prefs.Look(if (dark) "dark" else "light", false, "iris", effects))
            /* phone-only settings can carry over from the README test; show the one-time cards here */
            vm.prefs.onboarded = false; vm.prefs.notifAsked = false; vm.prefs.notifSnoozeUntil = 0; vm.catchUpAt = null; vm.prefs.a11yAsked = true
        }
        settle()
        /* a fresh phone opens "Make LoonieMate easier to use?" before the budget loads; it's a page over the tabs now */
        closePage()
    }

    /* Settings opens from the gear in the top bar, which isn't there while Settings is already open */
    private fun tab(name: String) { if (rule.onAllNodesWithTag("tab-$name").fetchSemanticsNodes().isNotEmpty()) { rule.onNodeWithTag("tab-$name").performClick(); settle() } }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle() }
    /** Closes the open page with its ✕, answering "Leave" if it asks. */
    private fun closePage() {
        if (rule.onAllNodesWithTag("page-close").fetchSemanticsNodes().isEmpty()) return
        rule.onNodeWithTag("page-close").performClick(); settle()
        if (rule.onAllNodesWithTag("leave-go").fetchSemanticsNodes().isNotEmpty()) { rule.onNodeWithTag("leave-go").performClick(); settle() }
        if (rule.onAllNodesWithTag("page").fetchSemanticsNodes().isNotEmpty()) problems.add("a page didn't close")
    }

    private fun tour(theme: String, dark: Boolean) {
        seed(dark, effects = false)
        for (t in listOf("Today", "Month", "Bills", "Spending", "Savings", "More")) { tab(t); shot("$theme-$t") }
        tab("Today"); rule.onNodeWithTag("fab").performClick(); shot("$theme-sheet-log", sheet = true); closePage()
        /* typing then going back asks first; Keep going stays on the page with what was typed, Leave closes it */
        tab("Today"); rule.onNodeWithTag("fab").performClick(); settle()
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("12.50"); back()
        if (rule.onAllNodesWithTag("leave-stay").fetchSemanticsNodes().isEmpty()) problems.add("$theme: leaving with typing didn't ask first")
        else { shot("$theme-leave-ask", dialog = true); rule.onNodeWithTag("leave-stay").performClick(); settle()
            if (rule.onAllNodesWithText("12.50").fetchSemanticsNodes().isEmpty()) problems.add("$theme: Keep going lost what was typed") }
        closePage()
        /* nothing typed: back just closes */
        rule.onNodeWithTag("fab").performClick(); settle(); back()
        if (rule.onAllNodesWithTag("page").fetchSemanticsNodes().isNotEmpty()) { problems.add("$theme: an untouched page didn't close on back"); closePage() }
        tab("Bills"); rule.onNodeWithTag("fab").performClick(); shot("$theme-sheet-bill", sheet = true); closePage()
        tab("More"); rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("account-0")); settle()
        rule.onNodeWithTag("account-0").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); shot("$theme-sheet-account", sheet = true); closePage()
        tab("More"); rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("food-settings")); settle()
        rule.onNodeWithTag("food-settings").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); shot("$theme-sheet-food", sheet = true); closePage()
        /* Personal Expenses: the card on the Spending tab (logging one item with a tap), the list page, and the card on Today */
        tab("Spending"); rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("self-edit")); settle()
        shot("$theme-Spending-self")
        /* the Log buttons are only on the current month (they log for today); the test household's budget starts next month */
        if (rule.onAllNodesWithTag("self-log-0").fetchSemanticsNodes().isNotEmpty()) { rule.onNodeWithTag("self-log-0").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle() }
        if (rule.onAllNodesWithText("Coffee").fetchSemanticsNodes().isEmpty()) problems.add("$theme: the Personal Expenses list isn't on the Spending tab")
        rule.onNodeWithTag("self-edit").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); shot("$theme-sheet-self", sheet = true); closePage()
        tab("Today"); if (runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("self-open")) }.isSuccess) shot("$theme-Today-self")
        else problems.add("$theme: no Personal Expenses card on Today")
        /* these sit further down a scrolling list, so they're drawn only when they're on screen */
        tab("More")
        if (runCatching { rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("test-reminder")) }.isSuccess) shot("$theme-More-reminders")
        tab("Today"); tab("More")
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("sync-create")); settle(); shot("$theme-More-sync")
        tab("Today"); tab("More"); if (runCatching { rule.onNodeWithTag("add-income").performScrollTo().performClick() }.isSuccess) { shot("$theme-sheet-income", sheet = true); closePage() }
        tab("Month")
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("month-income")); settle()
        rule.onNodeWithTag("month-income").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()
        if (rule.onAllNodesWithText("Lock this month").fetchSemanticsNodes().isEmpty()) problems.add("$theme: this month's income sheet didn't open")
        shot("$theme-sheet-month-income", sheet = true); closePage()
        /* getting on track: fill in balances, step through to Where you stand */
        tab("More")
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("get-on-track")); settle()
        rule.onNodeWithTag("get-on-track").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle(); shot("$theme-catchup-1", sheet = true)
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNodes().indices.forEach { i -> rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[i].performScrollTo().performTextInput("${200 + i * 150}") }
        repeat(4) { k -> rule.onNodeWithTag("cu-next").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle(); shot("$theme-catchup-${k + 2}", sheet = true) }
        /* step 5 is Personal Expenses, saved with its own button */
        rule.onNodeWithTag("self-save").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle(); shot("$theme-catchup-6", sheet = true)
        /* back goes a step back inside getting on track, not out of it */
        back(); if (rule.onAllNodesWithTag("self-save").fetchSemanticsNodes().isEmpty()) problems.add("$theme: back didn't go to the previous step")
        closePage()
        /* balance check: only the first account typed in; the rest count as $0 after one question */
        tab("More"); rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("checkin-more")); settle()
        rule.onNodeWithTag("checkin-more").performClick(); settle()
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performScrollTo().performTextInput("100")
        rule.onNodeWithTag("compare").performScrollTo().performClick(); settle()
        if (rule.onAllNodesWithTag("blank-zero").fetchSemanticsNodes().isEmpty()) problems.add("$theme: blank balances didn't ask once to count them as $0")
        else { shot("$theme-blank-zero", dialog = true); rule.onNodeWithTag("blank-zero").performClick(); settle()
            if (rule.onAllNodesWithTag("compare").fetchSemanticsNodes().isNotEmpty()) problems.add("$theme: counting blanks as $0 didn't go on to the result") }
        shot("$theme-sheet-checkin", sheet = true); closePage()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun light() = tour("light", false)
    @Test fun dark() = tour("dark", true)

    /** Settings → Household and province: province, people and the plan dates can be changed; quick buttons can be removed (W1–W4). */
    @Test fun householdProvinceAndQuickButtons() {
        seed(dark = false, effects = false)
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[MainViewModel::class.java]
        tab("More"); rule.onNodeWithTag("household-open").performScrollTo().performClick(); settle(); shot("sheet-household", sheet = true)
        rule.onNodeWithTag("hh-people").performTextClearance(); rule.onNodeWithTag("hh-people").performTextInput("Sam, Alex, Sam")
        rule.onNodeWithText("Ontario").performClick(); settle(); rule.onNodeWithText("Quebec").performClick(); settle()
        rule.onNodeWithTag("hh-save").performScrollTo().performClick(); settle()
        val s = vm.settings()
        org.junit.Assert.assertEquals("QC", s.s("province"))
        org.junit.Assert.assertEquals(listOf("Sam", "Alex"), s.strList("people"))
        /* the people show up as "Who paid" in Log spending */
        tab("Today"); rule.onNodeWithTag("fab").performClick(); settle()
        assertTrue("Who paid lists the people", rule.onAllNodesWithText("Alex").fetchSemanticsNodes().isNotEmpty())
        closePage()
        /* the test household has one saved quick button; it can be removed */
        tab("More"); rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("quick-del-0")); settle()
        shot("More-quick")
        rule.onNodeWithTag("quick-del-0").performClick(); settle()
        assertTrue("the quick button is gone", vm.settings()["quick"].arr().isEmpty())
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Settings → Reset LoonieMate only works after typing RESET, then everything is gone and first-time setup starts again. */
    @Test fun resetNeedsTheWordAndStartsOver() {
        seed(dark = false, effects = false)
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[MainViewModel::class.java]
        tab("More"); rule.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[0].performScrollToNode(androidx.compose.ui.test.hasTestTag("reset-open")); settle()
        rule.onNodeWithTag("reset-open").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick); settle()
        rule.onNodeWithTag("reset-type").performTextInput("reset"); settle()
        rule.onNodeWithTag("reset-go").assertIsNotEnabled()
        rule.onNodeWithTag("reset-type").performTextClearance(); rule.onNodeWithTag("reset-type").performTextInput("RESET"); settle(); shot("reset", sheet = true)
        rule.onNodeWithTag("reset-go").assertIsEnabled().performScrollTo().performClick(); settle()
        assertTrue("the budget is empty", vm.repo.docs.value.isEmpty())
        assertTrue("first-time setup starts again", rule.onAllNodesWithTag("a11y-continue").fetchSemanticsNodes().isNotEmpty())
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Android closes LoonieMate while a form is half filled (say, while checking a banking app): it comes back as it was (S1). */
    @Test fun aFormSurvivesBeingRebuilt() {
        seed(dark = false, effects = false)
        rule.onNodeWithTag("fab").performClick(); settle()
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[0].performTextInput("12.50"); settle()
        rule.activityRule.scenario.recreate(); settle()
        assertTrue("the page is still open", rule.onAllNodesWithTag("page").fetchSemanticsNodes().isNotEmpty())
        assertTrue("what was typed is still there", rule.onAllNodesWithText("12.50").fetchSemanticsNodes().isNotEmpty())
        back(); assertTrue("it still asks before leaving", rule.onAllNodesWithTag("leave-stay").fetchSemanticsNodes().isNotEmpty())
    }

    /** At Android's largest text size every tab still draws (docs/plans/accessibility.md, stage 1). */
    @Test fun largestText() {
        seed(dark = false, effects = false)
        org.robolectric.RuntimeEnvironment.setFontScale(2f)
        try {
            rule.activityRule.scenario.recreate(); settle()
            for (t in listOf("Today", "Month", "Bills", "Spending", "Savings", "More")) { tab(t); shot("big-$t") }
        } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Larger text, bold text and the easier-to-read font together still draw (docs/plans/accessibility.md, stage 3). */
    @Test fun readableText() {
        seed(dark = false, effects = false)
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[MainViewModel::class.java]
        rule.runOnUiThread { vm.prefs.setAccess(Prefs.Access(textSize = 2, bold = true, font = true)) }
        try { settle(); for (t in listOf("Today", "Savings", "More")) { tab(t); shot("readable-$t") }
            rule.runOnUiThread { vm.prefs.setAccess(Prefs.Access(contrast = true, colorBlind = true)) }; settle()
            for (t in listOf("Today", "Month")) { tab(t); shot("contrast-cb-$t") } }
        finally { rule.runOnUiThread { vm.prefs.setAccess(Prefs.Access()) } }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** With Visual effects on, the animations run without crashing and settle into place. */
    @Test fun effects() {
        rule.mainClock.autoAdvance = false
        seed(dark = false, effects = true)
        rule.mainClock.advanceTimeBy(2500)
        shot("fx-Today")
        rule.onNodeWithTag("tab-Month").performClick(); rule.mainClock.advanceTimeBy(2500); shot("fx-Month")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
