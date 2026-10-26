@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package looniemate.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
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

/**
 * Today: what to do, and am I okay (docs/plans/overhaul.md). The date and one status chip, at most one notice, the To do
 * list (overdue first, with what's done today folded underneath), the food and Personal Expenses money, quick buttons,
 * and the next 7 days. Nothing else.
 */
@Composable
fun TodayScreen(vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit, go: (Tab) -> Unit, state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()) {
    val m by vm.todayModel.collectAsStateWithLifecycle()
    val model = m ?: return
    val s = vm.settings()
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().enter(0), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.weekday(model.today).uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    Text(Fmt.longDate(model.today), style = MaterialTheme.typography.displaySmall)
                }
                /* tap for the why, on Month */
                if (!model.isNew) Surface(onClick = { go(Tab.Month) }, shape = CircleShape, color = Color.Transparent, modifier = Modifier.testTag("status-chip")) { StatusBadge(model.status.tone, model.status.label) }
            }
        }
        item { Notice(vm, open, go, model.isNew) }
        if (model.isNew) { item { WelcomeCard(vm, open) }; return@LazyColumn }
        if (model.readyToStart || (!vm.prefs.onboarded && vm.prefs.setupStep in 2 until looniemate.app.ui.sheets.SETUP_STEPS)) item { StartHereCard(vm, open) }
        item { TodoCard(model, s, vm, open) }
        item { MoneyCard(model) { go(Tab.Spending) } }
        item { QuickRow(model, vm, open) }
        if (model.next.isNotEmpty()) item {
            SectionCard("Coming up", Icons.Rounded.CalendarMonth, trailing = "Next 7 days", index = 4) {
                model.next.forEachIndexed { i, e -> EventRow(e, s, vm, showDate = true, compact = true, divider = i > 0, open = open) }
            }
        }
    }
}

/** The To do list: overdue first, then today, each with its tick; money landing today on top; done today folded below. */
@Composable
private fun TodoCard(model: looniemate.app.TodayModel, s: J, vm: MainViewModel, open: (Sheet) -> Unit) {
    val n = model.todo.size; val x = LocalExtra.current; var showDone by remember { mutableStateOf(false) }
    val fold = if (looniemate.app.ui.theme.LocalFx.current) Modifier.animateContentSize(androidx.compose.animation.core.tween(looniemate.app.ui.Motion.FOLD)) else Modifier
    SectionCard(if (n > 0) "To do" else "All done for today", if (n > 0) Icons.Rounded.Checklist else Icons.Rounded.Check, if (n > 0) "amber" else "green",
        modifier = fold.testTag("todo"), trailing = if (n > 0) "$n to do" else null, index = 1) {
        model.incoming.forEach { e -> Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            looniemate.app.ui.Dot(if (e.t("protect")) x.baby else x.good, 8.dp, round = true)
            Text("+${Fmt.money(e.n("amount"))} ${e.s("name")} lands today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (n == 0) {
            val nx = model.next.firstOrNull()
            Text(if (nx != null) "Next: " + nextText(nx) + " on " + Fmt.dLabel(nx.s("date")) + "." else "Nothing else coming this week.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val overdue = model.todo.filter { it.t("overdue") }; val now = model.todo.filter { !it.t("overdue") }
        if (overdue.isNotEmpty() && now.isNotEmpty()) looniemate.app.ui.DayHeader("Overdue")
        overdue.forEachIndexed { i, e -> key(e.s("kind") + e.s("key") + e.s("ym")) { TodoRow(e, s, vm, open, i > 0) } }
        if (overdue.isNotEmpty() && now.isNotEmpty()) looniemate.app.ui.DayHeader("Today", true)
        now.forEachIndexed { i, e -> key(e.s("kind") + e.s("key") + e.s("ym")) { TodoRow(e, s, vm, open, i > 0) } }
        if (model.done.isNotEmpty()) {
            if (n > 0) HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { showDone = !showDone }.padding(vertical = 10.dp).testTag("done-fold"),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = x.good, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Done today · ${model.done.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(if (showDone) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (showDone) "Hide what's done" else "Show what's done", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showDone) model.done.forEach { e -> key("done" + e.s("key")) { EventRow(e, s, vm, divider = false, open = open) } }
        }
    }
}

/** One To do item: a bill or a money move (EventRow), or reporting income, taxes, locking a month or the balance check. */
@Composable
private fun TodoRow(e: Obj, s: J, vm: MainViewModel, open: (Sheet) -> Unit, divider: Boolean) {
    val kind = e.s("kind")
    if (kind == "bill" || kind == "move") { EventRow(e, s, vm, divider = divider, open = open); return }
    var help by remember { mutableStateOf(false) }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    val (title, meta) = when (kind) {
        "report" -> (if (e.t("ei")) "Do your EI report for ${e.s("name")}" else "Report ${Fmt.monthOnly(e.s("forYm"))}'s income for ${e.s("name")}") to
            (if (e.t("overdue")) "Was due ${Fmt.dShort(e.s("date"))}" else if (e.s("date") == vm.today.value) "Due today" else "Due ${Fmt.dShort(e.s("date"))}")
        "tax" -> "File your ${e.s("year").toInt() - 1} taxes" to (if (e.t("overdue")) "As soon as you can. Benefits depend on it." else "By April 30. Benefits depend on it, even with no income.")
        "lock" -> "Lock ${Fmt.monthName(e.s("ym"))}" to "It's finished. Locking keeps its numbers as they were."
        else -> "Check your balances" to (e.n("days").toInt().takeIf { e["days"] != null && e["days"] != J.Null }?.let { "Last checked $it days ago" } ?: "Once a week keeps the plan honest")
    }
    val act: () -> Unit = when (kind) { "report" -> ({ vm.markReported(e) }); "tax" -> ({ vm.markTaxFiled(e.s("year")) }); "lock" -> ({ open(Sheet.MonthIncome(e.s("ym"))) }); else -> ({ open(Sheet.Checkin) }) }
    val menu = buildList {
        add(looniemate.app.ui.MenuItem(when (kind) { "report" -> "Reported"; "tax" -> "We've filed"; "lock" -> "Review and lock"; else -> "Check them now" }, onClick = act))
        if (kind == "report" || kind == "tax") add(looniemate.app.ui.MenuItem(if (kind == "tax") "Free ways to file" else "How to report") { help = true })
    }
    looniemate.app.ui.MenuRow(menu, onClick = if (kind == "lock" || kind == "check") act else ({ help = true }), spoken = "$title, $meta", vertical = 8.dp) {
        looniemate.app.ui.TickButton(false, when (kind) { "report" -> "Reported"; "tax" -> "Filed"; "lock" -> "Lock it"; else -> "Check them" }, act)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                if (e.t("overdue")) looniemate.app.ui.Pill("Overdue", "bad") }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (help) HelpDialog(kind, e) { help = false }
}

/** The help behind a report or the taxes: what to tell them, and free ways to file. */
@Composable
private fun HelpDialog(kind: String, e: Obj, close: () -> Unit) {
    val ctx = LocalContext.current
    val lines: List<Pair<String, String?>> = if (kind == "tax") listOf(
        "CRA SimpleFile: free for people with low or no income and simple taxes. File online, or by phone or paper if the CRA sends you an invitation." to Views.SIMPLEFILE,
        "StudioTax: free tax software if your income is under \$20,000 (\$40,000 combined for a couple filing together)." to Views.STUDIOTAX,
        "Free tax clinics: volunteers file it for you." to Views.TAX_HELP,
        "A spouse or partner needs to file too. The CCB, CGEB and provincial benefits depend on it every year." to null)
    else if (e.t("ei")) listOf("Every two weeks: any work and pay, and any days you were sick or away. The payment follows the report." to null)
    else listOf("Tell them what came in last month, and changes like a new baby, someone moving in or out, starting or stopping a job, or a new address." to null,
        "More on reporting" to Views.REPORT_HELP)
    AlertDialog(onDismissRequest = close, title = { Text(if (kind == "tax") "Free ways to file" else "Reporting income") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { lines.forEach { (t, url) ->
            Text(t, color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = if (url != null) Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } else Modifier) } } },
        confirmButton = { TextButton(onClick = close) { Text("Close") } })
}

/** Food and Personal Expenses money in one card: what's left, plainly. Never red for Personal Expenses. Tap either for Spending. */
@Composable
private fun MoneyCard(model: looniemate.app.TodayModel, toSpending: () -> Unit) {
    val fp = model.food; val x = LocalExtra.current; val left = fp.n("left")
    SectionCard("Spending money", Icons.Rounded.Restaurant, "green", index = 2) {
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Open Spending", onClick = toSpending).testTag("food-open").padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Food", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Money(left, MaterialTheme.typography.titleLarge, weight = FontWeight.SemiBold, color = if (left < 0) x.bad else MaterialTheme.colorScheme.onSurface)
            }
            Text(if (left >= 0) "left until ${Fmt.dShort(fp.s("end"))} · about ${Fmt.money(fp.n("perDay"))} a day" else "over for this half of the month",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            val allow = fp.n("allow")
            GrowBar(if (allow > 0) (fp.n("spent") / allow).toFloat() else 0f, if (left < 0) x.bad else x.good)
        }
        model.self?.let { c -> val plan = c.n("self"); val spent = c.n("selfSpent"); val sl = plan - spent
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClickLabel = "Open Spending", onClick = toSpending).testTag("self-open").padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Personal Expenses", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Money(if (plan > 0) maxOf(0.0, sl) else spent, MaterialTheme.typography.titleLarge, weight = FontWeight.SemiBold)
                }
                Text(when { plan <= 0 -> "logged this month"; sl >= 0 -> "left for you this month"; else -> "${Fmt.money(-sl)} more than planned this month" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plan > 0) { Spacer(Modifier.height(6.dp)); GrowBar(minOf(1.0, spent / plan).toFloat(), x.sky) }
            }
        }
    }
}

/** Log it: the quick buttons in a row that scrolls sideways, then Something else. */
@Composable
private fun QuickRow(model: looniemate.app.TodayModel, vm: MainViewModel, open: (Sheet) -> Unit) {
    Column(Modifier.enter(3)) {
        Text("Log it", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp).semantics { heading() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            model.quick.forEach { q -> QuickButton(q.label, Fmt.money(q.amount), q.fav, menu = quickMenu(q, vm, open)) { vm.quickLog(q) } }
            QuickButton("Something else", "", true, more = true) { open(Sheet.Log()) }
        }
        if (model.quick.isEmpty()) Text("Things you log often show up here as one-tap buttons.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    }
}

/** At most one notice at a time, the most important first: a saving problem, an update, notifications, late reminders, accessibility, a backup. */
@Composable
private fun Notice(vm: MainViewModel, open: (Sheet) -> Unit, go: (Tab) -> Unit, isNew: Boolean) {
    val problem by vm.repo.problem.collectAsStateWithLifecycle(); val upd by vm.updater.state.collectAsStateWithLifecycle()
    val ok by vm.notifOk.collectAsStateWithLifecycle(); val tick by vm.notifTick.collectAsStateWithLifecycle(); val late by vm.remindersLate.collectAsStateWithLifecycle()
    val lastBackup by vm.prefs.lastBackupFlow.collectAsStateWithLifecycle(); val today by vm.today.collectAsStateWithLifecycle()
    val ctx = LocalContext.current; val hints = remember { looniemate.app.data.PhoneHints.read(ctx) }
    var accessHidden by remember { mutableStateOf(vm.prefs.a11yAsked) }
    val days = lastBackup?.let { diffDays(it.take(10), today) }
    when {
        problem != null -> StorageProblemCard(vm, open)
        upd is Updater.State.Available || upd is Updater.State.Ready -> UpdateBanner(upd, vm, go)
        isNew -> {}
        !ok && System.currentTimeMillis() >= vm.prefs.notifSnoozeUntil && tick >= 0 -> NotificationsCard(vm)
        late -> LateRemindersCard(vm)
        !accessHidden && hints.isNotEmpty() -> AccessCard(vm, open) { accessHidden = true }
        !vm.sync.on && (days == null || days > 14) -> SectionCard(if (days == null) "Make a backup" else "Last backup was $days days ago", Icons.Rounded.Backup, "warn", index = 0) {
            Text("Your budget lives only on this phone. A backup keeps it safe if the phone is lost or reset.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp)); Button(onClick = { open(Sheet.Export) }) { Text("Back up now") } }
        else -> {}
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
private fun WelcomeCard(vm: MainViewModel, open: (Sheet) -> Unit) {
    val step = vm.prefs.setupStep
    SectionCard("Welcome to LoonieMate", Icons.Rounded.AutoAwesome, "amber") {
        Text("LoonieMate plans every paycheque, tells you when to move money between accounts, protects child benefits, and helps when a month comes up short. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Text("Starting fresh? Seven short steps set it up: your household, accounts, income, bills, food money, money for yourself, then where you stand today. Already using LoonieMate somewhere else? Bring your encrypted backup over.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { open(Sheet.Setup(maxOf(1, step))) }, modifier = Modifier.testTag("setup-start")) { Text(if (step > 1) "Continue setup (step $step of 7)" else "Set up step by step") }
            FilledTonalButton(onClick = { open(Sheet.Import) }) { Text("Import a backup") } }
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
    val step = vm.prefs.setupStep
    if (step in 2 until looniemate.app.ui.sheets.SETUP_STEPS) { SectionCard("Finish setting up", Icons.Rounded.AutoAwesome, "teal", index = 1) {
        Text("You got to step $step of ${looniemate.app.ui.sheets.SETUP_STEPS}. Pick up where you left off, or skip the rest.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { open(Sheet.Setup(step)) }, modifier = Modifier.testTag("setup-continue")) { Text("Continue setup") }
            TextButton(onClick = { vm.skipOnboarding() }) { Text("Not now") } } }
        return }
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
private fun AccessCard(vm: MainViewModel, open: (Sheet) -> Unit, onHide: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hints = androidx.compose.runtime.remember { looniemate.app.data.PhoneHints.read(ctx) }
    var hidden by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (hidden || hints.isEmpty()) return
    SectionCard("Make LoonieMate easier to use?", Icons.Rounded.AutoAwesome, "accent", index = 1) {
        Text(hints.values.joinToString(" ") + " LoonieMate can match it: bigger or bolder text, stronger contrast, more time and more.",
            style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { hidden = true; onHide(); open(Sheet.Access()) }) { Text("Choose") }
            TextButton(onClick = { vm.prefs.a11yAsked = true; hidden = true; onHide() }) { Text("Not now") }
        }
    }
}
