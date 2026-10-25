package looniemate.engine

/**
 * A small JSON tree that behaves like the web version's JavaScript objects.
 * Documents are kept as-is (unknown fields survive a round trip), and the
 * helpers below read values with the same loose rules JavaScript uses.
 */
sealed class J {
    object Null : J()
    data class B(val v: Boolean) : J()
    data class N(val v: Double) : J()
    data class S(val v: String) : J()
    class A(val list: MutableList<J> = mutableListOf()) : J()
    class O(val map: LinkedHashMap<String, J> = LinkedHashMap()) : J() {
        operator fun set(key: String, value: J?) { if (value == null) map.remove(key) else map[key] = value }
        fun put(key: String, value: Any?): O { this[key] = J.of(value); return this }
    }

    operator fun get(key: String): J? = (this as? O)?.map?.get(key)
    operator fun get(i: Int): J? = (this as? A)?.list?.getOrNull(i)

    override fun toString(): String = Json.stringify(this)

    companion object {
        /** Wraps plain Kotlin values (numbers, strings, lists, maps) as JSON. */
        fun of(v: Any?): J = when (v) {
            null -> Null
            is J -> v
            is Boolean -> B(v)
            is Number -> N(v.toDouble())
            is String -> S(v)
            is Map<*, *> -> O(LinkedHashMap<String, J>().also { m -> v.forEach { (k, x) -> if (x != Unit) m[k.toString()] = of(x) } })
            is Iterable<*> -> A(v.map { of(it) }.toMutableList())
            is Array<*> -> A(v.map { of(it) }.toMutableList())
            is JsonAble -> v.toJ()
            else -> S(v.toString())
        }
        fun obj(vararg pairs: Pair<String, Any?>): O = O().also { o -> pairs.forEach { (k, x) -> o[k] = of(x) } }
    }
}

interface JsonAble { fun toJ(): J }

/* ---- JavaScript-style reading ---- */

/** JavaScript `Number(v)`, with anything not finite turned into 0 (the web version's `num`). */
fun num(v: J?): Double = when (v) {
    null, J.Null -> 0.0
    is J.B -> if (v.v) 1.0 else 0.0
    is J.N -> if (v.v.isFinite()) v.v else 0.0
    is J.S -> jsNumber(v.v)?.takeIf { it.isFinite() } ?: 0.0
    is J.A -> if (v.list.isEmpty()) 0.0 else if (v.list.size == 1) num(v.list[0]) else 0.0
    is J.O -> 0.0
}

/** `isFinite(Number(v))` in JavaScript. */
fun jsFinite(v: J?): Boolean = when (v) {
    null -> false            // Number(undefined) is NaN
    J.Null -> true           // Number(null) is 0
    is J.B -> true
    is J.N -> v.v.isFinite()
    is J.S -> jsNumber(v.v)?.isFinite() ?: false
    is J.A -> v.list.isEmpty() || (v.list.size == 1 && jsFinite(v.list[0]))
    is J.O -> false
}

private fun jsNumber(s: String): Double? {
    val t = s.trim()
    if (t.isEmpty()) return 0.0
    if (t == "Infinity" || t == "+Infinity") return Double.POSITIVE_INFINITY
    if (t == "-Infinity") return Double.NEGATIVE_INFINITY
    if (t.startsWith("0x") || t.startsWith("0X")) return t.substring(2).toLongOrNull(16)?.toDouble()
    if (!Regex("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?$").matches(t)) return null
    return t.toDoubleOrNull()
}

/** JavaScript truthiness. */
fun truthy(v: J?): Boolean = when (v) {
    null, J.Null -> false
    is J.B -> v.v
    is J.N -> v.v != 0.0 && !v.v.isNaN()
    is J.S -> v.v.isNotEmpty()
    else -> true
}

/** `String(v)` for values that should be text; missing values become "". */
fun str(v: J?): String = when (v) {
    null, J.Null -> ""
    is J.S -> v.v
    is J.N -> Json.numStr(v.v)
    is J.B -> v.v.toString()
    else -> Json.stringify(v)
}

fun J?.obj(): J.O? = this as? J.O
fun J?.arr(): List<J> = (this as? J.A)?.list ?: emptyList()
fun J?.isFalse(): Boolean = this is J.B && !this.v
fun J?.isTrue(): Boolean = this is J.B && this.v
fun J?.isMissing(): Boolean = this == null || this == J.Null || (this is J.S && this.v.isEmpty())

/* ---- parsing and writing ---- */

object Json {
    fun parse(text: String): J = Parser(text).run { val v = value(); ws(); if (i != s.length) err("trailing text"); v }

    fun stringify(v: J?): String = StringBuilder().also { write(it, v ?: J.Null) }.toString()

    /** Formats a number the way JavaScript's JSON.stringify does for normal budget values. */
    fun numStr(d: Double): String {
        if (!d.isFinite()) return "null"
        if (d == Math.floor(d) && Math.abs(d) < 1e21) {
            if (d == 0.0) return "0"
            return java.math.BigDecimal(d).toPlainString()
        }
        // shortest round-trip form, like JavaScript
        var s = d.toString()
        if (s.contains('E')) {
            val bd = java.math.BigDecimal(s)
            val abs = Math.abs(d)
            s = if (abs >= 1e-6 && abs < 1e21) bd.toPlainString() else {
                val m = s.substringBefore('E'); val e = s.substringAfter('E').toInt()
                (if (m.endsWith(".0")) m.dropLast(2) else m) + "e" + (if (e >= 0) "+" else "") + e
            }
        }
        return s
    }

    private fun write(sb: StringBuilder, v: J) {
        when (v) {
            J.Null -> sb.append("null")
            is J.B -> sb.append(v.v)
            is J.N -> sb.append(numStr(v.v))
            is J.S -> quote(sb, v.v)
            is J.A -> { sb.append('['); v.list.forEachIndexed { i, x -> if (i > 0) sb.append(','); write(sb, x) }; sb.append(']') }
            is J.O -> { sb.append('{'); var first = true
                for ((k, x) in v.map) { if (!first) sb.append(','); first = false; quote(sb, k); sb.append(':'); write(sb, x) }
                sb.append('}') }
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t"); '\b' -> sb.append("\\b"); '\u000C' -> sb.append("\\f")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0
        fun err(m: String): Nothing = throw IllegalArgumentException("Bad JSON at $i: $m")
        fun ws() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t' || s[i] == '﻿')) i++ }
        fun value(): J {
            ws(); if (i >= s.length) err("end of text")
            return when (val c = s[i]) {
                '{' -> { i++; val o = J.O(); ws()
                    if (s.getOrNull(i) == '}') { i++; return o }
                    while (true) { ws(); val k = string(); ws(); if (s.getOrNull(i) != ':') err("expected :"); i++
                        o.map[k] = value(); ws()
                        when (s.getOrNull(i)) { ',' -> i++; '}' -> { i++; return o }; else -> err("expected , or }") } }
                    @Suppress("UNREACHABLE_CODE") o }
                '[' -> { i++; val a = J.A(); ws()
                    if (s.getOrNull(i) == ']') { i++; return a }
                    while (true) { a.list.add(value()); ws()
                        when (s.getOrNull(i)) { ',' -> i++; ']' -> { i++; return a }; else -> err("expected , or ]") } }
                    @Suppress("UNREACHABLE_CODE") a }
                '"' -> J.S(string())
                't' -> { expect("true"); J.B(true) }
                'f' -> { expect("false"); J.B(false) }
                'n' -> { expect("null"); J.Null }
                else -> if (c == '-' || c.isDigit()) number() else err("unexpected '$c'")
            }
        }
        fun expect(w: String) { if (!s.startsWith(w, i)) err("expected $w"); i += w.length }
        fun number(): J {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return J.N(s.substring(st, i).toDoubleOrNull() ?: err("bad number"))
        }
        fun string(): String {
            if (s.getOrNull(i) != '"') err("expected string"); i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) err("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> { val e = s[i++]
                        when (e) { 'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t'); 'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C'); 'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e) } }
                    else -> sb.append(c)
                }
            }
        }
    }
}

/** Deep copy. */
fun J.copy(): J = when (this) {
    is J.A -> J.A(list.map { it.copy() }.toMutableList())
    is J.O -> J.O(LinkedHashMap<String, J>().also { m -> map.forEach { (k, v) -> m[k] = v.copy() } })
    else -> this
}
