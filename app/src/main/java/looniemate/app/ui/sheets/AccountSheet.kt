@file:OptIn(ExperimentalLayoutApi::class)

package looniemate.app.ui.sheets

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.ui.toneColors
import looniemate.engine.Accounts
import looniemate.engine.Core
import looniemate.engine.s
import looniemate.engine.str
import looniemate.engine.arr
import looniemate.engine.*
import looniemate.engine.Views

/** Add or change one account: its name, type, and what it's for (or, for savings, what kind). */
@Composable
fun AccountSheet(vm: MainViewModel, id: String?, close: () -> Unit) {
    val s = vm.settings()
    val a = id?.let { Core.acctById(s, it) }
    val others = Core.accountsOf(s).filter { it.s("id") != id }
    var name by rememberSaveable { mutableStateOf(a?.s("name") ?: "") }
    var type by rememberSaveable { mutableStateOf(a?.s("type")?.takeIf { t -> Accounts.TYPES.any { it.first == t } } ?: "chequing") }
    var kind by rememberSaveable { mutableStateOf(a?.s("kind")?.ifEmpty { null } ?: "hisa") }
    /* a brand-new account gets "Main account" ticked if no account has it yet */
    val purposes = rememberSaveable(saver = stringListSaver()) { mutableStateListOf<String>().apply {
        if (a != null) addAll(a["purposes"].arr().map { str(it) }) else if (Core.acctFor(s, "main") == null) add("main") } }
    var err by rememberSaveable { mutableStateOf<String?>(null) }; var confirmDelete by rememberSaveable { mutableStateOf(false) }
    /* taking out cash (docs/plans/overhaul.md): the ATM's daily limit and bills, tellers, and what to do over the limit */
    var limit by rememberSaveable { mutableStateOf(a?.get("atmLimit")?.let { v -> if (num(v) > 0) money2(num(v)) else "" } ?: "") }
    var notes by rememberSaveable { mutableStateOf(a?.get("atmNotes")?.let { v -> num(v).toInt().takeIf { it == 5 || it == 10 }?.toString() } ?: "20") }
    var tellers by rememberSaveable { mutableStateOf(a?.get("tellers") != looniemate.engine.J.B(false)) }
    var over by rememberSaveable { mutableStateOf(a?.s("atmOver") ?: "") }
    var also by rememberSaveable { mutableStateOf(a?.s("atmAlso") ?: "") }; val pg = LocalPage.current
    val used = if (id != null) Accounts.billsUsing(s, vm.repo.docs.value.filterKeys { it.startsWith("bills/") }.values, id) else 0

    SheetTitle(if (a != null) "Edit account" else "Add an account")
    TextFieldRow(name, { name = it.take(40) }, "Name", placeholder = "TD Chequing")

    Text("Type", fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Accounts.TYPES.forEach { (k, l) -> FilterChip(type == k, { type = k }, label = { Text(l) }) }
    }

    if (type == "savings") {
        Text("Kind of savings account", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Accounts.SAV_KINDS.forEach { (k, l) -> FilterChip(kind == k, { kind = k }, label = { Text(l) }) }
        }
        Hint("Savings accounts don't need a purpose; savings funds are kept in them. TFSAs, RDSPs and RESPs get extra help with free government money.")
    } else {
        Text("What it's for", fontWeight = FontWeight.SemiBold)
        Hint("Tick what this account is for and LoonieMate works out where money should go. Each purpose can be on one account only.")
        Accounts.PURPOSES.forEach { p ->
            val on = p.key in purposes
            val holder = others.find { o -> o["purposes"].arr().any { str(it) == p.key } }
            Row(Modifier.fillMaxWidth().toggleable(on, role = Role.Checkbox) { v -> if (v) purposes.add(p.key) else purposes.remove(p.key) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top) {
                Checkbox(on, null, Modifier.padding(top = 2.dp, end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.label, fontWeight = FontWeight.SemiBold)
                    Text(p.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (holder != null) Text(if (on) "This moves it off ${holder.s("name")}." else "Now on ${holder.s("name")}.",
                        style = MaterialTheme.typography.bodySmall, color = if (on) toneColors("warn").first else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (on) FontWeight.SemiBold else null)
                }
            }
        }
    }

    if (type == "chequing" || type == "prepaid") {
        Text("Taking out cash", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Hint("For bills paid in cash and cash for food. LoonieMate plans each trip to the ATM around what this account's ATM can do.")
        MoneyField(limit, { limit = it; err = null }, "Daily ATM limit (optional)", hint = "Check your banking app; it's often \$500 or \$1,000. Blank means no limit.")
        SelectField(notes, listOf("20" to "\$20 bills", "10" to "\$10s and \$20s", "5" to "\$5s and up"), { notes = it }, "Bills the ATM gives out")
        looniemate.app.ui.screens.SwitchRow("Branches with tellers", "Turn off for an online bank like Simplii or Tangerine, so LoonieMate tells you when you'll need coins.", tellers) { pg?.mark(); tellers = it }
        if (Views.parseMoney(limit)?.let { it > 0 } == true) {
            Text("When the cash is more than the limit", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(over != "split", { pg?.mark(); over = "days" }, label = { Text("Go back each day") })
                FilterChip(over == "split", { pg?.mark(); over = "split" }, label = { Text("Use another account too") }) }
            val choices = others.filter { it.s("type") == "chequing" || it.s("type") == "prepaid" }.map { it.s("id") to it.s("name") }
            if (over == "split") { if (choices.isEmpty()) Hint("Add another chequing account or prepaid card first.") else SelectField(also, listOf("" to "Pick an account") + choices, { also = it; err = null }, "Take the rest from") }
            else Hint("LoonieMate spreads the cash over the days before it's needed.")
        }
    }

    if (id != null) Hint(if (used > 0) "Used by $used bill${if (used > 1) "s" else ""}." else "Not used by any bills.")
    ErrorText(err)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { err = vm.saveAccount(id, name, type, kind, purposes.toList(),
            if (type == "chequing" || type == "prepaid") MainViewModel.AtmSettings(limit, notes, tellers, if (Views.parseMoney(limit)?.let { it > 0 } == true) over.ifEmpty { "days" } else "", also) else null); if (err == null) close() }, modifier = Modifier.testTag("save-account")) {
            Text(if (a != null) "Save account" else "Add account") }
        Spacer(Modifier.weight(1f))
        if (a != null && id != null) {
            if (!confirmDelete) TextButton(onClick = { confirmDelete = true }) { Text("Remove", color = toneColors("bad").first) }
            else Button(onClick = { vm.deleteAccount(id); close() }) { Text("Tap to remove") }
        }
    }
    if (confirmDelete && used > 0) Hint("Its $used bill${if (used > 1) "s" else ""} will need a new account. You can Undo right after.")
}

@Composable
private fun Hint(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
