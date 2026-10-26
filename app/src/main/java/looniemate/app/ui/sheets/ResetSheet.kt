package looniemate.app.ui.sheets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import looniemate.app.MainViewModel
import looniemate.app.ui.Sheet
import looniemate.app.ui.toneColors

/**
 * Settings → Reset LoonieMate: erases everything on this phone and starts from scratch. Typing RESET is the only way
 * to confirm, so it can't happen by a stray tap. The sync file and any backups saved elsewhere are left alone.
 */
@Composable
fun ResetSheet(vm: MainViewModel, close: () -> Unit, open: (Sheet) -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val ok = typed.trim() == "RESET"
    SheetTitle("Reset LoonieMate")
    Text("This erases everything LoonieMate keeps on this phone and starts from scratch, as if it was just installed:", style = MaterialTheme.typography.bodyMedium)
    Text("• your accounts, income, benefits and bills\n• every month, everything logged, savings and Personal Expenses\n• balance checks and getting-on-track answers\n" +
        "• reminders, appearance and accessibility settings" + (if (vm.sync.on) "\n• syncing on this phone (the sync file itself stays where it is)" else ""),
        style = MaterialTheme.typography.bodyMedium)
    Text("It can't be undone. If you might want this budget again, make a backup first.", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = { open(Sheet.Export) }) { Text("Make a backup first") }
    TextFieldRow(typed, { typed = it.take(10) }, "Type RESET to confirm", modifier = Modifier.fillMaxWidth().testTag("reset-type"))
    val (bad, badBg) = toneColors("bad")
    Button(onClick = { if (ok) { vm.resetEverything(); close() } }, enabled = ok, modifier = Modifier.fillMaxWidth().testTag("reset-go"),
        colors = ButtonDefaults.buttonColors(containerColor = bad, contentColor = badBg)) { Text("Erase everything and start over") }
}
