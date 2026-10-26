@file:OptIn(ExperimentalLayoutApi::class)

package looniemate.app.ui.sheets

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.ui.LineRow
import looniemate.app.ui.Pill
import looniemate.app.ui.screens.SwitchRow
import looniemate.app.ui.toneColors
import looniemate.engine.Core
import looniemate.engine.Docs
import looniemate.engine.Fmt
import looniemate.engine.IncomeSetup
import looniemate.engine.Views
import looniemate.engine.*

@Composable
private fun DedToggle(ded: MutableMap<String, Boolean>, k: String, l: String, d: String, def: Boolean = false) = SwitchRow(l, d, ded[k] ?: def) { ded[k] = it }

@Composable
private fun Hint2(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

private fun acctOptions(s: J) = listOf("" to "Main account") + Core.accountsOf(s).map { it.s("id") to it.s("name") }

/**
 * A paycheque or other income ([assist0] null), or social assistance (a program key). Edits a copy
 * of the saved income so fields this version doesn't know about are kept.
 */
@Composable
fun IncomeSheet(vm: MainViewModel, id: String?, assist0: String?, close: () -> Unit) {
    val s = vm.settings(); val today = vm.today.value; val list = Core.incomesOf(s)
    val base: Obj = remember { id?.let { i -> list.find { it.s("id") == i }?.copy() as? Obj }
        ?: if (assist0 != null) IncomeSetup.newAssist(list, "as" + vm.repo.newId(), assist0) else IncomeSetup.newIncome(list, "i" + vm.repo.newId()) }
    val isNew = id == null
    var prog by remember { mutableStateOf(Core.assistOf(base)) }
    val P = prog?.let { Core.ASSIST[it] }
    val sc = base["sched"] as? Obj ?: Obj()
    var name by remember { mutableStateOf(base.s("name")) }
    var amount by remember { mutableStateOf(money2(base.n("amount"))) }
    var type by remember { mutableStateOf(sc.or("type", "monthly")) }
    var day by remember { mutableStateOf(if (sc.t("day")) Math.round(sc.n("day")).toString() else "1") }
    var anchor by remember { mutableStateOf(sc.s("anchor")) }
    var slot by remember { mutableStateOf(if (sc.t("slot")) Math.round(sc.n("slot")).toInt() else 3) }
    var account by remember { mutableStateOf(Core.acctId(s, base.s("account"))) }
    var role by remember { mutableStateOf(base.or("role", "other")) }
    var counts by remember { mutableStateOf(base.s("odsp")) }
    var person by remember { mutableStateOf(base.s("person")) }
    val rep0 = IncomeSetup.reportCfg(base)
    var repOn by remember { mutableStateOf(rep0.t("on")) }; var repDay by remember { mutableStateOf(Math.round(rep0.n("day").takeIf { it > 0 } ?: 7.0).toString()) }
    /* assistance deductions */
    val ded0 = if (prog != null) Core.dedCfg(s, base) else Obj()
    val ded = remember { mutableStateMapOf<String, Boolean>().apply { listOf("dis", "work", "full", "wrb", "supported", "couple").forEach { k -> ded0[k]?.let { put(k, truthy(it)) } } } }
    var lag by remember { mutableStateOf(ded0["lag"]?.takeIf { it != J.Null && str(it) != "" }?.let { Math.round(num(it)).toInt() }) }
    var deps by remember { mutableStateOf(if (ded0["deps"] != null) Math.round(ded0.n("deps")).toString() else "0") }
    var flat by remember { mutableStateOf(ded0["flat"]?.let { if (it == J.Null || str(it) == "") "" else money2(num(it)) } ?: "") }
    var rate by remember { mutableStateOf(ded0["rate"]?.let { if (it == J.Null || str(it) == "") "" else Math.round(num(it)).toString() } ?: "") }
    var err by remember { mutableStateOf<String?>(null) }; var confirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    SheetTitle(when { isNew && P != null -> "Add ${P.short}"; isNew -> "Add income"; else -> "Edit ${base.s("name")}" })
    if (P != null) {
        val progs = IncomeSetup.programs(s).let { if (it.any { p -> p.key == prog }) it else it + P }
        SelectField(prog!!, progs.map { it.key to it.name }, { prog = it; if (!isNew || name == P.short) name = Core.ASSIST.getValue(it).short }, "Program")
    }
    TextFieldRow(name, { name = it.take(40) }, "Name", placeholder = "Work")
    MoneyField(amount, { amount = it }, if (P != null) "Usual amount before deductions" else "Usual amount each time", hint = "Change a single month on the Month tab.")
    if (P == null) {
        SelectField(type, IncomeSetup.TYPES, { t -> type = t; IncomeSetup.schedFor(t, today).let { d -> if (d.t("day")) day = "1"; if (d.t("anchor")) anchor = d.s("anchor") } }, "How often")
        if (type == "monthly") TextFieldRow(day, { v -> day = v.filter { it.isDigit() }.take(2) }, "Day of the month", number = true)
        if (type == "weekly" || type == "biweekly") {
            val wd = if (isDate(anchor)) IncomeSetup.weekday(anchor) else 1
            SelectField(wd.toString(), IncomeSetup.WEEKDAYS.mapIndexed { i, w -> i.toString() to w }, { w -> anchor = IncomeSetup.withWeekday(J.obj("anchor" to anchor), w.toInt(), today).s("anchor") }, "Day of the week")
        }
        if (type == "biweekly") DateField(anchor, { anchor = it }, "A payday in the cycle", hint = "Tells LoonieMate which week it's paid. Any real payday works.")
    } else if (prog == "nsia") SelectField(slot.toString(), listOf("1" to "First of the three dates", "2" to "Second of the three dates", "3" to "Last of the three dates"), { slot = it.toInt() },
        "Which payment day is yours", hint = "Nova Scotia pays on one of the last three business days of the month.")
    SelectField(account, acctOptions(s), { account = it }, "Lands in")
    SelectField(role, IncomeSetup.ROLES, { role = it }, "What it's for")
    if (P == null && IncomeSetup.hasAssist(s)) {
        val hasOdsp = looniemate.engine.SavingsViews.hasOdsp(s)
        SelectField(counts, IncomeSetup.COUNTS.filter { it.first != "dis" || hasOdsp || counts == "dis" }, { counts = it }, "How assistance counts it", hint = "Used by the assistance deductions.")
        val people = s.strList("people").filter { it.isNotBlank() }
        if (people.isNotEmpty()) SelectField(person, listOf("" to "—") + people.map { it to it }, { person = it }, "Whose income", hint = "Two jobs for one person share one exemption.")
    }
    SwitchRow("Remind us to report income", if (P != null && P.report > 0) "${P.short} usually wants it by the ${P.report}th." else "For programs that need a monthly income report.", repOn) { repOn = it }
    if (repOn) TextFieldRow(repDay, { v -> repDay = v.filter { it.isDigit() }.take(2) }, "Report due by day", number = true)

    /* how assistance works out the payment */
    if (P != null) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("${P.short} deductions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Hint2("Turn on the deductions that apply. LoonieMate works out the payment from the usual amount using ${P.short}'s rules. Typing a real amount for a month on the Month tab always wins.")
        when (P.rule) {
            "odsp" -> { DedToggle(ded, "dis", "Work pay of a person with a disability", "The first \$1,000 of take-home pay each month is not counted. ODSP takes 75% of the rest. Each person with a disability gets their own \$1,000.")
                DedToggle(ded, "work", "Work pay of a spouse or adult without a disability", "The first \$200 each month is not counted. ODSP takes 50% of the rest.")
                DedToggle(ded, "full", "Income counted in full", "EI, CPP and spousal support come off dollar for dollar. Child support, the CCB and the Trillium Benefit are not counted.")
                DedToggle(ded, "wrb", "Add the \$100 work-related benefit", "ODSP adds \$100 a month for each adult with work pay.", true) }
            "ow" -> { DedToggle(ded, "work", "Work pay", "Each adult keeps the first \$200 of take-home pay a month, then OW takes 50% of the rest. This starts after 3 months in a row on assistance; before that, work pay counts in full.")
                DedToggle(ded, "full", "Income counted in full", "EI and most other income come off dollar for dollar.") }
            "qc" -> { DedToggle(ded, "work", "Work income", "\$200 a month for an adult alone, or \$300 for a couple, is not counted. Above that, the benefit drops by the difference, but 25% of it is added back.")
                DedToggle(ded, "full", "Income counted in full", "EI and other income come off dollar for dollar.") }
            "custom" -> { DedToggle(ded, "work", "Work income", "Quebec didn't publish the exact figures on its page. Enter the rule from your notice.")
                MoneyField(flat, { flat = it }, "Not counted each month")
                TextFieldRow(rate, { v -> rate = v.filter { it.isDigit() }.take(3) }, "Share taken above that (%)", number = true)
                DedToggle(ded, "full", "Income counted in full", "EI and other income come off dollar for dollar.") }
            "ns" -> { DedToggle(ded, "work", "Work pay", "On a sliding scale: the first \$350 of net pay each month is kept, then 75% of pay up to \$500, 50% up to \$750, and 25% above that.")
                DedToggle(ded, "supported", "Supported employment", "For people who need ongoing support to work: the first \$450 is kept instead.")
                DedToggle(ded, "full", "Income counted in full", "EI and other chargeable income come off dollar for dollar.") }
        }
        val L = lag ?: if (P.prov == "QC") 1 else 2
        SelectField(L.toString(), listOf("2" to "The payment for two months later (January pay → March payment)", "1" to "The payment for the next month (January pay → February payment)"),
            { lag = it.toInt() }, "Which payment a month's pay reduces", hint = "${P.short} payments are ${if (P.pays == "next") "paid at the end of the month before" else "paid on the 1st"} they cover. Check this against your own statements.")
        SwitchRow("We're a couple", P.assets?.let { "Asset limit: ${Fmt.money(it.couple)} for a couple, ${Fmt.money(it.single)} single." } ?: if (P.rule == "qc") "A couple can earn \$300 before it counts." else "Used for household rules.",
            ded["couple"] ?: false) { ded["couple"] = it }
        if (P.assets != null) TextFieldRow(deps, { v -> deps = v.filter { it.isDigit() }.take(2) }, "Dependants (not counting a spouse)", number = true, hint = "Adds ${Fmt.money(P.assets!!.dep)} each to the asset limit.")
        if (!isNew) { val c = Core.monthCalc(ymOf(today), Docs.toStore(vm.repo.docs.value, today), today); val oc = c["assistCalc"]?.get(base.s("id"))
            if (oc is Obj) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("${Fmt.monthName(ymOf(today))} · based on ${Fmt.monthOnly(oc.s("earnYm"))} income (saved settings)", fontWeight = FontWeight.SemiBold)
                    LineRow("Usual amount before deductions", Fmt.money(oc.n("base")))
                    oc["lines"].arr().forEach { l -> LineRow("${l["names"].arr().joinToString(" + ") { str(it) }}: ${Fmt.money(l.n("amount"))} (${l.s("how")})", "−" + Fmt.money(l.n("deduct"))) }
                    if (oc.n("wrb") > 0) LineRow("Work-related benefit", "+" + Fmt.money(oc.n("wrb")))
                    LineRow(if (oc.t("manual")) "Worked out (you typed the real amount this month)" else "Expected payment", Fmt.money(oc.n("calc")), total = true)
                }
            } }
        Hint2("An estimate. Child care and other work expenses can lower the deduction further. Your statement is the final word.")
        if (P.rule == "odsp") TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(IncomeSetup.ODSP_DIRECTIVE))) }) { Text("ODSP directive") }
    }

    /* next paydays, worked out from what's on screen */
    val draftSched: Obj
    if (P != null) {
        draftSched = if (prog == Core.assistOf(base)) (sc.copy() as Obj) else J.obj("type" to P.sched.s("type"))
        if (prog == "nsia") draftSched["slot"] = J.N(slot.toDouble())
    } else {
        draftSched = J.obj("type" to type)
        if (type == "monthly") draftSched["day"] = J.N((day.toIntOrNull() ?: 1).toDouble())
        if ((type == "weekly" || type == "biweekly") && isDate(anchor)) draftSched["anchor"] = J.S(anchor)
    }
    val nxt = IncomeSetup.nextDates(if (P != null) Core.assistSched(J.obj("assist" to prog, "sched" to draftSched)) else draftSched, today)
    Hint2("Next: " + (nxt.joinToString(", ") { Fmt.dShort(it) }.ifEmpty { "—" }))

    ErrorText(err)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            val amt = Views.parseMoney(amount); val dn = day.toIntOrNull(); val rd = repDay.toIntOrNull(); val dp = deps.toIntOrNull(); val rt = rate.toIntOrNull(); val fl = Views.parseMoney(flat)
            err = when {
                name.isBlank() -> "Give it a name."
                amt == null || amt.isNaN() -> "Enter a dollar amount."
                P == null && type == "monthly" && (dn == null || dn !in 1..31) -> "Enter a day from 1 to 31."
                P == null && type == "biweekly" && !isDate(anchor) -> "Pick a date."
                repOn && (rd == null || rd !in 1..28) -> "Enter a report day from 1 to 28."
                P?.assets != null && (dp == null || dp !in 0..12) -> "Enter a number of dependants from 0 to 12."
                P?.rule == "custom" && rate.isNotEmpty() && (rt == null || rt !in 0..100) -> "Enter a percentage from 0 to 100."
                P?.rule == "custom" && fl != null && fl.isNaN() -> "Enter a dollar amount for what's not counted."
                else -> null
            }
            if (err == null) {
                val o = base.copy() as Obj
                o["name"] = J.S(name.trim()); o["amount"] = J.N(amt!!); o["sched"] = draftSched; o["account"] = J.S(account); o["role"] = J.S(role)
                if (P != null) o["assist"] = J.S(prog!!)
                if (P == null && IncomeSetup.hasAssist(s)) { o["odsp"] = J.S(counts); if (s.strList("people").any { it.isNotBlank() }) o["person"] = J.S(person) }
                val rep = J.obj("day" to (if (repOn) rd!! else Math.round(rep0.n("day")).toInt()), "on" to repOn)
                if (base["report"] != null || repOn != rep0.t("on") || (repOn && rd != Math.round(rep0.n("day")).toInt())) o["report"] = rep
                if (P != null && prog != Core.assistOf(base) && base["report"] == null) o.map.remove("report")
                if (P != null) {
                    val cfg = (ded0.copy() as Obj); ded.forEach { (k, v) -> cfg[k] = J.B(v) }
                    if (lag != null) cfg["lag"] = J.N(lag!!.toDouble()); if (P.assets != null) cfg["deps"] = J.N(dp!!.toDouble())
                    if (P.rule == "custom") { cfg["flat"] = if (fl == null) J.S("") else J.N(fl); cfg["rate"] = if (rt == null) J.S("") else J.N(rt.toDouble()) }
                    if (isNew || Json.stringify(cfg) != Json.stringify(ded0)) o["ded"] = cfg
                }
                vm.saveIncome(isNew, o); close()
            }
        }) { Text(if (isNew) "Add" else "Save") }
        Spacer(Modifier.weight(1f))
        if (!isNew) { if (!confirm) TextButton(onClick = { confirm = true }) { Text("Remove", color = toneColors("bad").first) }
            else Button(onClick = { vm.deleteIncome(base.s("id")); close() }) { Text("Tap to remove") } }
    }
}

/** A government benefit or credit (settings.credits). */
@Composable
fun CreditSheet(vm: MainViewModel, id: String?, type0: String?, close: () -> Unit) {
    val s = vm.settings(); val today = vm.today.value
    val base: Obj = remember { id?.let { i -> Core.creditsOf(s).find { it.s("id") == i }?.copy() as? Obj } ?: IncomeSetup.newCredit((type0 ?: "other") + "-" + vm.repo.newId(), type0 ?: "other") }
    val isNew = id == null; val t = base.s("type"); val ty = Core.CREDIT_TYPES[t]
    var name by remember { mutableStateOf(base.s("name")) }
    var day by remember { mutableStateOf(Math.round((base["sched"] as? Obj)?.n("day")?.takeIf { it > 0 } ?: 15.0).toString()) }
    var amount by remember { mutableStateOf(money2(base.n("amount"))) }
    var account by remember { mutableStateOf(Core.acctId(s, base.s("account"))) }
    var start by remember { mutableStateOf(base.s("start")) }
    var protect by remember { mutableStateOf(base.t("protect")) }
    var err by remember { mutableStateOf<String?>(null) }; var confirm by remember { mutableStateOf(false) }

    SheetTitle(Core.creditName(base))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (protect) "Protected for the child" else "Spendable", if (protect) "baby" else ""); ty?.let { Hint2(it.whenText) } }
    if (t == "other") { TextFieldRow(name, { name = it.take(40) }, "Name"); TextFieldRow(day, { v -> day = v.filter { it.isDigit() }.take(2) }, "Day of the month", number = true) }
    MoneyField(amount, { amount = it }, "Usual amount each payment")
    SelectField(account, acctOptions(s), { account = it }, "Lands in")
    TextFieldRow(start, { start = it.take(7) }, "First payment month (optional)", placeholder = "YYYY-MM", hint = "For a benefit that hasn't started yet, like the CCB after the baby arrives.")
    SwitchRow("Protected for the child", "Goes to the Child Reserve instead of everyday money.", protect) { protect = it }
    val nxt = IncomeSetup.nextDates(Core.creditSched(J.obj("type" to t, "sched" to J.obj("type" to "monthly", "day" to (day.toIntOrNull() ?: 15)))), today, 3, start.takeIf { isYm(it) })
    Hint2("Next: " + (nxt.joinToString(", ") { Fmt.dShort(it) }.ifEmpty { "—" }))
    ErrorText(err)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { val amt = Views.parseMoney(amount); val dn = day.toIntOrNull()
            err = when { amt == null || amt.isNaN() -> "Enter a dollar amount."; start.isNotEmpty() && !isYm(start) -> "Use the format YYYY-MM, like 2026-11."
                t == "other" && (dn == null || dn !in 1..31) -> "Enter a day from 1 to 31."; else -> null }
            if (err == null) { val o = base.copy() as Obj
                o["amount"] = J.N(amt!!); o["account"] = J.S(account); o["start"] = J.S(start); o["protect"] = J.B(protect)
                if (t == "other") { o["name"] = J.S(name.trim()); o["sched"] = J.obj("type" to "monthly", "day" to dn!!) }
                vm.saveCredit(isNew, o); close() } }) { Text(if (isNew) "Add" else "Save") }
        Spacer(Modifier.weight(1f))
        if (!isNew) { if (!confirm) TextButton(onClick = { confirm = true }) { Text("Remove", color = toneColors("bad").first) }
            else Button(onClick = { vm.deleteCredit(base.s("id")); close() }) { Text("Tap to remove") } }
    }
}

/** One month's income: its own amounts and dates, the one-off payment, and locking the month (the web version's Income tab). */
@Composable
fun MonthIncomeSheet(vm: MainViewModel, ym: String, close: () -> Unit) {
    val today = vm.today.value; val s = vm.settings()
    val c = Core.monthCalc(ym, Docs.toStore(vm.repo.docs.value, today), today); val locked = c.t("closed")
    val incs = c["incomes"].arr().filterIsInstance<Obj>(); val crs = c["crAll"].arr().filterIsInstance<Obj>().filter { it.t("scheduled") }
    val amt = remember { mutableStateMapOf<String, String>().apply { incs.forEach { put("amt:" + it.s("id"), money2(it.n("amount"))) }; crs.forEach { put("cr:" + it.s("id"), money2(it.n("amount"))) }
        put("otherCredit", if (c.n("otherCredit") != 0.0) money2(c.n("otherCredit")) else "") } }
    val dates = remember { mutableStateMapOf<String, String>().apply { incs.forEach { put(it.s("id"), it.strList("dates").firstOrNull() ?: "") } } }
    var err by remember { mutableStateOf<String?>(null) }; var confirmUnlock by remember { mutableStateOf(false) }
    val mo = Fmt.monthOnly(ym)
    fun from(f: J?) = when { str(f) == ym -> "Set for this month"; f != null && f != J.Null && str(f).isNotEmpty() -> "Same as ${Fmt.monthOnly(str(f))}. Change it here to update $mo onward."
        else -> "The usual amount. Change it here to update this month onward." }

    SheetTitle("Income for ${Fmt.monthName(ym)}")
    Hint2("Each month keeps its own amounts. Changing one month never changes the months before it. Set up income sources and benefits in Settings.")
    if (incs.isEmpty()) Hint2("No income sources yet. Add them in Settings → Income and benefits.")
    incs.forEach { inc -> val iid = inc.s("id"); val type = inc["sched"].s("type"); val one = inc.strList("dates").size <= 1 && type !in listOf("weekly", "biweekly", "semimonthly")
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(inc.s("name"), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Hint2(IncomeSetup.roleLabel(inc.s("role")) + " · " + (Core.acctById(s, inc.s("account"))?.s("name") ?: "Main account")) }
                val oc = c["odspCalc"]
                MoneyField(amt["amt:$iid"] ?: "", { amt["amt:$iid"] = it; err = null }, if (inc.strList("dates").size > 1) "Each payment" else "Amount",
                    hint = if (oc is Obj && oc.s("id") == iid && oc.t("applied")) "Worked out by the assistance deductions. Type an amount to use the real payment instead." else from(inc["from"]))
                if (one && !locked) DateField(dates[iid] ?: "", { dates[iid] = it; err = null }, "Payment date", hint = if (inc.t("estimated")) "Estimate. Change it when the date is announced." else "Worked out from the schedule. Change it if it arrives on a different day.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    inc.strList("dates").forEach { d -> val bonus = d in inc.strList("bonusDates"); Pill(Fmt.dLabel(d) + if (bonus) " · extra" else "", if (bonus) "good" else "") }
                    if (inc.strList("dates").isEmpty()) Hint2("Nothing this month")
                }
                if (inc.strList("bonusDates").isNotEmpty()) Hint2("Extra payday this month. It pays no bills and goes to Emergency Savings once any shortfall is covered.")
            }
        }
    }
    Text("Government benefits and credits", fontWeight = FontWeight.SemiBold)
    if (crs.isEmpty()) Hint2("Nothing scheduled this month.")
    crs.forEach { x -> MoneyField(amt["cr:" + x.s("id")] ?: "", { amt["cr:" + x.s("id")] = it; err = null }, x.s("name") + " · " + x.strList("dates").joinToString(", ") { Fmt.dShort(it) } + (if (x.t("estimated")) " (estimate)" else ""),
        hint = (if (x.t("protect")) "Protected. " else "") + if (str(x["from"]) == ym) "Set for this month" else if (str(x["from"]).isNotEmpty()) "Same as ${Fmt.monthOnly(str(x["from"]))}" else "The usual amount") }
    MoneyField(amt["otherCredit"] ?: "", { amt["otherCredit"] = it; err = null }, "Other one-off payment", hint = "Only counts for $mo.")
    Column { LineRow("Spendable", Fmt.money(c.n("spendable"))); LineRow("Extra paydays", Fmt.money(c.n("bonus"))); LineRow("Protected", Fmt.money(c.n("ccb")))
        LineRow("Planned surplus", Fmt.money(c.n("plannedSurplus")), total = true, tone = if (c.n("plannedSurplus") < 0) "bad" else "good") }
    ErrorText(err)
    fun buildPatch(): Obj? {
        val patch = Obj(); val amtP = Obj(); val crP = Obj(); val dateP = Obj()
        for (inc in incs) { val k = "amt:" + inc.s("id"); val v = amt[k] ?: ""; if (v != money2(inc.n("amount"))) { val p = Views.parseMoney(v)
            if (p != null && p.isNaN()) { err = "Enter a dollar amount, like 1263.33"; return null }; amtP[inc.s("id")] = if (p == null) J.Null else J.N(p) }
            val d = dates[inc.s("id")] ?: ""; if (d != (inc.strList("dates").firstOrNull() ?: "")) { if (d.isNotEmpty() && (!isDate(d) || ymOf(d) != ym)) { err = "Pick a date in ${Fmt.monthName(ym)}."; return null }
                dateP[inc.s("id")] = if (d.isEmpty()) J.Null else J.S(d) } }
        for (x in crs) { val v = amt["cr:" + x.s("id")] ?: ""; if (v != money2(x.n("amount"))) { val p = Views.parseMoney(v)
            if (p != null && p.isNaN()) { err = "Enter a dollar amount, like 1263.33"; return null }; crP[x.s("id")] = if (p == null) J.Null else J.N(p) } }
        val oc = amt["otherCredit"] ?: ""; if (oc != (if (c.n("otherCredit") != 0.0) money2(c.n("otherCredit")) else "")) { val p = Views.parseMoney(oc)
            if (p != null && p.isNaN()) { err = "Enter a dollar amount, like 1263.33"; return null }; patch["otherCredit"] = if (p == null) J.Null else J.N(p) }
        if (amtP.map.isNotEmpty()) patch["amt"] = amtP; if (crP.map.isNotEmpty()) patch["cr"] = crP; if (dateP.map.isNotEmpty()) patch["date"] = dateP
        return patch
    }
    if (!locked) Button(onClick = { buildPatch()?.let { vm.saveMonthIncome(ym, it); close() } }, Modifier.fillMaxWidth()) { Text("Save for $mo") }

    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Lock this month", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (locked) {
        Hint2("${Fmt.monthName(ym)} is locked. Its income, bills and totals are saved exactly as they were, even if you change bills later.")
        if (!confirmUnlock) OutlinedButton(onClick = { confirmUnlock = true }) { Text("Unlock to make changes") }
        else Button(onClick = { vm.unlockMonth(ym); close() }) { Text("Tap again to unlock") }
    } else {
        Hint2((if (ym < ymOf(today)) "This month is over. " else "") + "Locking saves this month's income, bills and spending as a permanent record. Later changes to bills won't touch it.")
        OutlinedButton(onClick = { vm.lockMonth(ym); close() }) { Text("Lock $mo") }
    }
}
