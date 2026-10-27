package looniemate.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import looniemate.app.ui.theme.LocalAccess
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.theme.LocalFx
import looniemate.app.ui.theme.Numbers

/*
 * The shared pieces of the overhaul (docs/plans/overhaul.md): a row that does the obvious thing on tap, offers a short
 * menu on long-press (with a little buzz), and offers the same choices to TalkBack as actions; a round tick; the menu.
 */

/** Opens a page from anywhere (forms, settings pages), so rows don't need it passed down. */
val LocalOpen = staticCompositionLocalOf<(Sheet) -> Unit> { {} }

/** One thing a person can do with an item, in its long-press menu and its TalkBack actions. */
class RowAction(val label: String, val icon: ImageVector? = null, val danger: Boolean = false, val onClick: () -> Unit)

/** The long-press menu. */
@Composable
fun ActionMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<RowAction>) {
    DropdownMenu(expanded, onDismiss, modifier = Modifier.testTag("row-menu")) {
        actions.forEach { a ->
            DropdownMenuItem(text = { Text(a.label, color = if (a.danger) toneColors("bad").first else MaterialTheme.colorScheme.onSurface) },
                leadingIcon = a.icon?.let { i -> { Icon(i, null) } }, onClick = { onDismiss(); a.onClick() })
        }
    }
}

/** A round tick: an empty circle, or filled with a check. A 48 dp target (56 with Bigger buttons). */
@Composable
fun TickCircle(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme; val haptic = LocalHapticFeedback.current
    val fill by animateColorAsState(if (checked) cs.primary else cs.surface, if (LocalFx.current) tween(200) else tween(0), label = "tick")
    val target = if (LocalAccess.current.bigButtons) 56.dp else 48.dp
    Box(modifier.size(target).clip(CircleShape).combinedClickable(onClick = { if (!checked) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onClick() }),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(fill).border(2.dp, if (checked) cs.primary else cs.outline, CircleShape), contentAlignment = Alignment.Center) {
            if (checked) Icon(Icons.Rounded.Check, null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * The shared row. [tick] null: no tick (the [leading] dot or icon instead). Tap runs [onClick] (or opens the menu when
 * there's nothing to open); long-press opens the menu of [actions]. [spoken] is the whole row read as one sentence; the
 * tick and every action are offered to screen readers as actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionRow(
    title: String, spoken: String, modifier: Modifier = Modifier, subtitle: String = "", amount: String? = null, amountTone: String = "",
    tick: Boolean? = null, onTick: () -> Unit = {}, tickLabel: String = "Mark done", untickLabel: String = "Mark not done",
    overdue: Boolean = false, leading: (@Composable () -> Unit)? = null, pills: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null, actions: List<RowAction> = emptyList(), tag: String? = null,
) {
    val cs = MaterialTheme.colorScheme; val x = LocalExtra.current; val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val tap: () -> Unit = onClick ?: { if (actions.isNotEmpty()) menu = true }
    val long: (() -> Unit)? = if (actions.isNotEmpty()) ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }) else null
    val a11y = buildList {
        if (tick != null) add(CustomAccessibilityAction(if (tick) untickLabel else tickLabel) { onTick(); true })
        actions.forEach { a -> add(CustomAccessibilityAction(a.label) { a.onClick(); true }) }
    }
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = tap, onLongClick = long)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .clearAndSetSemantics { contentDescription = spoken; customActions = a11y
                if (onClick != null) onClick(label = "Open") { onClick(); true }
                if (long != null) onLongClick(label = "More choices") { menu = true; true } }
            .padding(vertical = rowPad(4.dp)),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tick != null) TickCircle(tick, onTick) else if (leading != null) Box(Modifier.size(if (LocalAccess.current.bigButtons) 56.dp else 48.dp), contentAlignment = Alignment.Center) { leading() }
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (tick == true) cs.onSurfaceVariant else cs.onSurface, modifier = Modifier.weight(1f, fill = false))
                    pills?.invoke(this)
                }
                if (overdue || subtitle.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (overdue) { Dot(x.bad, 8.dp, round = true); Text("Overdue", style = MaterialTheme.typography.bodySmall, color = x.bad, fontWeight = FontWeight.Bold) }
                    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (amount != null) Text(amount, style = MaterialTheme.typography.bodyLarge.merge(Numbers), fontWeight = FontWeight.SemiBold, maxLines = 1,
                color = if (amountTone.isNotEmpty()) toneColors(amountTone).first else cs.onSurface, modifier = Modifier.padding(end = 4.dp))
        }
        Box(Modifier.align(Alignment.TopEnd)) { ActionMenu(menu, { menu = false }, actions) }
    }
}

/** The ⋮ button on a page's top bar or a card, opening the same menu as a long-press. */
@Composable
fun MoreButton(actions: List<RowAction>, label: String = "More choices") {
    var menu by remember { mutableStateOf(false) }
    Box { IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, label) }; ActionMenu(menu, { menu = false }, actions) }
}

/** A short explanation in a dialog ("Why this move?"). */
@Composable
fun InfoDialog(title: String, text: String, onDismiss: () -> Unit) = AlertDialog(onDismissRequest = onDismiss,
    title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } })
