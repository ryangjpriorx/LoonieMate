@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package looniemate.app.ui.sheets

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.ui.screens.DebtBreakdown
import looniemate.app.ui.screens.SwitchRow
import looniemate.app.ui.toneColors
import looniemate.engine.Core
import looniemate.engine.Docs
import looniemate.engine.Fmt
import looniemate.engine.J
import looniemate.engine.Json
import looniemate.engine.Labels
import looniemate.engine.Obj
import looniemate.engine.Views
import looniemate.engine.isDate
import looniemate.engine.n
import looniemate.engine.num
import looniemate.engine.or
import looniemate.engine.s
import looniemate.engine.t
import looniemate.engine.*

private class Extra(date: String, amount: String) { var date by mutableStateOf(date); var amount by mutableStateOf(amount) }

@Composable
fun BillSheet(vm: MainViewModel, id: String?, group0: String, close: () -> Unit) {
    val s = vm.settings(); val today = vm.today.value
    val b: Obj = id?.let { vm.repo.get("bills/$it") } ?: run {
        val main = Core.acctFor(s, "main"); val every = Core.acctFor(s, "everyday")
        J.obj("group" to group0, "freq" to "monthly", "day" to 1, "category" to "other", "account" to (if (group0 == "adults") main else every ?: main)?.s("id"),
            "method" to (if (group0 == "adults") "Pre-authorized debit (PAD)" else "Debit card"))
    }
    val g = b.or("group", "adults")
    var name by rememberSaveable { mutableStateOf(b.s("name")) }
    var amount by rememberSaveable { mutableStateOf(if (b["amount"] != null) money2(b.n("amount")) else "") }
    var freq by rememberSaveable { mutableStateOf(b.or("freq", "monthly")) }
    var day by rememberSaveable { mutableStateOf(if (b.t("day")) b.n("day").toInt().toString() else "1") }
    var anchor by rememberSaveable { mutableStateOf(b.s("anchor").ifEmpty { today }) }
    var cat by rememberSaveable { mutableStateOf(b.or("category", "other")) }
    var acct by rememberSaveable { mutableStateOf(Core.acctId(s, b.s("account"))) }
    var method by rememberSaveable { mutableStateOf(b.s("method")) }
    var fund by rememberSaveable { mutableStateOf(b.s("fund")) }
    var nonEss by rememberSaveable { mutableStateOf(b["essential"].let { it is J.B && !it.v }) }
    var deferrable by rememberSaveable { mutableStateOf(b.t("deferrable")) }
    var start by rememberSaveable { mutableStateOf(b.s("start")) }; var end by rememberSaveable { mutableStateOf(b.s("end")) }
    var paused by rememberSaveable { mutableStateOf(b["active"].let { it is J.B && !it.v }) }
    val d0 = b["debt"]; val dp = Core.debtParts(d0)
    var debtOpen by rememberSaveable { mutableStateOf(dp != null && dp.P > 0) }
    var dmode by rememberSaveable { mutableStateOf(if (dp == null || dp.orig) "original" else "balance") }
    var principal by rememberSaveable { mutableStateOf(if (d0.n("principal") > 0) money2(d0.n("principal")) else "") }
    var dstart by rememberSaveable { mutableStateOf(d0.s("start")) }
    var missed by rememberSaveable { mutableStateOf(if (d0.n("missed") > 0) d0.n("missed").toInt().toString() else "") }
    var owed by rememberSaveable { mutableStateOf(if (d0.n("owed") > 0) money2(d0.n("owed")) else "") }
    var asOf by rememberSaveable { mutableStateOf(d0.s("asOf").ifEmpty { today }) }
    var rate by rememberSaveable { mutableStateOf(if (d0.n("rate") > 0) Json.numStr(d0.n("rate")) else "") }
    val extras = remember { mutableStateListOf<Extra>().apply { d0?.get("extra").let { it as? J.A }?.list?.forEach { add(Extra(it.s("date"), if (it.n("amount") > 0) money2(it.n("amount")) else "")) } } }
    var moreOpen by rememberSaveable { mutableStateOf(b.t("start") || b.t("end") || paused) }
    var err by rememberSaveable { mutableStateOf<String?>(null) }; var confirmDelete by rememberSaveable { mutableStateOf(false) }

    fun debtForm() = Views.DebtForm(dmode, principal, dstart, missed, owed, asOf, rate, extras.map { it.date to it.amount })

    Text((if (id != null) "Edit " else "Add ") + (Labels.GROUPS[g]?.label ?: "Adults").lowercase() + " bill", style = MaterialTheme.typography.headlineSmall)
    TextFieldRow(name, { name = it.take(60) }, "Name", placeholder = if (g == "baby") "Diapers" else if (g == "pets") "Dog food" else "Hydro")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MoneyField(amount, { amount = it }, "Amount each time", Modifier.weight(1f))
        SelectField(freq, Labels.FREQS, { freq = it }, "How often", Modifier.weight(1f))
    }
    if (freq == "monthly") TextFieldRow(day, { v -> day = v.filter { it.isDigit() }.take(2) }, "Day of the month it's due", number = true)
    else DateField(anchor, { anchor = it }, "Next due date")
    SelectField(cat, Labels.BILLCATS[g] ?: emptyList(), { cat = it }, "Type", hint = if (g == "adults") "Only rent and housing costs go under \"Rent & housing\"." else null)
    val accounts = Core.accountsOf(s).map { it.s("id") to it.s("name") }
    if (accounts.isNotEmpty()) SelectField(acct, accounts, { acct = it }, "Paid from")
    SelectField(method, Labels.METHODS.map { it to it }, { method = it }, "Paid by")
    SelectField(fund, listOf("" to "No, it's a regular bill") + Core.fundsOf(s).filter { it.s("key") != "child" }.map { it.s("key") to it.s("name") }, { fund = it }, "Puts money into savings",
        hint = "For savings like a vet fund or an RESP. It shows up as a money move instead of a bill.")
    SwitchRow("Non-essential", "We could give this up for a month if money is short (for example a streaming service).", nonEss) { nonEss = it }
    SwitchRow("Deferrable", "This can be paid late if we arrange it (for example a payment you can push back a few weeks).", deferrable) { deferrable = it }

    /* ---- debt ---- */
    Section("This bill pays off a debt (optional)", debtOpen) { debtOpen = !debtOpen }
    AnimatedVisibility(debtOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("For a loan, consumer proposal or payment plan. LoonieMate works out what's left and when it will be paid off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(dmode == "original", { dmode = "original" }, label = { Text("I know the original amount") })
                FilterChip(dmode == "balance", { dmode = "balance" }, label = { Text("I have a statement balance") })
            }
            if (dmode == "original") {
                MoneyField(principal, { principal = it }, "Original amount owed", hint = "The full amount at the start, before any payments.")
                DateField(dstart, { dstart = it }, "Started on", hint = "The day you owed the full amount. If you paid something to get it started, use that day or earlier.")
                TextFieldRow(missed, { v -> missed = v.filter { it.isDigit() }.take(3) }, "Missed payments", hint = "How many payments since the start didn't happen.", number = true, placeholder = "0")
            } else {
                MoneyField(owed, { owed = it }, "Balance on the statement")
                DateField(asOf, { asOf = it }, "Statement date", hint = "Extra payments before it are already in the balance.")
            }
            Text("Extra payments outside the regular schedule", fontWeight = FontWeight.SemiBold)
            Text("Like a payment to get a proposal started, or a lump sum from a tax refund. Future dates count toward the payoff too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extras.forEachIndexed { i, x ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DateField(x.date, { x.date = it }, "Date", Modifier.weight(1.3f))
                    MoneyField(x.amount, { x.amount = it }, "Amount", Modifier.weight(1f))
                    IconButton(onClick = { extras.removeAt(i) }) { Icon(Icons.Rounded.Close, "Remove this payment", tint = toneColors("bad").first) }
                }
            }
            OutlinedButton(onClick = { extras.add(Extra("", "")) }) { Text("+ Add a payment") }
            TextFieldRow(rate, { rate = it }, "Interest rate (% a year)", hint = "Consumer proposals usually have none.", number = true, placeholder = "0 if no interest")
            /* live preview, worked out by the same engine */
            val (debt, derr) = Views.readDebt(debtForm(), today)
            val amt = Views.parseMoney(amount)
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(4.dp)) {
                    when {
                        derr != null -> Text(derr, color = toneColors("bad").first, modifier = Modifier.padding(10.dp))
                        debt == null || amt == null || amt.isNaN() || amt <= 0 -> Text("Fill in the amounts to see what's left.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                        else -> {
                            val temp = previewBill(name, amt, freq, day, anchor, start, end, debt)
                            val docs = LinkedHashMap(vm.repo.docs.value); docs.entries.removeAll { it.key.startsWith("bills/") }; docs["bills/__preview"] = temp
                            val info = Core.debtInfo(Docs.toStore(docs, today), "__preview", today)
                            if (info == null) Text("Fill in the amounts to see what's left.", modifier = Modifier.padding(10.dp)) else DebtBreakdown(info)
                        }
                    }
                }
            }
        }
    }

    /* ---- start, stop, pause ---- */
    Section("Start, stop or pause (optional)", moreOpen) { moreOpen = !moreOpen }
    AnimatedVisibility(moreOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField(start, { start = it }, "Starts on", hint = if (g == "baby") "Leave blank to start on the baby's due date (if set)." else "Leave blank if it has already started.", clearable = true)
            DateField(end, { end = it }, "Ends on", clearable = true)
            SwitchRow("Paused", "Not counted in the budget.", paused) { paused = it }
        }
    }
    ErrorText(err)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            val amt = Views.parseMoney(amount); val dayN = day.toIntOrNull() ?: 0
            err = when {
                name.isBlank() -> "Give the bill a name."
                amt == null || amt.isNaN() || !(amt > 0) -> "Enter an amount greater than \$0."
                freq == "monthly" && dayN !in 1..31 -> "Pick a due day from 1 to 31."
                freq != "monthly" && !isDate(anchor) -> "Pick the next due date."
                isDate(start) && isDate(end) && end < start -> "The end date is before the start date."
                else -> null
            }
            val (debt, derr) = if (debtOpen) Views.readDebt(debtForm(), today) else (null to null)
            if (err == null && derr != null) err = derr
            if (err == null) {
                val doc = J.obj("name" to name.trim(), "group" to g, "amount" to amt, "freq" to freq, "category" to cat, "account" to acct, "method" to method,
                    "active" to !paused, "essential" to !nonEss, "deferrable" to deferrable, "fund" to fund, "updated" to System.currentTimeMillis())
                if (freq == "monthly") doc["day"] = J.N(dayN.toDouble()) else doc["anchor"] = J.S(anchor)
                if (isDate(start)) doc["start"] = J.S(start); if (isDate(end)) doc["end"] = J.S(end)
                if (debt != null) doc["debt"] = debt
                if (b.t("example") && id != null && num(b["amount"]) == amt) doc["example"] = J.B(true)
                vm.saveBill(id, doc); close()
            }
        }) { Text("Save bill") }
        Spacer(Modifier.weight(1f))
        if (id != null) {
            if (!confirmDelete) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = toneColors("bad").first) }
            else Button(onClick = { vm.deleteBill(id); close() }) { Text("Tap to delete") }
        }
    }
}

private fun previewBill(name: String, amt: Double, freq: String, day: String, anchor: String, start: String, end: String, debt: J): Obj {
    val o = J.obj("name" to name, "amount" to amt, "freq" to freq, "active" to true, "debt" to debt)
    if (freq == "monthly") o["day"] = J.N((day.toIntOrNull() ?: 1).toDouble()) else o["anchor"] = J.S(anchor)
    if (isDate(start)) o["start"] = J.S(start); if (isDate(end)) o["end"] = J.S(end)
    return o
}

@Composable
private fun Section(title: String, open: Boolean, toggle: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().clickable(onClick = toggle)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp))
            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
        }
    }
}
