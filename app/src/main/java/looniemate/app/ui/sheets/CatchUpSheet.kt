@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package looniemate.app.ui.sheets

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import looniemate.app.ui.EmptyNote
import looniemate.app.ui.LineRow
import looniemate.app.ui.toneColors
import looniemate.engine.CatchUp
import looniemate.engine.Core
import looniemate.engine.Docs
import looniemate.engine.Fmt
import looniemate.engine.Views
import looniemate.engine.*

/**
 * Getting on track: where the family stands today, partway through a month. Steps: what's in the accounts,
 * this month so far, anything they're behind on, food so far, Personal Expenses, and where they stand until the next payday.
 * See docs/plans/getting-on-track.md. [first] is true for first-time setup (finishing marks the starting month).
 */
@Composable
fun CatchUpSheet(vm: MainViewModel, first: Boolean, close: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(vm.catchUpResume()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.startCatchUp(first) }
    /* each step saves as it goes, so moving on means nothing typed is at risk; back goes a step back first */
    val page = LocalPage.current
    androidx.compose.runtime.LaunchedEffect(step) { page?.saved(); vm.catchUpAt = vm.today.value to step }
    androidx.activity.compose.BackHandler(enabled = step > 0) { step-- }
    val titles = listOf("What's in your accounts right now?", "This month so far", "Anything you're behind on?", "Food so far", "Personal Expenses", "Where you stand")
    Text("Step ${step + 1} of ${titles.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LinearProgressIndicator(progress = { (step + 1f) / titles.size }, modifier = Modifier.fillMaxWidth())
    SheetTitle(titles[step])
    when (step) {
        0 -> BalancesStep(vm) { step = 1 }
        1 -> SoFarStep(vm) { step = 2 }
        2 -> BehindStep(vm) { step = 3 }
        3 -> FoodStep(vm) { step = 4 }
        4 -> SelfPlanEditor(vm, "Next") { step = 5 }
        else -> StandStep(vm) { vm.finishCatchUp(first); close() }
    }
    if (step > 0) TextButton(onClick = { step-- }) { Text("Back") }
}

@Composable
private fun Hint3(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/* ---- step 1: balances, saved as today's balance check (the starting point) ---- */
@Composable
private fun BalancesStep(vm: MainViewModel, next: () -> Unit) {
    val A = Core.accountsOf(vm.settings())
    val typed = rememberSaveable(saver = stateMapSaver<String>()) { mutableStateMapOf<String, String>() }; var err by rememberSaveable { mutableStateOf<String?>(null) }
    val blanks = rememberBlankCheck()
    Text("Open your banking apps and type in what each account holds right now. Count cash on hand too. Leave an account blank if it's empty. This is your starting point; nothing before today needs to be filled in.",
        style = MaterialTheme.typography.bodyMedium)
    if (A.isEmpty()) { EmptyNote("Add your accounts in Settings first."); return }
    A.forEach { a -> MoneyField(typed[a.s("id")] ?: "", { typed[a.s("id")] = it; err = null }, a.s("name") + if (a.s("type") == "savings") " (savings)" else "") }
    ErrorText(err)
    Button(onClick = { val ids = A.map { it.s("id") }
            blanks.run(ids.count { (typed[it] ?: "").isBlank() }) { val (r, e) = vm.compareBalances(blanksAsZero(typed, ids)); if (r == null) err = e else { vm.saveCheckin(r, false); next() } } },
        Modifier.fillMaxWidth().testTag("cu-next")) { Text("Next") }
}

/* ---- step 2: bills dated the 1st to today (tick the paid ones), and paydays already in ---- */
@Composable
private fun SoFarStep(vm: MainViewModel, next: () -> Unit) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle(); val today = vm.today.value; val ym = ymOf(today)
    val st = Docs.toStore(docs, today)
    val bills = Core.billEvents(st, vm.catchUpFrom(), today).filter { !it.t("fund") }
    val c = Core.monthCalc(ym, st, today)
    val paid = c["incomes"].arr().flatMap { inc -> inc.strList("dates").filter { it <= today }.map { d -> Triple(d, inc.s("name"), inc.n("amount")) } }.sortedBy { it.first }
    Text("Tick the bills that are already paid. Anything left unticked is something you're behind on, and you'll decide what to do about it next.", style = MaterialTheme.typography.bodyMedium)
    if (bills.isEmpty()) Hint3("No bills were due between the 1st and today.")
    bills.forEach { e -> val on = looniemate.app.data.Ticks.isPaid(vm.repo, e.s("ym"), e.s("key"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(on, { vm.togglePaid(e) })
            Column(Modifier.weight(1f)) { Text(e.s("name"), fontWeight = FontWeight.SemiBold); Hint3("Due ${Fmt.dShort(e.s("date"))}") }
            Text(Fmt.money(e.n("amount")))
        } }
    Spacer(Modifier.padding(4.dp))
    Text("Money that already came in", fontWeight = FontWeight.SemiBold)
    if (paid.isEmpty()) Hint3("Nothing yet this month.")
    else { paid.forEach { (d, n, a) -> LineRow("${Fmt.dShort(d)}: $n", Fmt.money(a)) }
        Hint3("This is already in your balances, so only the paydays still to come count from here.") }
    Button(onClick = next, Modifier.fillMaxWidth().testTag("cu-next")) { Text("Next") }
}

/* ---- step 3: anything they're behind on: unpaid bills from this month, and anything else they owe ---- */
@Composable
private fun BehindStep(vm: MainViewModel, next: () -> Unit) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle(); val today = vm.today.value; val ym = ymOf(today)
    val st = Docs.toStore(docs, today); val s = st.settings
    val unpaid = Core.billEvents(st, vm.catchUpFrom(), addDays(today, -1)).filter { !it.t("fund") && !looniemate.app.data.Ticks.isPaid(vm.repo, it.s("ym"), it.s("key")) }
    val nextMonthPay = (Core.mainPaydays(s, ymAdd(ym, 1)).firstOrNull() ?: (ymAdd(ym, 1) + "-01"))
    if (unpaid.isNotEmpty()) {
        Text("Not paid yet this month", fontWeight = FontWeight.SemiBold)
        Hint3("These stay on your to-do list for this month. If one has to wait until next month, push it there.")
        unpaid.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("${e.s("name")} ${Fmt.money(e.n("amount"))}", fontWeight = FontWeight.SemiBold); Hint3("Was due ${Fmt.dShort(e.s("date"))}") }
                TextButton(onClick = { vm.catchUpPush(e.s("ym"), e.s("id"), nextMonthPay, e.n("amount")) }) { Text("Push to ${Fmt.dShort(nextMonthPay)}") }
            } }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
    }
    val undecided = CatchUp.undecided(s)
    if (undecided.isNotEmpty()) { Text("Not sure yet", fontWeight = FontWeight.SemiBold)
        undecided.forEach { x -> Row(verticalAlignment = Alignment.CenterVertically) { Text("${x.s("name")} ${Fmt.money(x.n("amount"))}", Modifier.weight(1f))
            TextButton(onClick = { vm.catchUpResolve(x.s("id"), "Removed ${x.s("name")}") }) { Text("Remove") } } }
        HorizontalDivider(Modifier.padding(vertical = 6.dp)) }

    Text("Something else you're behind on?", fontWeight = FontWeight.SemiBold)
    Hint3("Rent that came up short last month, a phone bill in arrears, money owed to someone. Add each one and say how you'd like to handle it.")
    var name by rememberSaveable { mutableStateOf("") }; var amount by rememberSaveable { mutableStateOf("") }; var how by rememberSaveable { mutableStateOf("date") }
    var date by rememberSaveable { mutableStateOf(Core.mainPaydays(s, ym).firstOrNull { it > today } ?: nextMonthPay) }
    var payment by rememberSaveable { mutableStateOf("") }; var day by rememberSaveable { mutableStateOf("1") }; var err by rememberSaveable { mutableStateOf<String?>(null) }
    TextFieldRow(name, { name = it.take(40); err = null }, "Who or what", placeholder = "Landlord")
    MoneyField(amount, { amount = it; err = null }, "How much is owed")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(how == "date", { how = "date" }, label = { Text("Pay it all on a date") })
        FilterChip(how == "plan", { how = "plan" }, label = { Text("Pay it off bit by bit") })
        FilterChip(how == "unsure", { how = "unsure" }, label = { Text("Not sure yet") })
    }
    when (how) {
        "date" -> DateField(date, { date = it }, "Pay it on", hint = "A payday is a good choice.")
        "plan" -> { MoneyField(payment, { payment = it }, "Each month"); TextFieldRow(day, { v -> day = v.filter { it.isDigit() }.take(2) }, "Day of the month", number = true,
            hint = "LoonieMate adds it as a bill that stops once it's paid off.") }
        else -> Hint3("It counts as owed today until you decide. You can turn it into a date or a payment plan later.")
    }
    ErrorText(err)
    OutlinedButton(onClick = {
        val a = Views.parseMoney(amount); val pay = Views.parseMoney(payment); val dn = day.toIntOrNull()
        err = when { name.isBlank() -> "Say who or what it's for."; a == null || a.isNaN() || a <= 0 -> "Enter how much is owed."
            how == "date" && !isDate(date) -> "Pick a date."; how == "plan" && (pay == null || pay.isNaN() || pay <= 0) -> "Enter how much each month."
            how == "plan" && (dn == null || dn !in 1..31) -> "Enter a day from 1 to 31."; else -> null }
        if (err == null) {
            when (how) { "date" -> vm.catchUpOnDate(name, a!!, date)
                "plan" -> vm.catchUpPlan(name, a!!, pay!!, dn!!, Core.acctFor(s, "main")?.s("id") ?: "", "Interac e-Transfer")
                else -> vm.catchUpUnsure(name, a!!) }
            name = ""; amount = ""; payment = "" }
    }) { Text("Add it") }
    Button(onClick = next, Modifier.fillMaxWidth().testTag("cu-next")) { Text("Next") }
}

/* ---- step 4: food so far, one rough number ---- */
@Composable
private fun FoodStep(vm: MainViewModel, next: () -> Unit) {
    val today = vm.today.value; val half = if (today.substring(8, 10).toInt() <= 15) "the 1st" else "the 16th"
    var amount by rememberSaveable { mutableStateOf("") }; var err by rememberSaveable { mutableStateOf<String?>(null) }
    Text("About how much has gone on groceries and takeout since $half? A rough number is fine. It's logged so this half's food money starts in the right place.",
        style = MaterialTheme.typography.bodyMedium)
    MoneyField(amount, { amount = it; err = null }, "Food since $half")
    ErrorText(err)
    Button(onClick = {
        if (amount.isBlank()) next() else { val a = Views.parseMoney(amount)
            if (a == null || a.isNaN() || a < 0) err = "Enter a dollar amount, or leave it blank."
            else { if (a > 0) err = vm.logExpense(a, "groceries", today, "Food so far (estimate)", "", false, "household", false); if (err == null) next() } }
    }, Modifier.fillMaxWidth().testTag("cu-next")) { Text(if (amount.isBlank()) "Skip" else "Next") }
}

/* ---- step 5: day by day from today, the first short day, and the fixes ---- */
@Composable
private fun StandStep(vm: MainViewModel, done: () -> Unit) {
    val docs by vm.repo.docs.collectAsStateWithLifecycle(); val today = vm.today.value; val ym = ymOf(today)
    val st = Docs.toStore(docs, today)
    val ck = Docs.checkins(docs).firstOrNull()
    val bal = (ck?.get("bal") as? Obj)?.map?.mapValues { num(it.value) } ?: emptyMap()
    val r = Core.runway(st, today, bal); val short = r["short"] as? Obj
    val bad = toneColors("bad").first
    Text("You have ${Fmt.money(r.n("start"))} in your everyday accounts. Here's every payday and bill until ${Fmt.dShort(r.s("to"))}.", style = MaterialTheme.typography.bodyMedium)
    val (fg, bg) = toneColors(if (short != null) "bad" else "good")
    Surface(shape = MaterialTheme.shapes.medium, color = bg, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            if (short != null) { val day = r["days"].arr().find { it.s("date") == short.s("date") }
                val biggest = day?.get("items").arr().filter { it.s("kind") != "in" && it.s("kind") != "food" && it.s("kind") != "self" }.maxByOrNull { it.n("amount") }
                Text("Short ${Fmt.money(short.n("amount"))} on ${Fmt.dShort(short.s("date"))}", fontWeight = FontWeight.Bold, color = fg)
                Text((if (biggest != null) "${biggest.s("name")} (${Fmt.money(biggest.n("amount"))}) is due ${if (short.s("date") == today) "now" else Fmt.dShort(short.s("date"))}. " else "") +
                    (if (short.t("nextIn")) "The next money comes in on ${Fmt.dShort(short.s("nextIn"))}." else "No more money is coming in before then."), style = MaterialTheme.typography.bodySmall)
            } else {
                Text("You can get to ${Fmt.dShort(r.s("to"))} without running short", fontWeight = FontWeight.Bold, color = fg)
                Text("The lowest point is ${Fmt.money(r.n("low"))} on ${Fmt.dShort(r.s("lowDate"))}.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    r["days"].arr().forEach { d ->
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(if (d.s("date") == today) "Today" else Fmt.dShort(d.s("date")), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(Fmt.money(d.n("bal")), fontWeight = FontWeight.SemiBold, color = if (d.n("bal") < 0) bad else MaterialTheme.colorScheme.onSurface)
        }
        d["items"].arr().forEach { e -> Hint3((if (e.s("kind") == "in") "+ " else "− ") + "${e.s("name")} ${Fmt.money(e.n("amount"))}" + if (e.s("kind") == "overdue") " (overdue)" else "") }
    }

    if (short != null) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Ways to cover it", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val c = Core.monthCalc(ym, st, today); val lines = c["billLines"].arr().filterIsInstance<Obj>()
        val upTo = short.s("date"); val nextIn = short.s("nextIn")
        val dueBy = Core.billEvents(st, today, upTo).filter { !it.t("fund") && !looniemate.app.data.Ticks.isPaid(vm.repo, it.s("ym"), it.s("key")) && it.s("ym") == ym }
        /* rent first: pay part now and the rest on the next payday */
        if (nextIn.isNotEmpty() && ymOf(nextIn) == ym) dueBy.sortedByDescending { if (lines.find { l -> l.s("id") == it.s("id") }?.s("category") == "housing") 1 else 0 }.take(2).forEach { e ->
            val part = minOf(short.n("amount"), e.n("amount"))
            Fix("Pay ${Fmt.money(e.n("amount") - part)} of ${e.s("name")} now and ${Fmt.money(part)} on ${Fmt.dShort(nextIn)}",
                "Ask first. If they agree, this also marks ${e.s("name")} as a bill that can be paid late.", "Split it") { vm.catchUpSplit(ym, e.s("id"), nextIn, part) } }
        /* give up a non-essential this month */
        lines.filter { !it.t("essential") && !it.t("skipped") && it.n("planned") > 0 && !it.t("deferredFrom") }.sortedByDescending { it.n("planned") }.take(2).forEach { l ->
            Fix("Give up ${l.s("name")} this month", "Saves ${Fmt.money(l.n("planned"))}. Call or cancel with the company yourself.", "Give it up") { vm.skip(ym, l.s("id"), l.n("planned")) } }
        /* Personal Expenses: suggested, never expected; only what's still unspent this month, and it goes back next month */
        val free = round2(c.n("self") - c.n("selfSpent"))
        if (free >= 1) { val all = minOf(free, short.n("amount")); val half = round2(Math.ceil(all / 2))
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Hold back some Personal Expenses" + (if (nextIn.isNotEmpty()) " until ${Fmt.dShort(nextIn)}" else " this month"), fontWeight = FontWeight.SemiBold)
                    Hint3("Only if you can. ${Fmt.money(free)} is still set aside for you this month; holding some back keeps it for the bills instead. It's back to normal next month.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.holdSelf(ym, c.n("selfHold") + all) }, modifier = Modifier.testTag("self-hold")) { Text("Hold back ${Fmt.money(all)}") }
                        if (half >= 5 && half < all) OutlinedButton(onClick = { vm.holdSelf(ym, c.n("selfHold") + half) }) { Text("Half (${Fmt.money(half)})") }
                    }
                }
            } }
        /* cover it from Emergency Savings */
        val em = Core.savingsState(st).emergency
        if (em > 0) { val amt = minOf(em, short.n("amount"))
            Fix("Use ${Fmt.money(amt)} from Emergency Savings", "Move it in your banking app first, then tap below. The Child Reserve is the last resort.", "I moved it") { vm.transfer("emergency", true, amt) } }
        Hint3("If it still doesn't add up, call the landlord or company before the due date, not after. Most will agree to a date that works if you ask early.")
    }
    Spacer(Modifier.padding(4.dp))
    Button(onClick = done, Modifier.fillMaxWidth().testTag("cu-done")) { Text("Done") }
}

@Composable
private fun Fix(title: String, note: String, button: String, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold); Hint3(note)
            OutlinedButton(onClick = onClick) { Text(button) }
        }
    }
}
