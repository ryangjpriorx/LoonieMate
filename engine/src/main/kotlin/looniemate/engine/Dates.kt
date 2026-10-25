package looniemate.engine

import java.time.LocalDate

/* Dates are plain "YYYY-MM-DD" strings and months "YYYY-MM", exactly like the web version,
   so they compare correctly as text and match the saved data. */

const val JS_EPSILON = 2.220446049250313E-16

fun pad(n: Int): String = if (n < 10) "0$n" else n.toString()

/** JavaScript `Math.round` (halves go up toward +infinity). */
fun jsRound(x: Double): Double = Math.floor(x + 0.5)

fun round2(n: Double): Double = jsRound((n + JS_EPSILON) * 100) / 100

fun ymd(d: LocalDate): String = "${d.year}-${pad(d.monthValue)}-${pad(d.dayOfMonth)}"

/** Like `new Date(y, m-1, d||1)`: out-of-range parts roll over the same way. */
fun parseDate(s: String): LocalDate {
    val p = s.split('-').map { it.toIntOrNull() ?: 0 }
    val y = p.getOrElse(0) { 1970 }; val m = p.getOrElse(1) { 1 }; val d = p.getOrElse(2) { 0 }.let { if (it == 0) 1 else it }
    return LocalDate.of(y, 1, 1).plusMonths((m - 1).toLong()).plusDays((d - 1).toLong())
}

fun ymOf(s: String): String = if (s.length >= 7) s.substring(0, 7) else s

fun ymAdd(ym: String, n: Int): String {
    var y = ym.substring(0, 4).toInt(); var m = ym.substring(5, 7).toInt() + n
    while (m > 12) { m -= 12; y++ }
    while (m < 1) { m += 12; y-- }
    return "$y-${pad(m)}"
}

fun dim(ym: String): Int = LocalDate.of(ym.substring(0, 4).toInt(), ym.substring(5, 7).toInt(), 1).lengthOfMonth()

fun addDays(s: String, n: Int): String = ymd(parseDate(s).plusDays(n.toLong()))

fun diffDays(a: String, b: String): Int = (parseDate(b).toEpochDay() - parseDate(a).toEpochDay()).toInt()

fun dayIn(ym: String, day: Double): String = ym + "-" + pad(minOf(maxOf(1, Math.floor(day).toInt().let { if (it == 0) 1 else it }), dim(ym)))
fun dayIn(ym: String, day: Int): String = dayIn(ym, day.toDouble())
fun dayIn(ym: String, day: J?): String = dayIn(ym, num(day))

private val YM = Regex("^\\d{4}-\\d{2}$")
private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
fun isYm(k: String?): Boolean = k != null && YM.matches(k)
fun isDate(k: String?): Boolean = k != null && DATE.matches(k)
fun isDate(v: J?): Boolean = v is J.S && isDate(v.v)
fun isYm(v: J?): Boolean = v is J.S && isYm(v.v)

fun sumOf2(list: Iterable<Double>): Double = round2(list.fold(0.0) { t, x -> t + (if (x.isFinite()) x else 0.0) })

/** JavaScript day of week: 0 = Sunday … 6 = Saturday. */
fun jsDow(d: LocalDate): Int = d.dayOfWeek.value % 7
fun jsDow(s: String): Int = jsDow(parseDate(s))

fun nthDow(y: Int, m: Int, dow: Int, n: Int): String {
    val d = LocalDate.of(y, m, 1)
    val off = (dow - jsDow(d) + 7) % 7
    return ymd(LocalDate.of(y, m, 1).plusDays((off + 7 * (n - 1)).toLong()))
}

fun easter(y: Int): String {
    val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
    val hh = (19 * a + b - d - g + 15) % 30; val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - hh - k) % 7
    val mm = (a + 11 * hh + 22 * l) / 451; val mo = (hh + l - 7 * mm + 114) / 31; val day = ((hh + l - 7 * mm + 114) % 31) + 1
    return ymd(LocalDate.of(y, mo, day))
}

/* ---- display formats (match the web version's en-CA output) ---- */
object Fmt {
    private val MON = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val MONTH = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val WD = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val WEEKDAY = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    /** $1,234.56 with a real minus sign, like the web version. */
    fun money(n: Double): String {
        val v = round2(if (n.isFinite()) n else 0.0)
        val abs = Math.abs(v)
        val s = String.format(java.util.Locale.US, "%,.2f", abs)
        return (if (v < 0) "−" else "") + "$" + s
    }
    fun money0(n: Double): String {
        val v = jsRound(if (n.isFinite()) n else 0.0)
        return (if (v < 0) "−" else "") + "$" + String.format(java.util.Locale.US, "%,d", Math.abs(v).toLong())
    }
    fun monthName(ym: String) = MONTH[ym.substring(5, 7).toInt() - 1] + " " + ym.substring(0, 4)
    fun monthShort(ym: String) = MON[ym.substring(5, 7).toInt() - 1]
    fun monthOnly(ym: String) = MONTH[ym.substring(5, 7).toInt() - 1]
    fun dShort(d: String): String { val x = parseDate(d); return MON[x.monthValue - 1] + " " + x.dayOfMonth }
    fun dLabel(d: String): String { val x = parseDate(d); return WD[jsDow(x)] + ", " + MON[x.monthValue - 1] + " " + x.dayOfMonth }
    fun weekday(d: String): String = WEEKDAY[jsDow(d)]
    fun monthYearLong(d: String): String { val x = parseDate(d); return MONTH[x.monthValue - 1] + " " + x.year }
    fun monthYearShort(d: String): String { val x = parseDate(d); return MON[x.monthValue - 1] + " " + x.year }
    fun longDate(d: String): String { val x = parseDate(d); return MONTH[x.monthValue - 1] + " " + x.dayOfMonth }
    fun relDay(d: String, today: String): String = when (diffDays(today, d)) { 0 -> "Today"; 1 -> "Tomorrow"; -1 -> "Yesterday"; else -> dLabel(d) }
    fun ord(n: Int): String { val s = arrayOf("th", "st", "nd", "rd"); val v = n % 100
        val idx = (v - 20) % 10; return n.toString() + (if (idx in 1..3) s[idx] else if (v in 1..3) s[v] else s[0]) }
}
