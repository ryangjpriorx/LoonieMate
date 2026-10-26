package looniemate.engine

/*
 * What a reminder notification should say, worked out from the budget at the moment it fires.
 * Kept free of Android code so the rules can be tested. See docs/plans/reminders-and-updates.md (Stage 4).
 *
 * Rules:
 *  - To-dos are bills that need paying (not automatic payments) and money moves, due on the waking day or overdue.
 *  - Automatic payments (PAD) get a heads-up N days ahead instead, moved earlier to a business day, grouped by account.
 *    Heads-ups only appear in the first reminder of the day; follow-ups are only to-dos.
 *  - A reminder belongs to the waking day it started in: a follow-up whose time is earlier than the day's first
 *    reminder time (after midnight) still covers the previous calendar day.
 *  - Nothing to say means no notification.
 */
object Reminders {
    const val PAD = "Pre-authorized debit (PAD)"

    class Config(
        /** minutes after midnight, first reminder first; up to three */
        val times: List<Int> = listOf(8 * 60 + 30, 19 * 60),
        /** different times on Saturday and Sunday, or null for the same every day */
        val weekendTimes: List<Int>? = null,
        /** how many days ahead to warn about automatic payments (0 turns it off) */
        val padDays: Int = 2,
        /** in the last reminder of the day, mention money moves due tomorrow */
        val movesEveningBefore: Boolean = false,
    )

    class Item(val kind: String, val key: String, val ym: String, val date: String, val name: String, val amount: Double,
               val overdue: Boolean, val account: String, val locked: Boolean, val sav: String = "")
    class PadGroup(val account: String, val by: String, val total: Double, val items: List<Item>, val movesIn: List<Item>)
    class Digest(val day: String, val todo: List<Item>, val pads: List<PadGroup>, val tomorrowMoves: List<Item>) {
        val isEmpty get() = todo.isEmpty() && pads.isEmpty() && tomorrowMoves.isEmpty()
    }

    fun isWeekend(d: String) = parseDate(d).dayOfWeek.value >= 6

    /** The reminder times for a calendar day. */
    fun timesFor(cfg: Config, day: String): List<Int> = (if (isWeekend(day) && cfg.weekendTimes != null) cfg.weekendTimes else cfg.times).take(3)

    /** The waking day a reminder at [minute] on [date] belongs to (the day before, for a follow-up after midnight). */
    fun wakingDay(cfg: Config, date: String, minute: Int): String {
        val prev = addDays(date, -1); val first = timesFor(cfg, prev).firstOrNull()
        return if (first != null && minute < first && timesFor(cfg, prev).drop(1).contains(minute)) prev else date
    }

    /** The day a heads-up for an automatic payment on [date] goes out: [days] before, moved earlier to a business day. */
    fun headsUpDay(date: String, days: Int): String { var d = addDays(date, -days); while (!Core.isBiz(d)) d = addDays(d, -1); return d }

    /**
     * What to say for reminder number [slot] (0 is the first of the day) on waking day [day].
     */
    fun digest(st: Store, day: String, slot: Int, slots: Int, cfg: Config): Digest {
        val s = st.settings
        val up = Core.upcoming(st, day, 14)
        fun acctName(ref: String) = Core.acctById(s, Core.acctId(s, ref).ifEmpty { Core.acctFor(s, "main")?.s("id") ?: "" })?.s("name") ?: "Main account"
        fun item(e: Obj, kind: String) = Item(kind, e.s("key"), e.s("ym"), e.s("date"), if (kind == "move") "Move ${Fmt.money(e.n("amount"))} to ${e.s("to")}" else e.s("name"),
            e.n("amount"), e.t("overdue"), if (kind == "move") e.s("from") else acctName(e.s("account")), st.months[e.s("ym")].t("closed"), e.s("sav"))
        val todo = up.filter { e -> e.s("date") <= day && when (e.s("kind")) {
                "bill" -> !e.t("paid") && e.s("method") != PAD
                "move" -> !e.t("done")
                else -> false } }
            .sortedWith(compareBy({ !it.t("overdue") }, { it.s("date") })).map { item(it, it.s("kind")) }
        var pads = emptyList<PadGroup>()
        if (slot == 0 && cfg.padDays > 0) {
            val due = up.filter { it.s("kind") == "bill" && it.s("method") == PAD && !it.t("paid") && it.s("date") > day && headsUpDay(it.s("date"), cfg.padDays) == day }.map { item(it, "pad") }
            pads = due.groupBy { it.account }.map { (acct, list) -> val by = list.minOf { it.date }
                val moves = up.filter { it.s("kind") == "move" && !it.t("done") && it.s("to") == acct && it.s("date") >= day && it.s("date") < by }.map { item(it, "move") }
                PadGroup(acct, by, sumOf2(list.map { it.amount }), list.sortedBy { it.date }, moves) }
        }
        val tomorrow = if (cfg.movesEveningBefore && slot == slots - 1 && slots > 1)
            up.filter { it.s("kind") == "move" && !it.t("done") && it.s("date") == addDays(day, 1) }.map { item(it, "move") } else emptyList()
        return Digest(day, todo, pads, tomorrow)
    }

    /** The digest without items already handled on the notification (by key; automatic payment groups as "pad:<account>"). */
    fun without(d: Digest, keys: Set<String>): Digest = Digest(d.day, d.todo.filter { it.key !in keys }, d.pads.filter { "pad:" + it.account !in keys },
        d.tomorrowMoves.filter { it.key !in keys })

    /** The one-line summary for the notification: "3 things today" and so on. */
    fun summary(d: Digest): String {
        val n = d.todo.size; val over = d.todo.count { it.overdue }
        val parts = mutableListOf<String>()
        if (n > 0) parts.add(if (n == 1) "1 thing to do today" else "$n things to do today")
        if (over > 0) parts.add("$over overdue")
        if (d.pads.isNotEmpty()) parts.add(if (d.pads.size == 1) "a payment comes out soon" else "${d.pads.size} accounts have payments coming out")
        if (d.tomorrowMoves.isNotEmpty() && n == 0) parts.add("money to move tomorrow")
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }

    /** "Coming out of Main Chequing by Wednesday: Bell $95.00 and Hydro $84.00, $179.00 in all." */
    fun padLine(g: PadGroup): String {
        val names = g.items.map { "${it.name} ${Fmt.money(it.amount)}" }
        val list = if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()
        val day = Fmt.weekday(g.by)
        return "Coming out of ${g.account} by $day: $list" + (if (g.items.size > 1) ", ${Fmt.money(g.total)} in all" else "") + "." +
            g.movesIn.firstOrNull()?.let { " After you move ${Fmt.money(it.amount)} to it on ${Fmt.weekday(it.date)}." }.orEmpty()
    }
}
