package looniemate.app.ui.sheets

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.ui.LineRow
import looniemate.app.ui.toneColors
import looniemate.engine.Core
import looniemate.engine.Docs
import looniemate.engine.Fmt
import looniemate.engine.Views
import looniemate.engine.*

/** Add or change a savings fund. Emergency Savings and the Child Reserve always exist; their start and goal live in settings. */
@Composable
fun FundSheet(vm: MainViewModel, key: String?, close: () -> Unit) {
    val s = vm.settings(); val f = key?.let { Core.fundOf(s, it) }
    val core = key == "emergency" || key == "child"
    var name by rememberSaveable { mutableStateOf(f?.s("name") ?: "") }
    var account by rememberSaveable { mutableStateOf(f?.let { Core.acctId(s, it.s("account")) } ?: (Core.accountsOf(s).find { it.s("type") == "savings" }?.s("id") ?: "")) }
    var start by rememberSaveable { mutableStateOf(money2(when (key) { "emergency" -> s.n("emergencyStart"); "child" -> s.n("childStart"); else -> f.n("start") })) }
    var goal by rememberSaveable { mutableStateOf(when (key) { "emergency" -> money2(s.n("emergencyGoal")); "child" -> Math.round(s.n("ccbEmergencyPct")).toString(); else -> if (f.n("goal") > 0) money2(f.n("goal")) else "" }) }
    var err by rememberSaveable { mutableStateOf<String?>(null) }; var confirm by rememberSaveable { mutableStateOf(false) }

    SheetTitle(when { key == null -> "Add a savings fund"; core -> f!!.s("name"); else -> "Edit ${f!!.s("name")}" })
    if (!core) TextFieldRow(name, { name = it.take(40) }, "Fund", placeholder = "Vet fund")
    val accts = listOf("" to "Not set") + Core.accountsOf(s).filter { it.s("type") == "savings" }.map { it.s("id") to it.s("name") }
    SelectField(account, accts, { account = it }, "Kept in", hint = if (accts.size == 1) "Add a savings account in Settings → Accounts to pick one." else null)
    MoneyField(start, { start = it }, "Balance at the start", hint = "What was already saved when you started using LoonieMate. Leave blank if none.")
    if (key == "child") TextFieldRow(goal, { v -> goal = v.filter { it.isDigit() }.take(3) }, "Usual share of the Child Benefit to Emergency Savings (%)", number = true,
        hint = "You can change it month by month on the Savings tab.")
    else MoneyField(goal, { goal = it }, if (key == "emergency") "Goal" else "Goal (optional)")
    ErrorText(err)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { err = vm.saveFund(key, name, account, start, goal); if (err == null) close() }) { Text(if (key == null) "Add fund" else "Save") }
        Spacer(Modifier.weight(1f))
        if (key != null && !core) {
            if (!confirm) TextButton(onClick = { confirm = true }) { Text("Remove fund", color = toneColors("bad").first) }
            else Button(onClick = { vm.deleteFund(key); close() }) { Text("Tap to remove") }
        }
    }
    if (confirm) Text("Bills that put money into it become regular bills. You can Undo right after.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Take money out of a fund to pay for what it was saved for. */
@Composable
fun UseFundSheet(vm: MainViewModel, key: String, close: () -> Unit) {
    val s = vm.settings(); val f = Core.fundOf(s, key); val isVet = Regex("vet", RegexOption.IGNORE_CASE).containsMatchIn(f.s("name"))
    val bal = Core.savingsState(Docs.toStore(vm.repo.docs.value, vm.today.value)).funds[key] ?: 0.0
    var amt by rememberSaveable { mutableStateOf("") }; var note by rememberSaveable { mutableStateOf("") }; var err by rememberSaveable { mutableStateOf<String?>(null) }
    SheetTitle(if (isVet) "Cash out a vet visit" else "Use money from ${f.s("name")}")
    Text("${f.s("name")} has ${Fmt.money(bal)}. This takes the money out of the fund. It doesn't count as spending in your monthly budget, because you already saved for it.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    MoneyField(amt, { amt = it }, if (isVet) "Vet bill amount" else "Amount")
    TextFieldRow(note, { note = it.take(60) }, "What for (optional)", placeholder = if (isVet) "Checkup, shots, emergency visit…" else null)
    ErrorText(err)
    Button(onClick = { val a = Views.parseMoney(amt); if (a == null || a.isNaN() || !(a > 0)) err = "Enter an amount greater than \$0." else { vm.useFund(key, a, note); close() } },
        Modifier.fillMaxWidth()) { Text(if (isVet) "Cash it out" else "Take it out") }
}

/** How much of a month's Child Benefit goes to Emergency Savings instead of the Child Reserve. */
@Composable
fun CcbSheet(vm: MainViewModel, ym: String, close: () -> Unit) {
    val c = Core.monthCalc(ym, Docs.toStore(vm.repo.docs.value, vm.today.value), vm.today.value); val total = c.n("ccb"); val mo = Fmt.monthOnly(ym)
    var amt by rememberSaveable { mutableStateOf(money2(c.n("ccbToEm"))) }; var err by rememberSaveable { mutableStateOf<String?>(null) }
    val v = Views.parseMoney(amt)?.takeIf { !it.isNaN() } ?: 0.0
    SheetTitle("Split the $mo Child Benefit")
    Text(if (c.n("net") >= 0) "$mo is on track (${Fmt.money(c.n("net"))} for savings), so this is a good month to put a little toward Emergency Savings."
        else "$mo is ${Fmt.money(-c.n("net"))} short. Keeping the Child Benefit protected is safest this month.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("How much goes to Emergency Savings", fontWeight = FontWeight.SemiBold)
    Slider(v.coerceIn(0.0, Math.ceil(total)).toFloat(), { amt = money2(Math.round(it).toDouble()); err = null }, valueRange = 0f..Math.ceil(total).toFloat().coerceAtLeast(1f))
    MoneyField(amt, { amt = it; err = null }, "Amount to Emergency Savings")
    Column { LineRow("Child Reserve", Fmt.money(maxOf(0.0, total - v))); LineRow("Emergency Savings", Fmt.money(v)) }
    ErrorText(err)
    Button(onClick = { val a = Views.parseMoney(amt)
        err = when { a == null || a.isNaN() || a < 0 -> "Enter an amount of \$0 or more."; a > total + 0.004 -> "The Child Benefit is ${Fmt.money(total)} this month."; else -> null }
        if (err == null) { vm.setCcbSplit(ym, a!!); close() } }, Modifier.fillMaxWidth()) { Text("Save split") }
}
