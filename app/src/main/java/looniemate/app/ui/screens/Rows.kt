package looniemate.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.ui.Dot
import looniemate.app.ui.MenuItem
import looniemate.app.ui.MenuRow
import looniemate.app.ui.Pill
import looniemate.app.ui.Sheet
import looniemate.app.ui.TickButton
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.theme.Numbers
import looniemate.engine.Core
import looniemate.engine.Fmt
import looniemate.engine.Labels
import looniemate.engine.Obj
import looniemate.engine.n
import looniemate.engine.s
import looniemate.engine.t
import looniemate.engine.*

/** A row's title: "Take out $240.00 cash", "Move $332.53 to Everyday Card", or the bill's name. */
fun rowTitle(e: Obj): String = when {
    e.t("cash") -> "Take out ${Fmt.money(e.n("atm"))} cash" + (if (e.n("parts") > 1) " (${e.n("part").toInt()} of ${e.n("parts").toInt()})" else "")
    e.s("kind") == "move" -> "Move ${Fmt.money(e.n("amount"))} to ${e.s("to")}" + (if (e.s("toAcct").isNotEmpty()) " (${e.s("toAcct")})" else "")
    else -> e.s("name")
}

/** What to know about taking out cash at the ATM (docs/plans/overhaul.md, cash and the ATM), in plain sentences. */
fun cashNotes(e: Obj): List<String> {
    if (!e.t("cash")) return emptyList()
    val out = mutableListOf<String>(); val amt = e.n("amount"); val atm = e.n("atm"); val notes = e.n("notes").toInt().takeIf { it > 0 } ?: 20
    val items = e["items"].arr()
    if (items.isNotEmpty()) out.add("For " + items.joinToString(", ") { "${it.s("name")} ${Fmt.money(it.n("amount"))}" } + ".")
    if (e.n("fromHand") > 0.004) out.add("${Fmt.money(e.n("fromHand"))} of it can come from the cash you already have.")
    if (e.n("extra") > 0.004) out.add("The ATM gives \$$notes bills, so take out ${Fmt.money(atm)}. The extra ${Fmt.money(e.n("extra"))} stays as cash on hand.")
    if (e.t("teller") && e.n("extra") > 0.004) out.add("Or get exactly ${Fmt.money(amt)} from a teller.")
    if (e.n("coins") > 0.004) out.add("Your bank has no tellers. If you need exactly ${Fmt.money(amt)}, get ${Fmt.money(e.n("coins"))} in coins as change when you buy something, or ask a store to break a bill.")
    if (e.n("parts") > 1) out.add("Your ATM's daily limit means taking it out over ${e.n("parts").toInt()} days.")
    if (e.s("split").isNotEmpty()) out.add("Your ATM's daily limit means taking the rest out of ${e.s("split")}.")
    if (e.n("overBy") > 0.004) out.add("That's ${Fmt.money(e.n("overBy"))} more than the ATM lets you take out in time. " +
        (if (e.t("teller")) "Get the rest from a teller." else "Start a day earlier next time, or ask whoever you're paying if they take an e-Transfer."))
    if (e.t("ask")) out.add("Would another account's ATM help instead of going back each day? Choose in Settings → Accounts.")
    return out
}

/**
 * One line in To do or Coming up: money in, a money move or cash to take out, a bill, or a one-time expense. A tap opens
 * it (the bill, or why the move is needed); a long press gives its menu. To do rows have the round tick on the left.
 */
@Composable
fun EventRow(e: Obj, settings: J, vm: MainViewModel, showDate: Boolean = false, compact: Boolean = false, divider: Boolean = true, open: ((Sheet) -> Unit)? = null, interactive: Boolean = true) {
    val x = LocalExtra.current; val cs = MaterialTheme.colorScheme
    fun acct(ref: String) = Core.acctById(settings, ref)?.s("name") ?: ""
    if (divider) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
    val kind = e.s("kind"); var why by remember { mutableStateOf(false) }
    val bill = if (kind == "bill") vm.repo.get("bills/${e.s("id")}") else null
    val menu = if (!interactive) emptyList() else buildList {
        when (kind) {
            "bill" -> {
                add(MenuItem(if (e.t("paid")) "Not paid after all" else "Paid", Icons.Rounded.Check) { vm.togglePaid(e) })
                if (open != null && bill != null) {
                    if (bill.t("deferrable") && e.s("deferredFrom").isEmpty()) add(MenuItem("Pay part later…", Icons.Rounded.Schedule) { open(Sheet.Adjust(e.s("ym"), e.s("id"), true)) })
                    if (bill["essential"].isFalse() && e.s("deferredFrom").isEmpty()) add(MenuItem("Give up this month…", Icons.Rounded.RemoveCircleOutline) { open(Sheet.Adjust(e.s("ym"), e.s("id"), false)) })
                    add(MenuItem("Change the bill", Icons.Rounded.Edit) { open(Sheet.Bill(e.s("id"), bill.or("group", "adults"))) })
                    add(MenuItem("Pause it", Icons.Rounded.Pause) { vm.pauseBill(e.s("id"), true) })
                }
            }
            "move" -> {
                add(MenuItem(if (e.t("done")) "Not done after all" else "Done", Icons.Rounded.Check) { vm.toggleMove(e) })
                add(MenuItem(if (e.t("cash")) "About this cash" else "Why this move?", Icons.AutoMirrored.Rounded.HelpOutline) { why = true })
                if (open != null) add(MenuItem("Change the accounts it uses", Icons.Rounded.AccountBalance) { open(Sheet.Settings("accounts")) })
            }
        }
    }
    val tap: (() -> Unit)? = if (!interactive) null else when { kind == "bill" && open != null && bill != null -> ({ open(Sheet.Bill(e.s("id"), bill.or("group", "adults"))) }); kind == "move" -> ({ why = true }); else -> null }
    MenuRow(menu, onClick = tap, spoken = spokenRow(e, ::acct), clickLabel = if (kind == "bill") "Open the bill" else if (kind == "move") "Why" else null, vertical = 8.dp) {
        val tick = !compact && (kind == "bill" || kind == "move")
        when {
            tick && kind == "bill" -> TickButton(e.t("paid"), "Paid") { vm.togglePaid(e) }
            tick -> TickButton(e.t("done"), "Done") { vm.toggleMove(e) }
            kind == "move" -> Box(Modifier.size(24.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(if (e.t("cash")) Icons.Rounded.LocalAtm else Icons.AutoMirrored.Rounded.ArrowForward, null, tint = cs.primary, modifier = Modifier.size(15.dp)) }
            kind == "in" -> Dot(if (e.t("protect")) x.baby else x.good, 10.dp, round = true)
            else -> Dot(x.group(e.s("group")))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tick && e.t("cash")) Icon(Icons.Rounded.LocalAtm, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                Text(rowTitle(e), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (e.t("overdue")) Pill("Overdue", "bad")
                if (e.t("example")) Pill("Example", "warn")
                if (e.s("deferredFrom").isNotEmpty()) Pill("From ${Fmt.monthShort(e.s("deferredFrom"))}", "warn")
            }
            val meta = when (kind) {
                "move" -> listOf("From " + e.s("from") + (if (e.s("fromAcct").isNotEmpty()) " (${e.s("fromAcct")})" else ""),
                    if (compact || e.t("cash")) "" else e.s("why"))
                "in" -> listOf(if (compact) "" else e.s("note"))
                "oneoff" -> listOf("One-time", Labels.OGROUPS[e.s("group")] ?: "", acct(e.s("account")))
                else -> listOf(if (e.t("overdue")) "Was due " + Fmt.dShort(e.s("date")) else "", acct(e.s("account")), if (compact) "" else Labels.shortMethod(e.s("method")))
            }.filter { it.isNotEmpty() }.toMutableList()
            if (showDate) meta.add(0, Fmt.dLabel(e.s("date")))
            if (e.t("cash") && !compact) e["items"].arr().takeIf { it.isNotEmpty() }?.let { meta.add("for " + it.joinToString(", ") { i -> i.s("name") }) }
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        when (kind) {
            "in" -> Text("+" + Fmt.money(e.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers), color = x.good, fontWeight = FontWeight.SemiBold)
            "move" -> if (compact) Text(Fmt.money(if (e.t("cash")) e.n("atm") else e.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers))
            "bill" -> Text(Fmt.money(e.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers), fontWeight = FontWeight.SemiBold)
            else -> Text(Fmt.money(e.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers))
        }
    }
    if (why) MoveDialog(e, vm) { why = false }
}

/** Why a money move is needed, or what to know about cash to take out, with Done right there. */
@Composable
fun MoveDialog(e: Obj, vm: MainViewModel, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(rowTitle(e)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("From ${e.s("from")}" + (if (!e.t("cash")) " to ${e.s("to")}" else "") + " · ${Fmt.dLabel(e.s("date"))}", fontWeight = FontWeight.SemiBold)
            if (e.s("why").isNotEmpty() && !e.t("cash")) Text(e.s("why"))
            cashNotes(e).forEach { Text(it) }
            if (!e.t("cash")) Text("Do it in your banking app, then tick it off here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = { vm.toggleMove(e); close() }) { Text(if (e.t("done")) "Not done after all" else "Done") } },
        dismissButton = { TextButton(onClick = close) { Text("Close") } })
}

/** Groups events under day headings ("Today", "Tomorrow", "Thu, Oct 29"). */
@Composable
fun EventList(list: List<Obj>, today: String, settings: J, vm: MainViewModel, open: ((Sheet) -> Unit)? = null) {
    var last = ""
    list.forEach { e ->
        val d = e.s("date")
        if (d != last) { last = d; looniemate.app.ui.DayHeader((if (e.t("overdue")) "Past due · " else "") + Fmt.relDay(d, today), d == today) }
        EventRow(e, settings, vm, divider = false, open = open)
    }
}

/** What a screen reader says for a row: "Bell, $95.00, due Wed, Oct 28, Main Chequing, not paid". */
fun spokenRow(e: Obj, acct: (String) -> String): String {
    val date = Fmt.dLabel(e.s("date")); val amt = Fmt.money(e.n("amount"))
    return when (e.s("kind")) {
        "move" -> listOf(rowTitle(e), "from ${e.s("from")}", "on $date", if (e.t("cash")) cashNotes(e).firstOrNull() ?: "" else e.s("why"), if (e.t("done")) "done" else "not done")
        "in" -> listOf("Money in: ${e.s("name")}", "plus $amt", "on $date", e.s("note"))
        "oneoff" -> listOf("One-time expense: ${e.s("name")}", amt, "on $date")
        else -> listOf(e.s("name"), amt, if (e.t("overdue")) "overdue since $date" else "due $date", Labels.GROUPS[e.s("group")]?.label ?: "", acct(e.s("account")),
            Labels.shortMethod(e.s("method")), if (e.t("paid")) "paid" else "not paid")
    }.filter { it.isNotBlank() }.joinToString(", ")
}
