package looniemate.app.ui.sheets

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import looniemate.app.MainViewModel
import looniemate.app.ui.DayHeader
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.GrowBar
import looniemate.app.ui.LineRow
import looniemate.app.ui.Pill
import looniemate.app.ui.Sheet
import looniemate.app.ui.screens.ExpenseRow
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.theme.Numbers
import looniemate.app.ui.toneColors
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

/** The web version's Food tab: the month's food allowance in two halves, the food logged, and the food budget settings. */
@Composable
fun FoodSheet(vm: MainViewModel, ym: String, close: () -> Unit, open: (Sheet) -> Unit) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val st = Docs.toStore(docs, today); val s = st.settings
    val c = Core.monthCalc(ym, st, today)
    val inMonth = ymOf(today) == ym
    val fp = if (inMonth) Core.foodPeriod(c, today) else null
    val locked = c.t("closed")

    SheetTitle("Food · ${Fmt.monthName(ym)}")
    Text("After fixed bills there is ${Fmt.money(c.n("leftAfterBills"))} left this month. Food gets ${c.n("foodPct").toInt()}% of that" +
        (if (c.n("food") <= s.n("foodMin") + 1) " (or the ${Fmt.money(s.n("foodMin"))} minimum)" else "") + ", and the rest is for savings. It updates when income or bills change.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LineRow("Food allowance this month", Fmt.money(c.n("food")), total = true)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Half("1st – 15th", c.n("foodHalf"), c.n("foodH1"), fp?.n("half") == 1.0, Modifier.weight(1f))
        Half("16th – end", c.n("foodHalf"), c.n("foodH2"), fp?.n("half") == 2.0, Modifier.weight(1f))
    }
    if (fp != null) Text("You can spend about ${Fmt.money(fp.n("perDay"))} a day on food until ${Fmt.dShort(fp.s("end"))}.", style = MaterialTheme.typography.bodyMedium)

    /* ---- food logged this month ---- */
    Spacer(Modifier.height(4.dp))
    Text("Food logged", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    val food = c["expenses"].arr().filterIsInstance<Obj>().filter { !it.t("oneTime") && Core.FOOD.contains(it.s("category")) }
    Column {
        if (food.isEmpty()) EmptyNote("No groceries or takeout logged this month yet.")
        var last = ""
        food.forEach { e -> val d = e.s("date"); if (d != last) { last = d; DayHeader(Fmt.relDay(d, today)) }; ExpenseRow(e, locked, vm) }
        LineRow("Food total", Fmt.money(c.n("foodH1") + c.n("foodH2")), total = true)
    }
    if (!locked) FilledTonalButton(onClick = { open(Sheet.Log(category = "groceries")) }) { Text("+ Log food") }

    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    FoodSettings(vm, ym, s)
}

@Composable
fun Half(label: String, allow: Double, spent: Double, now: Boolean, modifier: Modifier) {
    val x = LocalExtra.current; val over = spent > allow
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = if (now) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); if (now) Pill("Now", "good") }
            Text(Fmt.money(allow - spent), style = MaterialTheme.typography.titleLarge.merge(Numbers), fontWeight = FontWeight.SemiBold,
                color = if (over) toneColors("bad").first else MaterialTheme.colorScheme.onSurface)
            Text("left of ${Fmt.money(allow)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GrowBar(if (allow > 0) minOf(1.0, spent / allow).toFloat() else 0f, if (over) x.bad else x.good)
            Text("${Fmt.money(spent)} spent", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Share of leftover money for food, and the monthly minimum, with a preview of what the month would get. */
@Composable
fun FoodSettings(vm: MainViewModel, ym: String, s: Obj, bare: Boolean = false) {
    val pct0 = s.n("foodPct").let { Math.round(it).toString() }; val min0 = money2(s.n("foodMin"))
    var pct by rememberSaveable(pct0) { mutableStateOf(pct0) }; var min by rememberSaveable(min0) { mutableStateOf(min0) }
    var err by rememberSaveable { mutableStateOf<String?>(null) }
    if (!bare) Text("Food budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("Each month, food gets a share of the money left after fixed bills. It's split in two: one half for the 1st–15th and one for the 16th–end. Whatever food doesn't get goes to Emergency Savings.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextFieldRow(pct, { v -> pct = v.filter { it.isDigit() }.take(3); err = null }, "Share of leftover money for food (%)", hint = "40% is a starting point. Raise it if food runs short.", number = true)
    MoneyField(min, { min = it; err = null }, "Minimum food budget per month", hint = "Food never drops below this, even in a tight month.")
    val (vals, verr) = vm.readFood(pct, min)
    val changed = vals != null && (vals.first.toString() != pct0 || money2(vals.second) != min0)
    if (vals != null && changed) {
        val p = vm.foodPreview(ym, vals.first, vals.second)
        Text("With these, food would get ${Fmt.money(p.n("food"))} in ${Fmt.monthOnly(ym)} (${Fmt.money(p.n("foodHalf"))} for each half).",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
    ErrorText(err)
    Button(onClick = { if (vals == null) err = verr else { vm.saveFood(vals.first, vals.second) } }, enabled = changed || vals == null,
        modifier = Modifier.testTag("save-food")) { Text("Save food budget") }
}
