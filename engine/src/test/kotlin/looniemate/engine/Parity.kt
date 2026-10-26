package looniemate.engine

import java.io.File

/**
 * Compares the Kotlin engine with the web version's results in parity/fixtures.json
 * (made by parity/gen.js). Returns one line per difference; an empty list means they match.
 */
object Parity {
    private const val TOL = 1e-6

    fun run(fixtures: File, maxDiffs: Int = 60): List<String> {
        val fx = Json.parse(fixtures.readText())
        val diffs = mutableListOf<String>()
        fun cmp(label: String, js: J?, kt: J?) { if (diffs.size < maxDiffs) compare(js, kt, label, diffs, maxDiffs) }

        cmp("defaults", fx["defaults"], Docs.DEFAULTS)

        for (sc in fx["scenarios"].arr()) {
            val name = sc.s("name"); val docs = (sc["docs"] as Obj).map
            for (today in sc.strList("todays")) {
                val r = sc["results"]!![today]!!; val st = Docs.toStore(docs, today, allMonthsLoaded = false); val cur = ymOf(today); val p = "$name@$today"
                for ((ym, v) in (r["months"] as Obj).map) cmp("$p monthCalc $ym", v, Core.monthCalc(ym, st, today))
                cmp("$p upcoming", r["upcoming"], J.of(Core.upcoming(st, today, 10) { e -> if (e.t("note")) e.s("note") else "One-time expense" }))
                cmp("$p food", r["food"], Core.foodPeriod(Core.monthCalc(cur, st, today), today))
                cmp("$p moves", r["moves"], J.of(Core.moneyMoves(st, addDays(today, -10), addDays(today, 40))))
                val sv = Core.savingsState(st); cmp("$p savings", r["savings"], J.obj("emergency" to sv.emergency, "child" to sv.child, "funds" to sv.funds))
                val tg = Core.targetsThrough(st, cur, today); cmp("$p targets", r["targets"], J.obj("emergency" to tg.emergency, "child" to tg.child, "childSoFar" to tg.childSoFar))
                cmp("$p projection", r["projection"], J.of(Core.projection(st, today, cur, 6)))
                for ((id, v) in (r["debts"] as Obj).map) cmp("$p debt $id", v, Core.debtInfo(st, id, today) ?: J.Null)
                cmp("$p events", r["events"], J.of(Core.billEvents(st, ymAdd(cur, -1) + "-01", dayIn(ymAdd(cur, 1), 31))))
                cmp("$p paydays", r["paydays"], J.of(Core.mainPaydays(st.settings, cur)))
                cmp("$p funds", r["funds"], J.of(Core.fundsOf(st.settings)))
                r["runway"]?.let { rw ->
                    val bal = Core.accountsOf(st.settings).mapIndexed { i, a -> a.s("id") to (if (i % 2 != 0) 900 + i * 13.37 else 25.5 * (i + 1)) }.toMap()
                    cmp("$p runway", rw, Core.runway(st, today, bal)) }
                r["checkin"]?.let { ck ->
                    val ex = Checkins.expectedSince(st, Docs.allExpenses(docs), addDays(today, -12), 1500.25, today)
                    cmp("$p checkin expected", ck["expected"], J.obj("expected" to ex.expected, "parts" to J.obj("income" to ex.income, "bills" to ex.bills, "spent" to ex.spent, "savings" to ex.savings)))
                    val bal = Core.accountsOf(st.settings).mapIndexed { i, a -> a.s("id") to (if (i % 2 != 0) 900 + i * 13.37 else 25.5 * (i + 1)) }.toMap()
                    cmp("$p checkin coverage", ck["coverage"], J.of(Checkins.coverage(st, bal, today)))
                }
                for ((ym, v) in (r["status"] as Obj).map) { val c = Core.monthCalc(ym, st, today)
                    val stt = Views.status(c, Core.upcoming(st, today, 10), Views.phaseOf(ym, today, st.settings), today, st.settings)
                    cmp("$p status $ym", v, J.obj("tone" to stt.tone, "label" to stt.label, "pts" to stt.points)) }
            }
        }
        for ((k, v) in (fx["holidays"] as Obj).map) {
            val y = k.substring(0, 4).toInt(); val prov = k.substring(4)
            cmp("holidays $k", v, J.of(Core.holidaysOf(y, prov).map { J.obj("date" to it.date, "name" to it.name, "bank" to it.bank) }))
        }
        for (x in fx["sched"].arr()) {
            val sch = x["sch"]; val got = J.O()
            for (ym in (x["dates"] as Obj).map.keys.filter { isYm(it) }) { got[ym] = J.of(Core.schedDates(sch, ym)); if (Core.schedEstimated(sch, ym)) got["$ym?"] = J.B(true) }
            cmp("sched ${Json.stringify(sch)}", x["dates"], got)
        }
        val ds = J.obj("babyDue" to "2027-02-10")
        for (x in fx["bills"].arr()) {
            val b = x["b"]
            cmp("billDates ${Json.stringify(b)}", x["dates"], J.of(Core.billDates(b, "2026-11-01", "2028-03-31", ds)))
            val plans = listOf("2026-11", "2027-01", "2027-02", "2027-12").map { ym -> Core.billPlanForMonth(b, ym, ds).let { pl ->
                J.obj("amount" to pl.amount, "dates" to pl.dates).also { if (pl.setAside) it["setAside"] = J.B(true) } } }
            cmp("billPlan ${Json.stringify(b)}", x["plan"], J.of(plans))
        }
        for (x in fx["programs"].arr()) { val c = x.n("c"); val inc = x["inc"]
            val r = SavingsViews.rdspMoney(c, inc); val e = SavingsViews.respMoney(c, inc)
            cmp("rdspMoney ${Json.stringify(x["c"])} ${Json.stringify(inc)}", x["rdsp"], J.obj("grant" to r.grant, "bond" to r.bond))
            cmp("respMoney ${Json.stringify(x["c"])} ${Json.stringify(inc)}", x["resp"], J.obj("grant" to e.grant, "known" to e.known)) }
        fx["backup"]?.let { bk ->
            val t0 = System.currentTimeMillis()
            val (plain, _) = Backup.decrypt(bk.s("text"), bk.s("password")); cmp("backup made by the web version", bk["plain"], plain)
            println("  opened the web version's backup in ${System.currentTimeMillis() - t0} ms")
            try { Backup.decrypt(bk.s("text"), "not the password"); diffs.add("backup: a wrong password was accepted") }
            catch (e: Backup.BackupException) { if (e.problem != Backup.Problem.PASSWORD) diffs.add("backup: wrong password gave ${e.problem}") }
            try { Backup.decrypt("{\"hello\":1}", "x"); diffs.add("backup: a non-backup file was accepted") } catch (e: Backup.BackupException) { if (e.problem != Backup.Problem.NOT_BACKUP) diffs.add("backup: non-backup gave ${e.problem}") }
            val k = Backup.deriveKey("pw ✓", Backup.newSalt(), 1000)
            val (again, _) = Backup.decrypt(Backup.encrypt(bk["plain"]!!, k), "pw ✓"); cmp("backup round trip", bk["plain"], again)
        }
        fx["merge"].arr().forEachIndexed { i, m ->
            val r = Backup.merge((m["a"] as Obj).map, (m["b"] as Obj).map)
            cmp("merge #$i", m["result"], J.obj("data" to J.O(r.data), "stats" to J.obj("added" to r.added, "updated" to r.updated, "removed" to r.removed)))
        }
        return diffs
    }

    private fun isNullish(v: J?) = v == null || v == J.Null

    fun compare(js: J?, kt: J?, path: String, out: MutableList<String>, max: Int = 60) {
        if (out.size >= max) return
        if (isNullish(js) && isNullish(kt)) return
        when (js) {
            is J.N -> { val k = kt as? J.N; if (k == null || Math.abs(js.v - k.v) > TOL) out.add("$path: web ${Json.stringify(js)} ≠ app ${Json.stringify(kt)}") }
            is J.O -> { val k = kt as? J.O ?: return run { out.add("$path: web object ≠ app ${Json.stringify(kt).take(80)}") }
                for ((key, v) in js.map) compare(v, k.map[key], "$path.$key", out, max)
                for ((key, v) in k.map) if (!js.map.containsKey(key) && !isNullish(v)) out.add("$path.$key: only in app (${Json.stringify(v).take(60)})") }
            is J.A -> { val k = kt as? J.A ?: return run { out.add("$path: web list ≠ app ${Json.stringify(kt).take(80)}") }
                if (js.list.size != k.list.size) { out.add("$path: web has ${js.list.size} items, app has ${k.list.size}\n   web ${Json.stringify(js).take(400)}\n   app ${Json.stringify(k).take(400)}"); return }
                js.list.forEachIndexed { i, v -> compare(v, k.list[i], "$path[$i]", out, max) } }
            else -> if (Json.stringify(js) != Json.stringify(kt ?: J.Null)) out.add("$path: web ${Json.stringify(js).take(120)} ≠ app ${Json.stringify(kt).take(120)}")
        }
    }

    @JvmStatic fun main(args: Array<String>) {
        val f = File(args.getOrNull(0) ?: "parity/fixtures.json")
        val t0 = System.currentTimeMillis(); val d = run(f)
        if (d.isEmpty()) println("Parity: the app engine matches the web version exactly (${System.currentTimeMillis() - t0} ms).")
        else { println("Parity: ${d.size} difference(s):"); d.forEach { println(" - $it") }; kotlin.system.exitProcess(1) }
    }
}
