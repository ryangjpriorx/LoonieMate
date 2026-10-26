package looniemate.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import looniemate.app.MainViewModel
import looniemate.app.sync.SyncManager
import looniemate.app.ui.SectionCard
import looniemate.app.ui.toneColors

/** "Synced just now", "Synced 5 min ago", "Synced 3 hours ago", "Synced 2 days ago". */
fun syncedAgo(at: Long, now: Long = System.currentTimeMillis()): String {
    if (at <= 0) return "Not synced yet"
    val m = (now - at) / 60_000
    return "Synced " + when { m < 1 -> "just now"; m < 60 -> "$m min ago"; m < 24 * 60 -> "${m / 60} hour${if (m / 60 > 1) "s" else ""} ago"; else -> "${m / 1440} day${if (m / 1440 > 1) "s" else ""} ago" }
}

/** Settings → Sync between phones (docs/plans/sync.md, stage 3). */
@Composable
fun SyncCard(vm: MainViewModel, bare: Boolean = false) {
    val sm = vm.sync; val st by sm.state.collectAsStateWithLifecycle(); val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<Uri, Boolean>?>(null) }   // the chosen file, and whether it's new
    var pw by remember { mutableStateOf("") }; var pw2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }; var busy by remember { mutableStateOf(false) }; var confirmStop by remember { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u -> if (u != null) { pending = u to true; err = null } }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) { pending = u to false; err = null } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun run(block: suspend () -> String?) { busy = true; err = null; scope.launch { err = block(); busy = false; if (err == null) { pending = null; pw = ""; pw2 = "" } } }

    looniemate.app.ui.CardOrBare(bare, "Sync between phones", Icons.Rounded.CloudSync, "sky") {
        val p = pending
        when {
            p != null -> {
                Text(if (p.second) "Choose a password for the sync file" else "Enter the sync file's password", fontWeight = FontWeight.SemiBold)
                Text(if (p.second) "The file is locked with this password, the same way as a backup. You'll type it once on your other phone. If it's lost, the file can't be opened by anyone."
                    else "The password chosen when the sync file was made on your other phone.", style = MaterialTheme.typography.bodySmall, color = muted)
                Spacer(Modifier.height(6.dp))
                Password("Password", pw) { pw = it; err = null }
                if (p.second) Password("Type it again", pw2) { pw2 = it; err = null }
                err?.let { Text(it, color = toneColors("bad").first, fontWeight = FontWeight.SemiBold) }
                if (busy) { Spacer(Modifier.height(6.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Opening the file. The first time takes a few seconds.", style = MaterialTheme.typography.bodySmall, color = muted) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(enabled = !busy, onClick = {
                        err = when { pw.length < 8 -> "Use at least 8 characters."; p.second && pw != pw2 -> "The two passwords don't match."; else -> null }
                        if (err == null) run { sm.setUp(p.first, pw, p.second) } }, modifier = Modifier.testTag("sync-on")) { Text("Turn on sync") }
                    TextButton(enabled = !busy, onClick = { pending = null; pw = ""; pw2 = ""; err = null }) { Text("Cancel") }
                }
            }
            !sm.on -> {
                Text("Keep this budget the same on two phones. LoonieMate keeps an encrypted copy in a file you choose, usually in Google Drive, and both phones keep it up to date. Use the same Google account on both.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Text("On your first phone:", style = MaterialTheme.typography.bodySmall, color = muted)
                Button(onClick = { create.launch("LoonieMate sync.json") }, modifier = Modifier.testTag("sync-create")) { Text("Create a sync file") }
                Spacer(Modifier.height(6.dp))
                Text("On your other phone:", style = MaterialTheme.typography.bodySmall, color = muted)
                OutlinedButton(onClick = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("Use an existing sync file") }
                Text("In the file picker, open the menu and choose Drive (or another cloud app, like Nextcloud).", style = MaterialTheme.typography.bodySmall, color = muted)
            }
            else -> {
                Text("Syncing with ${vm.prefs.syncName ?: "the sync file"}", fontWeight = FontWeight.SemiBold)
                when (val s = st) {
                    is SyncManager.State.Syncing -> { Text("Syncing…", style = MaterialTheme.typography.bodySmall, color = muted); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    is SyncManager.State.Ok -> Text(syncedAgo(s.at) + ". Changes go across when either phone opens LoonieMate, and every few hours.", style = MaterialTheme.typography.bodySmall, color = muted)
                    is SyncManager.State.Error -> {
                        Text(s.message, color = toneColors("bad").first, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        if (s.kind == "password") { Spacer(Modifier.height(6.dp)); Password("New password", pw) { pw = it; err = null }
                            err?.let { Text(it, color = toneColors("bad").first) }
                            Button(enabled = !busy && pw.isNotEmpty(), onClick = { run { sm.newPassword(pw) } }) { Text("Save password") } }
                        if (s.kind == "unreachable" || s.kind == "notfile") TextButton(onClick = { pick.launch(arrayOf("application/json", "*/*")) }) { Text("Choose the file again") }
                    }
                    else -> {}
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(enabled = st !is SyncManager.State.Syncing, onClick = { scope.launch { sm.sync(force = true) } }, modifier = Modifier.testTag("sync-now")) { Text("Sync now") }
                    if (!confirmStop) TextButton(onClick = { confirmStop = true }) { Text("Stop syncing") }
                    else Button(onClick = { sm.stop(); confirmStop = false }) { Text("Tap to stop") }
                }
                if (confirmStop) Text("This phone keeps its budget; the file stays where it is and your other phone keeps syncing with it.", style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

@Composable
private fun Password(label: String, v: String, set: (String) -> Unit) = OutlinedTextField(v, set, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
    visualTransformation = PasswordVisualTransformation(), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password))
