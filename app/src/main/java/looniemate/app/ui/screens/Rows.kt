package looniemate.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.LocalAtm
import looniemate.app.ui.ActionRow
import looniemate.app.ui.RowAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.ui.DoneButton
import looniemate.app.ui.Dot
import looniemate.app.ui.Pill
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.theme.Numbers
import looniemate.engine.Core
import looniemate.engine.Fmt
import looniemate.engine.J
import looniemate.engine.Labels
import looniemate.engine.Obj
import looniemate.engine.n
import looniemate.engine.s
import looniemate.engine.t
import looniemate.engine.*

/**
 * One line in Coming up / To do: money in, a money move or cash to take out, a bill, or a one-time expense. Bills and
 * moves have a round tick; tap opens the bill (or explains the move), long-press offers the rest (docs/plans/overhaul.md).
 */
@Composable
fun EventRow(e: Obj, settings: J, vm: MainViewModel, showDate: Boolean = false, compact: Boolean = false, divider: Boolean = true) {
    val x = LocalExtra.current; val cs = MaterialTheme.colorScheme; val open = looniemate.app.ui.LocalOpen.current
    fun acct(ref: String) = Core.acctById(settings, ref)?.s("name") ?: ""
    if (divider) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
    val kind = e.s("kind"); val cash = e.t("cash")
    var why by remember { mutableStateOf(false) }
    val bill = if (kind == "bill") vm.repo.get("bills/" + e.s("id")) else null
    val actions = when {
        compact -> emptyList()
        kind == "bill" -> billActions(e, bill, vm, open)
        kind == "move" -> listOf(RowAction(if (e.t("done")) "Not done" else "Done") { vm.toggleMove(e) },
            RowAction(if (cash) "What's this cash for?" else "Why this move?") { why = true },
            RowAction("Change the accounts it uses") { open(looniemate.app.ui.Sheet.Settings("accounts")) })
        else -> emptyList()
    }
    val title = when (kind) {
        "move" -> if (cash) "Take out ${Fmt.money(e.n("amount"))} cash" else "Move ${Fmt.money(e.n("amount"))} to ${e.s("to")}" + (if (e.s("toAcct").isNotEmpty()) " (${e.s("toAcct")})" else "")
        else -> e.s("name") }
    val meta = when (kind) {
        "move" -> listOf((if (cash) "From " else "From ") + e.s("from") + (if (e.s("fromAcct").isNotEmpty()) " (${e.s("fromAcct")})" else ""), if (compact) "" else e.s("why"))
        "in" -> listOf(if (compact) "" else e.s("note"))
        "oneoff" -> listOf("One-time", Labels.OGROUPS[e.s("group")] ?: "", acct(e.s("account")))
        else -> listOf(acct(e.s("account")), if (e.t("overdue")) "was due " + Fmt.dShort(e.s("date")) else "", Labels.shortMethod(e.s("method")))
    }.filter { it.isNotEmpty() }.toMutableList()
    if (showDate) meta.add(0, Fmt.dLabel(e.s("date")))
    val tick: Boolean? = if (compact) null else when (kind) { "bill" -> e.t("paid"); "move" -> e.t("done"); else -> null }
    ActionRow(title, spokenRow(e, ::acct), subtitle = meta.joinToString(" · "),
        amount = when (kind) { "in" -> "+" + Fmt.money(e.n("amount")); "move" -> null; else -> Fmt.money(e.n("amount")) }, amountTone = if (kind == "in") "good" else "",
        tick = tick, onTick = { if (kind == "bill") vm.togglePaid(e) else vm.toggleMove(e) },
        tickLabel = if (kind == "bill") "Mark paid" else "Mark done", untickLabel = if (kind == "bill") "Mark not paid" else "Mark not done",
        overdue = e.t("overdue") && !compact,
        leading = { when (kind) {
            "move" -> Box(Modifier.size(24.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(if (cash) Icons.Rounded.LocalAtm else Icons.AutoMirrored.Rounded.ArrowForward, null, tint = cs.primary, modifier = Modifier.size(15.dp)) }
            "in" -> Dot(if (e.t("protect")) x.baby else x.good, 10.dp, round = true)
            else -> Dot(x.group(e.s("group"))) } },
        pills = if (e.t("example") || e.s("deferredFrom").isNotEmpty()) ({ if (e.t("example")) Pill("Example", "warn")
            if (e.s("deferredFrom").isNotEmpty()) Pill("Put off from ${Fmt.monthShort(e.s("deferredFrom"))}", "warn") }) else null,
        onClick = when { compact && kind == "bill" -> ({ open(looniemate.app.ui.Sheet.Bill(e.s("id"), bill?.s("group")?.ifEmpty { "adults" } ?: "adults")) })
            kind == "bill" && bill != null -> ({ open(looniemate.app.ui.Sheet.Bill(e.s("id"), bill.s("group").ifEmpty { "adults" })) })
            kind == "move" -> ({ why = true }); else -> null },
        actions = actions, tag = "row-" + e.s("key").ifEmpty { e.s("name") })
    if (why) looniemate.app.ui.InfoDialog(if (cash) "Take out cash" else "Why this move?", whyText(e)) { why = false }
}

/** The long-press choices for a bill: paid, pay part later, give up (non-essential), change, pause. */
fun billActions(e: Obj, bill: Obj?, vm: MainViewModel, open: (looniemate.app.ui.Sheet) -> Unit): List<RowAction> = buildList {
    add(RowAction(if (e.t("paid")) "Not paid" else "Paid") { vm.togglePaid(e) })
    if (bill != null) {
        val ym = e.s("ym").ifEmpty { ymOf(e.s("date")) }
        if (bill.t("deferrable")) add(RowAction("Pay part later…") { open(looniemate.app.ui.Sheet.Adjust(ym, e.s("id"), true)) })
        if (bill["essential"].isFalse()) add(RowAction("Give up this month…") { open(looniemate.app.ui.Sheet.Adjust(ym, e.s("id"), false)) })
        add(RowAction("Change the bill") { open(looniemate.app.ui.Sheet.Bill(e.s("id"), bill.s("group").ifEmpty { "adults" })) })
        add(RowAction("Pause it") { vm.setBillActive(e.s("id"), false) })
    }
}

/** What a move or a cash trip is for, in a few plain sentences. */
fun whyText(e: Obj): String {
    if (!e.t("cash")) return e.s("why").ifEmpty { "Keeps each account ready for what it pays." } + (if (e.s("from").isNotEmpty()) "\n\nFrom ${e.s("from")} to ${e.s("to")}." else "")
    val items = e["items"].arr().filterIsInstance<Obj>()
    val out = mutableListOf<String>()
    if (items.isNotEmpty()) out += "For " + items.joinToString(", ") { "${it.s("name")} ${Fmt.money(it.n("amount"))}" } + "."
    else out += e.s("why") + "."
    if (e.n("extra") > 0.004) out += "The ATM gives out whole bills, so ${Fmt.money(e.n("extra"))} extra stays as cash on hand." +
        (if (e.t("tellers")) " Or get exactly ${Fmt.money(e.n("exact"))} from a teller." else "")
    if (e.n("coins") > 0.004 && !e.t("tellers")) out += "Your bank has no tellers. Get ${Fmt.money(e.n("coins"))} in coins as change when you buy something, or ask a store to break a bill."
    if (e.n("short") > 0.004) out += "The ATM can't give out ${Fmt.money(e.n("short"))} of it in time. " +
        (if (e.t("tellers")) "Get the rest from a teller." else "Start a day earlier, or send an e-Transfer if they take one.")
    if (e.t("overLimit")) out += "This is more than the card's daily limit. Choose how to handle that on the account's page in Settings → Accounts."
    return out.joinToString("\n\n")
}

/** Groups events under day headings ("Today", "Tomorrow", "Thu, Oct 29"). */
@Composable
fun EventList(list: List<Obj>, today: String, settings: J, vm: MainViewModel) {
    var last = ""
    list.forEach { e ->
        val d = e.s("date")
        if (d != last) { last = d; looniemate.app.ui.DayHeader((if (e.t("overdue")) "Past due · " else "") + Fmt.relDay(d, today), d == today) }
        EventRow(e, settings, vm, divider = false)
    }
}

/** What a screen reader says for a row: "Bell, $95.00, due Wed, Oct 28, Adults, Main Chequing, not paid". */
fun spokenRow(e: Obj, acct: (String) -> String): String {
    val date = Fmt.dLabel(e.s("date")); val amt = Fmt.money(e.n("amount"))
    return when (e.s("kind")) {
        "move" -> if (e.t("cash")) listOf("Take out $amt cash", "from ${e.s("from")}", "on $date", e.s("why"), if (e.t("done")) "done" else "not done")
            else listOf("Move $amt to ${e.s("to")}", "from ${e.s("from")}", "on $date", e.s("why"), if (e.t("done")) "done" else "not done")
        "in" -> listOf("Money in: ${e.s("name")}", "plus $amt", "on $date", e.s("note"))
        "oneoff" -> listOf("One-time expense: ${e.s("name")}", amt, "on $date")
        else -> listOf(e.s("name"), amt, if (e.t("overdue")) "overdue since $date" else "due $date", Labels.GROUPS[e.s("group")]?.label ?: "", acct(e.s("account")),
            Labels.shortMethod(e.s("method")), if (e.t("paid")) "paid" else "not paid")
    }.filter { it.isNotBlank() }.joinToString(", ")
}
