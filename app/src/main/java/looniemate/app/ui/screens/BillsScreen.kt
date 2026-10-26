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

@Composable
fun BillsScreen(vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit) {
    val bm by vm.billsModel.collectAsStateWithLifecycle()
    val m = bm ?: return
    var group by rememberSaveable { mutableStateOf(vm.prefs.group) }
    val s = vm.settings()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (m.debts.isNotEmpty()) item { PayingOffCard(m.debts) }
        item {
            Row(Modifier.enter(1), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Labels.GROUPS.forEach { (k, g) ->
                    val sel = k == group; val (fg, bg) = toneColors(if (k == "baby") "baby" else if (k == "pets") "pets" else "adults")
                    Surface(onClick = { group = k; vm.prefs.group = k }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                        color = if (sel) bg else MaterialTheme.colorScheme.surfaceContainerLow, border = if (sel) BorderStroke(1.5.dp, fg) else null) {
                        Column(Modifier.padding(12.dp)) {
                            Text(g.label, fontWeight = FontWeight.Bold, color = if (sel) fg else MaterialTheme.colorScheme.onSurface)
                            Text(Fmt.money(m.totals[k] ?: 0.0) + "/mo", style = MaterialTheme.typography.bodySmall.merge(Numbers))
                            Text("${m.counts[k] ?: 0} bill${if ((m.counts[k] ?: 0) == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item {
            val g = Labels.GROUPS.getValue(group); val (fg, bg) = toneColors(if (group == "baby") "baby" else if (group == "pets") "pets" else "adults")
            Card(Modifier.fillMaxWidth().enter(2), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.fillMaxWidth().background(bg).padding(16.dp)) {
                    Text(g.label, style = MaterialTheme.typography.headlineSmall, color = fg)
                    Text(g.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val rows = m.rows[group].orEmpty()
                    if (rows.isEmpty()) { Spacer(Modifier.height(8.dp)); EmptyNote("No ${g.label.lowercase()} bills yet. Add the ones that repeat, like ${if (group == "baby") "diapers and wipes" else if (group == "pets") "pet food and litter" else "rent and phone plans"}.") }
                    rows.forEachIndexed { i, r -> if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)); BillLine(r, m.ym, s) { open(Sheet.Bill(r.id, group)) } }
                    LineRow("${g.label} total for ${Fmt.monthName(m.ym)}", Fmt.money(m.totals[group] ?: 0.0), total = true)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item {
            Text("Bills that come every 3 months or yearly are set aside a little each month, so the money is there when they're due. Tag a bill non-essential or deferrable and you can skip it or pay it later in a month that comes up short.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun BillLine(r: BillRow, ym: String, s: looniemate.engine.J, onClick: () -> Unit) {
    val b = r.bill; val p = r.line; val mon = Fmt.monthShort(ym)
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(b.or("name", "Bill"), fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (b.t("example")) Pill("Example", "warn")
                if (b["active"].let { it is looniemate.engine.J.B && !it.v }) Pill("Paused")
                if (b["essential"].let { it is looniemate.engine.J.B && !it.v }) Pill("Non-essential", "warn")
                if (b.t("deferrable")) Pill("Deferrable")
                if (b.t("fund")) Pill("Saves into " + Core.fundOf(s, b.s("fund")).s("name"), "good")
                if (b.t("debt")) Pill("Debt")
                if (p != null && p.t("skipped")) Pill("Skipped in $mon", "bad")
                if (p != null && p.t("deferredTo")) Pill("${Fmt.money(p.n("deferredAmt"))} deferred to ${Fmt.dShort(p.s("deferredTo"))}", "warn")
                if (r.inFrom > 0 && r.inMonth != null) Pill("+${Fmt.money(r.inFrom)} deferred from ${Fmt.monthShort(r.inMonth)}", "warn")
            }
            val acct = Core.acctById(s, b.s("account"))?.s("name") ?: (if (b.s("account").isNotEmpty()) b.s("account") + " (removed)" else "")
            Text(listOf(Labels.CATL[b.s("category")] ?: "Other", r.freq, acct, Labels.shortMethod(b.s("method"))).filter { it.isNotEmpty() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p != null && p.t("setAside")) Text("Set aside ${Fmt.money(p.n("amount"))} each month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (b.s("category") == "housing") Text("Covered first by income set to \"Covers rent first\"", style = MaterialTheme.typography.bodySmall, color = toneColors("adults").first)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fmt.money(b.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers), fontWeight = FontWeight.SemiBold)
            Text(if (p != null) (if (p.t("skipped")) "skipped" else Fmt.money(p.n("amount")) + " in $mon") else if (r.inFrom > 0) Fmt.money(r.inFrom) + " in $mon" else "not this month",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Row(verticalAlignment = Alignment.CenterVertically) { Money(d.info.n("remaining"), MaterialTheme.typography.titleMedium, weight = FontWeight.SemiBold)
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
