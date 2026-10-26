package looniemate.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import looniemate.app.ui.theme.DarkExtra
import looniemate.app.ui.theme.LightExtra
import looniemate.app.ui.theme.PALETTES
import looniemate.app.ui.theme.paletteScheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text has to be readable: at least 4.5:1 against everything it can sit on, in every colour theme, light and dark.
 * (WCAG AA for normal text.) Fails the build if a colour change drops below. docs/plans/accessibility.md, stage 1.
 */
class ContrastTest {
    private fun ratio(a: Color, b: Color): Double { val x = a.luminance() + 0.05; val y = b.luminance() + 0.05; return (maxOf(x, y) / minOf(x, y)).toDouble() }

    @Test fun everyThemeHasReadableText() {
        val problems = mutableListOf<String>()
        fun check(what: String, fg: Color, bg: Color) { val r = ratio(fg, bg); if (r < 4.5) problems.add("$what: %.2f".format(r)) }
        for (p in PALETTES) for (dark in listOf(false, true)) {
            val s = paletteScheme(p.key, dark); val x = if (dark) DarkExtra else LightExtra; val mode = "${p.key} ${if (dark) "dark" else "light"}"
            val surfaces = mapOf("surface" to s.surface, "low" to s.surfaceContainerLow, "container" to s.surfaceContainer, "high" to s.surfaceContainerHigh, "highest" to s.surfaceContainerHighest)
            for ((n, bg) in surfaces) {
                check("$mode text on $n", s.onSurface, bg); check("$mode muted text on $n", s.onSurfaceVariant, bg)
                check("$mode accent on $n", s.primary, bg); check("$mode second accent on $n", s.secondary, bg)
            }
            check("$mode accent on its container", s.onPrimaryContainer, s.primaryContainer)
            check("$mode second accent on its container", s.onSecondaryContainer, s.secondaryContainer)
            check("$mode button text", s.onPrimary, s.primary)
            for (t in listOf("good", "warn", "bad", "adults", "baby", "pets", "house", "sky")) { val (fg, tint) = x.tone(t)
                for ((n, bg) in surfaces) check("$mode $t on $n", fg, bg)
                check("$mode $t on its tint", fg, tint)
                /* the top of a section card fades from the card colour toward the tint */
                val top = lerp(s.surfaceContainerLow, tint, 0.55f)
                check("$mode $t on a $t card", fg, top); check("$mode text on a $t card", s.onSurface, top)
            }
        }
        assertTrue("Text below 4.5:1 contrast:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
