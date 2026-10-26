package looniemate.app.ui.sheets

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.LineRow
import looniemate.app.ui.Pill
import looniemate.app.ui.theme.Numbers
import looniemate.app.ui.toneColors
import looniemate.engine.Core
import looniemate.engine.Fmt
import looniemate.engine.n
import looniemate.engine.s
import looniemate.engine.t
import looniemate.engine.*

/** Type in what each account holds, and see how it compares with the plan (the web version's "Check my balances"). */
@Composable
fun CheckinSheet(vm: MainViewModel, close: () -> Unit) {
    val s = vm.settings(); val A = Core.accountsOf(s)
    val prev = Docs.checkins(vm.repo.docs.value).firstOrNull()
    val typed = rememberSaveable(saver = stateMapSaver<String>()) { mutableStateMapOf<String, String>() }
    var result by remember { mutableStateOf<MainViewModel.CheckResult?>(null) }
    var err by rememberSaveable { mutableStateOf<String?>(null) }
    val blanks = rememberBlankCheck()

    SheetTitle("Check my balances")
    if (A.isEmpty()) { EmptyNote("Add your accounts in Settings first."); return }
    Text("Open your banking apps and type in what each account holds right now. Count cash on hand too. Leave an account blank if it's empty.", style = MaterialTheme.typography.bodyMedium)
    val groups = listOf("Everyday accounts" to A.filter { it.s("type") != "savings" }, "Savings accounts" to A.filter { it.s("type") == "savings" })
    groups.filter { it.second.isNotEmpty() }.forEach { (title, list) ->
        Text(title, fontWeight = FontWeight.SemiBold)
        list.forEach { a -> val id = a.s("id"); val last = prev?.get("bal")?.get(id)
            MoneyField(typed[id] ?: "", { typed[id] = it; result = null; err = null }, a.s("name"), hint = if (last != null && last != J.Null) "Last time: ${Fmt.money(num(last))}" else null) }
    }
    ErrorText(err)
    if (result == null) Button(onClick = { val ids = A.map { it.s("id") }
        blanks.run(ids.count { (typed[it] ?: "").isBlank() }) { val (r, e) = vm.compareBalances(blanksAsZero(typed, ids)); result = r; err = e } }, modifier = Modifier.testTag("compare")) { Text("Compare with the plan") }
    AnimatedVisibility(result != null) { result?.let { r -> Result(r, vm, close) } }
}

@Composable
private fun Result(r: MainViewModel.CheckResult, vm: MainViewModel, close: () -> Unit) {
    var how by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val ex = r.ex; val gap = r.gap
        when {
            ex == null || gap == null -> Banner("good", "First check-in saved as your starting point.", "Next time, LoonieMate compares your balances with everything the plan expected in between.")
            Math.abs(gap) < 5 -> Banner("good", "Right on plan.", "Your everyday accounts hold ${Fmt.money(r.day)}, and the plan expected ${Fmt.money(ex.expected)}.")
            gap < 0 -> Banner("bad", "${Fmt.money(-gap)} less than the plan expected.", "You have ${Fmt.money(r.day)}; the plan expected ${Fmt.money(ex.expected)}. Usually it's spending that wasn't logged, a bill that was higher than planned, or a payment that hasn't arrived yet.")
            else -> Banner("warn", "${Fmt.money(gap)} more than the plan expected.", "You have ${Fmt.money(r.day)}; the plan expected ${Fmt.money(ex.expected)}. Maybe a bill hasn't come out yet, something was logged twice, or income was higher than planned.")
        }
        if (ex != null && r.prev != null) {
            TextButton(onClick = { how = !how }) { Text(if (how) "Hide how the plan worked it out" else "How the plan worked it out") }
            AnimatedVisibility(how) { Column {
                LineRow("At the last check (${Fmt.dLabel(r.prev.s("date"))})", Fmt.money(r.prev.n("day")))
                LineRow("Money in since then", "+" + Fmt.money(ex.income))
                LineRow("Bills due since then", "−" + Fmt.money(ex.bills))
                LineRow("Spending logged since then", "−" + Fmt.money(ex.spent))
                LineRow("Savings moves marked done", (if (ex.savings >= 0) "+" else "−") + Fmt.money(Math.abs(ex.savings)))
                LineRow("Expected now", Fmt.money(ex.expected), total = true)
            } }
        }
        Text("Enough for bills until payday?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        r.coverage.forEach { x -> val short = x.n("short") > 0.5
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(x.s("name"), fontWeight = FontWeight.SemiBold)
                    Text((if (x.n("need") > 0) "Needs ${Fmt.money(x.n("need"))} before ${Fmt.dShort(x.s("until"))}" + (if (x.n("food") > 0) ", including ${Fmt.money(x.n("food"))} food money" else "")
                        else "Nothing more needed before ${Fmt.dShort(x.s("until"))}") + (if (x.n("income") > 0) " · ${Fmt.money(x.n("income"))} coming in" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.money(x.n("have")), style = MaterialTheme.typography.bodyLarge.merge(Numbers))
                    Pill(if (short) "Short ${Fmt.money(x.n("short"))}" else "OK", if (short) "bad" else "good")
                }
            }
        }
        val shortAccts = r.coverage.filter { it.n("short") > 0.5 }
        if (shortAccts.isNotEmpty()) Text((shortAccts.filter { !it.t("isMain") }.map { "Move ${Fmt.money(it.n("short"))} into ${it.s("name")} from your main account." } +
            (if (shortAccts.any { it.t("isMain") }) listOf("The main account is short for what's coming before payday. Check the Month tab for ways to cover it.") else emptyList())).joinToString(" "),
            style = MaterialTheme.typography.bodyMedium)
        if (r.hasSavings) { val sgap = round2(r.sav - r.funds)
            Text(if (Math.abs(sgap) < 5) "Savings accounts match your savings funds (${Fmt.money(r.funds)})."
                else "Savings accounts hold ${Fmt.money(r.sav)}, but your savings funds add up to ${Fmt.money(r.funds)}. " + (if (sgap < 0) "A transfer may not have happened yet." else "Record the extra as a transfer so the funds match."),
                style = MaterialTheme.typography.bodySmall, color = if (Math.abs(sgap) < 5) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { vm.saveCheckin(r, false); close() }, modifier = Modifier.testTag("save-checkin")) { Text("Save this check-in") }
            if (gap != null && gap < 0 && Math.abs(gap) >= 5) OutlinedButton(onClick = { vm.saveCheckin(r, true); close() }) { Text("Log ${Fmt.money(-gap)} as spending") }
        }
    }
}

@Composable
private fun Banner(tone: String, title: String, body: String) {
    val (fg, bg) = toneColors(tone)
    Surface(shape = MaterialTheme.shapes.medium, color = bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = fg)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
