package looniemate.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import looniemate.engine.Backup
import looniemate.engine.Core
import looniemate.engine.Docs
import looniemate.engine.Fmt
import looniemate.engine.J
import looniemate.engine.Labels
import looniemate.engine.Obj
import looniemate.engine.Store
import looniemate.engine.Views
import looniemate.engine.addDays
import looniemate.engine.copy
import looniemate.engine.isDate
import looniemate.engine.isYm
import looniemate.engine.n
import looniemate.engine.or
import looniemate.engine.round2
import looniemate.engine.s
import looniemate.engine.strList
import looniemate.engine.sumOf2
import looniemate.engine.t
import looniemate.engine.ymAdd
import looniemate.engine.ymOf
import java.time.LocalDate

/* ---------- what each screen shows ---------- */

class TodayModel(
    val today: String, val title: String, val isNew: Boolean, val status: Views.Status, val reminders: List<Views.Reminder>,
    val todo: List<Obj>, val incoming: List<Obj>, val food: Obj, val foodTone: String, val quick: List<Views.Quick>,
    val debts: List<Views.Debt>, val next: List<Obj>,
)

class MonthModel(
    val ym: String, val today: String, val phase: String, val c: Obj, val status: Views.Status, val up: List<Obj>, val days: Int,
    val skips: List<Obj>, val defers: List<Obj>, val showAdjust: Boolean, val short: Boolean,
    val safeToMove: Double, val safeNote: String, val food: Obj?, val dueTotal: Double, val dueCount: Int, val dueOverdue: Int,
    val child: Double, val childShould: Double, val plan: List<Views.PayCard>, val debts: List<Views.Debt>, val emergency: Double,
    val byCat: List<Pair<String, Double>>, val examples: Boolean, val isNew: Boolean,
)

class BillRow(val id: String, val bill: Obj, val line: Obj?, val inFrom: Double, val inMonth: String?, val freq: String)
class BillsModel(val ym: String, val totals: Map<String, Double>, val counts: Map<String, Int>, val rows: Map<String, List<BillRow>>, val debts: List<Views.Debt>)

class SpendingModel(val ym: String, val entries: List<Obj>, val oneTime: List<Obj>, val total: Double, val oneTimeTotal: Double, val byCat: List<Pair<String, Double>>, val locked: Boolean, val emergencyPlan: Double)

class Undo(val message: String, val paths: Map<String, J?>)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val la = app as LoonieApp
    val repo = la.repo
    val prefs = la.prefs
    val updater = la.updater

    val today = MutableStateFlow(todayStr())
    /** the month shown on Month, Bills and Spending */
    val ym = MutableStateFlow<String?>(null)

    private val messages = Channel<Undo>(Channel.BUFFERED)
    val snack = messages.receiveAsFlow()

    fun todayStr(): String = LocalDate.now().toString()
    fun refreshDay() { val t = todayStr(); if (t != today.value) today.value = t }

    private fun store(docs: Map<String, J>, t: String): Store = Docs.toStore(docs, t)
    fun settings(): Obj = Docs.settings(repo.get("settings/main"), today.value)
    fun defaultYm(t: String, s: J): String { val cur = ymOf(t); val st = s.s("startYm"); return if (isYm(st) && st > cur) st else cur }
    fun shownYm(): String = ym.value ?: defaultYm(today.value, settings())

    fun isNew(s: J, bills: Map<String, Obj>) = Core.incomesOf(s).isEmpty() && bills.isEmpty() && Core.accountsOf(s).isEmpty()

    fun titleFor(s: J): String { val n = s.s("householdName").trim()
        return if (n.isEmpty()) "Family Budget" else if (n.endsWith("s", ignoreCase = true)) "$n' Budget" else "$n's Budget" }

    val todayModel: StateFlow<TodayModel?> = combine(repo.docs, today, prefs.lastBackupFlow) { d, t, lb -> Triple(d, t, lb) }
        .mapLatest { (d, t, lb) -> buildToday(d, t, lb) }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val monthModel: StateFlow<MonthModel?> = combine(repo.docs, today, ym) { d, t, y -> Triple(d, t, y) }
        .mapLatest { (d, t, y) -> buildMonth(d, t, y) }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val billsModel: StateFlow<BillsModel?> = combine(repo.docs, today, ym) { d, t, y -> Triple(d, t, y) }
        .mapLatest { (d, t, y) -> buildBills(d, t, y) }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val spendingModel: StateFlow<SpendingModel?> = combine(repo.docs, today, ym) { d, t, y -> Triple(d, t, y) }
        .mapLatest { (d, t, y) -> buildSpending(d, t, y) }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun buildToday(d: Map<String, J>, t: String, lastBackup: String?): TodayModel {
        val st = store(d, t); val s = st.settings; val ym = ymOf(t); val c = Core.monthCalc(ym, st, t)
        val up = Core.upcoming(st, t, 7) { Labels.expLabel(it) }
        val status = Views.status(c, up, "now", t, s)
        val todo = up.filter { (it.s("kind") == "bill" && !it.t("paid") && it.s("date") <= t) || (it.s("kind") == "move" && !it.t("done") && it.s("date") <= t) }
        val incoming = up.filter { it.s("kind") == "in" && it.s("date") == t }
        val next = up.filter { it.s("date") > t && (it.s("kind") != "bill" || !it.t("paid")) }.take(6)
        val fp = Core.foodPeriod(c, t)
        val tone = if (fp.n("spent") > fp.n("allow")) "bad" else if (fp.n("spent") > fp.n("expected") + 10) "warn" else "good"
        return TodayModel(t, titleFor(s), isNew(s, st.bills), status, Views.reminders(st, t, lastBackup), todo, incoming, fp, tone,
            Views.quickButtons(s, Docs.allExpenses(d), t), Views.debts(st, t), next)
    }

    private fun buildMonth(d: Map<String, J>, t: String, y: String?): MonthModel {
        val st = store(d, t); val s = st.settings; val ym = y ?: defaultYm(t, s); val c = Core.monthCalc(ym, st, t)
        val phase = Views.phaseOf(ym, t, s); val days = s.n("upcomingDays").toInt().takeIf { it > 0 } ?: 10
        val upAll = Core.upcoming(st, t, days) { Labels.expLabel(it) }
        val status = Views.status(c, upAll, phase, t, s)
        val up = upAll.filter { !(it.s("kind") == "bill" && it.t("paid") && it.s("date") < t) }
        val lines = c["billLines"].arr().filterIsInstance<Obj>()
        val own = lines.filter { !it.t("deferredFrom") && it.n("planned") > 0 }
        val skips = own.filter { !it.t("essential") && !it.t("deferredTo") }.sortedByDescending { it.n("planned") }
        val defers = own.filter { it.t("deferrable") && !it.t("skipped") }.sortedByDescending { it.n("planned") }
        val short = c.n("net") < 0
        val showAdjust = phase != "past" && !c.t("closed") && (short || c.n("skippedTotal") != 0.0 || c.n("deferredTotal") != 0.0)
        val bal = Core.savingsState(st); val thr = Core.targetsThrough(st, ym, t); val owed = round2(maxOf(0.0, thr.emergency - bal.emergency))
        val (safe, note) = when {
            short && phase != "past" -> -c.n("net") to "Cover the rest from Emergency Savings. Child Reserve is last resort only."
            phase == "now" -> { val lastReg = c.strList("regularDays").lastOrNull()
                owed to (if (owed > 0) "This month's ${if (c.n("bonus") > 0) "surplus and 3rd paycheck" else "surplus"}" + (if (owed - c.n("target") > 0.5) " + ${Fmt.money(owed - c.n("target"))} from earlier months" else "") + "." +
                    (if (lastReg != null && lastReg > t) " Move it after ${Fmt.dShort(lastReg)}." else "") else "Nothing waiting to move right now.") }
            phase == "future" -> c.n("target") to "Planned for Emergency Savings"
            else -> c.n("target") to "Moved in ${Fmt.monthOnly(ym)}: ${Fmt.money(c["moved"].n("emergency"))}"
        }
        val due = upAll.filter { it.s("kind") == "bill" && !it.t("paid") }
        val byCat = (c["byCat"] as? Obj)?.map?.entries?.map { it.key to looniemate.engine.num(it.value) }?.sortedByDescending { it.second } ?: emptyList()
        return MonthModel(ym, t, phase, c, status, up, days, skips, defers, showAdjust, short, safe, note, if (ymOf(t) == ym) Core.foodPeriod(c, t) else null,
            sumOf2(due.map { it.n("amount") }), due.size, due.count { it.t("overdue") }, bal.child, thr.childSoFar,
            Views.planCards(c, s), Views.debts(st, t), bal.emergency, byCat, st.bills.values.any { it.t("example") }, isNew(s, st.bills))
    }

    private fun buildBills(d: Map<String, J>, t: String, y: String?): BillsModel {
        val st = store(d, t); val s = st.settings; val ym = y ?: defaultYm(t, s); val c = Core.monthCalc(ym, st, t)
        val lines = c["billLines"].arr().filterIsInstance<Obj>()
        val plan = lines.filter { !it.t("deferredFrom") }.associateBy { it.s("id") }
        val inFrom = HashMap<String, Pair<Double, String>>()
        lines.filter { it.t("deferredFrom") }.forEach { l -> val p = inFrom[l.s("billId")]; inFrom[l.s("billId")] = ((p?.first ?: 0.0) + l.n("amount")) to l.s("deferredFrom") }
        val rows = Labels.GROUPS.keys.associateWith { g -> st.bills.entries.filter { it.value.or("group", "adults") == g }.sortedBy { it.value.s("name").lowercase() }
            .map { (id, b) -> BillRow(id, b, plan[id], inFrom[id]?.first ?: 0.0, inFrom[id]?.second, Views.freqText(b, s, t)) } }
        val totals = Labels.GROUPS.keys.associateWith { c["byGroup"].n(it) }
        return BillsModel(ym, totals, rows.mapValues { it.value.size }, rows, Views.debts(st, t))
    }

    private fun buildSpending(d: Map<String, J>, t: String, y: String?): SpendingModel {
        val st = store(d, t); val s = st.settings; val ym = y ?: defaultYm(t, s); val c = Core.monthCalc(ym, st, t)
        val all = c["expenses"].arr().filterIsInstance<Obj>()
        val byCat = (c["byCat"] as? Obj)?.map?.entries?.map { it.key to looniemate.engine.num(it.value) }?.sortedByDescending { it.second } ?: emptyList()
        val one = all.filter { it.t("oneTime") }
        return SpendingModel(ym, all.filter { !it.t("oneTime") }, one, sumOf2(all.map { it.n("amount") }), sumOf2(one.map { it.n("amount") }), byCat,
            st.months[ym].t("closed"), c.n("target"))
    }

    /* ---------- changes, each with Undo ---------- */

    private fun change(message: String, paths: List<String>, block: () -> Unit) {
        val before = paths.associateWith { repo.docs.value[it]?.copy() }
        block()
        if (message.isNotEmpty()) messages.trySend(Undo(message, before))
    }
    fun undo(u: Undo) { u.paths.forEach { (p, v) -> repo.restore(p, v) } }
    fun say(msg: String) { messages.trySend(Undo(msg, emptyMap())) }

    private fun patchMonth(ym: String, patch: Obj) = repo.update("months/$ym", patch)

    fun saveSettings(patch: Obj) {
        val raw = (repo.get("settings/main")?.copy() as? Obj) ?: Obj(); val s = settings()
        if (!isYm(raw["startYm"])) raw["startYm"] = s["startYm"]; if (!isDate(raw["trackFrom"])) raw["trackFrom"] = s["trackFrom"]
        patch.map.forEach { (k, v) -> raw[k] = v }
        repo.set("settings/main", raw)
    }

    fun togglePaid(e: Obj) { val ym = e.s("ym"); val cur = repo.get("months/$ym")?.get("paid")?.get(e.s("key")).let { looniemate.engine.truthy(it) }
        change(if (cur) "Marked not paid" else "Marked paid", listOf("months/$ym")) { patchMonth(ym, J.obj("paid" to J.obj(e.s("key") to !cur))) } }

    fun toggleMove(e: Obj) { val ym = e.s("ym"); val m = repo.get("months/$ym"); val cur = looniemate.engine.truthy(m?.get("moves")?.get(e.s("key")))
        val patch = J.obj("moves" to J.obj(e.s("key") to !cur))
        val sav = e.s("sav")
        if (sav.isNotEmpty()) { val k = sav.replace("-out", ""); val sign = if (sav.endsWith("-out")) -1 else 1; val amt = e.n("amount") * sign
            patch["moved"] = J.obj(k to round2(m?.get("moved").n(k) + if (cur) -amt else amt)) }
        change(if (cur) "Marked not done" else "Done. Nice.", listOf("months/$ym")) { patchMonth(ym, patch) } }

    fun locked(ym: String) = repo.get("months/$ym").t("closed")

    /** Saves a spending entry. Returns an error message, or null when it worked. */
    fun logExpense(amount: Double?, category: String, date: String, note: String, who: String, oneTime: Boolean, group: String, addQuick: Boolean): String? {
        if (amount == null || amount.isNaN() || !(amount > 0)) return "Enter an amount greater than \$0."
        if (!oneTime && category.isEmpty()) return "Pick what it was for."
        if (oneTime && note.isBlank()) return "Give it a short name, like \"Car seat\"."
        if (!isDate(date)) return "Pick a date."
        val ym = ymOf(date); if (locked(ym)) return "${Fmt.monthName(ym)} is locked. Unlock it in the web version to add to it."
        val id = repo.newId(); val path = "months/$ym/expenses/$id"
        val doc = J.obj("date" to date, "amount" to amount, "category" to (if (oneTime) "oneoff" else category), "note" to note.trim(), "oneTime" to oneTime, "created" to System.currentTimeMillis())
        if (oneTime) doc["group"] = J.S(group); if (who.isNotEmpty()) doc["who"] = J.S(who)
        change("Saved ${Fmt.money(amount)} · ${if (oneTime) note.trim() else Labels.SPENDL[category]}", listOf(path, "settings/main")) {
            repo.set(path, doc)
            if (addQuick && !oneTime) { val list = settings()["quick"].arr().map { it.copy() }.toMutableList()
                if (list.none { it.s("category") == category && round2(it.n("amount")) == amount && it.s("note") == note.trim() }) list.add(J.obj("category" to category, "amount" to amount, "note" to note.trim()))
                saveSettings(J.obj("quick" to list.takeLast(8))) }
        }
        return null
    }

    fun quickLog(q: Views.Quick) {
        val t = today.value; if (locked(ymOf(t))) { say("${Fmt.monthName(ymOf(t))} is locked."); return }
        val path = "months/${ymOf(t)}/expenses/${repo.newId()}"
        change("Logged ${Fmt.money(q.amount)} · ${q.label}", listOf(path)) {
            repo.set(path, J.obj("date" to t, "amount" to round2(q.amount), "category" to q.category, "note" to q.note, "oneTime" to false, "created" to System.currentTimeMillis())) }
    }

    fun deleteExpense(e: Obj) { val p = "months/${ymOf(e.s("date"))}/expenses/${e.s("id")}"
        change("Deleted ${Fmt.money(e.n("amount"))}", listOf(p, "meta/tombstones")) { repo.delete(p) } }

    fun saveBill(id: String?, doc: Obj) { val bid = id ?: repo.newId(); val p = "bills/$bid"
        change(if (id != null) "Bill updated" else "Bill added", listOf(p)) { repo.set(p, doc) } }

    fun deleteBill(id: String) { val p = "bills/$id"; change("Bill deleted", listOf(p, "meta/tombstones")) { repo.delete(p) } }

    fun skip(ym: String, id: String, amount: Double) = change("Giving up ${Fmt.money(amount)} in ${Fmt.monthOnly(ym)}", listOf("months/$ym")) {
        patchMonth(ym, J.obj("skipped" to J.obj(id to J.obj("amount" to round2(amount))))) }
    fun unskip(ym: String, id: String) = change("Back in the budget", listOf("months/$ym")) { patchMonth(ym, J.obj("skipped" to J.obj(id to J.Null))) }
    fun defer(ym: String, id: String, date: String, amount: Double) = change("${Fmt.money(amount)} deferred to ${Fmt.dShort(date)}", listOf("months/$ym")) {
        patchMonth(ym, J.obj("deferred" to J.obj(id to J.obj("date" to date, "amount" to round2(amount))))) }
    fun undefer(ym: String, id: String) = change("Back to its normal date", listOf("months/$ym")) { patchMonth(ym, J.obj("deferred" to J.obj(id to J.Null))) }

    fun transfer(kind: String, out: Boolean, amount: Double) { val ym = ymOf(today.value); val cur = repo.get("months/$ym")?.get("moved").n(kind)
        val name = Core.fundOf(settings(), kind).s("name")
        change("${if (out) "Took out" else "Moved"} ${Fmt.money(amount)} ${if (out) "from" else "to"} $name", listOf("months/$ym")) {
            patchMonth(ym, J.obj("moved" to J.obj(kind to round2(cur + if (out) -amount else amount)))) } }

    fun reminderAction(r: Views.Reminder) { when (r.action) {
        "tax-filed" -> { val tf = (settings()["taxFiled"]?.copy() as? Obj) ?: Obj(); tf[r.arg] = J.B(true)
            change("Nice work. Reminder cleared for this year.", listOf("settings/main")) { saveSettings(J.obj("taxFiled" to tf)) } }
        "reported" -> change("Marked as reported", listOf("months/${r.arg2}")) { patchMonth(r.arg2, J.obj("reported" to J.obj(r.arg to true))) }
    } }

    /* ---------- encrypted backups (the same file the web version makes) ---------- */

    class Opened(val payload: Obj, val bills: Int, val months: Int, val entries: Int, val name: String, val exportedAt: String)

    suspend fun exportTo(uri: Uri, password: String) = withContext(Dispatchers.Default) {
        val text = Backup.encrypt(Backup.payload(repo.docs.value, java.time.Instant.now().toString()), password)
        withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
        prefs.lastBackup = java.time.Instant.now().toString()
    }

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }

    suspend fun open(text: String, password: String): Opened = withContext(Dispatchers.Default) {
        val (p, _) = Backup.decrypt(text, password); val keys = (p["data"] as Obj).map.keys
        Opened(p, keys.count { Regex("^bills/[^/]+$").matches(it) }, keys.count { Regex("^months/[^/]+$").matches(it) }, keys.count { it.contains("/expenses/") },
            p["data"]?.get("settings/main").s("householdName"), p.s("exportedAt"))
    }

    fun applyBackup(o: Opened, combine: Boolean): String {
        val incoming = (o.payload["data"] as Obj).map
        return if (combine) { val r = Backup.merge(repo.docs.value, incoming); repo.replaceAll(r.data); prefs.lastBackup = java.time.Instant.now().toString()
            "Combined. ${r.added} new, ${r.updated} updated" + (if (r.removed > 0) ", ${r.removed} removed" else "") + "." }
        else { repo.replaceAll(LinkedHashMap(incoming)); prefs.lastBackup = o.exportedAt.ifEmpty { java.time.Instant.now().toString() }; "Backup imported." }
    }

    fun hasData(): Boolean = repo.docs.value.keys.any { it != "meta/tombstones" }

    fun checkUpdates(manual: Boolean) { viewModelScope.launch { if (manual) updater.check(true) else updater.checkIfDue() } }
    fun downloadUpdate(r: looniemate.app.update.Updater.Release) { viewModelScope.launch { updater.download(r) } }

    init { checkUpdates(false) }

    companion object { fun groupLabel(g: String) = Labels.GROUPS[g]?.label ?: Labels.OGROUPS[g] ?: "Adults"
        fun nextPaydays(s: J, ym: String): List<String> = (Core.mainPaydays(s, ymAdd(ym, 1)) + Core.mainPaydays(s, ymAdd(ym, 2))).take(3)
        fun firstOfNext(ym: String) = ymAdd(ym, 1) + "-01"
        fun plusDays(d: String, n: Int) = addDays(d, n) }
}
