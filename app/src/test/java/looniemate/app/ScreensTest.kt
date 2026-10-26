package looniemate.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
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

    private fun save(node: SemanticsNodeInteraction, name: String) {
        val b = node.captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun shot(name: String, sheet: Boolean = false) {
        settle()
        try { save(if (sheet) rule.onAllNodes(isRoot()).onLast() else rule.onRoot(), name) }
        catch (e: Throwable) { problems.add("$name: ${e.javaClass.simpleName}: ${e.message?.take(300)}") }
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
