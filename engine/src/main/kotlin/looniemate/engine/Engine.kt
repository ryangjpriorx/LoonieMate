package looniemate.engine

/*
 * The budget engine: a line-by-line port of the web version's `core` script.
 * Results are JSON trees with the same field names, so a month locked on the phone
 * looks the same to the web version, and the parity tests can compare the two directly.
 */

typealias Obj = J.O

/* ---- small readers ---- */
fun J?.n(k: String): Double = num(this?.get(k))
fun J?.s(k: String): String = str(this?.get(k))
fun J?.t(k: String): Boolean = truthy(this?.get(k))
/** `x.k || def` for text */
fun J?.or(k: String, def: String): String = this?.get(k).let { if (truthy(it)) str(it) else def }
fun J?.strList(k: String): List<String> = this?.get(k).arr().map { str(it) }
internal fun js(v: Any?): J = J.of(v)

/** The data the engine works on, in the same shape as the web version's `st()`. */
class Store(
    val settings: Obj,
    val bills: Map<String, Obj>,
    val months: Map<String, Obj>,
    /** month → expense id → expense; a month that is missing counts as "not loaded" */
    val expenses: Map<String, Map<String, Obj>>,
) {
    fun withBills(b: Map<String, Obj>) = Store(settings, b, months, expenses)
}

data class Holiday(val date: String, val name: String, val bank: Boolean)
data class Resolved(val value: Double, val from: String?)
data class Deferral(val date: String, val amount: Double?)
data class Skip(val amount: Double?)

object Core {
    val FOOD = setOf("groceries", "takeout")

    /* ---------- bank holidays ---------- */
    private val PROV_FEB = mapOf("ON" to "Family Day", "AB" to "Family Day", "BC" to "Family Day", "NB" to "Family Day", "SK" to "Family Day", "MB" to "Louis Riel Day", "PE" to "Islander Day", "NS" to "Heritage Day")
    private val PROV_AUG = mapOf("ON" to "Civic Holiday", "AB" to "Heritage Day", "BC" to "British Columbia Day", "SK" to "Saskatchewan Day", "NB" to "New Brunswick Day", "NS" to "Natal Day", "MB" to "Terry Fox Day", "NT" to "Civic Holiday", "NU" to "Civic Holiday", "YT" to "Discovery Day")
    private val HOL = java.util.concurrent.ConcurrentHashMap<String, List<Holiday>>()

    fun holidaysOf(y: Int, provIn: String? = null): List<Holiday> {
        val prov = if (provIn.isNullOrEmpty()) "ON" else provIn
        return HOL.getOrPut("$y$prov") {
            val out = mutableListOf<Holiday>(); val taken = mutableSetOf<String>()
            fun fixed(m: Int, d: Int, name: String) {
                val orig = ymd(java.time.LocalDate.of(y, m, d)); var dt = java.time.LocalDate.of(y, m, d)
                while (jsDow(dt) == 0 || jsDow(dt) == 6 || taken.contains(ymd(dt))) dt = dt.plusDays(1)
                val s = ymd(dt); taken.add(s); out.add(Holiday(s, name + (if (s != orig) " (observed)" else ""), true))
            }
            fun mov(s: String, name: String, bank: Boolean) { if (bank) taken.add(s); out.add(Holiday(s, name, bank)) }
            fixed(1, 1, "New Year's Day")
            PROV_FEB[prov]?.let { mov(nthDow(y, 2, 1, 3), it, false) }
            mov(addDays(easter(y), -2), "Good Friday", true)
            run { var d = java.time.LocalDate.of(y, 5, 24); while (jsDow(d) != 1) d = d.minusDays(1); mov(ymd(d), "Victoria Day", true) }
            fixed(7, 1, "Canada Day")
            PROV_AUG[prov]?.let { mov(if (prov == "YT") nthDow(y, 8, 1, 3) else nthDow(y, 8, 1, 1), it, false) }
            if (prov == "QC") mov(ymd(java.time.LocalDate.of(y, 6, 24)), "Fête nationale du Québec", false)
            mov(nthDow(y, 9, 1, 1), "Labour Day", true)
            fixed(9, 30, "National Day for Truth and Reconciliation")
            mov(nthDow(y, 10, 1, 2), "Thanksgiving", true)
            fixed(11, 11, "Remembrance Day")
            fixed(12, 25, "Christmas Day"); fixed(12, 26, "Boxing Day")
            out.sortedBy { it.date }
        }
    }

    fun isBiz(d: String): Boolean {
        val dw = jsDow(d); if (dw == 0 || dw == 6) return false
        return holidaysOf(d.substring(0, 4).toInt()).none { it.bank && it.date == d }
    }
    fun bizBefore(d0: String): String { var d = d0; while (!isBiz(d)) d = addDays(d, -1); return d }
    fun bizAfter(d0: String): String { var d = d0; while (!isBiz(d)) d = addDays(d, 1); return d }
    fun shiftBiz(d: String, mode: String) = when (mode) { "after" -> bizAfter(d); "none" -> d; else -> bizBefore(d) }

    /* ---------- payment schedules ---------- */
    private val PUB_QC = mapOf("2027-01" to "2026-12-30")
    private val PUB_CWB = mapOf("2026-01" to "2026-01-12", "2026-07" to "2026-07-10", "2026-10" to "2026-10-09")

    private fun rawDates(sch: J, ym: String): List<String> {
        val y = ym.substring(0, 4).toInt(); val mo = ym.substring(5, 7).toInt(); val t = sch.s("type")
        return when (t) {
            "monthly" -> listOf(dayIn(ym, if (sch.t("day")) sch.n("day") else 1.0))
            "semimonthly" -> listOf(dayIn(ym, 15), dayIn(ym, 31))
            "quarterly" -> {
                val months = if (sch.t("months")) sch["months"].arr() else listOf(1, 4, 7, 10).map { js(it) }
                if (months.any { it is J.N && it.v == mo.toDouble() }) listOf(dayIn(ym, if (sch.t("day")) sch.n("day") else 5.0)) else emptyList()
            }
            "ccb" -> listOf(if (mo == 12) nthDow(y, 12, 5, 2) else "$ym-20")
            "cwb" -> if (mo == 1 || mo == 7 || mo == 10) listOf(PUB_CWB[ym] ?: "$ym-10") else emptyList()
            "thirdthu" -> listOf(nthDow(y, mo, 4, 3))
            "weekly", "biweekly" -> {
                val anchor = sch["anchor"]; if (!isDate(anchor)) return emptyList(); val a = str(anchor)
                val step = if (t == "weekly") 7 else 14; val first = "$ym-01"; val last = dayIn(ym, 31)
                var k = maxOf(0, Math.ceil(diffDays(a, first).toDouble() / step).toInt()); val out = mutableListOf<String>()
                var d = addDays(a, step * k)
                while (d <= last) { if (d >= first) out.add(d); k++; d = addDays(a, step * k) }
                out
            }
            else -> emptyList()
        }
    }

    /** Every date a schedule pays in month ym, after moving off weekends and bank holidays. */
    fun schedDates(sch: J?, ym: String): List<String> {
        if (sch == null || !sch.t("type")) return emptyList()
        val t = sch.s("type")
        when (t) {
            "lastbiz", "odsp" -> return listOf(bizBefore(dayIn(ym, 31)))
            "firstbiz" -> return listOf(bizAfter("$ym-01"))
            "qc" -> { val out = mutableListOf<String>()
                for (b in listOf(ym, ymAdd(ym, 1))) { val d = PUB_QC[b] ?: bizBefore("$b-01"); if (ymOf(d) == ym) out.add(d) }
                return out }
            "ns" -> { val ds = mutableListOf<String>(); var d = dayIn(ym, 31)
                while (ds.size < 3) { if (isBiz(d)) ds.add(0, d); d = addDays(d, -1) }
                val slotRaw = sch["slot"]; val sv = if (jsFinite(slotRaw) && slotRaw != null && num(slotRaw) != 0.0) num(slotRaw) else 3.0
                return listOf(ds[minOf(2, maxOf(0, (sv - 1).toInt()))]) }
        }
        val mode = if (sch.t("shift")) sch.s("shift") else "before"
        val out = mutableListOf<String>()
        for (m in listOf(ymAdd(ym, -1), ym, ymAdd(ym, 1))) for (d in rawDates(sch, m)) {
            val p = if (t == "cwb" && PUB_CWB[m] != null) d else shiftBiz(d, mode)
            if (ymOf(p) == ym) out.add(p)
        }
        return out.distinct().sorted()
    }

    fun schedEstimated(sch: J?, ym: String): Boolean {
        if (sch == null) return false; val mo = ym.substring(5, 7).toInt(); val t = sch.s("type")
        return (t == "odsp" && mo == 12) || (t == "cwb" && (mo == 1 || mo == 7 || mo == 10) && PUB_CWB[ym] == null)
    }

    /* ---------- social assistance programs ---------- */
    class Assets(val single: Double, val couple: Double, val dep: Double)
    class Program(val key: String, val name: String, val short: String, val prov: String, val sched: Obj, val pays: String, val report: Int, val assets: Assets?, val rule: String)
    val ASSIST: Map<String, Program> = linkedMapOf(
        "odsp" to Program("odsp", "Ontario Disability Support Program (ODSP)", "ODSP", "ON", J.obj("type" to "odsp"), "next", 7, Assets(40000.0, 50000.0, 500.0), "odsp"),
        "ow" to Program("ow", "Ontario Works (OW)", "Ontario Works", "ON", J.obj("type" to "odsp"), "next", 15, Assets(10000.0, 15000.0, 500.0), "ow"),
        "qcsa" to Program("qcsa", "Québec Social Assistance Program", "Social Assistance", "QC", J.obj("type" to "qc"), "same", 0, null, "qc"),
        "qcss" to Program("qcss", "Québec Social Solidarity Program", "Social Solidarity", "QC", J.obj("type" to "qc"), "same", 0, null, "qc"),
        "qcrb" to Program("qcrb", "Québec Basic Income Program", "Basic Income", "QC", J.obj("type" to "qc"), "same", 0, null, "custom"),
        "nsia" to Program("nsia", "Nova Scotia Income Assistance (ESIA)", "Income Assistance", "NS", J.obj("type" to "ns"), "next", 10, null, "ns"),
    )
    fun assistOf(src: J?): String? {
        if (src == null) return null
        val a = src["assist"]; if (truthy(a) && ASSIST.containsKey(str(a))) return str(a)
        if (src["sched"].s("type") == "odsp") return "odsp"
        return null
    }
    fun assistSched(src: J): J? {
        val k = assistOf(src) ?: return src["sched"]
        val o = ASSIST.getValue(k).sched.copy() as Obj
        if (k == "nsia") { val sl = src["sched"]?.get("slot"); o["slot"] = if (truthy(sl)) sl else js(3) }
        return o
    }
    /** Deduction settings for one assistance income (older ODSP settings carried over). */
    fun dedCfg(s: J, src: J): Obj {
        val d = src["ded"]; if (truthy(d)) return d as? Obj ?: Obj()
        val k = assistOf(src)
        if (k == "odsp" && s.t("odsp")) { val o = s["odsp"]
            val lag0 = o?.get("lag"); val lag = if ((lag0 is J.N && lag0.v == 0.0) || (lag0 is J.S && lag0.v == "0")) 1 else 2
            return J.obj("dis" to o.t("dis"), "work" to o.t("spouse"), "full" to o.t("full"), "wrb" to !o?.get("wrb").isFalse(), "lag" to lag, "couple" to o.t("couple"), "deps" to o.n("deps")) }
        return Obj()
    }
    fun nsExempt(e: Double, sup: Boolean): Double {
        val t = if (sup) 450.0 else 350.0; val a = if (sup) doubleArrayOf(487.5, 612.5) else doubleArrayOf(462.5, 587.5)
        if (e <= t) return e; if (e <= 500) return t + (e - t) * 0.75; if (e <= 750) return a[0] + (e - 500) * 0.5; return a[1] + (e - 750) * 0.25
    }
    private fun schedPerMonth(sch: J?): Int = when (sch.s("type")) { "weekly" -> 4; "biweekly" -> 2; else -> 99 }

    /* ---------- benefits and credits ---------- */
    class CreditType(val key: String, val name: String, val sched: Obj, val protect: Boolean, val prov: String?, val whenText: String)
    private fun q(vararg m: Int, day: Int) = J.obj("type" to "quarterly", "months" to m.toList(), "day" to day)
    val CREDIT_TYPES: Map<String, CreditType> = linkedMapOf(
        "ccb" to CreditType("ccb", "Canada Child Benefit (CCB)", J.obj("type" to "ccb"), true, null, "20th of each month (earlier in December)"),
        "ocb" to CreditType("ocb", "Ontario Child Benefit (OCB)", J.obj("type" to "ccb"), true, "ON", "Paid together with the CCB"),
        "cgeb" to CreditType("cgeb", "Canada Groceries and Essentials Benefit (CGEB)", q(1, 4, 7, 10, day = 5), false, null, "Formerly the GST/HST credit. Early Jan, Apr, Jul and Oct"),
        "otb" to CreditType("otb", "Ontario Trillium Benefit (OTB)", J.obj("type" to "monthly", "day" to 10), false, "ON", "10th of each month"),
        "cwb" to CreditType("cwb", "Canada Workers Benefit advance (ACWB)", J.obj("type" to "cwb"), false, null, "January, July and October"),
        "cdb" to CreditType("cdb", "Canada Disability Benefit (CDB)", J.obj("type" to "thirdthu"), false, null, "Third Thursday of each month"),
        "bcfb" to CreditType("bcfb", "BC Family Benefit", J.obj("type" to "ccb"), true, "BC", "Paid together with the CCB"),
        "acfb" to CreditType("acfb", "Alberta Child and Family Benefit (ACFB)", q(2, 5, 8, 11, day = 27), true, "AB", "February, May, August and November"),
        "nscb" to CreditType("nscb", "Nova Scotia Child Benefit", J.obj("type" to "ccb"), true, "NS", "Paid together with the CCB"),
        "nsaltc" to CreditType("nsaltc", "Nova Scotia Affordable Living Tax Credit", q(1, 4, 7, 10, day = 5), false, "NS", "Paid together with the CGEB"),
        "nbctb" to CreditType("nbctb", "New Brunswick Child Tax Benefit", J.obj("type" to "ccb"), true, "NB", "Paid together with the CCB"),
        "nbhstc" to CreditType("nbhstc", "New Brunswick HST Credit", q(1, 4, 7, 10, day = 5), false, "NB", "Paid together with the CGEB"),
        "slitc" to CreditType("slitc", "Saskatchewan Low-Income Tax Credit", q(1, 4, 7, 10, day = 5), false, "SK", "Paid together with the CGEB"),
        "nlcb" to CreditType("nlcb", "Newfoundland and Labrador Child Benefit", J.obj("type" to "ccb"), true, "NL", "Paid together with the CCB"),
        "nlis" to CreditType("nlis", "Newfoundland and Labrador Income Supplement", q(1, 4, 7, 10, day = 5), false, "NL", "Paid together with the CGEB"),
        "nldb" to CreditType("nldb", "Newfoundland and Labrador Disability Benefit", J.obj("type" to "monthly", "day" to 25), false, "NL", "Around the 25th of each month"),
        "peicb" to CreditType("peicb", "PEI Child Benefit", J.obj("type" to "ccb"), true, "PE", "Paid together with the CCB"),
        "peieb" to CreditType("peieb", "PEI Essentials Benefit", q(1, 4, 7, 10, day = 5), false, "PE", "Paid together with the CGEB"),
        "provchild" to CreditType("provchild", "Provincial or territorial child benefit (paid with the CCB)", J.obj("type" to "ccb"), true, null, "Paid together with the CCB"),
        "provcredit" to CreditType("provcredit", "Provincial or territorial credit (paid with the CGEB)", q(1, 4, 7, 10, day = 5), false, null, "Paid together with the CGEB"),
        "other" to CreditType("other", "Other benefit or credit", J.obj("type" to "monthly", "day" to 15), false, null, "You choose the day"),
    )

    fun incomesOf(s: J?): List<Obj> = s?.get("incomes").arr().filterIsInstance<Obj>().filter { it.t("id") }
    fun creditsOf(s: J?): List<Obj> = s?.get("credits").arr().filterIsInstance<Obj>().filter { it.t("id") && it.t("type") }
    fun creditSched(cr: J): J {
        if (cr.s("type") == "other") { val o = J.obj("type" to "monthly", "day" to 15); (cr["sched"] as? Obj)?.map?.forEach { (k, v) -> o[k] = v }; return o }
        return (CREDIT_TYPES[cr.s("type")] ?: CREDIT_TYPES.getValue("other")).sched
    }
    fun creditName(cr: J): String = if (cr.s("type") == "other" || CREDIT_TYPES[cr.s("type")] == null) cr.or("name", "Other credit") else CREDIT_TYPES.getValue(cr.s("type")).name

    private fun present(v: J?): J? = if (v != null && v != J.Null && !(v is J.S && v.v.isEmpty())) v else null
    /** month-by-month amounts: new keys, plus the older ones saved before incomes were configurable */
    private fun incAmtGet(id: String): (J) -> J? = { x -> val i = x["income"] ?: Obj()
        val a = i["amt"]?.get(id); if (truthy(i["amt"]) && present(a) != null) a else if (id == "ryan") i["ryan"] else if (id == "stacey") i["staceyPer"] else null }
    private fun crAmtGet(cr: J): (J) -> J? = { x -> val i = x["income"] ?: Obj(); val id = cr.s("id")
        val c1 = i["cr"]?.get(id); val c2 = i["credits"]?.get(id)
        if (truthy(i["cr"]) && present(c1) != null) c1
        else if (cr.s("type") == "ccb" && i["ccb"] != null && present(i["ccb"]) != null) i["ccb"]
        else if (truthy(i["credits"]) && present(c2) != null) c2 else null }

    fun mainPaydays(s: J, ym: String): List<String> = incomesOf(s).filter { it.s("role") == "main" }
        .flatMap { x -> schedDates(assistSched(x), ym).filter { d -> !isDate(x["start"]) || d >= x.s("start") } }.sorted()

    fun effectiveStart(b: J, s: J?): String? {
        if (isDate(b["start"])) return b.s("start")
        if (b.s("group") == "baby" && s != null && isDate(s["babyDue"])) return s.s("babyDue")
        return null
    }

    /** Due dates of a recurring bill between from and to (inclusive). */
    fun billDates(b: J?, from: String, to: String, s: J?): List<String> {
        if (b == null || b["active"].isFalse()) return emptyList()
        val st = effectiveStart(b, s); var lo = from; var hi = to
        if (st != null && st > lo) lo = st
        val freq = b.s("freq")
        if (b.t("freq") && freq != "monthly" && isDate(b["anchor"]) && b.s("anchor") > lo) lo = b.s("anchor")
        if (isDate(b["end"]) && b.s("end") < hi) hi = b.s("end")
        if (lo > hi) return emptyList()
        val out = mutableListOf<String>()
        if (freq == "weekly" || freq == "biweekly") {
            val step = if (freq == "weekly") 7 else 14; val a = if (isDate(b["anchor"])) b.s("anchor") else lo
            var k = Math.ceil(diffDays(a, lo).toDouble() / step).toInt(); var d = addDays(a, step * k)
            while (d <= hi) { if (d >= lo) out.add(d); k++; d = addDays(a, step * k) }
        } else if (freq == "quarterly" || freq == "yearly") {
            if (!isDate(b["anchor"])) return emptyList()
            val step = if (freq == "quarterly") 3 else 12; val an = b.s("anchor")
            val ay = an.substring(0, 4).toInt(); val am = an.substring(5, 7).toInt(); val ad = an.substring(8, 10).toInt()
            var ym = ymOf(lo)
            while (ym <= ymOf(hi)) {
                val diff = (ym.substring(0, 4).toInt() * 12 + ym.substring(5, 7).toInt()) - (ay * 12 + am)
                if (((diff % step) + step) % step == 0) { val d = dayIn(ym, ad); if (d >= lo && d <= hi) out.add(d) }
                ym = ymAdd(ym, 1)
            }
        } else {
            var ym = ymOf(lo)
            while (ym <= ymOf(hi)) { val d = dayIn(ym, if (b.t("day")) b.n("day") else 1.0); if (d >= lo && d <= hi) out.add(d); ym = ymAdd(ym, 1) }
        }
        return out
    }

    class Plan(val amount: Double, val dates: List<String>, val setAside: Boolean = false)
    /** What a bill costs the plan in a month. Every-3-months and yearly bills set aside a slice each month. */
    fun billPlanForMonth(b: J?, ym: String, s: J?): Plan {
        val first = "$ym-01"; val last = dayIn(ym, 31)
        if (b == null || b["active"].isFalse()) return Plan(0.0, emptyList())
        val dates = billDates(b, first, last, s); val f = b.s("freq")
        if (f == "quarterly" || f == "yearly") {
            val st = effectiveStart(b, s)
            val on = (st == null || st <= last) && (!isDate(b["end"]) || b.s("end") >= first)
            return Plan(if (on) round2(b.n("amount") / (if (f == "quarterly") 3 else 12)) else 0.0, dates, true)
        }
        return Plan(round2(b.n("amount") * dates.size), dates)
    }

    /** Month-by-month value that carries forward; later months never change earlier ones. */
    fun resolve(months: Map<String, Obj>, ym: String, get: (J) -> J?, fallback: J?, okMonth: ((String) -> Boolean)? = null): Resolved {
        val keys = months.keys.filter { isYm(it) && it <= ym && (okMonth == null || okMonth(it)) }.sortedDescending()
        for (k in keys) { val v = get(months[k] ?: Obj())
            if (v != null && v != J.Null && !(v is J.S && v.v.isEmpty()) && jsFinite(v)) return Resolved(num(v), k) }
        return Resolved(num(fallback), null)
    }

    fun listExp(map: Map<String, Obj>?): List<Obj> {
        if (map == null) return emptyList()
        return map.entries.map { (id, e) -> J.obj("id" to id).also { o -> e.map.forEach { (k, v) -> o[k] = v } } }
            .filter { e -> isDate(e["date"]) && jsFinite(e["amount"]) }
            .sortedWith { a, b -> val da = a.s("date"); val db = b.s("date")
                if (da < db) 1 else if (da > db) -1 else (b.n("created") - a.n("created")).let { if (it > 0) 1 else if (it < 0) -1 else 0 } }
    }

    /* ---------- accounts and funds ---------- */
    fun accountsOf(s: J?): List<Obj> = s?.get("accounts").arr().filterIsInstance<Obj>().filter { it.t("id") }
    fun acctById(s: J?, ref: String?): Obj? { if (ref.isNullOrEmpty()) return null; val a = accountsOf(s); return a.find { it.s("id") == ref } ?: a.find { it.s("name") == ref } }
    fun acctId(s: J?, ref: String?): String = acctById(s, ref)?.s("id") ?: ""
    fun acctFor(s: J?, purpose: String): Obj? = accountsOf(s).find { a -> a["purposes"].arr().any { str(it) == purpose } }
    private val FUNDCODE = mapOf("emergency" to "emer", "child" to "child")

    fun fundsOf(s: J?): List<Obj> {
        val raw = s?.get("funds").arr(); val list = if (raw.isNotEmpty()) raw else emptyList()
        val core = listOf(J.obj("key" to "emergency", "name" to "Emergency Savings", "account" to ""), J.obj("key" to "child", "name" to "Child Reserve", "account" to "", "protect" to true))
        val out = core.map { f -> val o = f.copy() as Obj
            (list.find { it is Obj && it.s("key") == f.s("key") } as? Obj)?.map?.forEach { (k, v) -> o[k] = v }
            o["key"] = f["key"]; o["name"] = f["name"]; o["protect"] = f["protect"]; o }.toMutableList()
        for (f in list) if (f is Obj && f.t("key") && core.none { it.s("key") == f.s("key") }) out.add(J.obj("goal" to 0, "start" to 0).also { o -> f.map.forEach { (k, v) -> o[k] = v } })
        return out
    }
    fun fundOf(s: J?, key: String): Obj = fundsOf(s).find { it.s("key") == key } ?: J.obj("key" to key, "name" to key, "account" to "")

    /* ---------- skips and deferrals ---------- */
    fun getDef(m: J?, id: String): Deferral? {
        val v = if (m != null && m.t("deferred")) m["deferred"]?.get(id) else null
        if (!truthy(v)) return null
        if (v is J.S) return if (isDate(v.v)) Deferral(v.v, null) else null
        return if (isDate(v?.get("date"))) Deferral(v.s("date"), v?.get("amount").let { if (it == null || it == J.Null) null else num(it) }) else null
    }
    fun getSkip(m: J?, id: String): Skip? {
        val v = if (m != null && m.t("skipped")) m["skipped"]?.get(id) else null
        if (!truthy(v)) return null
        if (v.isTrue()) return Skip(null)
        val a = v?.get("amount"); if (a == null || a == J.Null) return Skip(null)
        return if (num(a) > 0) Skip(num(a)) else null
    }
    private fun isDeferred(st: Store, b: J?, id: String, ym: String) = b != null && b.t("deferrable") && getDef(st.months[ym], id) != null
    private fun isSkipped(st: Store, b: J?, id: String, ym: String) = b != null && b["essential"].isFalse() && getSkip(st.months[ym], id) != null && !isDeferred(st, b, id, ym)

    /** Bills pushed into month ym from another month. */
    fun deferredIn(st: Store, ym: String): List<Obj> {
        val s = st.settings; val out = mutableListOf<Obj>()
        for ((k, mm) in st.months) {
            if (!isYm(k) || !mm.t("deferred")) continue
            for (bid in (mm["deferred"] as? Obj)?.map?.keys?.toList() ?: emptyList()) {
                val df = getDef(mm, bid) ?: continue; if (ymOf(df.date) != ym) continue
                val b = st.bills[bid] ?: continue; if (!b.t("deferrable")) continue
                var amt: Double
                val snapLines = mm["snapshot"]?.get("billLines")
                if (mm.t("closed") && mm.t("snapshot") && truthy(snapLines)) {
                    val l = snapLines.arr().find { it.s("id") == bid }
                    amt = if (l != null) num(if (l["planned"] != null && l["planned"] != J.Null) l["planned"] else l["amount"]) else 0.0
                } else amt = billPlanForMonth(b, k, s).amount
                if (df.amount != null) amt = minOf(amt, df.amount)
                if (amt > 0) out.add(J.obj("id" to "$bid~$k", "billId" to bid, "name" to b.or("name", "Bill"), "group" to b.or("group", "adults"), "category" to b.or("category", "other"),
                    "account" to b.or("account", ""), "method" to b.or("method", ""), "amount" to round2(amt), "planned" to round2(amt), "dates" to listOf(df.date), "deferredFrom" to k,
                    "essential" to true, "skipped" to false, "setAside" to false, "example" to false))
            }
        }
        return out
    }

    /** Every bill payment due between from and to, after skips and deferrals. */
    fun billEvents(st: Store, from: String, to: String): List<Obj> {
        val s = st.settings; val out = mutableListOf<Obj>()
        for ((id, b) in st.bills) {
            if (b["active"].isFalse()) continue
            val left = HashMap<String, HashMap<String, Double>>()
            for (d in billDates(b, from, to, s)) {
                val ym = ymOf(d); var amt = round2(b.n("amount"))
                val redAmt: Double?; val hasRed: Boolean
                if (isDeferred(st, b, id, ym)) { hasRed = true; redAmt = getDef(st.months[ym], id)!!.amount }
                else if (isSkipped(st, b, id, ym)) { hasRed = true; redAmt = getSkip(st.months[ym], id)!!.amount }
                else { hasRed = false; redAmt = null }
                if (hasRed) {
                    if (redAmt == null) continue
                    if (left[ym] == null) { /* take the skipped or deferred part from the month's earliest payments first */
                        val all = billDates(b, "$ym-01", dayIn(ym, 31), s); var rem: Double = redAmt; val mp = HashMap<String, Double>(); left[ym] = mp
                        for (x in all) { val cut = minOf(rem, b.n("amount")); mp[x] = round2(b.n("amount") - cut); rem = round2(rem - cut) }
                    }
                    amt = left[ym]!![d] ?: amt; if (amt <= 0.004) continue
                }
                out.add(J.obj("id" to id, "key" to "$id@$d", "date" to d, "ym" to ym, "name" to b.or("name", "Bill"), "amount" to amt, "group" to b.or("group", "adults"),
                    "account" to b.or("account", ""), "acct" to acctId(s, b.s("account")), "method" to b.or("method", ""), "fund" to b.or("fund", ""), "example" to b.t("example")))
            }
        }
        var ym = ymOf(from)
        while (ym <= ymOf(to)) {
            for (l in deferredIn(st, ym)) { val d = l["dates"].arr()[0].let { str(it) }
                if (d >= from && d <= to) out.add(J.obj("id" to l.s("billId"), "key" to l.s("billId") + "@" + d + "~d", "date" to d, "ym" to ym, "name" to l.s("name"), "amount" to l.n("amount"),
                    "group" to l.s("group"), "account" to l.s("account"), "acct" to acctId(s, l.s("account")), "method" to l.s("method"), "fund" to (st.bills[l.s("billId")]).or("fund", ""), "deferredFrom" to l.s("deferredFrom"))) }
            ym = ymAdd(ym, 1)
        }
        return out
    }

    private val SNAP_SKIP = setOf("expenses", "paid", "moved", "expensesLoaded", "closed", "ym")

    fun monthCalc(ym: String, st: Store, today: String): Obj {
        val s = st.settings; val months = st.months; val m: Obj = months[ym] ?: Obj()
        val moved = J.obj("emergency" to round2(num(m["moved"]?.get("emergency"))), "child" to round2(num(m["moved"]?.get("child"))))
        val paid = m["paid"].let { if (truthy(it)) it!! else Obj() }
        val expenses = listExp(st.expenses[ym]); val expensesLoaded = st.expenses.containsKey(ym)
        if (m.t("closed") && m.t("snapshot")) {
            val o = (m["snapshot"]!!.copy() as? Obj) ?: Obj()
            o["ym"] = js(ym); o["closed"] = js(true); o["moved"] = moved; o["paid"] = paid; o["expenses"] = js(expenses); o["expensesLoaded"] = js(expensesLoaded)
            return o
        }
        /* incomes: each one's dates this month; extra bi-weekly or weekly paydays beyond the usual count are bonus */
        val incomes = incomesOf(s).map { src -> val sch = assistSched(src)
            var dates = schedDates(sch, ym).filter { d -> !isDate(src["start"]) || d >= src.s("start") }
            val ovd = if (m["income"] != null && m["income"].t("date")) m["income"]!!["date"]?.get(src.s("id")) else null
            if (isDate(ovd) && dates.size <= 1 && ymOf(str(ovd)) == ym) dates = listOf(str(ovd))
            val per = schedPerMonth(sch); val r = resolve(months, ym, incAmtGet(src.s("id")), src["amount"]); val amount = round2(r.value)
            val reg = dates.take(per); val bon = dates.drop(per)
            J.obj("id" to src["id"], "name" to src.or("name", "Income"), "role" to src.or("role", "other"), "account" to src.or("account", ""), "sched" to sch, "assist" to assistOf(src),
                "amount" to amount, "from" to r.from, "dates" to dates, "regular" to reg, "bonusDates" to bon, "total" to round2(amount * reg.size), "bonus" to round2(amount * bon.size),
                "estimated" to (schedEstimated(sch, ym) && !isDate(ovd)))
        }
        /* social assistance: work pay and other counted income reduce the payment, using each program's rules */
        val assistCalc = Obj(); var odspCalc: J = J.Null
        for (A in incomes.filter { it.t("assist") && it["dates"].arr().isNotEmpty() }) {
            val P = ASSIST.getValue(A.s("assist")); val srcA = incomesOf(s).find { it.s("id") == A.s("id") }!!; val cfg = dedCfg(s, srcA)
            if (!(cfg.t("dis") || cfg.t("work") || cfg.t("full"))) continue
            val d0 = str(A["dates"].arr()[0])
            val B = if (P.pays == "next") ymAdd(ymOf(d0), 1) else ymOf(addDays(d0, 7))
            val L = cfg["lag"].let { if (it != null && it != J.Null && !(it is J.S && it.v.isEmpty())) num(it).toInt() else if (P.prov == "QC") 1 else 2 }
            val earnYm = ymAdd(B, -L)
            val groups = LinkedHashMap<String, Obj>()
            for (src in incomesOf(s)) {
                var t = src.or("odsp", ""); if (t.isEmpty() || assistOf(src) != null) continue
                if (t == "dis" && P.rule != "odsp") t = "spouse"
                if ((t == "dis" && !cfg.t("dis")) || (t == "spouse" && !cfg.t("work")) || (t == "full" && !cfg.t("full"))) continue
                val n = schedDates(assistSched(src), earnYm).filter { d -> !isDate(src["start"]) || d >= src.s("start") }.size
                val amt = round2(resolve(months, earnYm, incAmtGet(src.s("id")), src["amount"]).value * n); if (amt <= 0) continue
                val key = if ((P.rule == "qc" || P.rule == "custom") && t != "full") "work|household" else t + "|" + src.or("person", src.s("id"))
                val g = groups.getOrPut(key) { J.obj("type" to t, "person" to src.or("person", ""), "names" to emptyList<String>(), "amount" to 0) }
                (g["names"] as J.A).list.add(js(src.or("name", "Income"))); g["amount"] = js(round2(g.n("amount") + amt))
            }
            val lines = mutableListOf<Obj>(); var ded = 0.0; var wrb = 0.0; val workers = LinkedHashSet<String>()
            for (g in groups.values) {
                var ch: Double; val how: String; val E = g.n("amount"); val type = g.s("type")
                if (type == "full") { ch = E; how = "in full" }
                else if (P.rule == "odsp" && type == "dis") { ch = maxOf(0.0, E - 1000) * 0.75; how = "−\$1,000, then 75%" }
                else if (P.rule == "odsp" || P.rule == "ow") { ch = maxOf(0.0, E - 200) * 0.5; how = "−\$200, then 50%" }
                else if (P.rule == "qc") { val f = if (cfg.t("couple")) 300 else 200; ch = maxOf(0.0, E - f) * 0.75; how = "−\$$f, then dollar for dollar with 25% added back" }
                else if (P.rule == "custom") { val f = cfg.n("flat"); val r = cfg.n("rate"); val on = f > 0 || r > 0
                    ch = if (on) maxOf(0.0, E - f) * r / 100 else 0.0; how = if (on) "−\$${Json.numStr(f)}, then ${Json.numStr(r)}%" else "enter the rule from your notice" }
                else if (P.rule == "ns") { ch = E - nsExempt(E, cfg.t("supported")); how = if (cfg.t("supported")) "supported employment scale" else "sliding scale" }
                else { ch = 0.0; how = "" }
                ch = round2(maxOf(0.0, ch)); ded += ch
                lines.add(J.obj("deduct" to ch, "how" to how).also { o -> g.map.forEach { (k, v) -> o[k] = v } })
                if (type != "full") workers.add(g.s("person").ifEmpty { str(g["names"].arr().firstOrNull()) })
            }
            if (P.rule == "odsp" && !cfg["wrb"].isFalse()) wrb = 100.0 * workers.size
            val base = round2(srcA.n("amount")); val calc = round2(maxOf(0.0, base - ded + wrb)); val manual = A.s("from") == ym && A["from"] != J.Null
            val ac = J.obj("id" to A["id"], "name" to A["name"], "program" to A["assist"], "base" to base, "lines" to lines, "deduct" to round2(ded), "wrb" to wrb, "calc" to calc,
                "earnYm" to earnYm, "benefitYm" to B, "lag" to L, "manual" to manual, "applied" to !manual)
            assistCalc[A.s("id")] = ac
            if (!manual) { A["amount"] = js(calc); A["total"] = js(round2(calc * A["regular"].arr().size)); A["bonus"] = js(round2(calc * A["bonusDates"].arr().size)) }
            if (odspCalc == J.Null) odspCalc = ac
        }
        val rentInc = incomes.filter { it.s("role") == "rent" }; val mainInc = incomes.filter { it.s("role") == "main" }
        val ryan = sumOf2(rentInc.map { it.n("total") }); val ryanDate = rentInc.flatMap { it.strList("dates") }.sorted().firstOrNull() ?: "$ym-01"
        val paydays = mainInc.flatMap { it.strList("dates") }.sorted(); val regularDays = mainInc.flatMap { it.strList("regular") }.sorted(); val bonusDays = incomes.flatMap { it.strList("bonusDates") }.sorted()
        val staceyPer = if (mainInc.isNotEmpty()) mainInc[0].n("amount") else 0.0; val staceyRegular = sumOf2(mainInc.map { it.n("total") })
        val otherIncome = sumOf2(incomes.filter { it.s("role") != "rent" && it.s("role") != "main" }.map { it.n("total") }); val bonus = sumOf2(incomes.map { it.n("bonus") })
        /* benefits and credits */
        val crAll = creditsOf(s).map { cr -> val sch = creditSched(cr); val started = !isYm(cr["start"]) || ym >= cr.s("start")
            val dates = if (started) schedDates(sch, ym) else emptyList()
            val r = if (dates.isNotEmpty()) resolve(months, ym, crAmtGet(cr), cr["amount"]) { k -> (!isYm(cr["start"]) || k >= cr.s("start")) && schedDates(sch, k).isNotEmpty() } else Resolved(0.0, null)
            J.obj("key" to cr["id"], "id" to cr["id"], "type" to cr["type"], "name" to creditName(cr), "protect" to cr.t("protect"), "account" to cr.or("account", ""), "amount" to round2(r.value), "from" to r.from,
                "date" to (dates.firstOrNull() ?: "$ym-01"), "dates" to dates, "scheduled" to dates.isNotEmpty(), "estimated" to schedEstimated(sch, ym)) }
        val credits = crAll.filter { !it.t("protect") }
        val otherCredit = round2(num(m["income"]?.get("otherCredit")))
        val creditsTotal = round2(sumOf2(credits.map { it.n("amount") * it["dates"].arr().size }) + otherCredit)
        val prot = crAll.filter { it.t("protect") && it["dates"].arr().isNotEmpty() }
        val ccb = round2(sumOf2(prot.map { it.n("amount") * it["dates"].arr().size })); val ccbOn = prot.isNotEmpty(); val ccbDate = prot.map { it.s("date") }.sorted().firstOrNull() ?: dayIn(ym, 20)
        val ccbE = m["income"]?.get("ccbToEmergency")
        val ccbEmRaw = if (ccbE != null && ccbE != J.Null && !(ccbE is J.S && ccbE.v.isEmpty())) num(ccbE) else ccb * minOf(100.0, maxOf(0.0, s.n("ccbEmergencyPct"))) / 100
        val ccbToEm = round2(minOf(ccb, maxOf(0.0, ccbEmRaw))); val ccbChild = round2(ccb - ccbToEm)
        val billLines = st.bills.entries.map { (id, b) -> val p = billPlanForMonth(b, ym, s); val ess = !b["essential"].isFalse()
            val dfo = if (b.t("deferrable")) getDef(m, id) else null; val df = dfo?.date
            val dAmt = if (dfo != null) round2(minOf(p.amount, dfo.amount ?: p.amount)) else 0.0
            val sko = if (df == null && !ess) getSkip(m, id) else null
            val sAmt = if (sko != null) round2(minOf(p.amount, sko.amount ?: p.amount)) else 0.0
            J.obj("id" to id, "name" to b.or("name", "Bill"), "group" to b.or("group", "adults"), "category" to b.or("category", "other"), "account" to b.or("account", ""), "method" to b.or("method", ""),
                "amount" to round2(p.amount - dAmt - sAmt), "planned" to p.amount, "skipped" to (sAmt > 0), "skippedAmt" to sAmt, "deferredTo" to df, "deferredAmt" to dAmt, "essential" to ess,
                "deferrable" to b.t("deferrable"), "dates" to p.dates, "setAside" to p.setAside, "example" to b.t("example")) }
            .filter { it.n("planned") > 0 || it["dates"].arr().isNotEmpty() } + deferredIn(st, ym)
        val skippedTotal = sumOf2(billLines.filter { it.t("skipped") }.map { it.n("skippedAmt") })
        val deferredTotal = sumOf2(billLines.filter { it.t("deferredTo") }.map { it.n("deferredAmt") })
        val housing = sumOf2(billLines.filter { it.s("category") == "housing" }.map { it.n("amount") })
        val billsTotal = sumOf2(billLines.map { it.n("amount") }); val otherBills = round2(billsTotal - housing)
        val byGroup = J.obj("adults" to 0, "baby" to 0, "pets" to 0); billLines.forEach { l -> byGroup[l.s("group")] = js(round2(byGroup.n(l.s("group")) + l.n("amount"))) }
        val rentGap = round2(maxOf(0.0, housing - ryan)); val ryanLeft = round2(maxOf(0.0, ryan - housing))
        val regCount = regularDays.size
        val perPayBills = round2((otherBills + rentGap) / maxOf(1, regCount))
        val spendable = round2(ryan + staceyRegular + otherIncome + creditsTotal)
        /* food: a share of what is left after fixed bills (worked out before skips or deferrals), never below the minimum */
        val leftAfterBills = round2(spendable - (billsTotal + skippedTotal + deferredTotal))
        val pct = minOf(100.0, maxOf(0.0, s.n("foodPct"))) / 100
        val rawFood = maxOf(s.n("foodMin"), minOf(maxOf(0.0, leftAfterBills), leftAfterBills * pct))
        val foodHalf = Math.floor(rawFood / 2); val food = foodHalf * 2
        val perPayFood = round2(food / maxOf(1, regCount))
        val plannedSurplus = round2(spendable - billsTotal - food)
        var h1 = 0.0; var h2 = 0.0; var other = 0.0; var oneTime = 0.0; val byCat = Obj()
        for (e in expenses) { val a = e.n("amount"); val one = e.t("oneTime")
            if (FOOD.contains(e.s("category")) && !one) { if (e.s("date").substring(8, 10).toInt() <= 15) h1 += a else h2 += a }
            else { other += a; if (one) oneTime += a }
            val k = if (one) "oneoff" else e.s("category"); byCat[k] = js(round2(byCat.n(k) + a)) }
        h1 = round2(h1); h2 = round2(h2); other = round2(other); oneTime = round2(oneTime)
        val foodOver = round2(maxOf(0.0, h1 - foodHalf) + maxOf(0.0, h2 - foodHalf))
        val liveSurplus = round2(plannedSurplus - other - foodOver)
        /* the 3rd paycheck covers any shortfall first; net can be negative (money has to come out of savings) */
        val net = round2(liveSurplus + bonus); val target = round2(maxOf(0.0, net))
        return J.obj("ym" to ym, "closed" to false, "moved" to moved, "paid" to paid, "expenses" to expenses, "expensesLoaded" to expensesLoaded,
            "incomes" to incomes, "crAll" to crAll, "odspCalc" to odspCalc, "assistCalc" to assistCalc, "otherIncome" to otherIncome, "paydays" to paydays, "regularDays" to regularDays, "bonusDays" to bonusDays,
            "ryan" to ryan, "ryanDate" to ryanDate, "staceyPer" to staceyPer, "staceyRegular" to staceyRegular, "bonus" to bonus,
            "credits" to credits, "otherCredit" to otherCredit, "creditsTotal" to creditsTotal, "ccb" to ccb, "ccbToEm" to ccbToEm, "ccbChild" to ccbChild, "ccbOn" to ccbOn, "ccbDate" to ccbDate,
            "billLines" to billLines, "housing" to housing, "billsTotal" to billsTotal, "otherBills" to otherBills, "byGroup" to byGroup, "rentGap" to rentGap, "ryanLeft" to ryanLeft,
            "skippedTotal" to skippedTotal, "deferredTotal" to deferredTotal, "perPayBills" to perPayBills, "food" to food, "foodHalf" to foodHalf, "foodPct" to jsRound(pct * 100).toInt(),
            "leftAfterBills" to leftAfterBills, "perPayFood" to perPayFood, "spendable" to spendable, "plannedSurplus" to plannedSurplus, "net" to net, "foodH1" to h1, "foodH2" to h2,
            "otherSpent" to other, "oneTimeSpent" to oneTime, "byCat" to byCat, "foodOver" to foodOver, "liveSurplus" to liveSurplus, "target" to target)
    }

    /** What gets saved when a month is locked. */
    fun snapshot(c: Obj): Obj = Obj().also { o -> c.map.forEach { (k, v) -> if (k !in SNAP_SKIP) o[k] = v.copy() } }

    class Savings(val emergency: Double, val child: Double, val funds: Map<String, Double>)
    fun savingsState(st: Store): Savings {
        val s = st.settings; val funds = LinkedHashMap<String, Double>()
        for (f in fundsOf(s)) { val k = f.s("key"); funds[k] = when (k) { "emergency" -> s.n("emergencyStart"); "child" -> s.n("childStart"); else -> f.n("start") } }
        for ((k, m) in st.months) if (isYm(k)) { val mv = m["moved"]; if (truthy(mv)) (mv as? Obj)?.map?.forEach { (fk, v) -> funds[fk] = (funds[fk] ?: 0.0) + num(v) } }
        for (k in funds.keys.toList()) funds[k] = round2(funds[k]!!)
        return Savings(funds["emergency"] ?: 0.0, funds["child"] ?: 0.0, funds)
    }

    class Targets(val emergency: Double, val child: Double, val childSoFar: Double)
    /** Cumulative savings targets from the plan start through month upTo. */
    fun targetsThrough(st: Store, upTo: String, today: String): Targets {
        val s = st.settings; var em = s.n("emergencyStart"); var ch = s.n("childStart"); var chSoFar = s.n("childStart")
        if (isYm(s["startYm"]) && isYm(upTo)) { var guard = 0; var ym = s.s("startYm")
            while (ym <= upTo && guard < 240) { val c = monthCalc(ym, st, today)
                em += (if (c["net"] != null && c["net"] != J.Null) c.n("net") else c.n("target")) + c.n("ccbToEm")
                val cc = if (c["ccbChild"] != null && c["ccbChild"] != J.Null) c.n("ccbChild") else c.n("ccb"); ch += cc
                if (cc > 0 && c.s("ccbDate") <= today) chSoFar += cc
                ym = ymAdd(ym, 1); guard++ } }
        return Targets(round2(em), round2(ch), round2(chSoFar))
    }

    fun projection(st: Store, today: String, from: String, count: Int): List<Obj> {
        val bal = savingsState(st); val first = targetsThrough(st, from, today); var em = 0.0; var ch = 0.0; val rows = mutableListOf<Obj>()
        for (i in 0 until count) { val ym = ymAdd(from, i); val c = monthCalc(ym, st, today)
            val netOr = if (c["net"] != null && c["net"] != J.Null) c.n("net") else c.n("target")
            if (i == 0) { em = round2(maxOf(bal.emergency, first.emergency)); ch = round2(maxOf(bal.child, first.child)) }
            else { em = round2(em + netOr + c.n("ccbToEm")); ch = round2(ch + (if (c["ccbChild"] != null && c["ccbChild"] != J.Null) c.n("ccbChild") else c.n("ccb"))) }
            rows.add(J.obj("ym" to ym, "income" to round2(c.n("spendable") + c.n("bonus")), "bills" to c.n("billsTotal"), "food" to c.n("food"), "spent" to round2(c.n("otherSpent") + c.n("foodOver")),
                "toSavings" to round2(netOr + c.n("ccbToEm")), "bonus" to c.n("bonus"), "surplus" to c.n("liveSurplus"), "em" to em, "ch" to ch, "ccb" to c.n("ccb"))) }
        return rows
    }

    /** When to move money between accounts, and how much. Everything is routed by the accounts' purposes. */
    fun moneyMoves(st: Store, from: String, to: String): List<Obj> {
        val s = st.settings; val raw = mutableListOf<Obj>(); val start = if (s.t("startYm")) s.s("startYm") else "0000-00"
        val A = accountsOf(s); fun nm(id: String?): String = acctById(s, id)?.s("name") ?: ""
        val main = acctFor(s, "main")?.s("id") ?: ""; val everyday = acctFor(s, "everyday")?.s("id") ?: ""
        val cashAcct = A.find { it.s("type") == "cash" }?.s("id") ?: ""
        fun acctOf(ref: String): String = acctId(s, ref).ifEmpty { main }
        fun add(date: String?, fromId: String, toId: String, amount0: Double, why: String, kind: String = "") {
            val amount = round2(amount0); if (date == null || !(amount > 0.004 && date >= from && date <= to)) return
            val fk = if (kind.isNotEmpty()) kind.replaceFirst("-out", "") else ""; val out = kind.isNotEmpty() && kind.endsWith("-out"); val toFund = kind.isNotEmpty() && !out
            val fCode = FUNDCODE[fk] ?: ("f" + fk.take(20))
            val cFrom = if (out) fCode else fromId; val cTo = if (toFund) fCode else toId
            if (cFrom.isEmpty() || cTo.isEmpty() || cFrom == cTo) return
            raw.add(J.obj("date" to date, "code" to "$cFrom-$cTo", "from" to (if (out) fundOf(s, fk).s("name") else nm(fromId)), "to" to (if (toFund) fundOf(s, fk).s("name") else nm(toId)),
                "fromAcct" to (if (out) nm(fundOf(s, fk).s("account")) else ""), "toAcct" to (if (toFund) nm(fundOf(s, fk).s("account")) else ""), "amount" to amount, "why" to why, "sav" to kind))
        }
        fun paysAround(ym: String) = mainPaydays(s, ymAdd(ym, -1)) + mainPaydays(s, ym)
        fun lastPayOnOrBefore(d: String): String? = paysAround(ymOf(d)).filter { it <= d }.lastOrNull()
        fun why(id: String): String { val a = acctById(s, id); val p = a?.get("purposes").arr().map { str(it) }
            return if (p.contains("online")) "Online bills and subscriptions due before next payday" else if (a.s("type") == "cash") "Cash bills before next payday" else "Bills paid from " + (a.or("name", "this account")) + " before next payday" }
        var ym = ymAdd(ymOf(from), -1)
        while (ym <= ymAdd(ymOf(to), 1)) {
            val c = monthCalc(ym, st, from); val first = "$ym-01"; val last = dayIn(ym, 31)
            val all = billEvents(st, first, last); val ev = all.filter { !it.t("fund") }
            /* bills that pay into a savings fund become moves into that fund */
            all.filter { it.t("fund") }.forEach { x -> add(x.s("date"), x.s("acct").ifEmpty { main }, "", x.n("amount"), x.s("name"), x.s("fund")) }
            /* cash bills: withdraw from the account that pays them */
            if (cashAcct.isNotEmpty()) ev.filter { it.s("method") == "Cash" && it.t("acct") && it.s("acct") != cashAcct }.forEach { x -> add(x.s("date"), x.s("acct"), cashAcct, x.n("amount"), "Withdraw cash for " + x.s("name")) }
            /* any account that receives income (other than the main account) pays its own bills first */
            class In(val date: String, val amount: Double, val name: String)
            val ins = LinkedHashMap<String, MutableList<In>>()
            for (inc in c["incomes"].arr()) { val a = acctOf(inc.s("account")); if (a == main) continue
                for (d in inc.strList("regular") + inc.strList("bonusDates")) ins.getOrPut(a) { mutableListOf() }.add(In(d, inc.n("amount"), inc.s("name"))) }
            for (cr in c["credits"].arr()) { val a = acctOf(cr.s("account")); if (a == main) continue
                for (d in cr.strList("dates")) ins.getOrPut(a) { mutableListOf() }.add(In(d, cr.n("amount"), cr.s("name"))) }
            for (a in ins.keys) { val list = ins.getValue(a).sortedBy { it.date }; val total = sumOf2(list.map { it.amount }); val d0 = list[0].date
                val need = sumOf2(ev.filter { it.s("acct") == a }.map { it.n("amount") }); val diff = round2(total - need)
                if (need <= 0) list.forEach { x -> add(x.date, a, main, x.amount, x.name + " into the main account") }
                else if (diff > 0) add(d0, a, main, diff, "What's left of ${list.map { it.name }.distinct().joinToString(" and ")} after its bills")
                else if (diff < 0) add(lastPayOnOrBefore(d0) ?: d0, main, a, -diff, "Top up so its bills are covered on " + Fmt.dShort(d0)) }
            val incomeAccts = ins.keys.toSet()
            /* each payday: fund every other account for the bills due before the next payday */
            for (P in c.strList("paydays")) {
                val nxt = (paysAround(ymAdd(ymOf(P), 1)) + mainPaydays(s, ymAdd(ymOf(P), 2))).find { it > P }
                val win = billEvents(st, P, if (nxt != null) addDays(nxt, -1) else addDays(P, 13)).filter { !it.t("fund") }
                val accts = win.map { it.s("acct") }.filter { it.isNotEmpty() && it != main && it !in incomeAccts }.distinct()
                accts.forEach { id -> add(P, main, id, sumOf2(win.filter { it.s("acct") == id }.map { it.n("amount") }), why(id)) }
            }
            /* extra paydays go to savings after any shortfall is covered */
            if (ym >= start) { var room = maxOf(0.0, c.n("net"))
                for (inc in c["incomes"].arr()) for (d in inc.strList("bonusDates")) { val amt = inc.n("amount"); val toSave = minOf(amt, room); room = round2(room - toSave)
                    add(d, main, "", toSave, if (toSave < amt - 0.004) "Extra ${inc.s("name")} this month, after covering a shortfall" else "Extra ${inc.s("name")} this month", "emergency") } }
            /* food money for each half of the month, sent on the payday before it starts */
            if (ym >= start && c.n("foodHalf") > 0 && everyday.isNotEmpty()) listOf(first, "$ym-16").forEachIndexed { i, H ->
                add(lastPayOnOrBefore(H) ?: H, main, everyday, c.n("foodHalf"), "Food for ${if (i > 0) "the 16th–end" else "the 1st–15th"} of ${Fmt.monthOnly(ym)}") }
            /* savings */
            if (ym >= start) { val bonusSaved = maxOf(0.0, minOf(c.n("bonus"), c.n("net"))); val movedReg = maxOf(0.0, c["moved"].n("emergency") - bonusSaved)
                add(last, main, "", maxOf(0.0, c.n("liveSurplus") - movedReg), "What's left this month", "emergency")
                if (c.n("net") < 0) { val fp = c.strList("regularDays").firstOrNull() ?: first
                    add(fp, "", main, -c.n("net"), "Cover this month's shortfall (if savings can't, the Child Reserve is the last resort)", "emergency-out") } }
            if (c.n("ccb") > 0) { val pa = acctOf(c["crAll"].arr().find { it.t("protect") && it["dates"].arr().isNotEmpty() }.s("account"))
                add(c.s("ccbDate"), pa, "", c.n("ccbChild"), "Child benefits (protected)", "child")
                add(c.s("ccbDate"), pa, "", c.n("ccbToEm"), "Your share of the child benefits for Emergency Savings", "emergency") }
            ym = ymAdd(ym, 1)
        }
        val map = LinkedHashMap<String, Obj>()
        for (r in raw) { val k = r.s("code") + "@" + r.s("date"); val m = map[k]
            if (m != null) { m["amount"] = js(round2(m.n("amount") + r.n("amount"))); m["why"] = js(m.s("why") + " · " + r.s("why")) }
            else map[k] = J.obj("key" to k, "ym" to ymOf(r.s("date"))).also { o -> r.map.forEach { (kk, v) -> o[kk] = v } } }
        return map.values.map { m -> val mm = st.months[m.s("ym")]; m["done"] = js(mm != null && truthy(mm["moves"]?.get(m.s("key")))); m }
    }

    fun upcoming(st: Store, today: String, days: Int, labelOf: ((Obj) -> String)? = null): List<Obj> {
        val s = st.settings; val end = addDays(today, days)
        var from = addDays(today, -21); if (isDate(s["trackFrom"]) && from < s.s("trackFrom")) from = s.s("trackFrom")
        val out = mutableListOf<Obj>()
        for (e in billEvents(st, from, end)) { if (e.t("fund")) continue; val m = st.months[e.s("ym")]; val paid = m != null && truthy(m["paid"]?.get(e.s("key")))
            if (e.s("date") < today && paid) continue
            out.add(J.obj("kind" to "bill", "paid" to paid, "overdue" to (e.s("date") < today && !paid)).also { o -> e.map.forEach { (k, v) -> if (k != "kind" && k != "paid" && k != "overdue") o[k] = v } }) }
        var mfrom = addDays(today, -7); if (isDate(s["trackFrom"]) && mfrom < s.s("trackFrom")) mfrom = s.s("trackFrom")
        for (mv in moneyMoves(st, mfrom, end)) { if (mv.s("date") < today && mv.t("done")) continue
            out.add(J.obj("kind" to "move", "overdue" to (mv.s("date") < today && !mv.t("done"))).also { o -> mv.map.forEach { (k, v) -> if (k != "kind" && k != "overdue") o[k] = v } }) }
        for ((ym, mp) in st.expenses) for (e in listExp(mp))
            if (e.t("oneTime") && e.s("date") >= today && e.s("date") <= end) out.add(J.obj("kind" to "oneoff", "date" to e.s("date"), "id" to e["id"], "ym" to ym,
                "name" to (if (e.t("note")) e.s("note") else labelOf?.invoke(e) ?: "One-time expense"), "amount" to round2(e.n("amount")), "group" to e.or("group", "household"), "account" to e.or("account", "")))
        val yms = mutableListOf(ymOf(today)); if (ymOf(end) != ymOf(today)) yms.add(ymOf(end))
        fun inR(d: String) = d >= today && d <= end
        for (ym in yms) { val c = monthCalc(ym, st, today)
            fun lands(ref: String): String { val a = acctById(s, acctId(s, ref).ifEmpty { acctFor(s, "main")?.s("id") ?: "" }); return if (a != null) "Lands in " + a.s("name") else "" }
            for (inc in c["incomes"].arr()) { val bd = inc.strList("bonusDates")
                for (d in inc.strList("dates")) if (inR(d)) { val bonus = bd.contains(d)
                    out.add(J.obj("kind" to "in", "date" to d, "name" to inc.s("name") + (if (bonus) " (extra this month)" else ""),
                        "note" to listOf(lands(inc.s("account")), if (inc.s("role") == "rent") "Covers rent first" else "", if (bonus) "Extra payday: goes to savings" else "", if (inc.t("estimated")) "Date is an estimate" else "").filter { it.isNotEmpty() }.joinToString(" · "),
                        "amount" to inc.n("amount"), "bonus" to bonus)) } }
            for (cr in c["credits"].arr()) for (d in cr.strList("dates")) if (cr.n("amount") > 0 && inR(d))
                out.add(J.obj("kind" to "in", "date" to d, "name" to cr.s("name"), "note" to listOf("Spendable credit", lands(cr.s("account")), if (cr.t("estimated")) "Date is an estimate" else "").filter { it.isNotEmpty() }.joinToString(" · "), "amount" to cr.n("amount")))
            for (cr in c["crAll"].arr().filter { it.t("protect") }) for (d in cr.strList("dates")) if (cr.n("amount") > 0 && inR(d))
                out.add(J.obj("kind" to "in", "date" to d, "name" to cr.s("name"), "note" to "Protected for the Child Reserve · " + lands(cr.s("account")), "amount" to cr.n("amount"), "protect" to true))
        }
        val rank = mapOf("in" to 0, "move" to 1, "bill" to 2, "oneoff" to 3)
        return out.sortedWith { a, b -> val da = a.s("date"); val db = b.s("date")
            if (da < db) -1 else if (da > db) 1 else (rank[a.s("kind")] ?: 0) - (rank[b.s("kind")] ?: 0) }
    }

    /* ---------- debt payoff ----------
       original: the amount first owed, the start date, interest and missed payments; every payment since is replayed
       balance:  a statement balance on a date; payments after that date are replayed
       Interest is simple daily interest on the balance between payments. Missed payments count as the earliest ones. */
    class DebtParts(val orig: Boolean, val P: Double, val start: String?, val rate: Double, val missed: Int, val extra: List<Pair<String, Double>>)
    fun debtParts(d: J?): DebtParts? {
        if (!truthy(d)) return null
        val orig = d.s("mode") == "original" || (d?.get("mode").let { it == null || it == J.Null } && d?.get("principal").let { it != null && it != J.Null } && d?.get("owed").let { it == null || it == J.Null })
        val extra = d?.get("extra").arr().filter { truthy(it) && isDate(it["date"]) && it.n("amount") > 0 }.map { it.s("date") to round2(it.n("amount")) }.sortedBy { it.first }
        val st = if (orig) d?.get("start") else d?.get("asOf")
        return DebtParts(orig, if (orig) d.n("principal") else d.n("owed"), if (isDate(st)) str(st) else null, maxOf(0.0, d.n("rate")), if (orig) maxOf(0, Math.floor(d.n("missed")).toInt()) else 0, extra)
    }

    private class Ev(val date: String, val amount: Double, val x: Boolean)
    /* same order as the web version: by date, extra payments before regular ones on the same day, otherwise as listed */
    private val evOrder = compareBy<Ev>({ it.date }, { if (it.x) 0 else 1 })

    fun debtInfo(st: Store, id: String, today: String): Obj? {
        val b = st.bills[id] ?: return null; if (!b.t("debt") || !(b.n("amount") > 0)) return null
        val dp = debtParts(b["debt"]) ?: return null; if (!(dp.P > 0)) return null
        val start = dp.start ?: today; val r = dp.rate / 100; val one = st.withBills(mapOf(id to b))
        fun accrue(bal: Double, from: String, to: String) = if (r > 0 && to > from) bal * r * diffDays(from, to) / 365 else 0.0
        /* the past: start → today. Extra payments count from the start date itself; regular ones from the day after */
        val past = ((if (start < today) billEvents(one, addDays(start, 1), today) else emptyList()).map { Ev(it.s("date"), it.n("amount"), false) } +
            dp.extra.filter { it.first >= start && it.first <= today }.map { Ev(it.first, it.second, true) }).sortedWith(evOrder)
        var bal = dp.P; var last = start; var paid = 0.0; var intSoFar = 0.0; var made = 0; var missedUsed = 0; var xPaid = 0.0; var xCount = 0
        for (e in past) { val i = accrue(bal, last, e.date); bal += i; intSoFar += i; last = e.date
            if (!e.x && missedUsed < dp.missed) { missedUsed++; continue }
            val pay = minOf(bal, e.amount); bal -= pay; paid += pay; if (e.x) { xPaid += pay; xCount++ } else made++
            if (bal <= 0.004) { bal = 0.0; break } }
        if (bal > 0) { val i = accrue(bal, last, today); bal += i; intSoFar += i }
        val remaining = round2(bal)
        /* the future: today → paid off */
        var left = bal; var n = 0; var finish: String? = null; var futInt = 0.0; var lastF = today; var ym = ymOf(today); var stuck = false; var xFuture = 0.0
        var k = 0
        while (k < 480 && left > 0.004 && !stuck) {
            val from = if (k == 0) addDays(today, 1) else "$ym-01"; val to = dayIn(ym, 31)
            val evs = (billEvents(one, from, to).map { Ev(it.s("date"), it.n("amount"), false) } + dp.extra.filter { it.first >= from && it.first <= to }.map { Ev(it.first, it.second, true) }).sortedWith(evOrder)
            for (e in evs) { if (left <= 0.004) break
                val i = accrue(left, lastF, e.date)
                if (!e.x && r > 0 && e.amount <= i + 0.004 && dp.extra.none { it.first > e.date }) { stuck = true; break }
                left += i; futInt += i; lastF = e.date; left = round2(left - minOf(left, e.amount)); if (e.x) xFuture += e.amount else n++; finish = e.date }
            ym = ymAdd(ym, 1); k++
        }
        return J.obj("mode" to (if (dp.orig) "original" else "balance"), "principal" to dp.P, "owed" to dp.P, "start" to start, "asOf" to start, "rate" to dp.rate, "missed" to missedUsed, "missedAsked" to dp.missed,
            "paid" to round2(paid), "made" to made, "extraPaid" to round2(xPaid), "extraCount" to xCount, "extraFuture" to round2(xFuture), "interestSoFar" to round2(intSoFar), "remaining" to remaining,
            "payments" to n, "finish" to (if (left <= 0.004) finish else null), "futureInterest" to round2(futInt), "stuck" to stuck)
    }

    fun foodPeriod(c: J, today: String): Obj {
        val ym = if (c.t("ym")) c.s("ym") else ymOf(today); val day = today.substring(8, 10).toInt(); val n = dim(ym); val first = day <= 15
        val len = if (first) 15 else n - 15; val idx = if (first) day else day - 15; val allow = c.n("foodHalf")
        val spent = if (first) c.n("foodH1") else c.n("foodH2"); val left = round2(allow - spent); val daysLeft = len - idx + 1
        return J.obj("half" to (if (first) 1 else 2), "allow" to allow, "spent" to spent, "left" to left, "target" to round2(allow / len), "expected" to round2(allow * idx / len), "daysLeft" to daysLeft,
            "perDay" to round2(maxOf(0.0, left) / maxOf(1, daysLeft)), "end" to (if (first) "$ym-15" else dayIn(ym, 31)), "len" to len)
    }
}
