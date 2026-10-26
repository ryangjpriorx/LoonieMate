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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.MainViewModel.SelfRow
import looniemate.app.ui.Sheet
import looniemate.engine.Fmt
import looniemate.engine.s
import looniemate.engine.t
import looniemate.engine.ymOf
import looniemate.engine.*

/**
 * Personal Expenses: the list of things bought for yourself and how often, planned openly so it doesn't turn up later
 * as "unlogged spending". What's spent this month is on the Spending tab. docs/plans/personal-expenses.md.
 */
@Composable
fun SelfSheet(vm: MainViewModel, close: () -> Unit, @Suppress("UNUSED_PARAMETER") open: (Sheet) -> Unit) {
    SheetTitle("Personal Expenses")
    SelfPlanEditor(vm, "Save", onSaved = close)
}

/** The words used wherever the list is set: kind, and clear that nobody has to say what each thing is. */
@Composable
fun SelfIntro() {
    Text("Most people spend a little on themselves: a coffee, smokes, a game, a night out. List what you usually get and how often. " +
        "Planning for it beats pretending it isn't there.", style = MaterialTheme.typography.bodyMedium)
    Text("It's set aside after bills and food, before savings. Naming each one is up to you, and you can change the list any time on the Spending tab.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The list: one card per thing, with what it is (optional), how much each time, and how often (so many times a day,
 * week, month or payday). Items left without an amount are skipped. Used by the Personal Expenses page and by a step of
 * getting on track.
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
                TextFieldRow(r.name, { rows[i] = r.copy(name = it.take(30)); err = null }, "What is it? (optional)", placeholder = "Coffee, smokes, a game…")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    MoneyField(r.amount, { rows[i] = r.copy(amount = it); err = null }, "Each time", modifier = Modifier.weight(1f).testTag("self-amount-$i"))
                    TextFieldRow(r.times, { v -> rows[i] = r.copy(times = v.filter { it.isDigit() || it == '.' || it == ',' }.take(4)); err = null }, "How many",
                        modifier = Modifier.weight(0.7f).testTag("self-times-$i"), number = true)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vm.SELF_PERS.forEach { (k, l) -> FilterChip(r.per == k, { page?.mark(); rows[i] = r.copy(per = k) }, label = { Text(l) }) } }
                vm.selfRowMonthly(r)?.takeIf { it > 0 }?.let { m ->
                    Text("About ${Fmt.money(m)} a month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (rows.size > 1) TextButton(onClick = { page?.mark(); rows.removeAt(i) }) { Text("Remove this one") }
            }
        }
    }
    TextButton(onClick = { rows.add(SelfRow()) }, modifier = Modifier.testTag("self-add")) { Text("+ Add another") }
    val (plan, _) = vm.readSelf(rows)
    if (plan != null && plan.isNotEmpty()) Text("Altogether about ${Fmt.money(vm.selfMonthly(plan))} in ${Fmt.monthOnly(ymOf(vm.today.value))}.",
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    ErrorText(err)
    Button(onClick = { val (p, e) = vm.readSelf(rows); if (p == null) err = e else { vm.saveSelf(p); page?.saved(); onSaved() } },
        Modifier.fillMaxWidth().testTag("self-save")) { Text(saveLabel) }
}

/** The list saved with the page (polish-and-audit S1). */
private val selfRowsSaver = androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.snapshots.SnapshotStateList<SelfRow>, ArrayList<String>>(
    save = { l -> ArrayList(l.flatMap { listOf(it.name, it.amount, it.times, it.per) }) },
    restore = { a -> mutableStateListOf<SelfRow>().apply { a.chunked(4).forEach { add(SelfRow(it[0], it[1], it[2], it[3])) } } })
