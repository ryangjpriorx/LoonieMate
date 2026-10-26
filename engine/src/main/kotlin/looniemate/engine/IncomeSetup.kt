package looniemate.engine

/*
 * Setting up income: paycheques, social assistance and government benefits, kept in
 * settings.incomes and settings.credits exactly as the web version's Income tab keeps them.
 * Any field this code doesn't know about is left as it is.
 */
object IncomeSetup {
    /** Keep in step with INCOME_TYPES in web/index.html ("odsp" is only used by assistance). */
    val TYPES = listOf("biweekly" to "Every 2 weeks", "weekly" to "Every week", "semimonthly" to "Twice a month (15th and last day)", "monthly" to "Monthly on a set day",
        "lastbiz" to "Last business day of the month", "firstbiz" to "First business day of the month")
    /** Keep in step with ROLES in web/index.html. */
    val ROLES = listOf("rent" to "Covers rent first", "main" to "Main paycheck (bills and food are split across these paydays)", "other" to "Extra income")
    /** How assistance counts an income (keep in step with ODSP_T). */
    val COUNTS = listOf("" to "Not counted", "dis" to "Work pay of a person with a disability (ODSP)", "spouse" to "Work pay", "full" to "Counted in full (EI, CPP, spousal support)")
    val WEEKDAYS = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    const val ODSP_DIRECTIVE = "https://ontario.ca/document/ontario-disability-support-program-policy-directives-income-support/53-deductions"

    fun typeLabel(t: String) = (TYPES.find { it.first == t }?.second ?: if (t == "odsp") "ODSP / Ontario Works schedule" else "").substringBefore(" (")
    fun roleLabel(r: String) = (ROLES.find { it.first == r } ?: ROLES[2]).second.substringBefore(" (")

    /** Day of the week the way JavaScript counts it: 0 is Sunday. */
    fun weekday(d: String): Int = parseDate(d).dayOfWeek.value % 7

    /** The assistance programs to offer: the family's province, or all of them when "show all provinces" is on. */
    fun programs(s: J): List<Core.Program> = Core.ASSIST.values.filter { s.t("showAllProv") || it.prov == s.or("province", "ON") }
    /** The benefits to offer, the same way. */
    fun creditTypes(s: J): List<Core.CreditType> = Core.CREDIT_TYPES.values.filter { s.t("showAllProv") || it.prov == null || it.prov == s.or("province", "ON") }

    fun hasAssist(s: J) = Core.incomesOf(s).any { Core.assistOf(it) != null }

    /** The income report reminder: {on, day}, with the program's usual day when it has one (the web version's reportCfg). */
    fun reportCfg(src: J): Obj {
        (src["report"] as? Obj)?.let { return it }
        val p = Core.assistOf(src)?.let { Core.ASSIST[it] }
        return if (p != null && p.report > 0) J.obj("on" to true, "day" to p.report) else J.obj("on" to false, "day" to 7)
    }

    /** A new paycheque, as the web version adds one. */
    fun newIncome(list: List<Obj>, id: String): Obj = J.obj("id" to id, "name" to "New income", "amount" to 0, "sched" to J.obj("type" to "monthly", "day" to 1), "account" to "",
        "role" to (if (list.any { it.s("role") == "main" }) "other" else "main"))

    /** New social assistance, as the web version adds it. */
    fun newAssist(list: List<Obj>, id: String, k: String): Obj { val p = Core.ASSIST.getValue(k)
        return J.obj("id" to id, "name" to p.short, "amount" to 0, "assist" to k, "sched" to J.obj("type" to p.sched.s("type")), "account" to "",
            "role" to (if (list.any { it.s("role") == "rent" }) "other" else "rent"), "ded" to Obj()) }

    /** A new benefit, as the web version adds it. */
    fun newCredit(id: String, type: String): Obj = J.obj("id" to id, "type" to type, "amount" to 0, "account" to "", "protect" to (Core.CREDIT_TYPES[type]?.protect ?: false),
        "name" to (if (type == "other") "Other credit" else ""))

    /** The schedule after picking how often it's paid, with the web version's defaults (day 1, or the next Monday). */
    fun schedFor(type: String, today: String): Obj {
        val o = J.obj("type" to type)
        if (type == "monthly") o["day"] = J.N(1.0)
        if (type == "weekly" || type == "biweekly") { var d = today; while (weekday(d) != 1) d = addDays(d, 1); o["anchor"] = J.S(d) }
        return o
    }

    /** Moves a weekly or every-2-weeks schedule to another day of the week (the first such day on or after its current payday). */
    fun withWeekday(sched: Obj, want: Int, today: String): Obj {
        val o = sched.copy() as Obj; var d = if (isDate(sched["anchor"])) sched.s("anchor") else today
        while (weekday(d) != want) d = addDays(d, 1); o["anchor"] = J.S(d); return o
    }

    /** The next few paydays from today. */
    fun nextDates(sched: J?, today: String, n: Int = 3, startYm: String? = null): List<String> {
        val out = mutableListOf<String>(); var k = 0
        while (k < 12 && out.size < n) { val ym = ymAdd(ymOf(today), k); if (startYm == null || !isYm(startYm) || ym >= startYm) out += Core.schedDates(sched, ym).filter { it >= today }; k++ }
        return out.take(n)
    }
}
