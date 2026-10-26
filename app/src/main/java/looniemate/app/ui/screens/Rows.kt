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

/** One line in Coming up / To do: money in, a money move, a bill, or a one-time expense. */
@Composable
fun EventRow(e: Obj, settings: J, vm: MainViewModel, showDate: Boolean = false, compact: Boolean = false, divider: Boolean = true) {
    val x = LocalExtra.current; val cs = MaterialTheme.colorScheme
    fun acct(ref: String) = Core.acctById(settings, ref)?.s("name") ?: ""
    if (divider) HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.6f))
    /* for screen readers: the whole row as one sentence, with Paid or Done offered as an action (docs/plans/accessibility.md) */
    val spoken = spokenRow(e, ::acct)
    val actions = when {
        compact -> emptyList()
        e.s("kind") == "bill" -> listOf(CustomAccessibilityAction(if (e.t("paid")) "Mark not paid" else "Mark paid") { vm.togglePaid(e); true })
        e.s("kind") == "move" -> listOf(CustomAccessibilityAction(if (e.t("done")) "Mark not done" else "Mark done") { vm.toggleMove(e); true })
        else -> emptyList()
    }
    Row(Modifier.fillMaxWidth().padding(vertical = looniemate.app.ui.rowPad(10.dp)).clearAndSetSemantics { contentDescription = spoken; customActions = actions },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val kind = e.s("kind")
        when (kind) {
            "move" -> Box(Modifier.size(24.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = cs.primary, modifier = Modifier.size(15.dp)) }
            "in" -> Dot(if (e.t("protect")) x.baby else x.good, 10.dp, round = true)
            else -> Dot(x.group(e.s("group")))
        }
        Column(Modifier.weight(1f)) {
            val title = when { e.t("cash") -> "Take out ${Fmt.money(e.n("atm"))} cash" + (if (e.n("parts") > 1) " (${e.n("part").toInt()} of ${e.n("parts").toInt()})" else "")
                kind == "move" -> "Move ${Fmt.money(e.n("amount"))} to ${e.s("to")}" + (if (e.s("toAcct").isNotEmpty()) " (${e.s("toAcct")})" else ""); else -> e.s("name") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = if (e.t("overdue")) x.bad else cs.onSurface, modifier = Modifier.weight(1f, fill = false))
                if (e.t("example")) Pill("Example", "warn")
                if (e.s("deferredFrom").isNotEmpty()) Pill("Deferred from ${Fmt.monthShort(e.s("deferredFrom"))}", "warn")
            }
            val meta = when (kind) {
                "move" -> listOf("From " + e.s("from") + (if (e.s("fromAcct").isNotEmpty()) " (${e.s("fromAcct")})" else ""), if (compact) "" else e.s("why"))
                "in" -> listOf(if (compact) "" else e.s("note"))
                "oneoff" -> listOf("One-time", Labels.OGROUPS[e.s("group")] ?: "", acct(e.s("account")))
                else -> listOf(if (e.t("overdue")) "Was due " + Fmt.dShort(e.s("date")) else "", if (compact) "" else (Labels.GROUPS[e.s("group")]?.label ?: ""), acct(e.s("account")), Labels.shortMethod(e.s("method")))
            }.filter { it.isNotEmpty() }.toMutableList()
            if (showDate) meta.add(0, Fmt.dLabel(e.s("date")))
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (kind) {
                "in" -> Text("+" + Fmt.money(e.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers), color = x.good, fontWeight = FontWeight.SemiBold)
                "move" -> if (!compact) DoneButton(e.t("done"), "Mark done", "Done") { vm.toggleMove(e) }
                "bill" -> { Text(Fmt.money(e.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers), fontWeight = FontWeight.SemiBold)
                    if (!compact) DoneButton(e.t("paid"), "Mark paid", "Paid") { vm.togglePaid(e) } }
                else -> Text(Fmt.money(e.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers))
            }
        }
    }
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
        "move" -> listOf(if (e.t("cash")) "Take out ${Fmt.money(e.n("atm"))} cash" else "Move $amt to ${e.s("to")}", "from ${e.s("from")}", "on $date", e.s("why"), if (e.t("done")) "done" else "not done")
        "in" -> listOf("Money in: ${e.s("name")}", "plus $amt", "on $date", e.s("note"))
        "oneoff" -> listOf("One-time expense: ${e.s("name")}", amt, "on $date")
        else -> listOf(e.s("name"), amt, if (e.t("overdue")) "overdue since $date" else "due $date", Labels.GROUPS[e.s("group")]?.label ?: "", acct(e.s("account")),
            Labels.shortMethod(e.s("method")), if (e.t("paid")) "paid" else "not paid")
    }.filter { it.isNotBlank() }.joinToString(", ")
}
