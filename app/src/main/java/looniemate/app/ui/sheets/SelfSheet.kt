@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package looniemate.app.ui.sheets

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.MainViewModel
import looniemate.app.MainViewModel.SelfRow
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.LineRow
import looniemate.app.ui.Sheet
import looniemate.app.ui.screens.ExpenseRow
import looniemate.engine.Core
import looniemate.engine.Docs
import looniemate.engine.Fmt
import looniemate.engine.Obj
import looniemate.engine.arr
import looniemate.engine.n
import looniemate.engine.s
import looniemate.engine.t
import looniemate.engine.ymOf
import looniemate.engine.*

/**
 * Personal Expenses: a little money each person can spend on themselves, planned openly so it doesn't turn up later as
 * "unlogged spending". Never asks what it's for. docs/plans/personal-expenses.md.
 */
@Composable
fun SelfSheet(vm: MainViewModel, close: () -> Unit, open: (Sheet) -> Unit) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val ym = ymOf(today); val c = Core.monthCalc(ym, Docs.toStore(docs, today), today)
    SheetTitle("Personal Expenses")
    if (c.n("selfPlanned") > 0 || c.n("selfSpent") > 0) {
        Text("This month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LineRow("Planned for ${Fmt.monthOnly(ym)}", Fmt.money(c.n("selfPlanned")))
        if (c.n("selfHold") > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { LineRow("Held back this month", "−" + Fmt.money(c.n("selfHold"))) }
            TextButton(onClick = { vm.holdSelf(ym, 0.0) }) { Text("Put it back") } }
        LineRow("Logged so far", Fmt.money(c.n("selfSpent")))
        val left = c.n("self") - c.n("selfSpent")
        LineRow(if (left >= 0) "Left this month" else "More than planned", Fmt.money(Math.abs(left)), total = true)
        val logged = c["expenses"].arr().filterIsInstance<Obj>().filter { !it.t("oneTime") && it.s("category") == Core.SELF }
        Column {
            if (logged.isEmpty()) EmptyNote("Nothing logged under Personal Expenses this month. Logging it is up to you.")
            logged.forEach { e -> ExpenseRow(e, c.t("closed")) { vm.deleteExpense(e) } }
        }
        FilledTonalButton(onClick = { open(Sheet.Log(category = Core.SELF)) }) { Text("+ Log Personal Expenses") }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Your plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    SelfPlanEditor(vm, "Save", onSaved = close)
}

/** The words used wherever the plan is set: kind, and clear that nobody has to say what it's for. */
@Composable
fun SelfIntro() {
    Text("Most people spend a little on themselves: a coffee, smokes, a game, a night out. Whatever yours is, you don't need to say. " +
        "Planning \$20 a week beats planning \$0 and spending \$80.", style = MaterialTheme.typography.bodyMedium)
    Text("It's set aside after bills and food, before savings. Leave it blank for none, and change it any time in Settings.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * One line per person (or one for everyone): how much, and per week, per paycheque or per month. Blank means $0.
 * Used by the Personal Expenses page and by a step of getting on track.
 */
@Composable
fun SelfPlanEditor(vm: MainViewModel, saveLabel: String, onSaved: () -> Unit) {
    val page = LocalPage.current
    val rows = rememberSaveable(saver = selfRowsSaver) { mutableStateListOf<SelfRow>().apply { addAll(vm.selfRows().ifEmpty { listOf(SelfRow()) }) } }
    var err by rememberSaveable { mutableStateOf<String?>(null) }
    SelfIntro()
    rows.forEachIndexed { i, r ->
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFieldRow(r.name, { rows[i] = r.copy(name = it.take(30)); err = null }, "Who (optional)", placeholder = "Me, Alex, both of us…")
                MoneyField(r.amount, { rows[i] = r.copy(amount = it); err = null }, "How much", modifier = Modifier.fillMaxWidth().testTag("self-amount-$i"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("week" to "Per week", "pay" to "Per paycheque", "month" to "Per month").forEach { (k, l) ->
                        FilterChip(r.per == k, { page?.mark(); rows[i] = r.copy(per = k) }, label = { Text(l) }) } }
                if (rows.size > 1) TextButton(onClick = { page?.mark(); rows.removeAt(i) }) { Text("Remove this line") }
            }
        }
    }
    TextButton(onClick = { rows.add(SelfRow()) }) { Text("+ Add another person") }
    val (plan, _) = vm.readSelf(rows)
    if (plan != null && plan.isNotEmpty()) Text("That's about ${Fmt.money(vm.selfMonthly(plan))} in ${Fmt.monthOnly(ymOf(vm.today.value))}.",
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    ErrorText(err)
    Button(onClick = { val (p, e) = vm.readSelf(rows); if (p == null) err = e else { vm.saveSelf(p); page?.saved(); onSaved() } },
        Modifier.fillMaxWidth().testTag("self-save")) { Text(saveLabel) }
}

/** The plan's lines saved with the page (S1). */
private val selfRowsSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateList<SelfRow>, ArrayList<String>>(
    save = { l -> ArrayList(l.flatMap { listOf(it.name, it.amount, it.per) }) },
    restore = { a -> mutableStateListOf<SelfRow>().apply { a.chunked(3).forEach { add(SelfRow(it[0], it[1], it[2])) } } })
