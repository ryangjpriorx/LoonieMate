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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.MainViewModel
import looniemate.app.ui.DayHeader
import looniemate.app.ui.Dot
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.LineRow
import looniemate.app.ui.SectionCard
import looniemate.app.ui.Sheet
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.theme.Numbers
import looniemate.engine.Core
import looniemate.engine.Fmt
import looniemate.engine.Labels
import looniemate.engine.Obj
import looniemate.engine.n
import looniemate.engine.s
import looniemate.engine.*

/**
 * Personal Expenses on the Spending tab: this month's planned and logged amounts, and the list of things bought for
 * yourself, each with a one-tap Log for today. Calm colours whatever the numbers (docs/plans/personal-expenses.md).
 */
@Composable
private fun SelfSpendingCard(m: looniemate.app.SpendingModel, vm: MainViewModel, open: (Sheet) -> Unit) {
    val c = m.self; val plan = c.n("self"); val spent = c.n("selfSpent"); val (fg, _) = looniemate.app.ui.toneColors("sky")
    SectionCard("Personal Expenses", Icons.Rounded.LocalCafe, "sky", trailing = if (plan > 0) "${Fmt.money(spent)} of ${Fmt.money(plan)}" else null, index = 1) {
        if (m.selfItems.isEmpty()) {
            Text("A little money for yourselves, planned so it doesn't come as a surprise: a coffee, smokes, a game, a night out. List what you usually get and how often.",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { open(Sheet.Self) }, modifier = Modifier.testTag("self-edit")) { Text("Set up Personal Expenses") }
            return@SectionCard
        }
        if (plan > 0) { looniemate.app.ui.GrowBar(minOf(1.0, spent / plan).toFloat(), fg); Spacer(Modifier.height(6.dp)) }
        val left = plan - spent
        Text((if (left >= 0) "${Fmt.money(left)} left for you in ${Fmt.monthOnly(m.ym)}." else "${Fmt.money(-left)} more than planned in ${Fmt.monthOnly(m.ym)}.") +
            (if (c.n("selfHold") > 0) " ${Fmt.money(c.n("selfHold"))} is held back this month." else ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (c.n("selfHold") > 0 && !m.locked) TextButton(onClick = { vm.holdSelf(m.ym, 0.0) }) { Text("Put it back") }
        Spacer(Modifier.height(4.dp))
        m.selfItems.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.s("name").ifEmpty { "Personal expense" }, fontWeight = FontWeight.SemiBold)
                    Text("${Fmt.money(p.n("amount"))} · ${vm.selfHowOften(p)} · about ${Fmt.money(Core.selfItemMonthly(p, m.regCount))} a month",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (m.isCurrent && !m.locked) androidx.compose.material3.OutlinedButton(onClick = { vm.logSelfItem(p) },
                    modifier = Modifier.testTag("self-log-$i")) { Text("Log ${Fmt.money(p.n("amount"))}") }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(onClick = { open(Sheet.Self) }, modifier = Modifier.testTag("self-edit")) { Text("Change the list") }
            if (m.isCurrent && !m.locked) TextButton(onClick = { open(Sheet.Log(category = Core.SELF)) }) { Text("Log something else") }
        }
    }
}

@Composable
fun SpendingScreen(vm: MainViewModel, pad: PaddingValues, open: (Sheet) -> Unit) {
    val sm by vm.spendingModel.collectAsStateWithLifecycle()
    val m = sm ?: return
    val people = vm.settings()["people"]
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { CategoryCard(m.ym, m.byCat, 0) }
        item { SelfSpendingCard(m, vm, open) }
        item {
            SectionCard("Everything logged in ${Fmt.monthOnly(m.ym)}", Icons.Rounded.FormatListBulleted, "amber", trailing = Fmt.money(m.total - m.oneTimeTotal), index = 2) {
                if (m.entries.isEmpty()) EmptyNote("Nothing logged for this month yet.")
                var last = ""
                m.entries.forEach { e -> val d = e.s("date"); if (d != last) { last = d; DayHeader(Fmt.dLabel(d)) }; ExpenseRow(e, m.locked) { vm.deleteExpense(e) } }
                Spacer(Modifier.height(6.dp))
                Text("Groceries and takeout come out of the food allowance. Everything else lowers this month's amount for savings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionCard("One-time expenses · ${Fmt.monthOnly(m.ym)}", Icons.Rounded.Sell, "amber", index = 2) {
                Text("Things outside the regular budget: a car seat, a vet visit, a birthday. Add future ones ahead of time and they show up in Coming up.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                if (m.oneTime.isEmpty()) EmptyNote("No one-time expenses this month.")
                m.oneTime.forEach { e -> ExpenseRow(e, m.locked) { vm.deleteExpense(e) } }
                LineRow("Total this month", Fmt.money(m.oneTimeTotal), total = true)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { open(Sheet.Log(oneTime = true)) }, enabled = !m.locked) { Text("Add a one-time expense") }
            }
        }
    }
}

@Composable
fun ExpenseRow(e: Obj, locked: Boolean, onDelete: () -> Unit) {
    val x = LocalExtra.current
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val spoken = listOf(Labels.expLabel(e), Fmt.money(e.n("amount")), Fmt.dLabel(e.s("date")), e.s("note").takeIf { e.s("category") != "oneoff" } ?: "", e.s("who")).filter { it.isNotBlank() }.joinToString(", ")
    Row(Modifier.fillMaxWidth().padding(vertical = looniemate.app.ui.rowPad(6.dp)).clearAndSetSemantics { contentDescription = spoken
            if (!locked) customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true }) },
        verticalAlignment = Alignment.CenterVertically) {
        Dot(if (Core.FOOD.contains(e.s("category"))) x.good else if (e.s("category") == "oneoff") x.group(e.s("group")) else MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(Labels.expLabel(e), fontWeight = FontWeight.SemiBold)
            val meta = listOf(if (e.s("category") != "oneoff") e.s("note") else Fmt.dLabel(e.s("date")), e.s("who"), if (e.s("category") == "oneoff") Labels.OGROUPS[e.s("group")] ?: "" else "").filter { it.isNotEmpty() }
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(Fmt.money(e.n("amount")), style = MaterialTheme.typography.bodyLarge.merge(Numbers))
        if (!locked) IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
