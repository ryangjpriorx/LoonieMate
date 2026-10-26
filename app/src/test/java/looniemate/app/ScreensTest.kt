package looniemate.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
    private val app get() = rule.activity.application as LoonieApp
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
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[MainViewModel::class.java]
        File(out, "debug.txt").appendText("$name app=${System.identityHashCode(app)} vmApp=${System.identityHashCode(vm.getApplication<LoonieApp>())} " +
            "testPrefs=${System.identityHashCode(app.prefs)} ${app.prefs.look.value} vmPrefs=${System.identityHashCode(vm.prefs)} ${vm.prefs.look.value} activity=${System.identityHashCode(rule.activity)}\n")
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
        rule.runOnUiThread {
            app.repo.replaceAll(LinkedHashMap(docs))
            app.prefs.setLook(Prefs.Look(if (dark) "dark" else "light", false, "iris", effects))
        }
        settle()
    }

    private fun tab(name: String) { rule.onNodeWithTag("tab-$name").performClick(); settle() }
    private fun back() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; settle() }

    private fun tour(theme: String, dark: Boolean) {
        seed(dark, effects = false)
        for (t in listOf("Today", "Month", "Bills", "Spending", "More")) { tab(t); shot("$theme-$t") }
        tab("Today"); rule.onNodeWithTag("fab").performClick(); shot("$theme-sheet-log", sheet = true); back()
        tab("Bills"); rule.onNodeWithTag("fab").performClick(); shot("$theme-sheet-bill", sheet = true); back()
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
