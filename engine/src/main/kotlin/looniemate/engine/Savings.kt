package looniemate.engine

/*
 * The Savings tab's extras, ported from the web version: government savings programs (RDSP, RESP,
 * TFSA, FHSA), the assistance asset limit, the Canada Learning Bond checklist and the ready-made
 * savings funds. rdspMoney() and respMoney() are checked against the web version by the parity tests.
 */
object SavingsViews {
    /** 2026 figures from canada.ca (keep in step with PROG in web/index.html). */
    const val RDSP_GRANT_LOW = 117045.0; const val RDSP_BOND_FULL = 38237.0; const val RDSP_BOND_NONE = 58523.0
    const val ACESG20 = 57375.0; const val ACESG10 = 114750.0; const val TFSA = 7000.0; const val FHSA = 8000.0
    const val RDSP_LINK = "https://www.canada.ca/en/employment-social-development/programs/disability/savings/how-much"
    const val RESP_LINK = "https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/registered-education-savings-plans-resps/canada-education-savings-programs-cesp/canada-education-savings-grant-cesg.html"
    const val CLB_LINK = "https://www.canada.ca/en/revenue-agency/services/tax/individuals/topics/registered-education-savings-plans-resps/canada-education-savings-programs-cesp/canada-learning-bond.html"

    private fun unknown(inc: J?) = inc == null || inc == J.Null || (inc is J.S && inc.v.isEmpty())

    class Rdsp(val grant: Double, val bond: Double?)
    fun rdspMoney(contrib: Double, inc: J?): Rdsp {
        val low = unknown(inc) || num(inc) <= RDSP_GRANT_LOW
        val grant = if (low) 3 * minOf(contrib, 500.0) + 2 * minOf(maxOf(contrib - 500, 0.0), 1000.0) else minOf(contrib, 1000.0)
        val i = num(inc)
        val bond = if (unknown(inc)) null else if (i <= RDSP_BOND_FULL) 1000.0 else if (i >= RDSP_BOND_NONE) 0.0 else round2(1000 * (RDSP_BOND_NONE - i) / (RDSP_BOND_NONE - RDSP_BOND_FULL))
        return Rdsp(round2(grant), bond)
    }

    class Resp(val grant: Double, val known: Boolean)
    fun respMoney(contrib: Double, inc: J?): Resp {
        val base = minOf(contrib * 0.2, 500.0); val i = num(inc); val known = !unknown(inc)
        val add = if (!known) minOf(contrib, 500.0) * 0.2 else if (i < ACESG20) minOf(contrib, 500.0) * 0.2 else if (i <= ACESG10) minOf(contrib, 500.0) * 0.1 else 0.0
        return Resp(round2(base + add), known)
    }

    fun hasOdsp(s: J): Boolean = Core.incomesOf(s).any { Core.assistOf(it) == "odsp" }

    /** Money moved this year into the savings funds kept in an account. */
    fun contribFor(st: Store, acctId: String, year: String): Double {
        var t = 0.0
        for (f in Core.fundsOf(st.settings).filter { it.s("account") == acctId })
            for ((ym, m) in st.months) if (ym.startsWith(year)) { val v = num(m["moved"]?.get(f.s("key"))); if (v > 0) t += v }
        return round2(t)
    }

    class Row(val name: String, val kind: String, val text: String)
    class Idea(val title: String, val text: String, val link: String?, val linkText: String?)
    class Asset(val short: String, val limit: Double, val counted: Double?, val pct: Double, val checkDate: String?)
    class Programs(val rows: List<Row>, val ideas: List<Idea>, val asset: Asset?) { val isEmpty get() = rows.isEmpty() && ideas.isEmpty() && asset == null }

    /** The web version's "Savings programs" card, as data. */
    fun programs(st: Store, today: String, lastCheck: J?): Programs {
        val s = st.settings; val y = today.take(4); val A = Core.accountsOf(s); val inc = s["familyIncome"]
        val reg = A.filter { it.s("type") == "savings" && it.s("kind") in listOf("tfsa", "rdsp", "resp", "fhsa") }
        val rows = reg.map { a -> val c = contribFor(st, a.s("id"), y); val k = a.s("kind")
            val text = when (k) {
                "rdsp" -> { val r = rdspMoney(c, inc)
                    "Put in ${Fmt.money(c)} this year → about ${Fmt.money(r.grant)} in grant" +
                        (if (r.bond == null) ". Enter your family income below to estimate the bond (up to \$1,000 a year, no deposit needed)." else if (r.bond > 0) " plus ${Fmt.money(r.bond)} bond (no deposit needed)." else ".") +
                        (if (c < 500) " Putting in ${Fmt.money(500 - c)} more would earn ${Fmt.money(rdspMoney(500.0, inc).grant - r.grant)} more grant." else "") + " RDSP money is fully exempt for ODSP." }
                "resp" -> { val r = respMoney(c, inc); "Put in ${Fmt.money(c)} this year → about ${Fmt.money(r.grant)} in grants (20%, plus more for low income on the first \$500)." + (if (c < 500) " The first \$500 earns the most." else "") }
                "tfsa" -> "New room in $y: ${Fmt.money(TFSA)}. Growth and withdrawals are tax-free and don't lower federal benefits like the CCB." +
                    (if (hasOdsp(s)) " ODSP's directives don't list TFSAs as exempt, so expect it to count toward the asset limit." else "")
                else -> "Up to ${Fmt.money(FHSA)} a year, tax-free when used for a first home."
            }
            Row(a.s("name"), k, text) }
        val kinds = reg.map { it.s("kind") }.toSet(); val ideas = mutableListOf<Idea>()
        if (hasOdsp(s) && "rdsp" !in kinds) ideas.add(Idea("RDSP", "For someone approved for the Disability Tax Credit. With low family income the government adds up to \$1,000 a year with no deposit needed, and triples the first \$500 you put in. RDSP money doesn't count for ODSP.", RDSP_LINK, "How much you could get"))
        if ("resp" !in kinds && (Core.creditsOf(s).any { it.t("protect") } || isDate(s["babyDue"]))) ideas.add(Idea("RESP", "The government adds 20% of what you put in (more for low income), on top of the Canada Learning Bond.", RESP_LINK, "Grant details"))
        if ("tfsa" !in kinds) ideas.add(Idea("TFSA", "Savings grow tax-free, and money taken out doesn't lower federal benefits.", null, null))
        /* the assistance asset limit, from the last balance check */
        val aSrc = Core.incomesOf(s).find { val k = Core.assistOf(it); k != null && Core.ASSIST[k]?.assets != null }
        val asset = aSrc?.let { src -> val P = Core.ASSIST[Core.assistOf(src)]!!; val od = Core.dedCfg(s, src); val a = P.assets!!
            val lim = (if (od.t("couple")) a.couple else a.single) + a.dep * od.n("deps")
            val bal = lastCheck?.get("bal")
            if (bal is Obj) { var t = 0.0; A.filter { !(it.s("type") == "savings" && it.s("kind") in listOf("rdsp", "resp")) }.forEach { t += maxOf(0.0, num(bal[it.s("id")])) }
                val counted = round2(t); Asset(P.short, lim, counted, minOf(100.0, counted / lim * 100), lastCheck.s("date")) }
            else Asset(P.short, lim, null, 0.0, null) }
        return Programs(rows, ideas, asset)
    }

    /** Whether to show the Canada Learning Bond checklist: there are kids (or a baby on the way). */
    fun hasKids(st: Store): Boolean = Core.creditsOf(st.settings).any { it.t("protect") } || isDate(st.settings["babyDue"]) || st.bills.values.any { it.s("group") == "baby" }
    val CLB_STEPS = listOf("sin" to "Get your child a Social Insurance Number (SIN)", "resp" to "Open an RESP for your child. Many banks offer one with no fees and no deposit needed.",
        "applied" to "Ask the RESP provider to apply for the Canada Learning Bond.")

    class Ready(val key: String, val name: String, val yearly: Double, val group: String)
    /** Keep in step with READY in web/index.html. */
    val READY = listOf(Ready("christmas", "Christmas & birthdays", 600.0, "adults"), Ready("winter", "Winter coats & boots", 300.0, "baby"), Ready("school", "Back to school", 240.0, "baby"),
        Ready("sizes", "Kids' next-size clothes", 360.0, "baby"), Ready("car", "Car repairs & upkeep", 600.0, "adults"), Ready("health", "Dental, eyes & medical", 300.0, "adults"),
        Ready("renew", "Licence, plates & renewals", 240.0, "adults"), Ready("moving", "Moving costs", 1200.0, "adults"))
}
