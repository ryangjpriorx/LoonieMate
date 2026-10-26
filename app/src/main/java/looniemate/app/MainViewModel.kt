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
import looniemate.engine.*

/* ---------- what each screen shows ---------- */

class TodayModel(
    val today: String, val title: String, val isNew: Boolean, val status: Views.Status, val reminders: List<Views.Reminder>,
    val todo: List<Obj>, val incoming: List<Obj>, val food: Obj, val foodTone: String, val quick: List<Views.Quick>,
    val debts: List<Views.Debt>, val next: List<Obj>, val lastCheck: Obj?, val hasAccounts: Boolean,
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

class FundView(val f: Obj, val balance: Double, val linked: List<String>, val adding: Double)
class SavingsModel(val ym: String, val today: String, val s: Obj, val c: Obj, val emergency: Double, val child: Double, val thr: Core.Targets,
                   val funds: List<FundView>, val projection: List<Obj>, val programs: SavingsViews.Programs, val kids: Boolean)

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
    /** whether reminders will actually be shown; checked again each time the app comes to the front */
    val notifOk = MutableStateFlow(true)
    fun refreshNotif() { notifOk.value = looniemate.app.notify.Notifs.enabled(getApplication()); remindersLate.value = prefs.remindersOn && prefs.reminderLateCount >= 2 }
    /** reminders have arrived more than an hour late at least twice: the phone is putting LoonieMate to sleep */
    val remindersLate = MutableStateFlow(false)
    fun dismissLate() { prefs.reminderLateCount = 0; remindersLate.value = false }
    fun snoozeNotifCard() { prefs.notifAsked = true; prefs.notifSnoozeUntil = System.currentTimeMillis() + 30L * 86_400_000; refreshNotif(); notifTick.value++ }
    val notifTick = MutableStateFlow(0)
    /** set when the app was opened from an update notification: "update" or "install" */
    val openUpdate = MutableStateFlow<String?>(null)

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

    val savingsModel: StateFlow<SavingsModel?> = combine(repo.docs, today, ym) { d, t, y -> Triple(d, t, y) }
        .mapLatest { (d, t, y) -> buildSavings(d, t, y) }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun buildSavings(d: Map<String, J>, t: String, y: String?): SavingsModel {
        val st = store(d, t); val s = st.settings; val ym = y ?: defaultYm(t, s); val c = Core.monthCalc(ym, st, t)
        val bal = Core.savingsState(st); val lines = c["billLines"].arr().filterIsInstance<Obj>()
        val funds = Core.fundsOf(s).map { f -> val k = f.s("key"); val ids = st.bills.filter { it.value.s("fund") == k }.keys
            FundView(f, bal.funds[k] ?: 0.0, ids.mapNotNull { st.bills[it]?.s("name") }, sumOf2(lines.filter { it.s("id") in ids }.map { it.n("amount") })) }
        val cur = ymOf(t); val from = if (isYm(s["startYm"]) && s.s("startYm") > cur) s.s("startYm") else cur
        return SavingsModel(ym, t, s, c, bal.emergency, bal.child, Core.targetsThrough(st, ym, t), funds, Core.projection(st, t, from, 6),
            SavingsViews.programs(st, t, Docs.checkins(d).firstOrNull()), SavingsViews.hasKids(st))
    }

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
            Views.quickButtons(s, Docs.allExpenses(d), t), Views.debts(st, t), next, Docs.checkins(d).firstOrNull(), Core.accountsOf(s).isNotEmpty())
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
        looniemate.app.notify.ReminderAlarm.refreshShown(getApplication())
    }
    fun undo(u: Undo) { u.paths.forEach { (p, v) -> repo.restore(p, v) }; looniemate.app.notify.ReminderAlarm.refreshShown(getApplication()) }
    fun say(msg: String) { messages.trySend(Undo(msg, emptyMap())) }

    private fun patchMonth(ym: String, patch: Obj) = repo.update("months/$ym", patch)

    fun saveSettings(patch: Obj) {
        val raw = (repo.get("settings/main")?.copy() as? Obj) ?: Obj(); val s = settings()
        if (!isYm(raw["startYm"])) raw["startYm"] = s["startYm"]; if (!isDate(raw["trackFrom"])) raw["trackFrom"] = s["trackFrom"]
        patch.map.forEach { (k, v) -> raw[k] = v }
        repo.set("settings/main", raw)
    }

    fun togglePaid(e: Obj) { val ym = e.s("ym"); val cur = looniemate.app.data.Ticks.isPaid(repo, ym, e.s("key"))
        change(if (cur) "Marked not paid" else "Marked paid", listOf("months/$ym")) { looniemate.app.data.Ticks.setPaid(repo, ym, e.s("key"), !cur) } }

    fun toggleMove(e: Obj) { val ym = e.s("ym"); val cur = looniemate.app.data.Ticks.isMoved(repo, ym, e.s("key"))
        change(if (cur) "Marked not done" else "Done. Nice.", listOf("months/$ym")) { looniemate.app.data.Ticks.setMoved(repo, ym, e.s("key"), e.s("sav"), e.n("amount"), !cur) } }

    fun locked(ym: String) = repo.get("months/$ym").t("closed")

    /** Saves a spending entry. Returns an error message, or null when it worked. */
    fun logExpense(amount: Double?, category: String, date: String, note: String, who: String, oneTime: Boolean, group: String, addQuick: Boolean): String? {
        if (amount == null || amount.isNaN() || !(amount > 0)) return "Enter an amount greater than \$0."
        if (!oneTime && category.isEmpty()) return "Pick what it was for."
        if (oneTime && note.isBlank()) return "Give it a short name, like \"Car seat\"."
        if (!isDate(date)) return "Pick a date."
        val ym = ymOf(date); if (locked(ym)) return "${Fmt.monthName(ym)} is locked. Unlock it on the Month tab to add to it."
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

    /* ---------- accounts (settings.accounts, the same list the web version's Accounts tab edits) ---------- */

    /** Saves an account. Returns an error message, or null when it worked. */
    fun saveAccount(id: String?, name: String, type: String, kind: String, purposes: List<String>): String? {
        if (name.isBlank()) return "Give the account a name."
        val s = settings()
        if (Core.accountsOf(s).any { it.s("id") != id && it.s("name").equals(name.trim(), ignoreCase = true) }) return "You already have an account called ${name.trim()}."
        val list = Accounts.saved(s, id ?: ("a" + repo.newId()), name, type, kind, purposes)
        change(if (id != null) "Saved ${name.trim()}" else "Added ${name.trim()}", listOf("settings/main")) { saveSettings(J.obj("accounts" to list)) }
        return null
    }

    fun deleteAccount(id: String) {
        val s = settings(); val name = Core.acctById(s, id)?.s("name") ?: "the account"
        val used = Accounts.billsUsing(s, repo.docs.value.filterKeys { it.startsWith("bills/") }.values, id)
        change("Removed $name" + (if (used > 0) ". Give its bills a new account." else ""), listOf("settings/main")) { saveSettings(Accounts.removed(s, id)) }
    }

    /* ---------- savings funds (settings.funds, plus emergencyStart/Goal, childStart and ccbEmergencyPct) ---------- */

    /** Saves a savings fund. A null key adds a new one. Returns an error message, or null when it worked. */
    fun saveFund(key: String?, name: String, account: String, start: String, goal: String): String? {
        val st0 = Views.parseMoney(start); if (st0 == null || st0.isNaN()) return "Enter the balance at the start (0 if none)."
        val core = key == "emergency" || key == "child"
        val g = if (key == "child") { val p = goal.trim().toDoubleOrNull()?.let { Math.round(it).toInt() }; if (p == null || p !in 0..100) return "Enter a share from 0 to 100."; p.toDouble() }
            else Views.parseMoney(goal).let { if (it == null) 0.0 else if (it.isNaN() || it < 0) return "Enter a goal in dollars, or leave it blank." else it }
        if (!core && name.isBlank()) return "Give the fund a name."
        val s = settings(); val list = Core.fundsOf(s).map { it.copy() as Obj }.toMutableList()
        val k = key ?: ("f" + repo.newId())
        val f = list.find { it.s("key") == k } ?: J.obj("key" to k, "name" to "", "account" to "", "goal" to 0, "start" to 0).also { list.add(it) }
        f["account"] = J.S(account); if (!core) { f["name"] = J.S(name.trim().take(40)); f["start"] = J.N(st0); f["goal"] = J.N(g) }
        val patch = J.obj("funds" to list)
        when (key) { "emergency" -> { patch["emergencyStart"] = J.N(st0); patch["emergencyGoal"] = J.N(g) }; "child" -> { patch["childStart"] = J.N(st0); patch["ccbEmergencyPct"] = J.N(g) } }
        change(if (key == null) "Added ${name.trim()}" else "Saved ${f.s("name").ifEmpty { name.trim() }}", listOf("settings/main")) { saveSettings(patch) }
        return null
    }

    /** Removes a fund. Bills that paid into it become regular bills, as in the web version. */
    fun deleteFund(key: String) {
        val s = settings(); val name = Core.fundOf(s, key).s("name")
        val bills = repo.docs.value.filter { it.key.startsWith("bills/") && it.value.s("fund") == key }.keys
        change("Removed $name", listOf("settings/main") + bills) {
            saveSettings(J.obj("funds" to Core.fundsOf(s).filter { it.s("key") != key }.map { it.copy() }))
            bills.forEach { repo.update(it, J.obj("fund" to "")) }
        }
    }

    /** A ready-made fund: the fund, plus a monthly non-essential bill that pays into it. */
    fun addReadyFund(r: SavingsViews.Ready) {
        val s = settings(); val key = "f" + repo.newId(); val id = repo.newId()
        val sav = Core.accountsOf(s).find { it.s("type") == "savings" }?.s("id") ?: ""; val main = Core.acctFor(s, "main")?.s("id") ?: ""
        val pay = Core.mainPaydays(s, ymOf(today.value)).firstOrNull(); val day = pay?.substring(8, 10)?.toInt() ?: 1
        val funds = Core.fundsOf(s).map { it.copy() } + J.obj("key" to key, "name" to r.name, "account" to sav, "goal" to r.yearly, "start" to 0)
        change("Added ${r.name}: ${Fmt.money(r.yearly / 12)} a month toward ${Fmt.money(r.yearly)} a year", listOf("settings/main", "bills/$id")) {
            saveSettings(J.obj("funds" to funds))
            repo.set("bills/$id", J.obj("name" to r.name + " savings", "group" to r.group, "category" to "other", "amount" to round2(r.yearly / 12), "freq" to "monthly", "day" to day,
                "account" to main, "method" to "Interac e-Transfer", "fund" to key, "essential" to false, "deferrable" to false, "active" to true, "updated" to System.currentTimeMillis()))
        }
    }

    /** Takes money out of a fund to pay for what it was saved for (not counted as spending). */
    fun useFund(key: String, amount: Double, note: String) {
        val t = today.value; val ym = ymOf(t); val m = repo.get("months/$ym"); val prev = m?.get("moved").n(key)
        val uses = m?.get("fundUses").arr().map { it.copy() } + J.obj("fund" to key, "amount" to amount, "note" to note.trim(), "date" to t)
        val f = Core.fundOf(settings(), key); val acct = Core.acctById(settings(), f.s("account"))?.s("name")
        change("Took ${Fmt.money(amount)} out of ${f.s("name")}" + (if (acct != null) ". Move it from $acct to the account you pay with." else ""), listOf("months/$ym")) {
            patchMonth(ym, J.obj("moved" to J.obj(key to round2(prev - amount)), "fundUses" to uses)) }
    }

    fun setCcbSplit(ym: String, amount: Double) = change("${Fmt.money(amount)} of the Child Benefit goes to Emergency Savings", listOf("months/$ym")) {
        patchMonth(ym, J.obj("income" to J.obj("ccbToEmergency" to round2(amount)))) }

    fun toggleClb(k: String) { val c = (settings()["clb"]?.copy() as? Obj) ?: Obj(); c[k] = J.B(!c.t(k)); change("", emptyList()) { saveSettings(J.obj("clb" to c)) } }

    fun setFamilyIncome(v: String): String? {
        val t = v.trim().replace(",", "").replace("$", "")
        val value: Any = if (t.isEmpty()) "" else (t.toDoubleOrNull()?.let { maxOf(0, Math.round(it).toInt()) } ?: return "Enter a whole dollar amount, or leave it blank.")
        change("Saved", listOf("settings/main")) { saveSettings(J.obj("familyIncome" to value)) }; return null
    }

    /* ---------- income and benefits (settings.incomes and settings.credits, as on the web version's Income tab) ---------- */

    fun saveIncome(isNew: Boolean, src: Obj) { val s = settings(); val list = Core.incomesOf(s).map { it.copy() }.toMutableList()
        val i = list.indexOfFirst { it.s("id") == src.s("id") }; if (i >= 0) list[i] = src else list.add(src)
        change(if (isNew) "Added ${src.s("name")}" else "Saved ${src.s("name")}", listOf("settings/main")) { saveSettings(J.obj("incomes" to list)) } }

    fun deleteIncome(id: String) { val s = settings(); val name = Core.incomesOf(s).find { it.s("id") == id }?.s("name") ?: "Income"
        change("Removed $name", listOf("settings/main")) { saveSettings(J.obj("incomes" to Core.incomesOf(s).filter { it.s("id") != id }.map { it.copy() })) } }

    fun saveCredit(isNew: Boolean, cr: Obj) { val s = settings(); val list = Core.creditsOf(s).map { it.copy() }.toMutableList()
        val i = list.indexOfFirst { it.s("id") == cr.s("id") }; if (i >= 0) list[i] = cr else list.add(cr)
        change(if (isNew) "Added ${Core.creditName(cr)}" else "Saved ${Core.creditName(cr)}", listOf("settings/main")) { saveSettings(J.obj("credits" to list)) } }

    fun deleteCredit(id: String) { val s = settings(); val cr = Core.creditsOf(s).find { it.s("id") == id }
        change("Removed ${cr?.let { Core.creditName(it) } ?: "benefit"}", listOf("settings/main")) { saveSettings(J.obj("credits" to Core.creditsOf(s).filter { it.s("id") != id }.map { it.copy() })) } }

    /** One month's own income amounts and dates (months/<ym>.income), which never change the months before it. */
    fun saveMonthIncome(ym: String, patch: Obj) { if (patch.map.isEmpty()) return
        change("Saved for ${Fmt.monthName(ym)}", listOf("months/$ym")) { patchMonth(ym, J.obj("income" to patch)) } }

    /** Locks a month with a snapshot of its numbers, the way the web version does. */
    fun lockMonth(ym: String) { val t = today.value; val c = Core.monthCalc(ym, Docs.toStore(repo.docs.value, t), t)
        change("${Fmt.monthOnly(ym)} is locked", listOf("months/$ym")) { patchMonth(ym, J.obj("closed" to true, "closedOn" to t, "snapshot" to Core.snapshot(c))) } }
    fun unlockMonth(ym: String) = change("${Fmt.monthOnly(ym)} is unlocked", listOf("months/$ym")) { patchMonth(ym, J.obj("closed" to false)) }

    /* ---------- catching up: things the family is behind on (docs/plans/getting-on-track.md) ---------- */

    fun catchUpOnDate(name: String, amount: Double, date: String) { val ym = ymOf(date); val p = "months/$ym/expenses/${repo.newId()}"
        change("${name.trim()}: ${Fmt.money(amount)} planned for ${Fmt.dShort(date)}", listOf(p)) { repo.set(p, CatchUp.oneTime(name, amount, date)) } }

    fun catchUpPlan(name: String, owed: Double, payment: Double, day: Int, account: String, method: String) { val p = "bills/${repo.newId()}"
        change("${name.trim()}: ${Fmt.money(payment)} a month", listOf(p)) { repo.set(p, CatchUp.plan(repo.docs.value, today.value, name, owed, payment, day, account, method)) } }

    fun catchUpUnsure(name: String, amount: Double) = change("Added ${name.trim()}", listOf("settings/main")) {
        saveSettings(J.obj("catchUp" to CatchUp.unsure(settings(), "cu" + repo.newId(), name, amount, today.value))) }

    /**
     * Pay [amount] of a bill on [date] instead (part of rent on the next payday, or a bill pushed to next month).
     * The engine only moves bills that can be paid late, so this marks the bill that way too.
     */
    fun catchUpSplit(ym: String, id: String, date: String, amount: Double) { val b = "bills/$id"; val name = repo.get(b).s("name")
        change("${Fmt.money(amount)} of $name moved to ${Fmt.dShort(date)}", listOf(b, "months/$ym")) {
            if (!repo.get(b).t("deferrable")) repo.update(b, J.obj("deferrable" to true))
            patchMonth(ym, J.obj("deferred" to J.obj(id to J.obj("date" to date, "amount" to round2(amount))))) } }
    fun catchUpPush(ym: String, id: String, date: String, amount: Double) = catchUpSplit(ym, id, date, amount)

    /** First-time setup starts tracking from the 1st, so bills left unticked this month count as things to catch up on. */
    fun startCatchUp(first: Boolean) { if (first) { val ym = ymOf(today.value); if (settings().s("trackFrom") > "$ym-01") saveSettings(J.obj("trackFrom" to "$ym-01")) } }

    /** The window the steps look back over: the same one Today's overdue list and Where you stand use (21 days, since tracking began). */
    fun catchUpFrom(): String { val t = today.value; val tf = settings().s("trackFrom"); var from = addDays(t, -21); if (isDate(tf) && from < tf) from = tf
        val m1 = ymOf(t) + "-01"; return if (from < m1) m1 else from }

    /**
     * Finishing the steps: this month becomes a starting month (no savings target). First-time setup also starts
     * tracking from the 1st, so bills left unticked this month show as things to catch up on.
     */
    fun finishCatchUp(first: Boolean) { val ym = ymOf(today.value)
        change(if (first) "You're all set from today" else "Back on track from today", listOf("settings/main", "months/$ym")) {
            if (first) saveSettings(J.obj("trackFrom" to "$ym-01"))
            patchMonth(ym, J.obj("starting" to true)) }
        prefs.onboarded = true }

    /** An undecided item was paid, or turned into a date or a payment plan. */
    fun catchUpResolve(id: String, message: String = "") = change(message, listOf("settings/main")) {
        saveSettings(J.obj("catchUp" to CatchUp.undecided(settings()).filter { it.s("id") != id }.map { it.copy() })) }

    /* ---------- balance checks (checkins/<date>, the same documents the web version saves) ---------- */

    class CheckResult(val date: String, val bal: Map<String, Double>, val day: Double, val sav: Double, val prev: Obj?, val ex: Checkins.Expected?,
                      val gap: Double?, val funds: Double, val hasSavings: Boolean, val coverage: List<Obj>)

    /** Compares the balances typed in (by account id) with the plan. Returns the result, or an error message. */
    fun compareBalances(typed: Map<String, String>): Pair<CheckResult?, String?> {
        val t = today.value; val d = repo.docs.value; val st = Docs.toStore(d, t); val s = st.settings; val A = Core.accountsOf(s)
        if (A.isEmpty()) return null to "Add your accounts first."
        val bal = LinkedHashMap<String, Double>()
        for (a in A) { val v = Views.parseMoney(typed[a.s("id")])
            if (v == null) return null to "Enter the balance for ${a.s("name")} (0 if empty)."
            if (v.isNaN()) return null to "${a.s("name")}: enter a dollar amount."
            bal[a.s("id")] = v }
        val day = sumOf2(A.filter { it.s("type") != "savings" }.map { bal[it.s("id")] ?: 0.0 }); val sav = sumOf2(A.filter { it.s("type") == "savings" }.map { bal[it.s("id")] ?: 0.0 })
        val prev = Checkins.previous(d, t)
        val ex = prev?.let { Checkins.expectedSince(st, Docs.allExpenses(d), it.s("date"), it.n("day"), t) }
        val gap = ex?.let { round2(day - it.expected) }
        val funds = round2(Core.savingsState(st).funds.values.sum())
        return CheckResult(t, bal, day, sav, prev, ex, gap, funds, A.any { it.s("type") == "savings" }, Checkins.coverage(st, bal, t)) to null
    }

    /** Saves the check-in. With [logGap], money that's missing is logged as unlogged spending first, so the next check starts even. */
    fun saveCheckin(r: CheckResult, logGap: Boolean) {
        val p = "checkins/${r.date}"; val logIt = logGap && r.gap != null && r.gap < 0
        val exp = if (logIt) "months/${ymOf(r.date)}/expenses/${repo.newId()}" else null
        change(if (logIt) "Logged the difference and saved the check-in" else "Check-in saved", listOfNotNull(p, exp)) {
            if (exp != null) repo.set(exp, J.obj("date" to r.date, "amount" to round2(-r.gap!!), "category" to "other", "note" to "Unlogged spending (found at balance check)",
                "oneTime" to false, "created" to System.currentTimeMillis()))
            repo.set(p, J.obj("date" to r.date, "bal" to J.O(LinkedHashMap(r.bal.mapValues { J.N(it.value) })), "day" to r.day, "sav" to r.sav,
                "expected" to (if (logIt) r.day else r.ex?.expected), "gap" to (if (logIt) 0.0 else r.gap)))
        }
    }

    /* ---------- food budget (settings.foodPct and foodMin, as on the web version's Food tab) ---------- */

    /** Reads the two food settings from what was typed, the way the web version checks them. Returns them, or an error message. */
    fun readFood(pct: String, min: String): Pair<Pair<Int, Double>?, String?> {
        val p = pct.trim().toDoubleOrNull()?.let { Math.round(it).toInt() }
        if (p == null || p !in 0..100) return null to "Enter a share from 0 to 100."
        val m = Views.parseMoney(min)
        if (m == null || m.isNaN() || m < 0) return null to "Enter a dollar amount for the minimum."
        return (p to m) to null
    }

    /** What a month would look like with these food settings, before saving them. */
    fun foodPreview(ym: String, pct: Int, min: Double): Obj {
        val t = today.value; val docs = LinkedHashMap(repo.docs.value)
        val raw = (docs["settings/main"]?.copy() as? Obj) ?: Obj(); raw["foodPct"] = J.N(pct.toDouble()); raw["foodMin"] = J.N(min); docs["settings/main"] = raw
        return Core.monthCalc(ym, Docs.toStore(docs, t), t)
    }

    fun saveFood(pct: Int, min: Double) = change("Food budget saved", listOf("settings/main")) { saveSettings(J.obj("foodPct" to pct, "foodMin" to min)) }

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
    /**
     * Start update: downloads and checks the new version, then installs it. The download carries on if the
     * person leaves the app; then a "Ready to install" notification brings them back (Android only shows its
     * install prompt while the app is on screen).
     */
    fun startUpdate(r: looniemate.app.update.Updater.Release) { la.scope.launch {
        updater.download(r)
        val s = updater.state.value
        if (s is looniemate.app.update.Updater.State.Ready) {
            if (la.foreground) updater.install(s.r, s.file) else looniemate.app.notify.UpdateCheck.notifyReady(getApplication(), s.r)
        }
    } }
    fun downloadUpdate(r: looniemate.app.update.Updater.Release) = startUpdate(r)

    /** opened from a notification: show the update, and install right away if it was "Ready to install" */
    fun openedForUpdate(install: Boolean) {
        val s = updater.state.value
        if (install && s is looniemate.app.update.Updater.State.Ready) updater.install(s.r, s.file)
        else if (s is looniemate.app.update.Updater.State.Idle || s is looniemate.app.update.Updater.State.UpToDate || s is looniemate.app.update.Updater.State.Failed) checkUpdates(true)
    }

    /* the first check happens in MainActivity.onResume, which also runs on a fresh start */

    companion object { fun groupLabel(g: String) = Labels.GROUPS[g]?.label ?: Labels.OGROUPS[g] ?: "Adults"
        fun nextPaydays(s: J, ym: String): List<String> = (Core.mainPaydays(s, ymAdd(ym, 1)) + Core.mainPaydays(s, ymAdd(ym, 2))).take(3)
        fun firstOfNext(ym: String) = ymAdd(ym, 1) + "-01"
        fun plusDays(d: String, n: Int) = addDays(d, n) }
}
