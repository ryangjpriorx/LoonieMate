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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/**
 * Month: the plan, and how it's going (docs/plans/overhaul.md). The month, one sentence of status (Why? unfolds the
 * rest), where the money goes as one bar and five lines, Make it fit when it's short, Money in (each payday, folded),
 * what's been spent so far, and a lock card once the month is over. Nothing that Today or another tab already shows.
 */
@Composable
fun MonthScreen(vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit, state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(), go: (looniemate.app.ui.Tab) -> Unit = {}) {
    val mm by vm.monthModel.collectAsStateWithLifecycle()
    val m = mm ?: return
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { looniemate.app.ui.MonthSwitcher(vm) }
        item { StatusCard(m) }
        if (m.phase == "past" && !m.c.t("closed")) item {
            SectionCard("${Fmt.monthName(m.ym)} is finished", Icons.Rounded.Lock, "accent", index = 1) {
                Text("Lock it so its numbers stay exactly as they were, even if a bill or an income changes later.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp)); FilledTonalButton(onClick = { open(Sheet.MonthIncome(m.ym)) }, modifier = Modifier.testTag("month-lock")) { Text("Review and lock") }
            }
        }
        item { WhereItGoes(m) }
        if (m.showAdjust) item { AdjustCard(m, vm, open) }
        item { MoneyIn(m, open) }
        item { SpentSoFar(m) { go(looniemate.app.ui.Tab.Spending) } }
        if (m.examples) item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Example amounts", "warn"); Spacer(Modifier.width(8.dp))
                Text("Some bills are examples.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.clearExamples() }, modifier = Modifier.testTag("clear-examples")) { Text("Clear them") }
            }
        }
    }
}

/** One sentence of status, and Why? for the rest of the points. */
@Composable
private fun StatusCard(m: MonthModel) {
    val tone = if (m.status.tone == "plan") "accent" else m.status.tone; val (_, bg) = toneColors(tone); var why by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val pts = m.status.points
    Card(Modifier.fillMaxWidth().enter(0).animateContentSize(androidx.compose.animation.core.tween(looniemate.app.ui.Motion.FOLD)), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = lerp(MaterialTheme.colorScheme.surfaceContainerLow, bg, 0.85f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(m.status.tone, m.status.label, big = true)
            pts.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (pts.size > 1) {
                if (why) pts.drop(1).forEach { p -> Row { Text("•  ", fontWeight = FontWeight.Bold); Text(p, style = MaterialTheme.typography.bodyMedium) } }
                TextButton(onClick = { why = !why }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.testTag("status-why")) { Text(if (why) "Less" else "Why?") }
            }
        }
    }
}

/** Where the money goes: one bar (bills, food, Personal Expenses, savings, or short) and the five numbers that matter. */
@Composable
private fun WhereItGoes(m: MonthModel) {
    val c = m.c; val x = LocalExtra.current; val cs = MaterialTheme.colorScheme
    val inn = c.n("spendable"); val bills = c.n("billsTotal"); val food = c.n("food"); val self = c.n("self"); val left = c.n("plannedSurplus")
    val parts = listOf(Triple("Bills", bills, x.sky), Triple("Food", food, x.good), Triple("Personal Expenses", self, x.baby),
        if (left >= 0) Triple("Left for savings", left, cs.primary) else Triple("Short", -left, x.bad)).filter { it.second > 0.004 }
    val total = maxOf(inn, bills + food + self, 1.0)
    SectionCard("Where the money goes", Icons.Rounded.PieChart, "teal", index = 2, trailingContent = {
        if (c.t("closed")) Pill("Locked", "") else if (c["bonusDays"].let { it is J.A && it.list.isNotEmpty() }) Pill("Extra payday", "good") }) {
        Row(Modifier.fillMaxWidth().height(14.dp).clip(MaterialTheme.shapes.small).semantics(mergeDescendants = true) {
            contentDescription = parts.joinToString(", ") { "${it.first} ${Fmt.money(it.second)}" } }) {
            parts.forEach { (_, v, col) -> Box(Modifier.weight((v / total).toFloat().coerceAtLeast(0.001f)).fillMaxSize().background(col)) }
            val rest = (1 - parts.sumOf { it.second } / total).toFloat(); if (rest > 0.001f) Spacer(Modifier.weight(rest))
        }
        Spacer(Modifier.height(10.dp))
        LineRow("Money in", Fmt.money(inn))
        parts.forEach { (l, v, col) -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(col); Spacer(Modifier.width(8.dp))
            Text(l, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text((if (l == "Short") "−" else "") + Fmt.money(v), style = MaterialTheme.typography.bodyMedium.merge(Numbers), fontWeight = if (l == "Left for savings" || l == "Short") FontWeight.Bold else null,
                color = if (l == "Short") x.bad else Color.Unspecified) } }
        /* what's been spent outside the plan so far lowers what's left for savings */
        val off = c.n("otherSpent") + c.n("foodOver") + c.n("selfOver")
        if (off > 0.004 && m.phase != "future") Text("Other spending so far: ${Fmt.money(off)}, so about ${Fmt.money(maxOf(0.0, c.n("liveSurplus")))} is left for savings.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

/** Money in: each payday and benefit with what it pays for, folded to one line; tap to unfold, long-press for its menu. */
@Composable
private fun MoneyIn(m: MonthModel, open: (Sheet) -> Unit) {
    val c = m.c; val x = LocalExtra.current
    SectionCard("Money in", Icons.Rounded.Payments, "teal", index = 3) {
        if (m.plan.isEmpty()) EmptyNote("No income set up yet. Add it in Settings → Income and benefits.")
        m.plan.forEachIndexed { i, p ->
            var unfold by androidx.compose.runtime.saveable.rememberSaveable(p.title + p.date) { androidx.compose.runtime.mutableStateOf(false) }
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            val first = p.lines.firstOrNull()
            looniemate.app.ui.MenuRow(listOf(looniemate.app.ui.MenuItem(if (c.t("closed")) "This month's income and lock" else "Change this month's amount…") { open(Sheet.MonthIncome(m.ym)) },
                looniemate.app.ui.MenuItem("Open the income") { open(Sheet.Settings("income")) }), onClick = { unfold = !unfold }, clickLabel = if (unfold) "Fold up" else "Show what it pays for", vertical = 8.dp) {
                looniemate.app.ui.Dot(when (p.kind) { "ccb" -> x.baby; "bonus" -> MaterialTheme.colorScheme.primary; else -> x.good }, 10.dp, round = true)
                Column(Modifier.weight(1f)) {
                    Text(p.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    if (p.date.isNotEmpty()) Text(p.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (first != null && first.value.isNotEmpty()) Text(first.value, style = MaterialTheme.typography.bodyLarge.merge(Numbers), fontWeight = FontWeight.SemiBold)
                Icon(if (unfold) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.animation.AnimatedVisibility(unfold) {
                Column(Modifier.padding(start = 22.dp, bottom = 8.dp)) { p.lines.drop(if (first?.label == "Comes in") 1 else 0).forEach { l -> LineRow(l.label, l.value, l.total, l.tone) } }
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { open(Sheet.MonthIncome(m.ym)) }, modifier = Modifier.testTag("month-income")) {
            Text(if (c.t("closed")) "This month's income and lock" else "Change ${Fmt.monthOnly(m.ym)}'s income") }
    }
}

/** What's been spent this month, the top five kinds, and See all for the Spending tab. */
@Composable
private fun SpentSoFar(m: MonthModel, toSpending: () -> Unit) {
    val x = LocalExtra.current; val top = m.byCat.take(5)
    SectionCard("Spent so far", Icons.Rounded.ShoppingCart, "amber", trailing = if (m.byCat.isNotEmpty()) Fmt.money(m.byCat.sumOf { it.second }) else null, index = 4) {
        if (top.isEmpty()) EmptyNote("Nothing logged for ${Fmt.monthOnly(m.ym)} yet.")
        val max = top.maxOfOrNull { it.second } ?: 1.0
        top.forEach { (k, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Labels.SPENDL[k] ?: k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(118.dp), maxLines = 2)
                GrowBar((v / max).toFloat(), if (Core.FOOD.contains(k)) x.good else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Text(Fmt.money(v), style = MaterialTheme.typography.bodyMedium.merge(Numbers))
            }
        }
        TextButton(onClick = toSpending, modifier = Modifier.testTag("spent-all")) { Text("See all spending") }
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
    val big = looniemate.app.ui.bigText()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dot(x.group(l.s("group")))
        Column(Modifier.weight(1f)) { Text(l.s("name"), fontWeight = FontWeight.SemiBold)
            Text("${Labels.GROUPS[l.s("group")]?.label ?: ""} · ${Fmt.money(l.n("planned"))} in $mo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            /* at large text sizes the buttons go under the name instead of squeezing it */
            if (big) Row(verticalAlignment = Alignment.CenterVertically) { actions() } }
        if (!big) Row(verticalAlignment = Alignment.CenterVertically) { actions() }
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
                Text(Labels.SPENDL[k] ?: k, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(118.dp), maxLines = 2)
                GrowBar((v / max).toFloat(), if (Core.FOOD.contains(k)) x.good else MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Text(Fmt.money(v), style = MaterialTheme.typography.bodyMedium.merge(Numbers))
            }
        }
    }
}
