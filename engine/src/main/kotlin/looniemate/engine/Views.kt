package looniemate.engine

/* What the screens show, worked out the same way as the web version. Kept free of Android code
   so it can be tested on its own. */

object Labels {
    val SPEND = listOf("groceries" to "Groceries", "takeout" to "Takeout & snacks", "personal" to "Personal care", "household" to "Household", "baby" to "Baby extras",
        "pet" to "Pet extras", "transport" to "Transportation", "health" to "Pharmacy & health", "clothing" to "Clothing", "fun" to "Gifts & fun", "other" to "Other")
    val SPENDL: Map<String, String> = SPEND.toMap() + ("oneoff" to "One-time extras")
    class Group(val label: String, val desc: String)
    val GROUPS = linkedMapOf("adults" to Group("Adults", "Rent, utilities, phones, loans and subscriptions"),
        "baby" to Group("Kids", "Diapers, wipes, school, childcare and other costs for the kids"), "pets" to Group("Pets", "Food, litter, vet and prevention"))
    val OGROUPS = linkedMapOf("adults" to "Adults", "baby" to "Kids", "pets" to "Pets", "household" to "Household")
    val BILLCATS = mapOf(
        "adults" to listOf("housing" to "Rent & housing", "utilities" to "Utilities", "phone" to "Phone & internet", "insurance" to "Insurance", "loan" to "Loan payment",
            "subscription" to "Subscription", "transport" to "Transportation", "health" to "Health", "other" to "Other"),
        "baby" to listOf("diapers" to "Diapers & wipes", "skin" to "Skin & bath care", "nursing" to "Nursing supplies", "childcare" to "Childcare", "school" to "School",
            "clothing" to "Clothing", "health" to "Health", "other" to "Other"),
        "pets" to listOf("petfood" to "Food", "litter" to "Litter & supplies", "vet" to "Vet & meds", "insurance" to "Insurance", "other" to "Other"))
    val CATL: Map<String, String> = BILLCATS.values.flatten().toMap()
    val METHODS = listOf("Pre-authorized debit (PAD)", "Interac e-Transfer", "Debit card", "Cash", "Cheque")
    val FREQS = listOf("monthly" to "Monthly", "biweekly" to "Every 2 weeks", "weekly" to "Weekly", "quarterly" to "Every 3 months", "yearly" to "Yearly")
    fun shortMethod(m: String) = when (m) { "Pre-authorized debit (PAD)" -> "PAD"; "Interac e-Transfer" -> "e-Transfer"; else -> m }
    fun expLabel(e: J): String = if (e.t("oneTime")) e.or("note", "One-time expense") else SPENDL[e.s("category")] ?: "Other"
}

object Views {
    fun money(n: Double) = Fmt.money(n)
    private fun neg(n: Double) = if (n > 0) "−" + Fmt.money(n) else Fmt.money(0.0)

    fun phaseOf(ym: String, today: String, s: J): String {
        val cur = ymOf(today); if (ym == cur) return "now"; if (ym < cur) return "past"
        if (ym == s.s("startYm") && cur < s.s("startYm")) return "now"; return "future"
    }

    class Status(val tone: String, val label: String, val points: List<String>)

    /** The status badge and its reasons (the web version's getStatus). */
    fun status(c: J, up: List<Obj>, phase: String, today: String, s: J): Status {
        val pts = mutableListOf<String>(); var tone = "good"
        fun bump(t: String) { if (t == "bad" || (t == "warn" && tone == "good")) tone = t }
        val net = c.n("net")
        if (phase == "past") return Status("plan", if (c.t("closed")) "Locked month" else "Finished month", listOf(if (net < 0) "Ended ${money(-net)} short." else "Ended with ${money(net)} left over for savings."))
        if (phase == "future" && c.s("ym") != s.s("startYm")) {
            if (net < 0) return Status("bad", "Plan needs work", listOf("Bills and food add up to ${money(-net)} more than the income for ${Fmt.monthOnly(c.s("ym"))}."))
            return Status("plan", "Plan looks good", listOf("About ${money(c.n("target"))} should go to Emergency Savings in ${Fmt.monthOnly(c.s("ym"))}."))
        }
        val overdue = up.filter { it.s("kind") == "bill" && it.t("overdue") }
        val soon = up.filter { it.s("kind") == "bill" && !it.t("paid") && !it.t("overdue") && diffDays(today, it.s("date")) <= 3 }
        if (net < 0) { bump("bad"); pts.add("This month is ${money(-net)} short. Cover it from Emergency Savings first. Use the Child Reserve only as a last resort.") }
        if (overdue.isNotEmpty()) { bump("bad"); pts.add("${overdue.size} bill${if (overdue.size > 1) "s are" else " is"} past due (${money(sumOf2(overdue.map { it.n("amount") }))}). Pay ${if (overdue.size > 1) "them" else "it"}, then tap Paid.") }
        if (ymOf(today) == c.s("ym")) { val fp = Core.foodPeriod(c, today)
            if (fp.n("spent") > fp.n("allow")) { bump("warn"); pts.add("Food is ${money(fp.n("spent") - fp.n("allow"))} over for this half of the month.") }
            else if (fp.n("spent") > fp.n("expected") + 10) { bump("warn"); pts.add("Food spending is ahead of pace. Aim for ${money(fp.n("perDay"))} a day until ${Fmt.dShort(fp.s("end"))}.") }
            else pts.add("Food: ${money(fp.n("left"))} left until ${Fmt.dShort(fp.s("end"))} (${money(fp.n("perDay"))} a day).") }
        val mvLate = up.filter { it.s("kind") == "move" && it.t("overdue") }; val mvToday = up.filter { it.s("kind") == "move" && !it.t("done") && it.s("date") == today }
        if (mvLate.isNotEmpty()) { bump("warn"); pts.add("${mvLate.size} money move${if (mvLate.size > 1) "s" else ""} still to do from earlier days. See Coming up.") }
        if (mvToday.isNotEmpty()) pts.add("${mvToday.size} money move${if (mvToday.size > 1) "s" else ""} to do today (${money(sumOf2(mvToday.map { it.n("amount") }))}).")
        val deferredN = c["billLines"].arr().count { it.t("deferredTo") }
        if (c.n("deferredTotal") != 0.0) pts.add("Deferring $deferredN bill${if (deferredN > 1) "s" else ""} to later (${money(c.n("deferredTotal"))}).")
        if (c.n("skippedTotal") != 0.0) pts.add("Giving up ${money(c.n("skippedTotal"))} of non-essential bills this month.")
        if (soon.isNotEmpty()) { bump("warn"); pts.add("${soon.size} bill${if (soon.size > 1) "s" else ""} due in the next 3 days (${money(sumOf2(soon.map { it.n("amount") }))}).") }
        if (tone == "good" && pts.isEmpty()) pts.add("Bills are covered and the plan balances.")
        if (tone == "good" && c.n("liveSurplus") >= 0) pts.add(0, "Everything is covered. ${money(maxOf(0.0, c.n("liveSurplus")))} is on track for savings this month.")
        return Status(tone, mapOf("good" to "All good · On track", "warn" to "Heads up", "bad" to "Needs attention")[tone]!!, pts)
    }

    fun freqText(b: J, s: J, today: String): String {
        val f = if (b.t("freq")) b.s("freq") else "monthly"
        if (f == "monthly") return "Monthly on the " + Fmt.ord(maxOf(1, minOf(31, b.n("day").toInt().let { if (it == 0) 1 else it })))
        val next = Core.billDates(b, today, addDays(today, 400), s).firstOrNull()
        val lbl = mapOf("biweekly" to "Every 2 weeks", "weekly" to "Weekly", "quarterly" to "Every 3 months", "yearly" to "Yearly")[f] ?: f
        return lbl + (if (next != null) " · next " + Fmt.dShort(next) else "")
    }

    class Quick(val category: String, val amount: Double, val note: String, val fav: Boolean) { val label get() = note.ifEmpty { Labels.SPENDL[category] ?: "Spending" } }
    /** Saved quick buttons plus suggestions from what gets logged often. */
    fun quickButtons(s: J, allExpenses: List<Obj>, today: String): List<Quick> {
        val fav = s["quick"].arr().filter { truthy(it) && it.t("category") && it.n("amount") > 0 }.map { Quick(it.s("category"), it.n("amount"), it.s("note"), true) }
        val since = addDays(today, -90); val cnt = LinkedHashMap<String, Int>()
        for (e in allExpenses) if (!e.t("oneTime") && e.s("date") >= since && Labels.SPENDL.containsKey(e.s("category")) && e.s("category") != "oneoff") {
            val k = e.s("category") + "|" + Json.numStr(round2(e.n("amount"))); cnt[k] = (cnt[k] ?: 0) + 1 }
        val sug = cnt.entries.filter { (k, n) -> n >= 2 && fav.none { f -> f.category + "|" + Json.numStr(round2(f.amount)) == k } }
            .sortedByDescending { it.value }.take(maxOf(0, 6 - fav.size)).map { val (c, a) = it.key.split("|"); Quick(c, a.toDouble(), "", false) }
        return fav + sug
    }

    class Link(val text: String, val url: String?)
    class Reminder(val tone: String, val title: String, val body: String, val details: List<Link> = emptyList(), val detailsTitle: String = "",
                   val button: String? = null, val action: String = "", val arg: String = "", val arg2: String = "")

    const val TAX_HELP = "https://www.canada.ca/en/revenue-agency/campaigns/free-tax-help.html"
    const val SIMPLEFILE = "https://www.canada.ca/en/revenue-agency/campaigns/file-my-return"
    const val STUDIOTAX = "https://www.studiotax.com/paymentpage.html"
    const val REPORT_HELP = "https://stepstojustice.ca/steps/income-assistance/5-tell-odsp-about-money-you-make-and-life-changes/"

    /** whether to remind about reporting this income, and the day of the month it's due */
    fun reportCfg(src: J): Pair<Boolean, Int> {
        val r = src["report"]; if (truthy(r)) return r.t("on") to (r.n("day").toInt().takeIf { it > 0 } ?: 7)
        val k = Core.assistOf(src); val rep = k?.let { Core.ASSIST[it]?.report } ?: 0
        return if (rep > 0) true to rep else false to 7
    }

    /** Banners at the top: backup, tax season, and reporting income for social assistance. */
    fun reminders(st: Store, today: String, lastBackupDate: String?): List<Reminder> {
        val out = mutableListOf<Reminder>(); val s = st.settings
        val days = lastBackupDate?.let { diffDays(it.take(10), today) }
        if (days == null || days > 14) out.add(Reminder("warn", if (days != null) "Last backup was $days days ago." else "You haven't made a backup yet.", "Your budget lives only on this phone.", button = "Back up now", action = "backup"))
        val y = today.substring(0, 4).toInt(); val md = today.substring(5)
        if (!s["taxReminder"].isFalse() && md >= "02-01" && md <= "06-30" && !truthy(s["taxFiled"]?.get(y.toString()))) { val late = md > "04-30"
            out.add(Reminder(if (late) "bad" else "warn", if (late) "Haven't filed your ${y - 1} taxes yet? File as soon as you can." else "File your ${y - 1} taxes by April 30.",
                "The CCB, CGEB and provincial benefits depend on it every year, even with no income. A spouse or partner needs to file too.",
                listOf(Link("CRA SimpleFile: free for people with low or no income and simple taxes. File online, or by phone or paper if the CRA sends you an invitation.", SIMPLEFILE),
                    Link("StudioTax: free tax software if your income is under \$20,000 (\$40,000 combined for a couple filing together). It keeps your information on your own computer unless you NETFILE it.", STUDIOTAX),
                    Link("Free tax clinics: volunteers file it for you.", TAX_HELP),
                    Link("The CRA plans to start filing automatically for some people with low income, beginning with 2026 taxes. Until then, you still need to file.", null)),
                "Free ways to file", "We've filed", "tax-filed", y.toString())) }
        val ym = ymOf(today); val prevYm = ymAdd(ym, -1); val mm = st.months[ym]; val cPrev = Core.monthCalc(prevYm, st, today); val dom = today.substring(8, 10).toInt()
        for (src in Core.incomesOf(s)) { val (on, day) = reportCfg(src); if (!on) continue
            val due = minOf(28, maxOf(1, day)); val dueDate = "$ym-${pad(due)}"
            if (truthy(mm?.get("reported")?.get(src.s("id"))) || dom < due - 6 || dueDate < s.s("trackFrom")) continue
            val late = dom > due
            val others = cPrev["incomes"].arr().filter { it.s("id") != src.s("id") && it["dates"].arr().isNotEmpty() }
            out.add(Reminder(if (late) "bad" else "warn", "Report ${Fmt.monthOnly(prevYm)}'s income for ${src.s("name")} ${if (late) "(was due ${Fmt.dShort(dueDate)})" else "by ${Fmt.dShort(dueDate)}"}.",
                if (others.isNotEmpty()) "Money that came in during ${Fmt.monthOnly(prevYm)}: " + others.joinToString("; ") { x -> "${x.s("name")} ${money(x.n("amount") * x["dates"].arr().size)} (${x.strList("dates").joinToString(", ") { Fmt.dShort(it) }})" } + "." else "",
                listOf(Link("A new baby, someone moving in or out, starting or stopping a job, or moving to a new address.", null), Link("More on reporting", REPORT_HELP)),
                "Changes they usually need to hear about", "Reported", "reported", src.s("id"), ym)) }
        return out
    }

    /* ---- debts ---- */
    class Debt(val id: String, val name: String, val info: Obj, val pct: Double, val milestone: Pair<String, String>?, val next: String?, val amount: Double)
    fun debtPct(d: J): Double { val all = d.n("paid") + d.n("remaining"); return if (all > 0) maxOf(0.0, minOf(100.0, d.n("paid") / all * 100)) else 100.0 }
    fun milestone(d: J, p: Double): Pair<String, String>? {
        if (d.n("remaining") <= 0.004) return "good" to "Paid off!"; if (d.t("stuck")) return "bad" to "Not shrinking"; if (!d.t("finish")) return "bad" to "Check end date"
        if (d.n("payments") <= 3) return "good" to "Almost done"; if (p >= 75) return "good" to "¾ of the way"; if (p >= 50) return "good" to "Halfway there"
        if (p >= 25) return "" to "A quarter done"; return null
    }
    fun debts(st: Store, today: String): List<Debt> = st.bills.keys.mapNotNull { id ->
        val d = Core.debtInfo(st, id, today) ?: return@mapNotNull null; val b = st.bills.getValue(id); val p = debtPct(d)
        Debt(id, b.or("name", "Bill"), d, p, milestone(d, p), if (d.n("remaining") > 0) Core.billDates(b, today, addDays(today, 400), st.settings).firstOrNull() else null, b.n("amount"))
    }

    class Line(val label: String, val value: String = "", val total: Boolean = false, val tone: String = "")
    /** The breakdown under a debt, like the web version's debtLines. */
    fun debtLines(d: J): Pair<List<Line>, Line> {
        val lines = mutableListOf<Line>()
        lines.add(Line((if (d.s("mode") == "original") "Owed ${money(d.n("principal"))} on ${Fmt.dShort(d.s("start"))}" else "Statement: ${money(d.n("principal"))} on ${Fmt.dShort(d.s("start"))}") +
            (if (d.n("rate") > 0) " · ${Json.numStr(d.n("rate"))}% a year" else "")))
        lines.add(Line("${d.n("made").toInt()} regular payment${if (d.n("made").toInt() == 1) "" else "s"} made" + (if (d.n("missed") > 0) " · ${d.n("missed").toInt()} missed" else ""), "−" + money(round2(d.n("paid") - d.n("extraPaid")))))
        if (d.n("extraCount") > 0) lines.add(Line("${d.n("extraCount").toInt()} extra payment${if (d.n("extraCount").toInt() == 1) "" else "s"}", "−" + money(d.n("extraPaid"))))
        if (d.n("interestSoFar") > 0.004) lines.add(Line("Interest so far (estimate)", "+" + money(d.n("interestSoFar"))))
        lines.add(Line("Left to pay now", money(d.n("remaining")), total = true))
        if (d.n("missedAsked") > d.n("missed")) lines.add(Line("Only ${d.n("missed").toInt()} payment${if (d.n("missed").toInt() == 1) " was" else "s were"} due since the start, so that's all that was counted as missed.", tone = "warn"))
        val foot = when {
            d.t("stuck") -> Line("The payment doesn't cover the interest, so this debt won't shrink. Talk to the lender or a non-profit credit counsellor.", tone = "bad")
            d.t("finish") -> Line("${d.n("payments").toInt()} more regular payment${if (d.n("payments").toInt() == 1) "" else "s"}" + (if (d.n("extraFuture") > 0) " plus ${money(d.n("extraFuture"))} in planned extra payments" else "") +
                " · paid off ${Fmt.monthYearLong(d.s("finish"))}" + (if (d.n("futureInterest") > 0.004) " · about ${money(d.n("futureInterest"))} more interest" else ""))
            d.n("remaining") > 0 -> Line("Payments stop before this is paid off. Check the bill's end date.", tone = "bad")
            else -> Line("Paid off.", tone = "good")
        }
        return lines to foot
    }

    /* ---- the "Where the money goes" breakdown ---- */
    class PayCard(val title: String, val date: String, val lines: List<Line>, val kind: String = "")
    fun planCards(c: J, s: J): List<PayCard> {
        val inc = c["incomes"].arr(); val rent = inc.filter { it.s("role") == "rent" }; val main = inc.filter { it.s("role") == "main" }; val other = inc.filter { it.s("role") == "other" }
        val out = mutableListOf<PayCard>()
        if (rent.isNotEmpty()) out.add(PayCard(rent.joinToString(" + ") { it.s("name") }, rent.flatMap { it.strList("dates") }.joinToString(", ") { Fmt.dLabel(it) }, listOf(
            Line("Comes in", money(c.n("ryan"))), Line("Rent & housing", neg(c.n("housing"))),
            if (c.n("rentGap") > 0) Line("Rent gap (from the main paychecks)", money(-c.n("rentGap")), true, "bad") else Line("Left over", money(c.n("ryanLeft")), true))))
        else if (c.n("housing") > 0) out.add(PayCard("Rent & housing", "", listOf(Line("Paid from the main paychecks", money(c.n("housing"))))))
        val pays = main.flatMap { i -> i.strList("regular").map { d -> d to i } }.sortedBy { it.first }
        pays.forEachIndexed { idx, (d, i) -> val left = round2(i.n("amount") - c.n("perPayBills") - c.n("perPayFood"))
            out.add(PayCard(i.s("name") + (if (pays.size > 1) " · payday ${idx + 1}" else ""), Fmt.dLabel(d), listOf(Line("Comes in", money(i.n("amount"))), Line("Bills set-aside", neg(c.n("perPayBills"))),
                Line("Food allowance (" + (if (c.n("food") <= s.n("foodMin") + 1) "minimum" else "${c.n("foodPct").toInt()}% of what's left") + ")", neg(c.n("perPayFood"))),
                Line("Left over", money(left), true, if (left < 0) "bad" else "")))) }
        for (i in inc) for (d in i.strList("bonusDates")) out.add(PayCard(i.s("name") + " · extra payday", Fmt.dLabel(d), listOf(Line("Bills from this one", money(0.0)), Line("For Emergency Savings (after any shortfall)", money(i.n("amount")), true)), "bonus"))
        val others = other.filter { it.n("total") > 0 }.map { Line(it.s("name") + " · " + it.strList("regular").joinToString(", ") { d -> Fmt.dShort(d) }, money(it.n("total"))) }
        val credits = c["credits"].arr().filter { it.n("amount") > 0 }.map { Line(it.s("name") + " · " + it.strList("dates").joinToString(", ") { d -> Fmt.dShort(d) }, money(it.n("amount") * it["dates"].arr().size)) }
        val oc = if (c.n("otherCredit") != 0.0) listOf(Line("Other credit", money(c.n("otherCredit")))) else emptyList()
        if (others.isNotEmpty() || credits.isNotEmpty() || oc.isNotEmpty()) out.add(PayCard("Other money in", "Part of normal cash flow", others + credits + oc))
        if (c.n("ccb") > 0) { val prot = c["crAll"].arr().filter { it.t("protect") && it["dates"].arr().isNotEmpty() && it.n("amount") > 0 }
            out.add(PayCard("Protected child benefits", Fmt.dLabel(c.s("ccbDate")), prot.map { Line(it.s("name"), money(it.n("amount") * it["dates"].arr().size)) } +
                Line("Child Reserve · last resort only", money(c.n("ccbChild")), true) + (if (c.n("ccbToEm") > 0) listOf(Line("To Emergency Savings", money(c.n("ccbToEm")))) else emptyList()), "ccb")) }
        return out
    }

    /* ---- forms: the same checks and messages as the web version ---- */
    fun parseMoney(v: String?): Double? { val t = (v ?: "").replace(Regex("[\$,\\s]"), ""); if (t.isEmpty()) return null; return t.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { round2(it) } ?: Double.NaN }

    class DebtForm(val mode: String, val principal: String, val start: String, val missed: String, val owed: String, val asOf: String, val rate: String, val extras: List<Pair<String, String>>)
    /** Returns the debt to save, an error message, or neither (no debt) — the web version's readDebtForm. */
    fun readDebt(f: DebtForm, today: String): Pair<J?, String?> {
        val rateRaw = f.rate.trim(); val rate = if (rateRaw.isEmpty()) 0.0 else rateRaw.replace("%", "").trim().let { if (it.isEmpty()) 0.0 else it.toDoubleOrNull() ?: Double.NaN }
        if (!rate.isFinite() || rate < 0 || rate > 60) return null to "Interest rate: enter a percentage from 0 to 60."
        val extras = mutableListOf<J.O>()
        for ((d, a) in f.extras) { val am = parseMoney(a)
            if (d.isEmpty() && am == null) continue
            if (!isDate(d)) return null to "Extra payment: pick a date."
            if (am == null || am.isNaN() || !(am > 0)) return null to "Extra payment: enter an amount greater than \$0."
            if (d > addDays(today, 3650)) return null to "Extra payment: that date is too far ahead."
            extras.add(J.obj("date" to d, "amount" to am)) }
        extras.sortBy { it.s("date") }
        if (f.mode != "balance") {
            val p = parseMoney(f.principal) ?: return null to null
            if (p.isNaN()) return null to "Original amount owed: enter a dollar amount."; if (!(p > 0)) return null to null
            if (!isDate(f.start)) return null to "Pick the date the debt started."
            if (f.start > today) return null to "The start date is in the future."
            val mRaw = f.missed.trim(); val m = if (mRaw.isEmpty()) 0.0 else mRaw.toDoubleOrNull() ?: Double.NaN
            if (m.isNaN() || m != Math.floor(m) || m < 0 || m > 600) return null to "Missed payments: enter a whole number, like 0 or 2."
            if (extras.any { it.s("date") < f.start }) { val e0 = extras[0].s("date"); return null to "Your extra payment on ${Fmt.dShort(e0)}, ${e0.take(4)} is before the start date. Set “Started on” to that day or earlier." }
            return J.obj("mode" to "original", "principal" to p, "start" to f.start, "rate" to rate, "missed" to m.toInt(), "extra" to extras) to null
        }
        val ow = parseMoney(f.owed) ?: return null to null
        if (ow.isNaN()) return null to "Statement balance: enter a dollar amount."; if (!(ow > 0)) return null to null
        val ao = if (isDate(f.asOf)) f.asOf else today
        return J.obj("mode" to "balance", "owed" to ow, "asOf" to ao, "rate" to rate, "extra" to extras.filter { it.s("date") > ao }) to null
    }
}
