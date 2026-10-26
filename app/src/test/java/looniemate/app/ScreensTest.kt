package looniemate.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import looniemate.app.data.Prefs
import looniemate.engine.J
import looniemate.engine.Json
import looniemate.engine.arr
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

    /** Every window on screen (the app, plus a bottom sheet when one is open). */
    private fun windows(): List<android.view.View> {
        val g = Class.forName("android.view.WindowManagerGlobal"); val inst = g.getMethod("getInstance").invoke(null)
        val f = g.getDeclaredField("mViews"); f.isAccessible = true
        @Suppress("UNCHECKED_CAST") return (f.get(inst) as List<android.view.View>).filter { it.isShown && it.width > 0 }
    }

    private fun shot(name: String, sheet: Boolean = false) {
        settle()
        try {
            /* this test's own window, or the sheet window opened on top of it (earlier tests' windows can linger) */
            val decor = rule.activity.window.decorView; val all = windows(); val mine = all.indexOf(decor)
            val v = if (sheet) all.drop(mine + 1).lastOrNull() ?: error("no sheet window") else decor
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
        }
        settle()
    }

    /* Settings opens from the gear in the top bar, which isn't there while Settings is already open */
    private fun tab(name: String) { if (rule.onAllNodesWithTag("tab-$name").fetchSemanticsNodes().isNotEmpty()) { rule.onNodeWithTag("tab-$name").performClick(); settle() } }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle() }

    private fun tour(theme: String, dark: Boolean) {
        seed(dark, effects = false)
        for (t in listOf("Today", "Month", "Bills", "Spending", "Savings", "More")) { tab(t); shot("$theme-$t") }
        tab("Today"); rule.onNodeWithTag("fab").performClick(); shot("$theme-sheet-log", sheet = true); back()
        tab("Bills"); rule.onNodeWithTag("fab").performClick(); shot("$theme-sheet-bill", sheet = true); back()
        tab("More"); rule.onNodeWithTag("account-0").performClick(); shot("$theme-sheet-account", sheet = true); back()
        tab("More"); rule.onNodeWithTag("food-settings").performClick(); shot("$theme-sheet-food", sheet = true); back()
        tab("More"); rule.onNodeWithTag("checkin-more").performClick(); settle()
        rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction()).fetchSemanticsNodes().indices.forEach { i -> rule.onAllNodes(androidx.compose.ui.test.hasSetTextAction())[i].performScrollTo().performTextInput("${100 + i * 250}") }
        rule.onNodeWithTag("compare").performScrollTo().performClick(); shot("$theme-sheet-checkin", sheet = true); back()
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun light() = tour("light", false)
    @Test fun dark() = tour("dark", true)

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
