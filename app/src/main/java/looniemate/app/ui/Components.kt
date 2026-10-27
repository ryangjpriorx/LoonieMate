package looniemate.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.theme.LocalFx
import looniemate.app.ui.theme.Numbers
import looniemate.engine.Fmt
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.engine.*

/* ---------- colours by meaning ---------- */

/** The icon colour and soft background for a tone name. */
@Composable
fun toneColors(tone: String): Pair<Color, Color> {
    val x = LocalExtra.current; val cs = MaterialTheme.colorScheme
    return when (tone) {
        "good", "green" -> x.good to x.goodBg; "warn", "amber" -> x.warn to x.warnBg; "bad", "red" -> x.bad to x.badBg
        "rose", "baby" -> x.baby to x.babyBg; "teal", "pets" -> x.pets to x.petsBg; "sky" -> x.sky to x.skyBg; "gold", "house" -> x.house to x.houseBg
        "adults" -> x.adults to x.adultsBg
        else -> cs.primary to cs.primaryContainer
    }
}

/* ---------- motion: quick, and only once (docs/plans/overhaul.md); none at all with Less motion ---------- */

/** The tabs already shown this session (their cards don't fade in again). */
object Seen { val tabs: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf()) }
/** Which tab (or page) the cards belong to. */
val LocalEnterKey = androidx.compose.runtime.staticCompositionLocalOf { "" }
/** Shown inside a Settings page: cards draw just their content (the page's top bar has the title). */
val LocalPlainCard = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Fades something in the first time its tab is shown in a session, a little later for each of the first five. */
@Composable
fun Modifier.enter(index: Int = 0): Modifier {
    val key = LocalEnterKey.current
    if (!LocalFx.current || key in Seen.tabs) return this
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(20L * index.coerceAtMost(5)); a.animateTo(1f, tween(180, easing = FastOutSlowInEasing)) }
    return this.graphicsLayer { alpha = a.value }
}

/** A number that moves to its new value when it changes (a tick, a log); no count-up the first time. */
@Composable
fun rememberCountUp(target: Double): Double {
    val fx = LocalFx.current
    val a = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) { if (fx) a.animateTo(target.toFloat(), tween(300, easing = FastOutSlowInEasing)) else a.snapTo(target.toFloat()) }
    return if (a.isRunning) a.value.toDouble() else target
}

@Composable
fun Money(value: Double, style: TextStyle = MaterialTheme.typography.bodyLarge, color: Color = Color.Unspecified, modifier: Modifier = Modifier, countUp: Boolean = false, weight: FontWeight? = null) {
    val v = if (countUp) rememberCountUp(value) else value
    Text(Fmt.money(v), style = style.merge(Numbers), color = color, modifier = modifier, fontWeight = weight, maxLines = 1)
}

/* ---------- building blocks ---------- */

@Composable
fun IconBadge(icon: ImageVector, tone: String = "accent", size: Dp = 36.dp) {
    val (fg, bg) = toneColors(tone)
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.56f))
    }
}

/** A card with a tinted top, an icon badge and a title, used on every screen. */
/** Row padding, roomier with Bigger buttons on (shaky hands, small screens, one-handed use). */
@Composable
fun rowPad(normal: Dp): Dp = if (looniemate.app.ui.theme.LocalAccess.current.bigButtons) normal + 8.dp else normal

/** True at large text sizes (Android's font size at 150% or more): layouts stack instead of squeezing side by side. */
@Composable
fun bigText(): Boolean = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f

@Composable
fun SectionCard(
    title: String, icon: ImageVector, tone: String = "accent", modifier: Modifier = Modifier, trailing: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null, index: Int = 0, content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalPlainCard.current) { Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (trailing != null) { Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)) }
            content() }; return }
    val (_, bg) = toneColors(tone); val surface = MaterialTheme.colorScheme.surfaceContainerLow; val big = bigText()
    /* Stronger contrast outlines every card */
    val outlined = looniemate.app.ui.theme.LocalAccess.current.contrast
    Card(modifier.fillMaxWidth().enter(index), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = surface),
        border = if (outlined) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline) else null) {
        Column(Modifier.background(Brush.verticalGradient(0f to lerp(surface, bg, 0.55f), 0.35f to surface)).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(icon, tone)
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = if (big) 4 else 2, overflow = TextOverflow.Ellipsis)
                if (!big) { if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    trailingContent?.invoke(this) }
            }
            /* at large text sizes, what sits beside the title goes underneath it */
            if (big && (trailing != null || trailingContent != null)) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                trailingContent?.invoke(this) }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun Pill(text: String, tone: String = "") {
    val (fg, bg) = if (tone.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceContainerHighest else toneColors(tone)
    Surface(color = bg, contentColor = fg, shape = CircleShape) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), maxLines = 1)
    }
}

/** The status badge: an icon, a word and a colour (it no longer pulses; the icon and word already say it). */
@Composable
fun StatusBadge(tone: String, label: String, big: Boolean = false) {
    val (fg, bg) = when (tone) { "plan" -> toneColors("accent"); else -> toneColors(tone) }
    val pulse = 1f
    Surface(color = bg, shape = CircleShape) {
        Row(Modifier.padding(horizontal = if (big) 14.dp else 10.dp, vertical = if (big) 8.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
            /* an icon as well as the colour and the word, so status never depends on colour alone */
            Icon(when (tone) { "good" -> Icons.Rounded.CheckCircle; "warn" -> Icons.Rounded.Warning; "bad" -> Icons.Rounded.Error; else -> Icons.Rounded.Info }, null,
                tint = fg, modifier = Modifier.size(if (big) 18.dp else 15.dp).graphicsLayer { alpha = pulse })
            Spacer(Modifier.width(6.dp))
            Text(label, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.Bold)
        }
    }
}

/** A rounded progress bar that moves to its new length when it changes; an optional marker shows where you should be by now. */
@Composable
fun GrowBar(fraction: Float, color: Color, modifier: Modifier = Modifier, marker: Float? = null, height: Dp = 10.dp, track: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    val fx = LocalFx.current; val f0 = fraction.coerceIn(0f, 1f); val v = remember { Animatable(f0) }
    LaunchedEffect(f0) { if (fx) v.animateTo(f0, tween(300, easing = FastOutSlowInEasing)) else v.snapTo(f0) }
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        val w = size.width * v.value
        if (w > 0f) drawRoundRect(Brush.horizontalGradient(listOf(color, lerp(color, Color.White, 0.18f))), size = Size(maxOf(w, size.height), size.height), cornerRadius = r)
        if (marker != null) { val x = size.width * marker.coerceIn(0f, 1f); drawRect(ink.copy(alpha = 0.55f), topLeft = Offset(x - 1.5f, -3f), size = Size(3f, size.height + 6f)) }
    }
}

/** A ring that fills to show how much of a debt is paid. */
@Composable
fun ProgressRing(percent: Double, modifier: Modifier = Modifier, size: Dp = 58.dp) {
    val x = LocalExtra.current; val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fx = LocalFx.current; val p0 = (percent / 100).toFloat().coerceIn(0f, 1f); val v = remember { Animatable(p0) }
    LaunchedEffect(p0) { if (fx) v.animateTo(p0, tween(300, easing = FastOutSlowInEasing)) else v.snapTo(p0) }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = 6.dp.toPx(); val d = this.size.minDimension - sw; val tl = Offset(sw / 2, sw / 2)
            drawArc(track, 0f, 360f, false, topLeft = tl, size = Size(d, d), style = Stroke(sw))
            val sweep = 360f * v.value
            if (sweep > 0f) drawArc(Brush.sweepGradient(listOf(x.good, x.pets, x.good)), -90f, sweep, false, topLeft = tl, size = Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Text("${percent.toInt()}%", style = MaterialTheme.typography.labelMedium.merge(Numbers), fontWeight = FontWeight.Bold)
    }
}

/** "Mark paid" / "Mark done" with a little buzz when it's ticked off. */
@Composable
fun DoneButton(done: Boolean, label: String, doneLabel: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val click = { if (!done) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }
    val h = if (looniemate.app.ui.theme.LocalAccess.current.bigButtons) 44.dp else 34.dp
    if (done) FilledTonalButton(onClick = click, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(h)) {
        Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(doneLabel, style = MaterialTheme.typography.labelMedium) }
    else OutlinedButton(onClick = click, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(h)) {
        Text(label, style = MaterialTheme.typography.labelMedium) }
}

@Composable
fun DayHeader(text: String, highlight: Boolean = false) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp, round: Boolean = false) {
    Box(Modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(3.dp)).background(color))
}

@Composable
fun EmptyNote(text: String) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
}

/** A label and an amount on one line; totals are bold and get a line above. */
@Composable
fun LineRow(label: String, value: String, total: Boolean = false, tone: String = "") {
    val c = if (tone.isEmpty()) Color.Unspecified else toneColors(tone).first
    if (total) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (total) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (total) FontWeight.Bold else null, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.bodyMedium.merge(Numbers), fontWeight = if (total) FontWeight.Bold else null, color = c, maxLines = 1)
    }
}

/** A small tile with a label and a big number, tinted by tone. */
@Composable
fun StatTile(label: String, value: Double, note: String, tone: String, modifier: Modifier = Modifier, hero: Boolean = false, valueTone: String = "", action: (@Composable () -> Unit)? = null) {
    val (fg, bg) = toneColors(tone); val cs = MaterialTheme.colorScheme
    val container = if (hero) cs.primary else lerp(cs.surfaceContainerLow, bg, 0.75f)
    val content = if (hero) cs.onPrimary else cs.onSurface
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = container, contentColor = content) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (hero) content.copy(alpha = 0.85f) else fg)
            Money(value, MaterialTheme.typography.headlineSmall, color = if (valueTone.isNotEmpty()) toneColors(valueTone).first else content, countUp = true, weight = FontWeight.SemiBold)
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
            action?.invoke()
        }
    }
}

@Composable
fun ClickRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = rowPad(8.dp), horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

/**
 * The month switcher, at the top of Month and Spending (the only tabs about one month). The chosen month is shared by
 * the two; "Back to this month" shows whenever another month is open.
 */
@Composable
fun MonthSwitcher(vm: looniemate.app.MainViewModel) {
    val sel by vm.ym.collectAsStateWithLifecycle()
    val home = vm.defaultYm(vm.today.value, vm.settings()); val ym = sel ?: home
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { vm.ym.value = ymAdd(ym, -1).takeIf { it != home } }, modifier = Modifier.testTag("month-prev")) {
                Icon(androidx.compose.material.icons.Icons.Rounded.ChevronLeft, "Previous month") }
            Text(Fmt.monthName(ym), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            androidx.compose.material3.IconButton(onClick = { vm.ym.value = ymAdd(ym, 1).takeIf { it != home } }, modifier = Modifier.testTag("month-next")) {
                Icon(androidx.compose.material.icons.Icons.Rounded.ChevronRight, "Next month") }
        }
        if (ym != home) androidx.compose.material3.AssistChip(onClick = { vm.ym.value = null }, label = { Text("Back to ${Fmt.monthOnly(home)}") },
            modifier = Modifier.testTag("month-home"))
    }
}
