package looniemate.engine

/*
 * Balance checks: the family types in what each account holds, and LoonieMate compares that with
 * what the plan expected. A line-by-line port of the web version's expectedSince() and coverage(),
 * checked against them by the parity tests. Saved as checkins/<date>: {date, bal, day, sav, expected, gap}.
 */
object Checkins {
    class Expected(val expected: Double, val income: Double, val bills: Double, val spent: Double, val savings: Double)

    /** What the day-to-day accounts should hold today, starting from the last check-in (its date and its day-to-day total). */
    fun expectedSince(st: Store, expenses: List<Obj>, prevDate: String, prevDay: Double, today: String): Expected {
        val from = addDays(prevDate, 1)
        if (from > today) return Expected(prevDay, 0.0, 0.0, 0.0, 0.0)
        var income = 0.0
        var ym = ymOf(from)
        while (ym <= ymOf(today)) {
            val c = Core.monthCalc(ym, st, today)
            for (inc in c["incomes"].arr()) for (d in inc.strList("dates")) if (d >= from && d <= today) income += num(inc["amount"])
            for (cr in c["crAll"].arr()) for (d in cr.strList("dates")) if (d >= from && d <= today) income += num(cr["amount"])
            ym = ymAdd(ym, 1)
        }
        val bills = sumOf2(Core.billEvents(st, from, today).filter { !it.t("fund") }.map { it.n("amount") })
        val spent = sumOf2(expenses.filter { it.s("date") >= from && it.s("date") <= today }.map { it.n("amount") })
        var savings = 0.0
        for (m in Core.moneyMoves(st, from, today)) if (m.t("sav") && m.t("done")) savings += if (m.s("sav").endsWith("-out")) m.n("amount") else -m.n("amount")
        income = round2(income); savings = round2(savings)
        return Expected(round2(prevDay + income - bills - spent + savings), income, bills, spent, savings)
    }

    /**
     * Does each day-to-day account hold enough for what comes out of it before the next payday?
     * One entry per account: {id, name, need, have, short, bills, food, income, isMain, until}.
     */
    fun coverage(st: Store, bal: Map<String, Double>, today: String): List<Obj> {
        val s = st.settings
        val main = Core.acctFor(s, "main")?.s("id"); val every = Core.acctFor(s, "everyday")?.s("id")
        val pays = Core.mainPaydays(s, ymOf(today)) + Core.mainPaydays(s, ymAdd(ymOf(today), 1)) + Core.mainPaydays(s, ymAdd(ymOf(today), 2))
        val nextPay = pays.find { it > addDays(today, 2) } ?: addDays(today, 14); val end = addDays(nextPay, -1)
        val cashId = Core.accountsOf(s).find { it.s("type") == "cash" }?.s("id")?.ifEmpty { null }
        val ev = Core.billEvents(st, addDays(today, 1), end).filter { !it.t("fund") }
            .map { e -> (e.copy() as Obj).also { if (e.s("method") == "Cash" && cashId != null) it["acct"] = J.S(cashId) } }
        val mv = Core.moneyMoves(st, today, end).filter { !it.t("done") }
        /* money landing after today, by account */
        val inc = HashMap<String, Double>()
        fun addIn(ref: String, d: String, amt: J?) { if (d > today && d <= end) { val id = Core.acctId(s, ref).ifEmpty { main ?: "" }; inc[id] = (inc[id] ?: 0.0) + num(amt) } }
        var ym = ymOf(today)
        while (ym <= ymOf(end)) {
            val c = Core.monthCalc(ym, st, today)
            c["incomes"].arr().forEach { x -> x.strList("dates").forEach { d -> addIn(x.s("account"), d, x["amount"]) } }
            c["crAll"].arr().forEach { x -> x.strList("dates").forEach { d -> addIn(x.s("account"), d, x["amount"]) } }
            ym = ymAdd(ym, 1)
        }
        val fp = Core.foodPeriod(Core.monthCalc(ymOf(today), st, today), today)
        return Core.accountsOf(s).filter { it.s("type") != "savings" }.map { a ->
            val id = a.s("id"); val name = a.s("name")
            val bills = sumOf2(ev.filter { it.s("acct") == id }.map { it.n("amount") })
            val outs = sumOf2(mv.filter { it.s("from") == name }.map { it.n("amount") }); val ins = sumOf2(mv.filter { it.s("to") == name }.map { it.n("amount") })
            val food = if (id == every) maxOf(0.0, fp.n("left")) else 0.0; val income = round2(inc[id] ?: 0.0)
            val need = round2(maxOf(0.0, bills + outs + food - ins - income)); val have = round2(bal[id] ?: 0.0)
            J.obj("id" to id, "name" to name, "need" to need, "have" to have, "short" to round2(maxOf(0.0, need - have)), "bills" to bills, "food" to food,
                "income" to income, "isMain" to (id == main), "until" to nextPay)
        }
    }

    /** The most recent saved check-in before [today], which the new one is compared with. */
    fun previous(docs: Map<String, J>, today: String): Obj? = Docs.checkins(docs).firstOrNull { it.s("date") < today }
}
