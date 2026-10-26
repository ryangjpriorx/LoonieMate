@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package looniemate.app.ui.sheets

import androidx.compose.runtime.saveable.rememberSaveable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import looniemate.app.MainViewModel
import looniemate.app.ui.Sheet
import looniemate.app.ui.toneColors
import looniemate.app.ui.theme.LocalExtra
import looniemate.app.ui.theme.Numbers
import looniemate.engine.Backup
import looniemate.engine.Core
import looniemate.engine.Fmt
import looniemate.engine.Labels
import looniemate.engine.Views
import looniemate.engine.addDays
import looniemate.engine.isDate
import looniemate.engine.n
import looniemate.engine.round2
import looniemate.engine.s
import looniemate.engine.strList
import looniemate.engine.ymAdd
import looniemate.engine.*

/** The open form, shown as a full-screen page (see Page.kt). */
@Composable
fun Sheets(sheet: Sheet, vm: MainViewModel, onDismiss: () -> Unit, open: (Sheet) -> Unit, replace: (Sheet) -> Unit = open) {
    val close: () -> Unit = onDismiss
    /* a new page for each form, so one form's typing or scroll never carries into the next (setup chains forms) */
    /* closing "Make LoonieMate easier to use?" during first-time setup carries on to getting on track, like Skip (N7) */
    val onClose: () -> Unit = (sheet as? Sheet.Access)?.next?.let { n -> { replace(n) } } ?: onDismiss
    key(sheet) { Page(onClose = onClose) {
            when (sheet) {
                is Sheet.Log -> LogSheet(vm, sheet.oneTime, close, sheet.category, sheet.note, sheet.edit)
                is Sheet.Food -> FoodSheet(vm, sheet.ym, close, open)
                is Sheet.Bill -> BillSheet(vm, sheet.id, sheet.group, close)
                is Sheet.Adjust -> AdjustSheet(vm, sheet, close)
                is Sheet.Transfer -> TransferSheet(vm, sheet, close)
                is Sheet.Account -> AccountSheet(vm, sheet.id, close)
                is Sheet.Checkin -> CheckinSheet(vm, close)
                is Sheet.Fund -> FundSheet(vm, sheet.key, close)
                is Sheet.UseFund -> UseFundSheet(vm, sheet.key, close)
                is Sheet.Ccb -> CcbSheet(vm, sheet.ym, close)
                is Sheet.Income -> IncomeSheet(vm, sheet.id, sheet.assist, close)
                is Sheet.Credit -> CreditSheet(vm, sheet.id, sheet.type, close)
                is Sheet.MonthIncome -> MonthIncomeSheet(vm, sheet.ym, close)
                is Sheet.CatchUp -> CatchUpSheet(vm, sheet.first, close)
                is Sheet.Access -> AccessSheet(vm) { val n = sheet.next; if (n != null) replace(n) else close() }
                is Sheet.Update -> { SheetTitle("Update LoonieMate")
                    val st by vm.updater.state.collectAsStateWithLifecycle(); looniemate.app.ui.screens.UpdatePanel(st, vm, inSheet = true) }
                is Sheet.Self -> SelfSheet(vm, close, open)
                is Sheet.Export -> ExportSheet(vm, close)
                is Sheet.Reset -> ResetSheet(vm, close, open)
                is Sheet.Household -> HouseholdSheet(vm, close)
                is Sheet.Import -> ImportSheet(vm, close)
                is Sheet.Settings -> looniemate.app.ui.screens.SettingsPage(vm, sheet.section, open)
                is Sheet.AddFund -> looniemate.app.ui.screens.AddFundSheet(vm, open, close)
                is Sheet.AddIncome -> looniemate.app.ui.screens.AddIncomeSheet(vm, replace)
                is Sheet.Setup -> SetupSheet(vm, sheet.step, open, replace, close)
            }
    } }
}

@Composable
fun ErrorText(e: String?) { if (e != null) Text(e, color = toneColors("bad").first, fontWeight = FontWeight.SemiBold) }

/* ---------- log spending: three quick steps ---------- */
@Composable
private fun LogSheet(vm: MainViewModel, oneTime: Boolean, close: () -> Unit, category: String = "", note0: String = "", edit: String = "") {
    val today = vm.today.value; val s = vm.settings(); val people = s.strList("people").filter { it.isNotBlank() }
    /* changing a logged entry (edit is its path) starts from what was saved */
    val e0 = if (edit.isNotEmpty()) vm.repo.get(edit) else null
    var amt by rememberSaveable { mutableStateOf(e0?.let { money2(it.n("amount")) } ?: "") }; var cat by rememberSaveable { mutableStateOf(e0?.s("category")?.takeIf { it != "oneoff" } ?: category) }
    var date by rememberSaveable { mutableStateOf(e0?.s("date")?.takeIf { isDate(it) } ?: today) }
    var note by rememberSaveable { mutableStateOf(e0?.s("note") ?: note0) }; var who by rememberSaveable { mutableStateOf(e0?.s("who") ?: "") }; var fav by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf(e0?.or("group", "household") ?: "household") }; var err by rememberSaveable { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (edit.isEmpty()) try { focus.requestFocus() } catch (_: Exception) {} }
    SheetTitle(if (edit.isNotEmpty()) (if (oneTime) "Change the one-time expense" else "Change spending") else if (oneTime) "Add a one-time expense" else "Log spending")
    if (oneTime) TextFieldRow(note, { note = it }, "What is it?", placeholder = "Car seat, vet visit, birthday gift…")
    OutlinedTextField(amt, marking { v: String -> amt = v.replace(',', '.').filter { it.isDigit() || it == '.' } }, Modifier.fillMaxWidth().focusRequester(focus), label = { Text(if (oneTime) "Amount" else "How much?") },
        prefix = { Text("$", style = MaterialTheme.typography.headlineMedium) }, textStyle = MaterialTheme.typography.headlineMedium.merge(Numbers), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    val pg = LocalPage.current
    if (!oneTime) {
        Text("What was it for?", fontWeight = FontWeight.SemiBold)
        /* the kind logged last comes first */
        val last = vm.prefs.lastCategory
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (Labels.SPEND.filter { it.first == last } + Labels.SPEND.filter { it.first != last }).forEach { (k, l) -> FilterChip(cat == k, { pg?.mark(); cat = k }, label = { Text(l) }, modifier = Modifier.testTag("cat-$k")) }
        }
    } else SelectField(group, Labels.OGROUPS.entries.map { it.key to it.value }, { group = it }, "Who it's for")
    /* the date, a note, who paid and the quick-button tick fold under More, unless they're already set */
    var more by rememberSaveable { mutableStateOf(oneTime || edit.isNotEmpty() || date != today || (note.isNotEmpty() && !oneTime)) }
    if (!more) TextButton(onClick = { more = true }, modifier = Modifier.testTag("log-more")) { Text(if (oneTime) "More" else "Date, note and more") }
    else {
        Text("When?", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            FilterChip(date == today, { pg?.mark(); date = today }, label = { Text("Today") })
            if (!oneTime) FilterChip(date == addDays(today, -1), { pg?.mark(); date = addDays(today, -1) }, label = { Text("Yesterday") })
        }
        DateField(date, { date = it }, if (oneTime) "Date (today or later)" else "Or pick a date")
        if (!oneTime) {
            TextFieldRow(note, { note = it }, "Note (optional)", placeholder = "Store or what you bought")
            if (people.isNotEmpty()) { Text("Who paid", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { people.forEach { p -> FilterChip(who == p, { pg?.mark(); who = if (who == p) "" else p }, label = { Text(p) }) } } }
            if (edit.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(fav, { fav = it }); Text("Add a quick button for this (same amount and type)", style = MaterialTheme.typography.bodyMedium) }
        }
    }
    ErrorText(err)
    Button(onClick = {
        err = if (edit.isNotEmpty()) vm.updateExpense(edit, Views.parseMoney(amt), cat, date, note, who, oneTime, group) else vm.logExpense(Views.parseMoney(amt), cat, date, note, who, oneTime, group, fav)
        if (err == null) { if (!oneTime && cat.isNotEmpty()) vm.prefs.lastCategory = cat; close() }
    }, Modifier.fillMaxWidth().height(52.dp).testTag("log-save")) { Text(if (edit.isNotEmpty()) "Save changes" else if (oneTime) "Save one-time expense" else "Save") }
    if (e0 != null) TextButton(onClick = { vm.deleteExpense(J.obj("date" to e0.s("date"), "id" to edit.substringAfterLast('/'), "amount" to e0.n("amount"))); close() }, modifier = Modifier.testTag("log-delete")) {
        Text("Delete this", color = toneColors("bad").first) }
}

/* ---------- give up part of a bill this month, or pay part of it later ---------- */
@Composable
private fun AdjustSheet(vm: MainViewModel, a: Sheet.Adjust, close: () -> Unit) {
    val today = vm.today.value
    val st = looniemate.engine.Docs.toStore(vm.repo.docs.value, today); val b = st.bills[a.id] ?: return
    val c = Core.monthCalc(a.ym, st, today)
    val line = c["billLines"].arr().find { it.s("id") == a.id && !it.s("deferredFrom").isNotEmpty() }
    val total = line?.n("planned") ?: b.n("amount"); val short = maxOf(0.0, -c.n("net"))
    val start = round2(if (short > 0) minOf(total, Math.ceil(short)) else total)
    var v by rememberSaveable { mutableStateOf(start) }; var text by rememberSaveable { mutableStateOf(money2(start)) }
    val pays = (Core.mainPaydays(st.settings, ymAdd(a.ym, 1)) + Core.mainPaydays(st.settings, ymAdd(a.ym, 2))).take(3)
    var date by rememberSaveable { mutableStateOf(pays.firstOrNull() ?: (ymAdd(a.ym, 1) + "-01")) }
    var err by rememberSaveable { mutableStateOf<String?>(null) }
    val mo = Fmt.monthOnly(a.ym); val name = b.s("name")
    SheetTitle(if (a.defer) "Pay $name later" else "Give up $name")
    Text("$name costs ${Fmt.money(total)} in $mo. " + (if (a.defer) "Choose how much to push to later." else "Choose how much of it to go without this month.") + (if (short > 0) " $mo is short ${Fmt.money(short)}." else ""),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(v.toFloat(), { x -> v = round2(Math.round(x).toDouble().coerceIn(0.0, total)); text = money2(v) }, valueRange = 0f..maxOf(1f, total.toFloat()))
    MoneyField(text, { t -> text = t; Views.parseMoney(t)?.takeIf { !it.isNaN() }?.let { v = it.coerceIn(0.0, total) } }, if (a.defer) "Amount to defer" else "Amount to give up")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(if (a.defer) "Pay later" else "Giving up", v, Modifier.weight(1f)); Tile(if (a.defer) "Pay on time" else "Still paying", total - v, Modifier.weight(1f)) }
    if (short > 0) Text(if (v >= short) "That covers the shortfall." else "Still short ${Fmt.money(short - v)} after this.", color = if (v >= short) LocalExtra.current.good else MaterialTheme.colorScheme.onSurfaceVariant)
    if (a.defer) {
        DateField(date, { date = it }, "Pay the deferred part on", hint = "Pick a date in ${Fmt.monthOnly(ymAdd(a.ym, 1))} or later, ideally a payday.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pays.forEach { d -> FilterChip(date == d, { date = d }, label = { Text("Payday " + Fmt.dShort(d)) }) } }
        Text("Arrange the new date and amount with the company too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else Text("This only changes the budget. Pause, downgrade or cut back on the bill yourself.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ErrorText(err)
    Button(onClick = {
        val amt = Views.parseMoney(text)
        err = when {
            amt == null || amt.isNaN() || !(amt > 0) -> "Choose an amount greater than \$0."
            amt > total + 0.004 -> "You can ${if (a.defer) "defer" else "give up"} up to ${Fmt.money(total)}."
            a.defer && !isDate(date) -> "Pick a date."
            a.defer && date < ymAdd(a.ym, 1) + "-01" -> "Pick a date after ${Fmt.monthName(a.ym)}."
            else -> null
        }
        if (err == null) { if (a.defer) vm.defer(a.ym, a.id, date, amt!!) else vm.skip(a.ym, a.id, amt!!); close() }
    }, Modifier.fillMaxWidth()) { Text(if (a.defer) "Defer it" else "Give it up this month") }
}

@Composable
private fun Tile(label: String, v: Double, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp)) { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Fmt.money(v), style = MaterialTheme.typography.titleLarge.merge(Numbers), fontWeight = FontWeight.SemiBold) }
    }
}

/* ---------- record money moved into or out of savings ---------- */
@Composable
private fun TransferSheet(vm: MainViewModel, t: Sheet.Transfer, close: () -> Unit) {
    val funds = Core.fundsOf(vm.settings())
    var kind by rememberSaveable { mutableStateOf(t.kind) }; var out by rememberSaveable { mutableStateOf(false) }
    var amt by rememberSaveable { mutableStateOf(t.amount?.let { money2(it) } ?: "") }; var err by rememberSaveable { mutableStateOf<String?>(null) }
    SheetTitle("Record a savings transfer")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { funds.forEach { f -> FilterChip(kind == f.s("key"), { kind = f.s("key") }, label = { Text(f.s("name")) }) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!out, { out = false }, label = { Text("Moved money in") }); FilterChip(out, { out = true }, label = { Text("Took money out") }) }
    MoneyField(amt, { amt = it }, "Amount", hint = "Do the transfer in your banking app first, then record it here.")
    ErrorText(err)
    Button(onClick = { val a = Views.parseMoney(amt); if (a == null || a.isNaN() || !(a > 0)) err = "Enter an amount greater than \$0." else { vm.transfer(kind, out, a); close() } }, Modifier.fillMaxWidth()) { Text("Save") }
}

/* ---------- encrypted backups ---------- */
@Composable
private fun ExportSheet(vm: MainViewModel, close: () -> Unit) {
    var pw by rememberSaveable { mutableStateOf("") }; var pw2 by rememberSaveable { mutableStateOf("") }; var ack by rememberSaveable { mutableStateOf(false) }
    var err by rememberSaveable { mutableStateOf<String?>(null) }; var busy by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) { busy = false; return@rememberLauncherForActivityResult }
        scope.launch { try { vm.exportTo(uri, pw); vm.say("Backup saved. Keep the password safe."); close() } catch (e: Exception) { err = "Couldn't save the backup. Try again."; busy = false } }
    }
    SheetTitle("Export an encrypted backup")
    Text("Choose a password to lock the file. You'll need it to open the backup on another phone or in the web version.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color = toneColors("warn").second, shape = MaterialTheme.shapes.medium) {
        Text("If you lose this password, the backup can't be opened. Nobody can recover it, not even us. Write it down somewhere safe.", Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold) }
    PasswordField(pw, { pw = it }, "Password (at least 8 characters)")
    PasswordField(pw2, { pw2 = it }, "Type it again")
    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(ack, { ack = it }); Text("I've written the password down.", style = MaterialTheme.typography.bodyMedium) }
    ErrorText(err)
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    Button(enabled = !busy, onClick = {
        err = when { pw.length < 8 -> "Use at least 8 characters."; pw != pw2 -> "The two passwords don't match."; !ack -> "Tick the box to confirm you have written the password down."; else -> null }
        if (err == null) { busy = true; save.launch("budget-backup-${vm.today.value}.json") }
    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Locking…" else "Lock and save") }
}

@Composable
private fun PasswordField(v: String, onChange: (String) -> Unit, label: String) =
    OutlinedTextField(v, marking(onChange), Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))

@Composable
private fun ImportSheet(vm: MainViewModel, close: () -> Unit) {
    var text by rememberSaveable { mutableStateOf<String?>(null) }; var pw by rememberSaveable { mutableStateOf("") }
    var err by rememberSaveable { mutableStateOf<String?>(null) }; var busy by rememberSaveable { mutableStateOf(false) }
    var opened by remember { mutableStateOf<MainViewModel.Opened?>(null) }; var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch { try { text = vm.readText(uri); err = null } catch (e: Exception) { err = "Couldn't read that file." } } }
    SheetTitle("Import a backup")
    val o = opened
    if (o == null) {
        Text("Pick the backup file (it ends in .json), then enter the password it was locked with.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { pick.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text(if (text == null) "Choose the backup file" else "File chosen · choose another") }
        PasswordField(pw, { pw = it }, "Backup password")
        ErrorText(err)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Button(enabled = !busy, onClick = {
            val t = text
            if (t == null) { err = "Choose the backup file first."; return@Button }
            if (pw.isEmpty()) { err = "Enter the backup password."; return@Button }
            busy = true; err = null
            scope.launch { try { opened = vm.open(t, pw) } catch (e: Backup.BackupException) { err = if (e.problem == Backup.Problem.PASSWORD) "That password doesn't open this backup." else "That file isn't a LoonieMate backup." } catch (e: Exception) { err = "Couldn't open that backup." }; busy = false }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Unlocking…" else "Unlock") }
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(if (o.name.isNotEmpty()) "${o.name}'s budget" else "Budget backup", fontWeight = FontWeight.Bold)
                Text("${o.bills} bills · ${o.months} months · ${o.entries} spending entries" + (if (o.exportedAt.length >= 10) " · saved ${Fmt.dShort(o.exportedAt.take(10))}, ${o.exportedAt.take(4)}" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!vm.hasData()) Button(onClick = { vm.say(vm.applyBackup(o, false)); close() }, Modifier.fillMaxWidth()) { Text("Use this backup") }
        else {
            Text("Combine keeps everything from both: for anything changed in both places, the newest change wins.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { vm.say(vm.applyBackup(o, true)); close() }, Modifier.fillMaxWidth()) { Text("Combine with this phone") }
            if (!confirmReplace) TextButton(onClick = { confirmReplace = true }) { Text("Replace everything on this phone instead…") }
            else OutlinedButton(onClick = { vm.say(vm.applyBackup(o, false)); close() }, Modifier.fillMaxWidth()) { Text("Yes, replace everything on this phone", color = toneColors("bad").first) }
        }
    }
}
