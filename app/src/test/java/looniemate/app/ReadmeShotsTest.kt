package looniemate.app

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import looniemate.app.data.Prefs
import looniemate.engine.Core
import looniemate.engine.Docs
import looniemate.engine.J
import looniemate.engine.Json
import looniemate.engine.addDays
import looniemate.engine.arr
import looniemate.engine.copy
import looniemate.engine.s
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * The pictures on the README: phone-sized, light and dark, with a tidy made-up household
 * (past bills paid, backup done). Saved as readme-*.png next to the other screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h892dp-xhdpi")
class ReadmeShotsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val out = File(System.getProperty("screens") ?: "build/screens").apply { mkdirs() }

    private fun settle() { repeat(12) { Thread.sleep(60); rule.waitForIdle() } }

    private fun shot(name: String) {
        settle()
        val v = rule.activity.window.decorView
        val b = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { v.draw(android.graphics.Canvas(b)) }
        File(out, "readme-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The test household, tidied up the way a family using the app day to day would have it. */
    private fun tidyDocs(): LinkedHashMap<String, J> {
        val fx = Json.parse(File(System.getProperty("fixtures")!!).readText())
        val docs = LinkedHashMap<String, J>(); (fx["scenarios"].arr()[0]["docs"] as J.O).map.forEach { (k, v) -> docs[k] = v.copy() }
        val s = docs["settings/main"] as J.O
        s["householdName"] = J.S("Alex & Sam"); s["foodPct"] = J.N(40.0)
        docs.values.forEach { (it as? J.O)?.set("example", null) }
        val today = LocalDate.now().toString(); val st = Docs.toStore(docs, today)
        fun month(ym: String) = (docs.getOrPut("months/$ym") { J.O() } as J.O)
        for (e in Core.billEvents(st, s.s("trackFrom"), addDays(today, -1))) {
            val m = month(e.s("ym")); val paid = (m["paid"] as? J.O) ?: J.O().also { m["paid"] = it }; paid[e.s("key")] = J.B(true) }
        for (mv in Core.moneyMoves(st, addDays(today, -12), addDays(today, -1))) {
            val m = month(mv.s("ym")); val moves = (m["moves"] as? J.O) ?: J.O().also { m["moves"] = it }; moves[mv.s("key")] = J.B(true) }
        return docs
    }

    private fun tour(theme: String, dark: Boolean) {
        val vm = androidx.lifecycle.ViewModelProvider(rule.activity)[MainViewModel::class.java]
        val docs = tidyDocs()
        rule.runOnUiThread {
            vm.repo.replaceAll(docs)
            vm.prefs.setLook(Prefs.Look(if (dark) "dark" else "light", false, "iris", false))
            vm.prefs.lastBackup = Instant.now().toString()
            /* the README shows the budget itself, not the one-time setup cards */
            vm.prefs.onboarded = true; vm.prefs.notifAsked = true; vm.prefs.notifSnoozeUntil = Long.MAX_VALUE
        }
        settle()
        for ((tab, name) in listOf("Today" to "today", "Month" to "month", "Bills" to "bills", "More" to "settings")) {
            rule.onNodeWithTag("tab-$tab").performClick(); shot("$theme-$name")
        }
    }

    @Test fun light() = tour("light", false)
    @Test fun dark() = tour("dark", true)
}
