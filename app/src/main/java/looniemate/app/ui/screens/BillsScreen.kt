@file:OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package looniemate.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.BillRow
import looniemate.app.MainViewModel
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.LineRow
import looniemate.app.ui.Money
import looniemate.app.ui.Pill
import looniemate.app.ui.ProgressRing
import looniemate.app.ui.SectionCard
import looniemate.app.ui.Sheet
import looniemate.app.ui.enter
import looniemate.app.ui.toneColors
import looniemate.app.ui.theme.Numbers
import looniemate.engine.Core
import looniemate.engine.Fmt
import looniemate.engine.Labels
import looniemate.engine.Views
import looniemate.engine.isDate
import looniemate.engine.n
import looniemate.engine.or
import looniemate.engine.s
import looniemate.engine.t
import looniemate.engine.*

/**
 * Bills (docs/plans/overhaul.md): one list in the order they're next due, with filter chips (All, Adults, Kids, Pets,
 * Debts). Debts get their rings and payoff dates under Debts, and a one-line summary on the others.
 */
@Composable
fun BillsScreen(vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit, state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState()) {
    val bm by vm.billsModel.collectAsStateWithLifecycle()
    val m = bm ?: return
    var filter by rememberSaveable { mutableStateOf("all") }
    val s = vm.settings(); val x = looniemate.app.ui.theme.LocalExtra.current
    val all = m.rows.values.flatten()
    val shown = (if (filter == "all" || filter == "debts") all else m.rows[filter].orEmpty()).let { l -> if (filter == "debts") l.filter { it.bill.t("debt") } else l }
        .sortedWith(compareBy<BillRow> { if (it.bill["active"].isFalse()) 1 else 0 }.thenBy { it.next ?: "9999" }.thenBy { it.bill.s("name").lowercase() })
    val total = if (filter == "all" || filter == "debts") shown.sumOf { it.line?.n("amount") ?: 0.0 } else (m.totals[filter] ?: 0.0)
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).enter(0), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("all" to "All") + Labels.GROUPS.map { it.key to it.value.label } + (if (m.debts.isNotEmpty()) listOf("debts" to "Debts") else emptyList())).forEach { (k, l) ->
                    FilterChip(filter == k, { filter = k; if (k in Labels.GROUPS) vm.prefs.group = k }, label = { Text(l) }, modifier = Modifier.testTag("bills-$k")) }
            }
        }
        if (filter == "debts") item { PayingOffCard(m.debts) }
        else if (m.debts.isNotEmpty()) item {
            Surface(onClick = { filter = "debts" }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().enter(1)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Flag, null, tint = toneColors("sky").first); Spacer(Modifier.width(10.dp))
                    Text("Paying off ${m.debts.size} debt${if (m.debts.size == 1) "" else "s"} · ${Fmt.money(m.debts.sumOf { it.info.n("remaining") })} left", modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().enter(2), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (shown.isEmpty()) { Spacer(Modifier.height(8.dp)); EmptyNote(when (filter) { "baby" -> "No kids bills yet, like diapers and wipes."; "pets" -> "No pet bills yet, like pet food and litter."
                        "debts" -> "No debts."; else -> "No bills yet. Add the ones that repeat, like rent and phone plans." }); Spacer(Modifier.height(8.dp)) }
                    shown.forEachIndexed { i, r -> if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        BillLine(r, m.ym, s, billMenu(r, m.ym, vm, open)) { open(Sheet.Bill(r.id, r.bill.or("group", "adults"))) } }
                    if (shown.isNotEmpty()) LineRow("In ${Fmt.monthName(m.ym)}", Fmt.money(total), total = true)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item {
            Text("Bills that come every 3 months or yearly are set aside a little each month. Long-press a bill to pay part of it later, give it up for a month, or pause it.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** A bill's long-press menu on the Bills tab: pay part later or give it up this month (when it can be), pause it, change it. */
fun billMenu(r: BillRow, ym: String, vm: MainViewModel, open: (Sheet) -> Unit): List<looniemate.app.ui.MenuItem> = buildList {
    val b = r.bill; val paused = b["active"].isFalse(); val on = r.line != null && !paused
    if (on && b.t("deferrable") && r.line?.t("deferredTo") != true) add(looniemate.app.ui.MenuItem("Pay part later this month…") { open(Sheet.Adjust(ym, r.id, true)) })
    if (on && b["essential"].isFalse() && r.line?.t("skipped") != true) add(looniemate.app.ui.MenuItem("Give up this month…") { open(Sheet.Adjust(ym, r.id, false)) })
    add(looniemate.app.ui.MenuItem(if (paused) "Turn it back on" else "Pause it") { vm.pauseBill(r.id, !paused) })
    add(looniemate.app.ui.MenuItem("Change") { open(Sheet.Bill(r.id, b.or("group", "adults"))) })
}

@Composable
private fun BillLine(r: BillRow, ym: String, s: looniemate.engine.J, menu: List<looniemate.app.ui.MenuItem>, onClick: () -> Unit) {
    val b = r.bill; val p = r.line; val mon = Fmt.monthShort(ym); val x = looniemate.app.ui.theme.LocalExtra.current
    looniemate.app.ui.MenuRow(menu, onClick = onClick, clickLabel = "Change", vertical = 6.dp) {
        looniemate.app.ui.Dot(x.group(b.or("group", "adults")))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(b.or("name", "Bill"), fontWeight = FontWeight.Bold)
            val paused = b["active"].isFalse()
            val acct = Core.acctById(s, b.s("account"))?.s("name") ?: (if (b.s("account").isNotEmpty()) b.s("account") + " (removed)" else "")
            Text(listOf(if (paused) "" else r.next?.let { "next ${Fmt.dShort(it)}" } ?: "", r.freq, acct).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val pills = buildList {
                if (b.t("example")) add("Example" to "warn"); if (paused) add("Paused" to "")
                if (p != null && p.t("skipped")) add("Given up in $mon" to "bad")
                if (p != null && p.t("deferredTo")) add("${Fmt.money(p.n("deferredAmt"))} put off to ${Fmt.dShort(p.s("deferredTo"))}" to "warn")
                if (r.inFrom > 0 && r.inMonth != null) add("+${Fmt.money(r.inFrom)} from ${Fmt.monthShort(r.inMonth)}" to "warn")
                if (b.t("fund")) add("Saves into " + Core.fundOf(s, b.s("fund")).s("name") to "good")
            }
            if (pills.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { pills.forEach { (t, tone) -> Pill(t, tone) } }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(b.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers), fontWeight = FontWeight.SemiBold)
            if (p != null && p.t("setAside")) Text("${Fmt.money(p.n("amount"))}/mo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PayingOffCard(debts: List<Views.Debt>) {
    SectionCard("Paying off", Icons.Rounded.Flag, "sky", trailing = Fmt.money(debts.sumOf { it.info.n("remaining") }) + " left in total") {
        debts.forEachIndexed { i, d ->
            var openIt by remember { mutableStateOf(false) }
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { openIt = !openIt }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProgressRing(d.pct)
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.Bold)
                    FlowRow(verticalArrangement = Arrangement.Center) { Money(d.info.n("remaining"), MaterialTheme.typography.titleMedium, weight = FontWeight.SemiBold)
                        Text(" left of " + Fmt.money(d.info.n("paid") + d.info.n("remaining")), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val i0 = d.info
                    Text(when { i0.t("stuck") -> "Payment doesn't cover the interest"; isDate(i0["finish"]) -> "${i0.n("payments").toInt()} payments left · done ${Fmt.monthYearLong(i0.s("finish"))}"; i0.n("remaining") > 0 -> "Payments end before it's paid off"; else -> "Paid off" },
                        style = MaterialTheme.typography.bodySmall, color = if (i0.t("stuck") || (!isDate(i0["finish"]) && i0.n("remaining") > 0)) toneColors("bad").first else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (openIt) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            AnimatedVisibility(openIt) { DebtBreakdown(d.info) }
        }
    }
}

@Composable
fun DebtBreakdown(info: looniemate.engine.J) {
    val (lines, foot) = Views.debtLines(info)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            lines.forEach { l -> if (l.value.isEmpty() && l.tone.isNotEmpty()) Text(l.label, style = MaterialTheme.typography.bodySmall, color = toneColors(l.tone).first) else LineRow(l.label, l.value, l.total) }
            Spacer(Modifier.height(6.dp))
            Text(foot.label, style = MaterialTheme.typography.bodySmall, color = if (foot.tone.isEmpty()) MaterialTheme.colorScheme.onSurface else toneColors(foot.tone).first)
        }
    }
}
