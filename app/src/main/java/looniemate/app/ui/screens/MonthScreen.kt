@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package looniemate.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.MainViewModel
import looniemate.app.MonthModel
import looniemate.app.ui.Dot
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.GrowBar
import looniemate.app.ui.LineRow
import looniemate.app.ui.Pill
import looniemate.app.ui.SectionCard
import looniemate.app.ui.Sheet
import looniemate.app.ui.StatTile
import looniemate.app.ui.StatusBadge
import looniemate.app.ui.enter
import looniemate.app.ui.toneColors
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

@Composable
fun MonthScreen(vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit) {
    val mm by vm.monthModel.collectAsStateWithLifecycle()
    val m = mm ?: return
    val s = vm.settings(); val c = m.c
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (m.examples) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Example amounts", "warn"); Text("Some bills are examples. Replace them with your real numbers.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)) }
            }
        }
        item { StatusCard(m) }
        if (m.showAdjust) item { AdjustCard(m, vm, open) }
        item { Tiles(m, open) }
        item {
            SectionCard("Coming up", Icons.Rounded.CalendarMonth, trailing = "To " + Fmt.dShort(MainViewModel.plusDays(m.today, m.days)), index = 3) {
                if (m.up.isEmpty()) EmptyNote("Nothing due in the next few days.") else EventList(m.up, m.today, s, vm)
            }
        }
        item { PlanCard(m) }
        item { CategoryCard(m.ym, m.byCat, 5) }
        if (m.debts.isNotEmpty()) item { DebtCard(m.debts, 6) {} }
        item {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().enter(7)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Savings: ", fontWeight = FontWeight.Bold)
                    Text("Emergency ${Fmt.money(m.emergency)} · Child Reserve ${Fmt.money(m.child)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    TextButton(onClick = { open(Sheet.Transfer("emergency", null)) }) { Text("Record a transfer") }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(m: MonthModel) {
    val tone = if (m.status.tone == "plan") "accent" else m.status.tone; val (_, bg) = toneColors(tone)
    Card(Modifier.fillMaxWidth().enter(0), shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = lerp(MaterialTheme.colorScheme.surfaceContainerLow, bg, 0.85f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(m.status.tone, m.status.label, big = true)
            m.status.points.forEach { p -> Row { Text("•  ", fontWeight = FontWeight.Bold); Text(p, style = MaterialTheme.typography.bodyMedium) } }
        }
    }
}

@Composable
private fun AdjustCard(m: MonthModel, vm: MainViewModel, open: (Sheet) -> Unit) {
    val mo = Fmt.monthOnly(m.ym); val c = m.c
    SectionCard(if (m.short) "Clear the shortfall" else "Adjusted in $mo", if (m.short) Icons.Rounded.Bolt else Icons.Rounded.Tune, if (m.short) "red" else "green", index = 1,
        trailingContent = { Pill(if (m.short) "Still short ${Fmt.money(-c.n("net"))}" else "Shortfall covered", if (m.short) "bad" else "good") }) {
        Text((if (m.short) "$mo doesn't have enough money for everything. " else "") + "Give up a non-essential for the month, or push a deferrable bill to a later date when money is there. These change the budget only. Call or cancel with the company yourself.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text("Give up this month", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (m.skips.isEmpty()) EmptyNote("No bills tagged non-essential. Tag them on the Bills tab.")
        m.skips.forEach { l -> AdjustRow(l, mo) {
            if (l.t("skipped")) { Column(horizontalAlignment = Alignment.End) { Text("Giving up ${Fmt.money(l.n("skippedAmt"))}", style = MaterialTheme.typography.labelLarge)
                if (l.n("amount") > 0) Text("${Fmt.money(l.n("amount"))} still paid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { vm.unskip(m.ym, l.s("id")) }) { Text("Undo") } }
            else OutlinedButton(onClick = { open(Sheet.Adjust(m.ym, l.s("id"), false)) }) { Text("Give up…") } } }
        Spacer(Modifier.height(10.dp))
        Text("Pay later", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (m.defers.isEmpty()) EmptyNote("No bills tagged deferrable. Tag them on the Bills tab.")
        m.defers.forEach { l -> AdjustRow(l, mo) {
            if (l.t("deferredTo")) { Column(horizontalAlignment = Alignment.End) { Text("${Fmt.money(l.n("deferredAmt"))} on ${Fmt.dShort(l.s("deferredTo"))}", style = MaterialTheme.typography.labelLarge)
                if (l.n("amount") > 0) Text("${Fmt.money(l.n("amount"))} on time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { vm.undefer(m.ym, l.s("id")) }) { Text("Undo") } }
            else OutlinedButton(onClick = { open(Sheet.Adjust(m.ym, l.s("id"), true)) }) { Text("Defer…") } } }
        val freed = c.n("skippedTotal") + c.n("deferredTotal")
        if (freed > 0) LineRow("Freed up this month", Fmt.money(freed), total = true)
        if (m.short && m.skips.all { it.t("skipped") } && m.defers.all { it.t("deferredTo") })
            Text("Nothing left to skip or defer. Cover the rest from Emergency Savings first, and the Child Reserve only as a last resort.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun AdjustRow(l: Obj, mo: String, actions: @Composable () -> Unit) {
    val x = LocalExtra.current
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dot(x.group(l.s("group")))
        Column(Modifier.weight(1f)) { Text(l.s("name"), fontWeight = FontWeight.SemiBold)
            Text("${Labels.GROUPS[l.s("group")]?.label ?: ""} · ${Fmt.money(l.n("planned"))} in $mo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
private fun Tiles(m: MonthModel, open: (Sheet) -> Unit) {
    val c = m.c
    Column(Modifier.enter(2), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val mod = Modifier.weight(1f)
            when {
                m.short && m.phase != "past" -> StatTile("Shortfall this month", m.safeToMove, m.safeNote, "bad", mod)
                m.phase == "now" -> StatTile("Safe to move to Emergency Savings", m.safeToMove, m.safeNote, "accent", mod, hero = true,
                    action = { FilledTonalButton(onClick = { open(Sheet.Transfer("emergency", m.safeToMove.takeIf { it > 0 })) }) { Text("I moved money") } })
                m.phase == "future" -> StatTile("Planned for Emergency Savings", m.safeToMove, "", "accent", mod, hero = true)
                else -> StatTile("Emergency Savings target", m.safeToMove, m.safeNote, "accent", mod, hero = true)
            }
            val fp = m.food
            if (fp != null) StatTile("Food left · " + (if (fp.n("half") == 1.0) "1st–15th" else "16th–end"), fp.n("left"),
                "${Fmt.money(fp.n("perDay"))}/day for ${fp.n("daysLeft").toInt()} days", "green", mod)
            else StatTile("Food allowance", c.n("food"), "${Fmt.money(c.n("foodHalf"))} for each half of the month", "green", mod)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Bills due · next ${m.days} days", m.dueTotal, "${m.dueCount} bill${if (m.dueCount == 1) "" else "s"}" + (if (m.dueOverdue > 0) " · ${m.dueOverdue} past due" else ""), "sky", Modifier.weight(1f))
            StatTile("Child Reserve · protected", m.child, "Canada Child Benefit savings. Last resort only." + (if (m.childShould > m.child + 0.5) " Should be ${Fmt.money(m.childShould)}." else ""), "rose", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlanCard(m: MonthModel) {
    val c = m.c; val x = LocalExtra.current
    SectionCard("Where the money goes", Icons.Rounded.SwapHoriz, "teal", index = 4, trailingContent = {
        if (c.t("closed")) Pill("Locked", "") else if (c["bonusDays"].let { it is looniemate.engine.J.A && it.list.isNotEmpty() }) Pill("Extra payday", "good") }) {
        if (m.plan.isEmpty()) EmptyNote("No income set up yet. Set it up in the web version and import a backup.")
        m.plan.forEach { p ->
            val bg = when (p.kind) { "bonus" -> MaterialTheme.colorScheme.primaryContainer; "ccb" -> x.babyBg; else -> MaterialTheme.colorScheme.surfaceContainer }
            Surface(shape = MaterialTheme.shapes.medium, color = bg, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text(p.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(p.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Spacer(Modifier.height(4.dp))
                    p.lines.forEach { l -> LineRow(l.label, l.value, l.total, l.tone) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SumTile("Money in", c.n("spendable"), Modifier.weight(1f)); SumTile("Fixed bills", c.n("billsTotal"), Modifier.weight(1f)) }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SumTile("Food", c.n("food"), Modifier.weight(1f)); SumTile("Planned surplus", c.n("plannedSurplus"), Modifier.weight(1f), if (c.n("plannedSurplus") < 0) "bad" else "good") }
        Spacer(Modifier.height(8.dp))
        val bg = c["byGroup"]
        Text("Adults ${Fmt.money(bg.n("adults"))} · Kids ${Fmt.money(bg.n("baby"))} · Pets ${Fmt.money(bg.n("pets"))}" + (if (c.n("otherSpent") > 0) " · Everyday and one-time spending so far ${Fmt.money(c.n("otherSpent"))}" else ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SumTile(label: String, value: Double, modifier: Modifier, tone: String = "") {
    val (fg, bg) = toneColors("teal")
    Surface(modifier, shape = MaterialTheme.shapes.small, color = lerp(MaterialTheme.colorScheme.surfaceContainerLow, bg, 0.8f)) {
        Column(Modifier.padding(10.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = fg, fontWeight = FontWeight.Bold)
            looniemate.app.ui.Money(value, MaterialTheme.typography.titleMedium, color = if (tone.isEmpty()) MaterialTheme.colorScheme.onSurface else toneColors(tone).first, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CategoryCard(ym: String, byCat: List<Pair<String, Double>>, index: Int) {
    val x = LocalExtra.current
    SectionCard("Spending in ${Fmt.monthOnly(ym)}", Icons.Rounded.PieChart, "amber", trailing = if (byCat.isNotEmpty()) Fmt.money(byCat.sumOf { it.second }) else null, index = index) {
        if (byCat.isEmpty()) EmptyNote("No spending logged yet. Tap Log spending after each purchase.")
        val max = byCat.maxOfOrNull { it.second } ?: 1.0
        byCat.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Labels.SPENDL[k] ?: k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(118.dp), maxLines = 1)
                GrowBar((v / max).toFloat(), if (Core.FOOD.contains(k)) x.good else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Text(Fmt.money(v), style = MaterialTheme.typography.bodyMedium.merge(Numbers))
            }
        }
    }
}
