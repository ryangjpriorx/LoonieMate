@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package looniemate.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.BuildConfig
import looniemate.app.MainViewModel
import looniemate.app.update.Updater
import looniemate.app.ui.SectionCard
import looniemate.app.ui.Sheet
import looniemate.app.ui.toneColors
import looniemate.app.ui.theme.PALETTES
import looniemate.engine.Fmt
import looniemate.engine.diffDays
import looniemate.engine.*

@Composable
fun MoreScreen(vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit) {
    val look by vm.prefs.look.collectAsStateWithLifecycle()
    val upd by vm.updater.state.collectAsStateWithLifecycle()
    val lastBackup by vm.prefs.lastBackupFlow.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            SectionCard("Appearance", Icons.Rounded.Palette, trailing = "Only this phone", index = 0) {
                Text("Light or dark", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp))
                val modes = listOf("system" to "Match phone", "light" to "Light", "dark" to "Dark")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (k, l) -> SegmentedButton(selected = look.themeMode == k, onClick = { vm.prefs.setLook(look.copy(themeMode = k)) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(l) } }
                }
                Spacer(Modifier.height(14.dp))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("Colours from my wallpaper", "Material You: LoonieMate picks up the colours of your home screen.", look.dynamicColor) { vm.prefs.setLook(look.copy(dynamicColor = it)) }
                    Spacer(Modifier.height(10.dp))
                }
                if (!look.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    Text("Colour theme", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PALETTES.forEach { p -> val sel = look.palette == p.key
                            Surface(onClick = { vm.prefs.setLook(look.copy(palette = p.key)) }, shape = MaterialTheme.shapes.medium,
                                color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                border = if (sel) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(p.l[0]), Color(p.l[1])))))
                                    Spacer(Modifier.height(4.dp)); Text(p.name, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                SwitchRow("Visual effects", "Animations and motion. " + vm.prefs.effectsReason.ifEmpty { "Turn off for a calmer screen or to save battery." }, look.effects) { vm.prefs.setLook(look.copy(effects = it)) }
            }
        }
        item {
            SectionCard("Backup and moving to another phone", Icons.Rounded.Backup, "green", index = 1) {
                val days = lastBackup?.let { diffDays(it.take(10), today) }
                Text(when (days) { null -> "No backup yet. If this phone is lost, reset or the app is removed, the budget is gone."; 0 -> "Last backup: today."; 1 -> "Last backup: yesterday."; else -> "Last backup: $days days ago." },
                    fontWeight = if (days == null || days > 14) FontWeight.Bold else null, color = if (days == null || days > 14) toneColors("warn").first else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text("Backups are locked with a password you choose and saved as a file. The same file works in the web version, on another phone, and later with Google Drive sync. If you lose the password, the file can't be opened by anyone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { open(Sheet.Export) }) { Text("Export a backup") }; OutlinedButton(onClick = { open(Sheet.Import) }) { Text("Import a backup") } }
            }
        }
        item {
            SectionCard("Updates", Icons.Rounded.SystemUpdate, "sky", trailing = "Version " + BuildConfig.VERSION_NAME, index = 2) { UpdatePanel(upd, vm) }
        }
        item {
            SectionCard("Coming to the app next", Icons.Rounded.Schedule, "amber", index = 3) {
                Text("Food, Savings, Income and benefits, Accounts and balance checks are next, then Google Drive sync, reminders and a home screen widget. Until then, change those in the web version and import a backup here.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            SectionCard("About LoonieMate", Icons.Rounded.Info, index = 4) {
                Text("A budget planner for families on a tight budget in Canada. No account, no ads, nothing sent anywhere: your budget stays on this phone unless you export it. iPhone users can use the web version.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(vm.updater.repoUrl))) }) { Text("LoonieMate on GitHub") }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.width(12.dp)); Switch(checked, onChange)
    }
}

@Composable
private fun UpdatePanel(st: Updater.State, vm: MainViewModel) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun notes(r: Updater.Release) = r.notes.trim().take(1200)
    when (st) {
        is Updater.State.Available -> {
            Text("LoonieMate ${st.r.versionName} is available.", fontWeight = FontWeight.Bold)
            if (st.r.notes.isNotBlank()) Text(notes(st.r), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { vm.downloadUpdate(st.r) }) { Text("Download" + if (st.r.size > 0) " (${st.r.size / 1_000_000} MB)" else "") }
                TextButton(onClick = { vm.updater.skip(st.r) }) { Text("Skip this version") } }
        }
        is Updater.State.Downloading -> { Text("Downloading ${st.r.versionName}…"); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth()) }
        is Updater.State.Ready -> {
            Text("Version ${st.r.versionName} is downloaded and checked.", fontWeight = FontWeight.Bold)
            if (st.r.notes.isNotBlank()) Text(notes(st.r), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp)); Button(onClick = { vm.updater.install(st.r, st.file) }) { Text("Install now") }
            Text("Your budget stays exactly as it is.", style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 6.dp))
        }
        is Updater.State.NeedsPermission -> {
            Text("One-time step: allow LoonieMate to install its own updates.", fontWeight = FontWeight.Bold)
            Text("Android asks for this once. Turn on \"Allow from this source\", then come back here.", style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.height(10.dp)); Button(onClick = { ctx.startActivity(vm.updater.permissionIntent()) }) { Text("Open the setting") }
        }
        is Updater.State.Installing -> { Text("Installing…"); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        is Updater.State.Checking -> { Text("Checking GitHub…"); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        is Updater.State.Failed -> { Text(st.message, color = toneColors("bad").first); Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { FilledTonalButton(onClick = { vm.checkUpdates(true) }) { Text("Check again") }
                st.r?.let { r -> OutlinedButton(onClick = { vm.downloadUpdate(r) }) { Text("Download again") } } } }
        is Updater.State.UpToDate -> { Text("You have the newest version."); Spacer(Modifier.height(8.dp)); FilledTonalButton(onClick = { vm.checkUpdates(true) }) { Text("Check now") } }
        else -> { Text(if (vm.updater.enabled) "LoonieMate checks GitHub for new versions twice a day." else "This test build doesn't update itself.", style = MaterialTheme.typography.bodySmall, color = muted)
            Spacer(Modifier.height(8.dp)); FilledTonalButton(onClick = { vm.checkUpdates(true) }, enabled = vm.updater.enabled) { Text("Check now") } }
    }
    Spacer(Modifier.height(10.dp))
    var auto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(vm.prefs.autoUpdate) }
    SwitchRow("Check for updates automatically", "Only checks; nothing installs without you.", auto) { auto = it; vm.prefs.autoUpdate = it }
}
