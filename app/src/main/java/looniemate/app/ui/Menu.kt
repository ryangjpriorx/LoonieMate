@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package looniemate.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One thing a row can do from its long-press menu (docs/plans/overhaul.md, "Long-press, where it makes sense").
 * [danger] items (Delete) are drawn in the problem colour.
 */
class MenuItem(val label: String, val icon: ImageVector? = null, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * A row that does its obvious thing on a tap and offers [menu] on a long press, with a little buzz. Screen readers get
 * the row as one sentence ([spoken]) with the same menu as their actions, so the menu is never the only way in.
 * Without [onClick], a tap opens the menu too.
 */
@Composable
fun MenuRow(
    menu: List<MenuItem>, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, spoken: String? = null, clickLabel: String? = null,
    vertical: Dp = 10.dp, content: @Composable RowScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val longPress: (() -> Unit)? = if (menu.isEmpty()) null else ({ haptic.performHapticFeedback(HapticFeedbackType.LongPress); open = true })
    val tap: () -> Unit = onClick ?: { if (menu.isNotEmpty()) open = true }
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .combinedClickable(onClickLabel = clickLabel, onLongClickLabel = if (menu.isEmpty()) null else "More options", onClick = tap, onLongClick = longPress)
            .then(if (spoken != null) Modifier.clearAndSetSemantics {
                contentDescription = spoken; role = Role.Button
                onClick(clickLabel) { tap(); true }
                if (longPress != null) onLongClick("More options") { longPress(); true }
                customActions = menu.map { m -> CustomAccessibilityAction(m.label) { m.onClick(); true } }
            } else Modifier.semantics { customActions = menu.map { m -> CustomAccessibilityAction(m.label) { m.onClick(); true } } })
            .padding(vertical = rowPad(vertical), horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            menu.forEach { m ->
                val c = if (m.danger) toneColors("bad").first else MaterialTheme.colorScheme.onSurface
                DropdownMenuItem(text = { Text(m.label, color = c) }, onClick = { open = false; m.onClick() },
                    leadingIcon = m.icon?.let { ic -> { Icon(ic, null, tint = c) } })
            }
        }
    }
}

/**
 * The round tick at the start of a To do row: a 48 dp target (56 with Bigger buttons) around a 28 dp circle, filled with
 * a check once done, with a little buzz when it's ticked. Screen readers hear "done" or "not done".
 */
@Composable
fun TickButton(done: Boolean, label: String, onToggle: () -> Unit) {
    val haptic = LocalHapticFeedback.current; val cs = MaterialTheme.colorScheme
    val big = looniemate.app.ui.theme.LocalAccess.current.bigButtons
    Box(Modifier.size(if (big) 56.dp else 48.dp).clip(CircleShape)
        .combinedClickable(onClick = { if (!done) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggle() })
        .semantics { contentDescription = label; stateDescription = if (done) "done" else "not done"; role = Role.Checkbox },
        contentAlignment = Alignment.Center) {
        val ring = Modifier.size(if (big) 32.dp else 28.dp).clip(CircleShape)
        if (done) Box(ring.background(cs.primary), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = cs.onPrimary, modifier = Modifier.size(18.dp)) }
        else Box(ring.border(2.dp, cs.outline, CircleShape))
    }
}
