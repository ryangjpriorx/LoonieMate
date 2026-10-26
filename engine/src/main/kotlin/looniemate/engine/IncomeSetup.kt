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
    val ROLES = listOf("rent" to "Covers rent first", "main" to "Main paycheque (bills and food are split across these paydays)", "other" to "Extra income")
    /** The kinds of income besides assistance (keep in step with INCOME_KINDS in web/index.html). */
    val KINDS = listOf("pay" to "Pay from a job", "ei" to "Employment Insurance (EI)", "other" to "Other income (support payments, a pension, gifts)")
    /** Passed as the program when adding EI. */
    const val EI = "ei"

    /** The kind of a saved income; incomes saved before kinds existed are pay unless assistance counted them in full or not at all. */
    fun kindOf(src: J): String = src.s("kind").ifEmpty { when (src.s("odsp")) { "full" -> "other"; else -> "pay" } }

    /** Which pay lowers which payment, as a plain sentence with real months ("Pay from September lowers the November payment."). */
    fun ruleText(p: Core.Program, today: String): String {
        val b = ymAdd(ymOf(today), 1); val e = ymAdd(b, -p.lag)
        return if (p.from16) "Pay from ${Fmt.dShort(ymAdd(e, -1) + "-16")} to ${Fmt.dShort("$e-15")} lowers the ${Fmt.monthOnly(b)} payment, and so on each month."
        else "Pay from ${Fmt.monthOnly(e)} lowers the ${Fmt.monthOnly(b)} payment, and so on each month."
    }

    /** How the program treats income, in a few words. */
    fun countsText(p: Core.Program): String = when (p.rule) {
        "odsp" -> "Work pay: the first \$200 a month isn't counted, then ODSP takes 50% of the rest. For someone on ODSP for a disability it's the first \$1,000, then 75%. ODSP adds \$100 a month for each adult who works. EI, CPP and spousal support come off dollar for dollar; child support and child benefits don't count."
        "ow" -> "Work pay: each adult keeps the first \$200 a month, then OW takes 50% of the rest (after 3 months in a row on assistance; before that it counts in full). EI and most other income come off dollar for dollar."
        "qc" -> "Work income: \$200 a month for an adult alone, or \$300 for a couple, isn't counted. Above that, the benefit drops by the difference, with 25% added back. EI and other income come off dollar for dollar."
        "custom" -> "Quebec didn't publish the exact figures for work income. Enter the rule from your notice below. EI and other income come off dollar for dollar."
        "ns" -> "Work pay on a sliding scale: the first \$350 of net pay each month is kept, then 75% of pay up to \$500, 50% up to \$750, and 25% above that. EI and other chargeable income come off dollar for dollar."
        else -> ""
    }
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
    fun newIncome(list: List<Obj>, id: String): Obj = J.obj("id" to id, "name" to "New income", "kind" to "pay", "amount" to 0, "sched" to J.obj("type" to "monthly", "day" to 1), "account" to "",
        "role" to (if (list.any { it.s("role") == "main" }) "other" else "main"))

    /** New Employment Insurance: every 2 weeks, counted in full by assistance, reported every 2 weeks. */
    fun newEi(list: List<Obj>, id: String, today: String): Obj = J.obj("id" to id, "name" to "EI", "kind" to "ei", "odsp" to "full", "amount" to 0, "sched" to schedFor("biweekly", today), "account" to "",
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
