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
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Restaurant
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import looniemate.app.ui.ClickRow
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.LineRow
import looniemate.app.ui.IconBadge
import looniemate.app.ui.SectionCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.testTag
import looniemate.engine.Accounts
import looniemate.engine.Core
import looniemate.engine.Docs
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
        item { HouseholdCard(vm, open) }
        item { AccountsCard(vm, open) }
        item { IncomeCard(vm, open) }
        item { FoodCard(vm, open) }
        item { QuickButtonsCard(vm) }
        item { RemindersCard(vm) }
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
            }
        }
        item { AccessibilityCard(vm) }
        item {
            SectionCard("Backup and moving to another phone", Icons.Rounded.Backup, "green", index = 1) {
                val days = lastBackup?.let { diffDays(it.take(10), today) }
                Text(when (days) { null -> "No backup yet. If this phone is lost, reset or the app is removed, the budget is gone."; 0 -> "Last backup: today."; 1 -> "Last backup: yesterday."; else -> "Last backup: $days days ago." },
                    fontWeight = if (days == null || days > 14) FontWeight.Bold else null, color = if (days == null || days > 14) toneColors("warn").first else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(6.dp))
                Text("Backups are locked with a password you choose and saved as a file. The same file works in the web version and on another phone. If you lose the password, the file can't be opened by anyone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { open(Sheet.Export) }) { Text("Export a backup") }; OutlinedButton(onClick = { open(Sheet.Import) }) { Text("Import a backup") } }
            }
        }
        item { SyncCard(vm) }
        item {
            SectionCard("Updates", Icons.Rounded.SystemUpdate, "sky", trailing = "Version " + BuildConfig.VERSION_NAME, index = 2) { UpdatePanel(upd, vm) }
        }
        item {
            SectionCard("Need a fresh start?", Icons.Rounded.Refresh, "teal", index = 4) {
                Text("A missed paycheque or a surprise bill? Start again from today: what's in your accounts, what's paid, what you're behind on, and how to get to the next payday.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { open(Sheet.CatchUp(first = false)) }, modifier = Modifier.testTag("get-on-track")) { Text("Get back on track") }
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
        /* for testing: runs the getting-on-track steps as if for the first time (docs/plans/getting-on-track.md) */
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("For testing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.prefs.onboarded = false; vm.prefs.a11yAsked = false; open(Sheet.Access(next = Sheet.CatchUp(first = true))) }, modifier = Modifier.testTag("redo-setup")) {
                    Text("Redo first-time setup", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        /* the very bottom: erase everything and start from scratch (asks to type RESET) */
        item {
            SectionCard("Reset LoonieMate", Icons.Rounded.DeleteForever, "bad", index = 5) {
                Text("Erase everything on this phone and start from scratch, as if LoonieMate was just installed. You'll be asked to type RESET first.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { open(Sheet.Reset) }, modifier = Modifier.testTag("reset-open")) { Text("Reset LoonieMate", color = toneColors("bad").first) }
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
fun UpdatePanel(st: Updater.State, vm: MainViewModel, inSheet: Boolean = false) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun notes(r: Updater.Release) = r.notes.trim().take(1200)
    when (st) {
        is Updater.State.Available -> {
            Text("LoonieMate ${st.r.versionName} is available.", fontWeight = FontWeight.Bold)
            if (st.r.notes.isNotBlank()) Text(notes(st.r), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 4.dp))
            if (st.r.size > 0) Text("About ${String.format(java.util.Locale.US, "%.1f", st.r.size / 1_000_000.0)} MB. Your budget stays exactly as it is.", style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = { vm.startUpdate(st.r) }, modifier = Modifier.testTag("start-update")) { Text("Start update") }
                TextButton(onClick = { vm.updater.skip(st.r) }) { Text("Skip this version") } }
        }
        is Updater.State.Downloading -> { Text("Downloading ${st.r.versionName}…"); Spacer(Modifier.height(8.dp)); LinearProgressIndicator(progress = { st.progress }, modifier = Modifier.fillMaxWidth()) }
        is Updater.State.Ready -> {
            Text("Version ${st.r.versionName} is downloaded and checked.", fontWeight = FontWeight.Bold)
            if (st.r.notes.isNotBlank()) Text(notes(st.r), style = MaterialTheme.typography.bodySmall, color = muted, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp)); Button(onClick = { vm.updater.install(st.r, st.file) }) { Text("Install") }
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
    if (inSheet) return
    Spacer(Modifier.height(10.dp))
    var auto by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(vm.prefs.autoUpdate) }
    SwitchRow("Check for updates automatically", "Only checks; nothing installs without you.", auto) { auto = it; vm.prefs.autoUpdate = it }
}

/** Household name and people, province, and plan dates in one line each, with a way into the page where they're changed. */
@Composable
private fun HouseholdCard(vm: MainViewModel, open: (Sheet) -> Unit) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val s = Docs.settings(docs["settings/main"], today)
    val ppl = s.strList("people")
    SectionCard("Household and province", Icons.Rounded.Home, "teal", index = 0) {
        LineRow("Household", s.s("householdName").ifBlank { "Not named" })
        LineRow("People", if (ppl.isEmpty()) "None added" else ppl.joinToString(", "))
        LineRow("Province", looniemate.engine.Labels.PROVINCES.find { it.first == s.or("province", "ON") }?.second ?: "Ontario")
        if (isYm(s["startYm"])) LineRow("Plan starts", Fmt.monthName(s.s("startYm")))
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = { open(Sheet.Household) }, modifier = Modifier.testTag("household-open")) { Text("Change") }
    }
}

/** The one-tap spending buttons on Today: the saved ones, each with a way to remove it (the web version's Quick buttons card). */
@Composable
private fun QuickButtonsCard(vm: MainViewModel) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val quick = Docs.settings(docs["settings/main"], today)["quick"].arr().filter { it.t("category") && it.n("amount") > 0 }
    SectionCard("Quick buttons", Icons.Rounded.Bolt, "amber", index = 0) {
        Text("One-tap spending buttons on Today. Add one by ticking \"Add a quick button\" when you log spending; things you log often are suggested too.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (quick.isEmpty()) { Spacer(Modifier.height(8.dp)); EmptyNote("No saved quick buttons yet.") }
        quick.forEachIndexed { i, q -> val label = looniemate.engine.Views.Quick(q.s("category"), q.n("amount"), q.s("note"), true).label
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val cat = looniemate.engine.Labels.SPENDL[q.s("category")] ?: ""
                Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.SemiBold)
                    if (cat != label) Text(cat, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(Fmt.money(q.n("amount")))
                IconButton(onClick = { vm.removeQuick(i) }, modifier = Modifier.testTag("quick-del-$i")) { Icon(Icons.Rounded.Delete, "Remove the $label button") }
            }
        }
    }
}

/** Every account the family uses, and what each one is for. Money moves, new bills and food money are routed by these. */
@Composable
private fun AccountsCard(vm: MainViewModel, open: (Sheet) -> Unit) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val s = Docs.settings(docs["settings/main"], today)
    val list = Core.accountsOf(s)
    val bills = docs.filterKeys { it.startsWith("bills/") }.values
    val ck = Docs.checkins(docs).firstOrNull()
    SectionCard("Accounts", Icons.Rounded.AccountBalance, "gold", index = 0) {
        Text("Every account you use. Tick what each one is for and LoonieMate works out where money should go: which account pays new bills, where food money goes, and where to send savings.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (list.isEmpty()) EmptyNote("No accounts yet. Add the accounts you use.")
        else {
            if (Core.acctFor(s, "main") == null) {
                Text("Tick \"Main account\" on one account so money moves can work.", color = toneColors("bad").first, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
            }
            list.forEachIndexed { i, a ->
                val used = Accounts.billsUsing(s, bills, a.s("id"))
                Box(Modifier.testTag("account-$i")) {
                    ClickRow({ open(Sheet.Account(a.s("id"))) }) {
                        IconBadge(Icons.Rounded.AccountBalance, if (a.s("type") == "savings") "green" else "sky", 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(a.s("name"), fontWeight = FontWeight.SemiBold)
                            Text(Accounts.summary(a), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (used > 0) Text("Pays $used bill${if (used > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ck?.get("bal")?.get(a.s("id"))?.let { b -> if (b != J.Null) Text(Fmt.money(num(b)), style = MaterialTheme.typography.bodyMedium.merge(looniemate.app.ui.theme.Numbers)) }
                        Icon(Icons.Rounded.ChevronRight, "Edit ${a.s("name")}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (ck != null && list.isNotEmpty()) Text("Balances are from your last check on ${Fmt.dLabel(ck.s("date"))}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { open(Sheet.Account(null)) }, modifier = Modifier.testTag("add-account")) { Text("+ Add an account") }
            if (list.isNotEmpty()) OutlinedButton(onClick = { open(Sheet.Checkin) }, modifier = Modifier.testTag("checkin-more")) { Text("Check balances") }
        }
    }
}

/** The food budget in one line, with a way into the full food view where it's changed. */
@Composable
private fun FoodCard(vm: MainViewModel, open: (Sheet) -> Unit) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val s = Docs.settings(docs["settings/main"], today)
    SectionCard("Food budget", Icons.Rounded.Restaurant, "green", index = 0) {
        Text("Food gets ${Math.round(s.n("foodPct"))}% of the money left after fixed bills each month, and never less than ${Fmt.money(s.n("foodMin"))}.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(onClick = { open(Sheet.Food(today.take(7))) }, modifier = Modifier.testTag("food-settings")) { Text("Change food budget") }
    }
}

/** Paycheques, social assistance and government benefits: what comes in, when, and where it lands. */
@Composable
private fun IncomeCard(vm: MainViewModel, open: (Sheet) -> Unit) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val s = Docs.settings(docs["settings/main"], today)
    val incomes = Core.incomesOf(s); val assist = incomes.filter { Core.assistOf(it) != null }; val pays = incomes.filter { Core.assistOf(it) == null }
    fun next(d: List<String>) = d.firstOrNull()?.let { " · next ${Fmt.dShort(it)}" } ?: ""
    SectionCard("Income and benefits", Icons.Rounded.Payments, "teal", index = 0) {
        Text("What comes in and when. LoonieMate works out every payday, moving it to the business day before when it lands on a weekend or bank holiday.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Head("Social assistance")
        assist.forEach { x -> val p = Core.ASSIST[Core.assistOf(x)]
            RowOf("income-${x.s("id")}", x.s("name"), "${p?.short ?: ""} · ${Fmt.money(x.n("amount"))}" + next(IncomeSetup.nextDates(Core.assistSched(x), today, 1))) { open(Sheet.Income(x.s("id"))) } }
        val progs = IncomeSetup.programs(s)
        if (progs.isEmpty()) Text("No programs for your province yet. Add it as a regular income.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else { var pick by remember { mutableStateOf(progs.first().key) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                looniemate.app.ui.sheets.SelectField(pick, progs.map { it.key to it.name }, { pick = it }, "Program", Modifier.weight(1f))
                OutlinedButton(onClick = { open(Sheet.Income(null, pick)) }) { Text("Add") } } }

        Head("Paycheques and other income")
        if (pays.isEmpty()) Text("No paycheques yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        pays.forEach { x -> RowOf("income-${x.s("id")}", x.s("name"), "${Fmt.money(x.n("amount"))} · ${IncomeSetup.typeLabel(x["sched"].s("type").ifEmpty { "monthly" })}" + next(IncomeSetup.nextDates(x["sched"], today, 1))) { open(Sheet.Income(x.s("id"))) } }
        FilledTonalButton(onClick = { open(Sheet.Income(null)) }, modifier = Modifier.testTag("add-income")) { Text("+ Add a paycheque or other income") }

        Head("Government benefits and credits")
        Core.creditsOf(s).forEach { x -> RowOf("credit-${x.s("id")}", Core.creditName(x), Fmt.money(x.n("amount")) + (if (x.t("protect")) " · protected" else "") +
            next(IncomeSetup.nextDates(Core.creditSched(x), today, 1, x.s("start").takeIf { isYm(it) }))) { open(Sheet.Credit(x.s("id"))) } }
        val types = IncomeSetup.creditTypes(s); var pickC by remember { mutableStateOf(types.first().key) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            looniemate.app.ui.sheets.SelectField(pickC, types.map { it.key to it.name }, { pickC = it }, "Benefit", Modifier.weight(1f))
            OutlinedButton(onClick = { open(Sheet.Credit(null, pickC)) }) { Text("Add") } }
    }
}

@Composable
private fun Head(t: String) { Spacer(Modifier.height(10.dp)); Text(t, fontWeight = FontWeight.SemiBold) }

@Composable
private fun RowOf(tag: String, title: String, sub: String, onClick: () -> Unit) {
    Box(Modifier.testTag(tag)) { ClickRow(onClick) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Rounded.ChevronRight, "Edit $title", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

/** Settings → Accessibility (this phone only). Stage 2 of docs/plans/accessibility.md. */
@Composable
private fun AccessibilityCard(vm: MainViewModel) {
    val a by vm.prefs.access.collectAsStateWithLifecycle(); val look by vm.prefs.look.collectAsStateWithLifecycle()
    val still by vm.prefs.phoneNoMotion.collectAsStateWithLifecycle()
    fun set(n: looniemate.app.data.Prefs.Access) = vm.prefs.setAccess(n)
    SectionCard("Accessibility", Icons.Rounded.Accessibility, trailing = "Only this phone", index = 0) {
        Text("Bigger text", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp))
        val sizes = listOf("Normal", "Large", "Larger", "Largest")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            sizes.forEachIndexed { i, l -> SegmentedButton(selected = a.textSize == i, onClick = { set(a.copy(textSize = i)) }, shape = SegmentedButtonDefaults.itemShape(i, sizes.size)) {
                Text(l, maxLines = 1) } }
        }
        Text("On top of your phone's own text size.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        SwitchRow("Bold text", "Heavier letters throughout.", a.bold) { set(a.copy(bold = it)) }
        SwitchRow("Easier-to-read font", "Atkinson Hyperlegible, made for people with low vision.", a.font) { set(a.copy(font = it)) }
        SwitchRow("Stronger contrast", "Darker text, outlined cards and solid status colours.", a.contrast) { set(a.copy(contrast = it)) }
        SwitchRow("Colour-blind friendly colours", "Blue for good and orange for problems instead of green and red.", a.colorBlind) { set(a.copy(colorBlind = it)) }
        SwitchRow("Less motion", if (still) "Your phone's \"Remove animations\" is on, so motion stays off." else
            "No animations or moving colour. " + vm.prefs.effectsReason.ifEmpty { "Calmer, and easier on the battery." }, !look.effects || still) { vm.prefs.setLook(look.copy(effects = !it)) }
        SwitchRow("More time", "Undo and other messages stay until you close them.", a.moreTime) { set(a.copy(moreTime = it)) }
        SwitchRow("Bigger buttons", "Larger buttons with more space between them.", a.bigButtons) { set(a.copy(bigButtons = it)) }
    }
}
