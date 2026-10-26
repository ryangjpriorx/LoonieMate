package looniemate.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import looniemate.engine.*

/** Colours with a meaning (good, heads-up, problem) and the colours for each group of bills. */
@Immutable
data class Extra(
    val good: Color, val goodBg: Color, val warn: Color, val warnBg: Color, val bad: Color, val badBg: Color,
    val adults: Color, val adultsBg: Color, val baby: Color, val babyBg: Color, val pets: Color, val petsBg: Color,
    val house: Color, val houseBg: Color, val sky: Color, val skyBg: Color,
) {
    fun tone(t: String): Pair<Color, Color> = when (t) { "good" -> good to goodBg; "warn" -> warn to warnBg; "bad" -> bad to badBg
        "baby" -> baby to babyBg; "pets" -> pets to petsBg; "adults" -> adults to adultsBg; "house", "gold" -> house to houseBg; "sky" -> sky to skyBg; else -> Color.Unspecified to Color.Unspecified }
    fun group(g: String): Color = when (g) { "baby" -> baby; "pets" -> pets; "household" -> house; else -> adults }
}

/* light text colours are dark enough for 4.5:1 on every card colour (checked by ContrastTest) */
internal val LightExtra = Extra(
    Color(0xFF1C7240), Color(0xFFDDF1E4), Color(0xFF8C5900), Color(0xFFFBEFD6), Color(0xFFB33924), Color(0xFFFBE2DC),
    Color(0xFF2E65A5), Color(0xFFE1ECF8), Color(0xFFAB3A6A), Color(0xFFF8E3EC), Color(0xFF0D7067), Color(0xFFDDF2EF),
    Color(0xFF6B5A2E), Color(0xFFF1EBDC), Color(0xFF1A6999), Color(0xFFDDEFFA))
internal val DarkExtra = Extra(
    Color(0xFF5FD08F), Color(0xFF14301F), Color(0xFFF0B95A), Color(0xFF352812), Color(0xFFFF8A70), Color(0xFF3A1A14),
    Color(0xFF86B4E8), Color(0xFF1B2A3D), Color(0xFFF09AC0), Color(0xFF3A1E2C), Color(0xFF5CCFC2), Color(0xFF12312E),
    Color(0xFFD8C08A), Color(0xFF2E2817), Color(0xFF7CC4F0), Color(0xFF14293A))

val LocalExtra = staticCompositionLocalOf { LightExtra }
/** Whether the extra visuals (animation and motion) are on. */
val LocalFx = staticCompositionLocalOf { true }

class Palette(val key: String, val name: String, val l: List<Long>, val d: List<Long>)
/** The same colour themes as the web version: accent, second accent, soft background. */
val PALETTES = listOf(
    Palette("iris", "Iris", listOf(0xFF4740C4, 0xFF873EC4, 0xFFE6E4FB), listOf(0xFF9C97FF, 0xFFD69CFF, 0xFF26244A)),
    Palette("ocean", "Ocean", listOf(0xFF0B6B94, 0xFF0F7068, 0xFFDCEEF6), listOf(0xFF6CC4EA, 0xFF5FD3C4, 0xFF112A3A)),
    Palette("forest", "Forest", listOf(0xFF2C7136, 0xFF516C1B, 0xFFE1F0DD), listOf(0xFF7FD38A, 0xFFB6D96B, 0xFF17301B)),
    Palette("sunset", "Sunset", listOf(0xFFB23C0B, 0xFFAF3465, 0xFFFCE6D9), listOf(0xFFFF9A66, 0xFFFF8FB7, 0xFF3A2016)),
    Palette("berry", "Berry", listOf(0xFFAD2D66, 0xFF7A3FC0, 0xFFF8E0EB), listOf(0xFFF28AB8, 0xFFC69CFF, 0xFF3A1A2A)),
    Palette("slate", "Slate", listOf(0xFF3F5770, 0xFF566578, 0xFFE2E8EF), listOf(0xFF9DB4CC, 0xFFB7C4D4, 0xFF1E2833)),
)

internal fun paletteScheme(key: String, dark: Boolean): ColorScheme {
    val p = PALETTES.find { it.key == key } ?: PALETTES[0]
    return if (!dark) {
        val (a, a2, bg) = p.l.map { Color(it) }
        lightColorScheme(primary = a, onPrimary = Color.White, primaryContainer = bg, onPrimaryContainer = a, secondary = a2, onSecondary = Color.White,
            secondaryContainer = bg, onSecondaryContainer = a2, tertiary = a2, tertiaryContainer = bg, onTertiaryContainer = a2,
            background = Color(0xFFF7F8FC), surface = Color(0xFFF7F8FC), surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F3F8),
            surfaceContainer = Color(0xFFECEEF4), surfaceContainerHigh = Color(0xFFE6E8EF), surfaceContainerHighest = Color(0xFFE0E3EB),
            surfaceVariant = Color(0xFFE4E9F1), onSurfaceVariant = Color(0xFF566275), outline = Color(0xFFB8C0CD), outlineVariant = Color(0xFFD3DAE5))
    } else {
        val (a, a2, bg) = p.d.map { Color(it) }
        darkColorScheme(primary = a, onPrimary = Color(0xFF121029), primaryContainer = bg, onPrimaryContainer = a, secondary = a2, onSecondary = Color(0xFF121029),
            secondaryContainer = bg, onSecondaryContainer = a2, tertiary = a2, tertiaryContainer = bg, onTertiaryContainer = a2,
            background = Color(0xFF10131A), surface = Color(0xFF10131A), surfaceContainerLowest = Color(0xFF0B0E14), surfaceContainerLow = Color(0xFF151A23),
            surfaceContainer = Color(0xFF181E28), surfaceContainerHigh = Color(0xFF1F2632), surfaceContainerHighest = Color(0xFF27303D),
            surfaceVariant = Color(0xFF202836), onSurfaceVariant = Color(0xFF9BA7B8), outline = Color(0xFF475266), outlineVariant = Color(0xFF2A3342))
    }
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp))

private fun appTypography(): Typography {
    val t = Typography()
    return t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Money and numbers use same-width digits so columns line up. */
val Numbers = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun LoonieTheme(themeMode: String, dynamicColor: Boolean, palette: String, effects: Boolean, content: @Composable () -> Unit) {
    val dark = when (themeMode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx) } else paletteScheme(palette, dark)
    CompositionLocalProvider(LocalExtra provides if (dark) DarkExtra else LightExtra, LocalFx provides effects) {
        MaterialTheme(colorScheme = scheme, typography = appTypography(), shapes = AppShapes, content = content)
    }
}
