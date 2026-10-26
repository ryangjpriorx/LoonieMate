@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package looniemate.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
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
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.MainViewModel
import looniemate.app.update.Updater
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.GrowBar
import looniemate.app.ui.Money
import looniemate.app.ui.SectionCard
import looniemate.app.ui.Sheet
import looniemate.app.ui.StatusBadge
import looniemate.app.ui.Tab
import looniemate.app.ui.enter
import looniemate.app.ui.toneColors
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.theme.Numbers
import looniemate.engine.Fmt
import looniemate.engine.Views
import looniemate.engine.n
import looniemate.engine.s
import looniemate.engine.*

@Composable
fun TodayScreen(vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit, go: (Tab) -> Unit, state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()) {
    val m by vm.todayModel.collectAsStateWithLifecycle()
    val upd by vm.updater.state.collectAsStateWithLifecycle()
    val model = m ?: return
    val s = vm.settings()
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().enter(0), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.weekday(model.today).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(Fmt.longDate(model.today), style = MaterialTheme.typography.displaySmall)
                }
                if (!model.isNew) StatusBadge(model.status.tone, model.status.label)
            }
        }
        item { UpdateBanner(upd, vm, go) }
        item { StorageProblemCard(vm, open) }
        items(model.reminders.size) { i -> ReminderCard(model.reminders[i], vm, open, i) }
        if (model.isNew) { item { WelcomeCard(open) }; return@LazyColumn }
        /* asked once there's a budget to remind about, not on first launch */
        item { NotificationsCard(vm) }
        item { LateRemindersCard(vm) }
        if (model.readyToStart) item { StartHereCard(vm, open) }
        item { AccessCard(vm, open) }

        item {
            val n = model.todo.size
            SectionCard(if (n > 0) "To do today · $n" else "Nothing to do today", Icons.Rounded.Receipt, if (n > 0) "amber" else "green", index = 1) {
                if (n > 0) model.todo.forEachIndexed { i, e -> EventRow(e, s, vm, divider = i > 0, open = open) }
                else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val x = LocalExtra.current
                    Box(Modifier.size(46.dp).clip(CircleShape).background(Brush.linearGradient(listOf(x.good, x.pets))).enter(2), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.surface) }
                    Column { Text("You're all caught up.", fontWeight = FontWeight.Bold)
                        val nx = model.next.firstOrNull()
                        Text(if (nx != null) "Next: " + nextText(nx) + " on " + Fmt.dLabel(nx.s("date")) + "." else "Nothing else this week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (model.incoming.isNotEmpty()) { Spacer(Modifier.height(6.dp)); model.incoming.forEach { e -> EventRow(e, s, vm, open = open) } }
            }
        }
        item {
            val fp = model.food; val (fg, _) = toneColors(model.foodTone)
            SectionCard("Food money", Icons.Rounded.Restaurant, "green", trailing = if (fp.n("half") == 1.0) "1st–15th" else "16th–end", index = 2) {
                FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Money(fp.n("left"), MaterialTheme.typography.headlineLarge, countUp = true, color = if (fp.n("left") < 0) fg else MaterialTheme.colorScheme.onSurface)
                    Text("left · about ${Fmt.money(fp.n("perDay"))} a day for ${fp.n("daysLeft").toInt()} day${if (fp.n("daysLeft").toInt() > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                }
                Spacer(Modifier.height(10.dp))
                val allow = fp.n("allow")
                GrowBar(if (allow > 0) (fp.n("spent") / allow).toFloat() else 0f, fg, marker = if (allow > 0) (fp.n("expected") / allow).toFloat() else null)
                Spacer(Modifier.height(6.dp))
                Text("${Fmt.money(fp.n("spent"))} of ${Fmt.money(allow)} spent · the line shows where you'd be at ${Fmt.money(fp.n("target"))} a day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { open(Sheet.Food(model.today.take(7))) }, modifier = Modifier.testTag("food-open")) { Text("Food plan and settings") }
            }
        }
        model.self?.let { c -> item { SelfCard(c) { go(Tab.Spending) } } }
        if (model.hasAccounts) item {
            val ck = model.lastCheck; val days = ck?.let { diffDays(it.s("date"), model.today) }
            SectionCard("Balances", Icons.Rounded.AccountBalance, "gold", trailing = when (days) { null -> "Not checked yet"; 0 -> "Checked today"; 1 -> "Checked yesterday"; else -> "Checked $days days ago" }, index = 3) {
                val gap = ck?.get("gap")?.let { if (it == J.Null) null else num(it) }
                Text(if (ck != null && days!! <= 7 && gap != null) (if (Math.abs(gap) < 5) "At the last check, your accounts matched the plan."
                    else "At the last check, your accounts were ${Fmt.money(Math.abs(gap))} ${if (gap < 0) "lower" else "higher"} than the plan.")
                    else "Once a week, type in what your accounts actually hold. LoonieMate spots money that went missing or a bill that came out early.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (days == null || days > 7) Button(onClick = { open(Sheet.Checkin) }, modifier = Modifier.testTag("checkin-open")) { Text("Check my balances") }
                else FilledTonalButton(onClick = { open(Sheet.Checkin) }, modifier = Modifier.testTag("checkin-open")) { Text("Check my balances") }
            }
        }
        item {
            SectionCard("Log spending", Icons.Rounded.AccountBalanceWallet, "amber", trailing = "One tap logs it for today", index = 3) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.quick.forEach { q -> QuickButton(q.label, Fmt.money(q.amount), q.fav, menu = quickMenu(q, vm, open)) { vm.quickLog(q) } }
                    QuickButton("Something else", "", true, more = true) { open(Sheet.Log()) }
                }
                if (model.quick.isEmpty()) Text("Things you log often will show up here as one-tap buttons. You can also tick \"Add a quick button\" when logging.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (model.debts.isNotEmpty()) item { DebtCard(model.debts, 4) { go(Tab.Bills) } }
        if (model.next.isNotEmpty()) item {
            SectionCard("Next few days", Icons.Rounded.CalendarMonth, index = 5) {
                model.next.forEachIndexed { i, e -> EventRow(e, s, vm, showDate = true, compact = true, divider = i > 0, open = open) }
            }
        }
    }
}

private fun nextText(e: looniemate.engine.Obj): String = when (e.s("kind")) {
    "move" -> "move ${Fmt.money(e.n("amount"))} to ${e.s("to")}"; "in" -> e.s("name") + " comes in"; else -> e.s("name") + " " + Fmt.money(e.n("amount")) }

/** A one-tap spending button. A long press gives [menu]: log a different amount, save or remove it. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun QuickButton(label: String, amount: String, fav: Boolean, more: Boolean = false, menu: List<looniemate.app.ui.MenuItem> = emptyList(), onClick: () -> Unit) {
    val x = LocalExtra.current; val cs = MaterialTheme.colorScheme; var open by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val bg = if (more) cs.surfaceContainerHighest else if (fav) x.goodBg else cs.surfaceContainer
    Box {
        Surface(shape = MaterialTheme.shapes.medium, color = bg, modifier = Modifier.width(148.dp).clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClickLabel = if (menu.isEmpty()) null else "More options",
                onLongClick = if (menu.isEmpty()) null else ({ haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); open = true }))
            .semantics { customActions = menu.map { m -> androidx.compose.ui.semantics.CustomAccessibilityAction(m.label) { m.onClick(); true } } }) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                if (more) Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.AutoAwesome, null, tint = cs.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(label, color = cs.primary, fontWeight = FontWeight.SemiBold) }
                else { Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(amount, style = MaterialTheme.typography.titleMedium.merge(Numbers), fontWeight = FontWeight.Bold) }
            }
        }
        androidx.compose.material3.DropdownMenu(open, { open = false }) { menu.forEach { m ->
            androidx.compose.material3.DropdownMenuItem(text = { Text(m.label, color = if (m.danger) toneColors("bad").first else cs.onSurface) }, onClick = { open = false; m.onClick() }) } }
    }
}

/** The long-press menu for a quick button on Today. */
fun quickMenu(q: Views.Quick, vm: MainViewModel, open: (Sheet) -> Unit): List<looniemate.app.ui.MenuItem> = buildList {
    add(looniemate.app.ui.MenuItem("Log a different amount…") { open(Sheet.Log(category = q.category, note = q.note)) })
    if (q.fav) add(looniemate.app.ui.MenuItem("Remove this button", danger = true) { vm.removeQuick(q) })
    else add(looniemate.app.ui.MenuItem("Keep it as a quick button") { vm.addQuick(q.category, q.amount, q.note) })
}

/** Debt payoff: a ring for each debt, what's left, when it'll be done, and the next payment. */
@Composable
fun DebtCard(debts: List<Views.Debt>, index: Int, onOpen: () -> Unit) {
    val left = debts.sumOf { it.info.n("remaining") }; val paid = debts.sumOf { it.info.n("paid") }
    SectionCard("Debt payoff", Icons.Rounded.Flag, "sky", trailing = Fmt.money(left) + " left" + (if (debts.size > 1) " across ${debts.size}" else ""), index = index) {
        debts.forEachIndexed { i, d ->
            if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onOpen).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                looniemate.app.ui.ProgressRing(d.pct)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(d.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false)); d.milestone?.let { looniemate.app.ui.Pill(it.second, it.first) } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Money(d.info.n("remaining"), MaterialTheme.typography.titleMedium, weight = FontWeight.SemiBold, countUp = true)
                        Text(" left" + (d.info.s("finish").takeIf { it.isNotEmpty() }?.let { " · done " + Fmt.monthYearShort(it) } ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val next = d.next
                    Text(if (next != null) "Next ${Fmt.money(d.amount)} on ${Fmt.dShort(next)}" + (if (d.info.s("finish").isNotEmpty()) " · ${d.info.n("payments").toInt()} to go" else "") else if (d.info.n("remaining") <= 0.004) "${Fmt.money(d.info.n("paid"))} paid off" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (debts.size > 1) {
            Spacer(Modifier.height(8.dp))
            looniemate.app.ui.LineRow("Paid so far", "${Fmt.money(paid)} of ${Fmt.money(paid + left)}")
            Spacer(Modifier.height(4.dp))
            GrowBar(if (paid + left > 0) (paid / (paid + left)).toFloat() else 0f, LocalExtra.current.good)
        }
    }
}

@Composable
fun ReminderCard(r: Views.Reminder, vm: MainViewModel, open: (Sheet) -> Unit, index: Int) {
    val (fg, bg) = toneColors(r.tone); var more by remember { mutableStateOf(false) }; val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().enter(index).animateContentSize(), colors = CardDefaults.cardColors(containerColor = bg), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (r.body.isNotEmpty()) Text(r.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (r.details.isNotEmpty()) {
                Row(Modifier.clickable { more = !more }, verticalAlignment = Alignment.CenterVertically) {
                    Text(r.detailsTitle, style = MaterialTheme.typography.labelLarge, color = fg); Icon(if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = fg) }
                if (more) r.details.forEach { l -> Text("• " + l.text, style = MaterialTheme.typography.bodySmall,
                    color = if (l.url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (l.url != null) Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(l.url))) } else Modifier) }
            }
            r.button?.let { label -> Row { Spacer(Modifier.weight(1f))
                if (r.action == "backup") Button(onClick = { open(Sheet.Export) }) { Text(label) } else FilledTonalButton(onClick = { vm.reminderAction(r) }) { Text(label) } } }
        }
    }
}

@Composable
fun UpdateBanner(st: Updater.State, vm: MainViewModel, go: (Tab) -> Unit) {
    val r = when (st) { is Updater.State.Available -> st.r; is Updater.State.Ready -> st.r; else -> null } ?: return
    OutlinedCard(Modifier.fillMaxWidth().enter(0)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            looniemate.app.ui.IconBadge(Icons.Rounded.SystemUpdate, "accent")
            Column(Modifier.weight(1f)) { Text("LoonieMate ${r.versionName} is ready", fontWeight = FontWeight.Bold)
                Text("See what's new and install it from Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            TextButton(onClick = { go(Tab.More) }) { Text("Update") }
        }
    }
}

@Composable
private fun WelcomeCard(open: (Sheet) -> Unit) {
    SectionCard("Welcome to LoonieMate", Icons.Rounded.AutoAwesome, "amber") {
        Text("LoonieMate plans every paycheque, tells you when to move money between accounts, protects child benefits, and helps when a month comes up short. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Text("Already using the web version? Export an encrypted backup there and bring it over here. Starting fresh? Add your bank accounts first, then your bills. Setting up income and benefits in the app is coming next; for now do that in the web version.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { open(Sheet.Import) }) { Text("Import a backup") }
            FilledTonalButton(onClick = { open(Sheet.Account(null)) }) { Text("Add an account") }
            FilledTonalButton(onClick = { open(Sheet.Bill(null, "adults")) }) { Text("Add a bill") } }
    }
}

@Composable
fun EmptyIfNone(list: List<Any>, text: String) { if (list.isEmpty()) EmptyNote(text) }

/**
 * Asks about notifications once there's a budget to remind about, and says so when they're off.
 * "Not now" hides it for 30 days. See docs/plans/reminders-and-updates.md.
 */
@Composable
private fun NotificationsCard(vm: MainViewModel) {
    val ok by vm.notifOk.collectAsStateWithLifecycle(); val tick by vm.notifTick.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { vm.refreshNotif() }
    if (ok || System.currentTimeMillis() < vm.prefs.notifSnoozeUntil || tick < 0) return
    val asked = vm.prefs.notifAsked
    SectionCard(if (asked) "Notifications are off" else "Get a heads-up", Icons.Rounded.Notifications, if (asked) "warn" else "accent", index = 1) {
        Text(if (asked) "You won't get reminders to pay bills or move money, or hear about new versions of LoonieMate."
            else "LoonieMate can remind you on days a bill needs paying or money needs moving, and tell you when there's a new version. Only when something needs doing, at times you choose in Settings.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val act = ctx as? android.app.Activity
                val canAsk = looniemate.app.notify.Notifs.needsPermission() && androidx.core.content.ContextCompat.checkSelfPermission(ctx, looniemate.app.notify.Notifs.PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    (!asked || (act != null && androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(act, looniemate.app.notify.Notifs.PERMISSION)))
                vm.prefs.notifAsked = true
                if (canAsk) ask.launch(looniemate.app.notify.Notifs.PERMISSION)
                else try { ctx.startActivity(looniemate.app.notify.Notifs.settingsIntent(ctx)) } catch (_: Exception) {}
            }, modifier = Modifier.testTag("notif-on")) { Text("Turn on") }
            TextButton(onClick = { vm.snoozeNotifCard() }) { Text("Not now") }
        }
    }
}

/**
 * Shown when reminders have arrived more than an hour late twice: the phone is saving battery by putting LoonieMate
 * to sleep. Explains the fix and opens the right settings. Stage 7 of docs/plans/reminders-and-updates.md.
 */
@Composable
private fun LateRemindersCard(vm: MainViewModel) {
    val late by vm.remindersLate.collectAsStateWithLifecycle()
    if (!late) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pm = ctx.getSystemService(android.os.PowerManager::class.java)
    val unrestricted = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
    val samsung = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    SectionCard("Reminders are arriving late", Icons.Rounded.Notifications, "warn", index = 1) {
        Text("Your phone held back LoonieMate's reminders by more than an hour. Phones do this to save battery by putting apps to sleep.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text((if (!unrestricted) "To fix it, open the settings below, tap Battery, and choose Unrestricted. " else "") +
            (if (samsung) "On Samsung phones, also check Settings → Battery → Background usage limits, and take LoonieMate off \"Sleeping apps\" and \"Deep sleeping apps\"."
             else if (unrestricted) "Battery is already set to Unrestricted, so your phone maker may have its own setting. dontkillmyapp.com has the steps for each brand." else ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))) } catch (_: Exception) {} }) { Text("Open settings") }
            TextButton(onClick = { vm.dismissLate() }) { Text("Got it") }
        }
    }
}

/**
 * Something wrong with keeping the budget on this phone (docs/plans/polish-and-audit.md S2, S3): a change that couldn't be
 * saved (usually a full phone), or a saved budget that couldn't be read, so the app started empty. Stays until sorted.
 */
@Composable
private fun StorageProblemCard(vm: MainViewModel, open: (Sheet) -> Unit) {
    val problem by vm.repo.problem.collectAsStateWithLifecycle()
    when (problem) {
        "save" -> SectionCard("Your last change wasn't saved", Icons.Rounded.Warning, "bad", index = 0) {
            Text("LoonieMate couldn't save on this phone. This usually means the phone is full. Free up some space and LoonieMate will save again with your next change. " +
                "Until then, make a backup so nothing is lost if the app closes.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { open(Sheet.Export) }, modifier = Modifier.testTag("problem-backup")) { Text("Make a backup") }
        }
        "damaged" -> SectionCard("Your saved budget couldn't be opened", Icons.Rounded.Warning, "bad", index = 0) {
            Text("The budget file on this phone was damaged, so LoonieMate started empty. A copy of the damaged file was kept. " +
                (if (vm.sync.on) "Your budget is coming back from the sync file. " else "") +
                "If you have a backup, import it to get your budget back.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { open(Sheet.Import) }, modifier = Modifier.testTag("problem-import")) { Text("Import a backup") }
                TextButton(onClick = { vm.repo.dismissProblem() }) { Text("Start fresh") }
            }
        }
        else -> {}
    }
}

/**
 * Personal Expenses this month: what's gone on it against what was planned. Honest about the number but never a
 * telling-off: a calm colour whatever happens, and going over is said plainly, not as a warning.
 */
@Composable
private fun SelfCard(c: looniemate.engine.Obj, toSpending: () -> Unit) {
    val plan = c.n("self"); val spent = c.n("selfSpent"); val left = plan - spent; val (fg, _) = toneColors("sky")
    SectionCard("Personal Expenses", Icons.Rounded.LocalCafe, "sky", trailing = Fmt.monthOnly(c.s("ym")), index = 2) {
        FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Money(spent, MaterialTheme.typography.headlineLarge, countUp = true)
            Text(if (plan > 0) "of ${Fmt.money(plan)} this month" else "this month", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
        }
        if (plan > 0) { Spacer(Modifier.height(10.dp)); GrowBar(minOf(1.0, spent / plan).toFloat(), fg) }
        Spacer(Modifier.height(6.dp))
        Text(when {
            plan <= 0 -> "Logged under Personal Expenses. List what you usually get on the Spending tab and it's set aside each month."
            spent == 0.0 -> "${Fmt.money(plan)} is set aside for you this month. Logging it is up to you."
            left >= 0 -> "${Fmt.money(left)} left for you this month."
            else -> "${Fmt.money(-left)} more than planned this month."
        } + (if (c.n("selfHold") > 0) " ${Fmt.money(c.n("selfHold"))} is held back this month." else ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = toSpending, modifier = Modifier.testTag("self-open")) { Text("See the list") }
    }
}

/** Once accounts, income and bills are set up: find where the family stands today (docs/plans/getting-on-track.md). */
@Composable
private fun StartHereCard(vm: MainViewModel, open: (Sheet) -> Unit) {
    val tick by vm.refreshTodayTick.collectAsStateWithLifecycle()
    if (vm.prefs.onboarded || tick < 0) return
    SectionCard("Let's find where you stand today", Icons.Rounded.AutoAwesome, "teal", index = 1) {
        Text("You're starting partway through the month, so some money is already spent and some bills are paid. Six quick steps set your starting point from today, and show how to get to the next payday.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { open(Sheet.CatchUp(first = true)) }, modifier = Modifier.testTag("start-here")) { Text("Start") }
            TextButton(onClick = { vm.skipOnboarding() }) { Text("Not now") }
        }
    }
}

/**
 * For people already using the app: offered once, and only when the phone's own settings suggest it would help
 * (large text, TalkBack, colour correction…). docs/plans/accessibility.md, stage 6.
 */
@Composable
private fun AccessCard(vm: MainViewModel, open: (Sheet) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hints = androidx.compose.runtime.remember { looniemate.app.data.PhoneHints.read(ctx) }
    var hidden by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(vm.prefs.a11yAsked) }
    if (hidden || hints.isEmpty()) return
    SectionCard("Make LoonieMate easier to use?", Icons.Rounded.AutoAwesome, "accent", index = 1) {
        Text(hints.values.joinToString(" ") + " LoonieMate can match it: bigger or bolder text, stronger contrast, more time and more.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { hidden = true; open(Sheet.Access()) }) { Text("Choose") }
            TextButton(onClick = { vm.prefs.a11yAsked = true; hidden = true }) { Text("Not now") }
        }
    }
}
