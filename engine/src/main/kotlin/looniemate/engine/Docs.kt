package looniemate.engine

/**
 * Saved documents, keyed by path exactly like the web version and its backups:
 *   settings/main, bills/<id>, months/<ym>, months/<ym>/expenses/<id>, checkins/<date>, meta/tombstones
 */
object Docs {
    /** The defaults the web version fills in (keep in step with DEFAULTS in web/index.html). */
    val DEFAULTS: Obj get() = J.obj(
        "householdName" to "", "people" to emptyList<String>(), "ccbEmergencyPct" to 0, "accounts" to emptyList<Any>(), "incomes" to emptyList<Any>(),
        "credits" to emptyList<Any>(), "funds" to emptyList<Any>(), "foodPct" to 40, "foodMin" to 400, "emergencyStart" to 0, "childStart" to 0,
        "emergencyGoal" to 2000, "babyDue" to "", "upcomingDays" to 10, "incomeExample" to false)

    private val BILL = Regex("^bills/([^/]+)$")
    private val MONTH = Regex("^months/(\\d{4}-\\d{2})$")
    private val EXP = Regex("^months/(\\d{4}-\\d{2})/expenses/([^/]+)$")
    private val CHECKIN = Regex("^checkins/([^/]+)$")

    /** Settings with defaults filled in, like the web version's `settings()`. */
    fun settings(raw: J?, today: String): Obj {
        val s = DEFAULTS
        (raw as? Obj)?.map?.forEach { (k, v) -> s[k] = v.copy() }
        if (!isYm(s["startYm"])) s["startYm"] = J.S(ymOf(today))
        if (!isDate(s["trackFrom"])) s["trackFrom"] = J.S(today)
        return s
    }

    /**
     * Builds the engine's input. Every month from the first saved one through a year ahead
     * counts as loaded, so one-time expenses and locked months always see their spending.
     */
    fun toStore(docs: Map<String, J>, today: String, allMonthsLoaded: Boolean = true): Store {
        val bills = LinkedHashMap<String, Obj>(); val months = LinkedHashMap<String, Obj>(); val exp = LinkedHashMap<String, LinkedHashMap<String, Obj>>()
        for ((k, v) in docs) {
            val o = v as? Obj ?: continue
            BILL.find(k)?.let { bills[it.groupValues[1]] = o; return@let } ?: MONTH.find(k)?.let { months[it.groupValues[1]] = o } ?: EXP.find(k)?.let { m ->
                exp.getOrPut(m.groupValues[1]) { LinkedHashMap() }[m.groupValues[2]] = o }
        }
        if (allMonthsLoaded) {
            val first = (months.keys + exp.keys + ymOf(today)).minOrNull() ?: ymOf(today)
            var ym = first; val last = ymAdd(ymOf(today), 13)
            while (ym <= last) { exp.getOrPut(ym) { LinkedHashMap() }; ym = ymAdd(ym, 1) }
        }
        return Store(settings(docs["settings/main"], today), bills, months, exp)
    }

    fun checkins(docs: Map<String, J>): List<Obj> = docs.entries.filter { CHECKIN.matches(it.key) }.mapNotNull { it.value as? Obj }
        .filter { isDate(it["date"]) }.sortedByDescending { it.s("date") }

    /** Every expense saved anywhere, with its id and month (the web version's `allExpenses`). */
    fun allExpenses(docs: Map<String, J>): List<Obj> = docs.entries.mapNotNull { (k, v) ->
        val m = EXP.find(k) ?: return@mapNotNull null; val o = v as? Obj ?: return@mapNotNull null
        if (!isDate(o["date"])) return@mapNotNull null
        J.obj("id" to m.groupValues[2], "ym" to m.groupValues[1]).also { x -> o.map.forEach { (kk, vv) -> x[kk] = vv } }
    }
}
