package looniemate.app.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.MainViewModel
import looniemate.app.data.PhoneHints
import looniemate.app.data.Prefs
import looniemate.app.ui.StatusBadge
import looniemate.app.ui.screens.EventRow
import looniemate.app.ui.screens.SwitchRow
import looniemate.engine.J
import looniemate.engine.ymOf

/**
 * "Make LoonieMate easier to use?" The first screen of first-time setup (and a one-time card for people already using
 * the app). Matches the phone's settings in advance; every switch applies at once, so the app is the preview.
 * Continue keeps the choices; Skip keeps only what was matched to the phone. docs/plans/accessibility.md, stage 6.
 */
@Composable
fun AccessSheet(vm: MainViewModel, close: () -> Unit) {
    val ctx = LocalContext.current; val p = vm.prefs
    val hints = remember { PhoneHints.read(ctx) }
    val matched = remember { PhoneHints.applied(p.access.value, hints) }
    LaunchedEffect(Unit) { p.a11yAsked = true; p.setAccess(matched); if ("motion" in hints) p.setLook(p.look.value.copy(effects = false)) }
    val a by p.access.collectAsStateWithLifecycle(); val look by p.look.collectAsStateWithLifecycle()
    fun set(n: Prefs.Access) = p.setAccess(n)
    fun why(k: String) = hints[k]

    SheetTitle("Make LoonieMate easier to use?")
    Text("Switch on anything that helps. It changes right away, so you can see it here.", style = MaterialTheme.typography.bodyMedium)
    /* the preview: a sample bill and a status, drawn with the current switches */
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Example", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)); StatusBadge("warn", "Heads-up") }
            EventRow(J.obj("kind" to "bill", "name" to "Hydro", "amount" to 84.0, "date" to vm.today.value, "ym" to ymOf(vm.today.value), "key" to "preview",
                "group" to "adults", "method" to "Pre-authorized debit (PAD)", "paid" to false), vm.settings(), vm, compact = true, divider = false, interactive = false)
        }
    }
    Text("Bigger text", fontWeight = FontWeight.SemiBold)
    val sizes = listOf("Normal", "Large", "Larger", "Largest")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        sizes.forEachIndexed { i, l -> SegmentedButton(selected = a.textSize == i, onClick = { set(a.copy(textSize = i)) }, shape = SegmentedButtonDefaults.itemShape(i, sizes.size)) { Text(l, maxLines = 1) } }
    }
    why("text")?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    SwitchRow("Bold text", why("bold") ?: "Heavier letters throughout.", a.bold) { set(a.copy(bold = it)) }
    SwitchRow("Easier-to-read font", "Atkinson Hyperlegible, made for people with low vision.", a.font) { set(a.copy(font = it)) }
    SwitchRow("Stronger contrast", why("contrast") ?: "Darker text, outlined cards and solid status colours.", a.contrast) { set(a.copy(contrast = it)) }
    SwitchRow("Colour-blind friendly colours", why("colorBlind") ?: "Blue for good and orange for problems instead of green and red.", a.colorBlind) { set(a.copy(colorBlind = it)) }
    SwitchRow("Less motion", why("motion") ?: "No animations or moving colour.", !look.effects) { p.setLook(look.copy(effects = !it)) }
    SwitchRow("More time", why("moreTime") ?: "Undo and other messages stay until you close them.", a.moreTime) { set(a.copy(moreTime = it)) }
    SwitchRow("Bigger buttons", "Larger buttons with more space between them.", a.bigButtons) { set(a.copy(bigButtons = it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = close, modifier = Modifier.testTag("a11y-continue")) { Text("Continue") }
        TextButton(onClick = { p.setAccess(matched); close() }) { Text("Skip") }
    }
    Text("Change these any time in Settings → Accessibility.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
