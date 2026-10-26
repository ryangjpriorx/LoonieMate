package looniemate.engine

/*
 * Things a family is behind on, saved as data both versions already read (see docs/plans/getting-on-track.md):
 *  - pay it all on a date  → a planned one-time expense on that date (months/<ym>/expenses/<id>)
 *  - pay it off bit by bit → a bill with a statement balance (debt) that ends on its payoff date (bills/<id>)
 *  - not sure yet          → an entry in settings.catchUp, counted as owed today by runway()
 * Each is marked catchUp: true so the app can show it as a catch-up item; older copies ignore the mark.
 */
object CatchUp {
    fun oneTime(name: String, amount: Double, date: String, group: String = "household"): Obj =
        J.obj("date" to date, "amount" to round2(amount), "category" to "oneoff", "note" to name.trim(), "oneTime" to true, "group" to group,
            "created" to System.currentTimeMillis(), "catchUp" to true)

    /** A payment-plan bill: [payment] every month on [day] until [owed] is paid off. The end date comes from the debt engine. */
    fun plan(docs: Map<String, J>, today: String, name: String, owed: Double, payment: Double, day: Int, account: String, method: String): Obj {
        val b = J.obj("name" to name.trim(), "group" to "adults", "category" to "other", "amount" to round2(payment), "freq" to "monthly", "day" to day,
            "account" to account, "method" to method, "active" to true, "essential" to true, "deferrable" to false,
            "debt" to J.obj("mode" to "balance", "owed" to round2(owed), "asOf" to today, "rate" to 0), "catchUp" to true, "updated" to System.currentTimeMillis())
        val tmp = LinkedHashMap(docs); tmp["bills/__catchup"] = b
        Core.debtInfo(Docs.toStore(tmp, today), "__catchup", today)?.s("finish")?.takeIf { isDate(it) }?.let { b["end"] = J.S(it) }
        return b
    }

    /** settings.catchUp with one more undecided item. */
    fun unsure(s: J, id: String, name: String, amount: Double, today: String): List<J> =
        s["catchUp"].arr().map { it.copy() } + J.obj("id" to id, "name" to name.trim(), "amount" to round2(amount), "added" to today)

    fun undecided(s: J): List<Obj> = s["catchUp"].arr().filterIsInstance<Obj>().filter { it.t("id") }
}
