package looniemate.engine

/*
 * Keeping the budget the same on two phones through one shared file (docs/plans/sync.md).
 *
 * A three-way merge: each phone remembers the data as it was after its last sync (the base). For every item and every
 * field inside it, if only one side changed it since the base, that change wins; if both did, the newer document wins.
 * That lets an Undo on one phone stick on the other, which the plain combine (Backup.merge, which keeps a Paid tick if
 * either copy has it) can't do. Savings totals in a month (`moved`) add up both phones' changes instead of picking one.
 * Deletions carry over through tombstones, as before; an item merely missing from the file, or a copy in the file older
 * than the base, means the file is stale, and this phone's copy is kept. With no base yet (the first sync), it uses Backup.merge.
 */
object Sync {
    class Result(val data: LinkedHashMap<String, J>, val changedLocal: Boolean, val changedRemote: Boolean)

    private const val TOMB = "meta/tombstones"
    private val MONTH_DOC = Regex("^months/\\d{4}-\\d{2}$")

    /** JSON with object keys sorted, so two copies with the same content compare equal whatever their order. */
    fun canon(v: J?): String = when (v) {
        null, J.Null -> "null"
        is J.O -> v.map.keys.sorted().joinToString(",", "{", "}") { Json.stringify(J.S(it)) + ":" + canon(v.map[it]) }
        is J.A -> v.list.joinToString(",", "[", "]") { canon(it) }
        else -> Json.stringify(v)
    }
    private fun same(a: J?, b: J?) = canon(a) == canon(b)
    private fun absent(v: J?) = v == null || v == J.Null

    /** Three-way merge of one value. [rightNewer] settles a change on both sides. */
    private fun merge3(l: J?, r: J?, b: J?, rightNewer: Boolean): J? {
        if (same(l, r)) return l
        if (same(l, b)) return r
        if (same(r, b)) return l
        if (l is J.O && r is J.O) {
            val o = J.O(); val bo = b as? J.O
            for (k in LinkedHashSet<String>().apply { addAll(l.map.keys); addAll(r.map.keys) }) {
                if (k == "_u") continue
                val v = merge3(l.map[k], r.map[k], bo?.map?.get(k), rightNewer); if (v != null) o.map[k] = v
            }
            return o
        }
        return if (rightNewer) r else l
    }

    /** Savings totals: both phones' changes since the base add up. */
    private fun addUp(l: J?, r: J?, b: J?): J {
        val o = J.O(); val keys = LinkedHashSet<String>()
        listOf(l, r, b).forEach { (it as? J.O)?.map?.keys?.let(keys::addAll) }
        for (k in keys) o.map[k] = J.N(round2(num(b?.get(k)) + (num(l?.get(k)) - num(b?.get(k))) + (num(r?.get(k)) - num(b?.get(k)))))
        return o
    }

    private fun mergeDoc(key: String, l: J?, r: J?, b: J?): J? {
        if (same(l, r)) return l
        /* The file can be stale: a cloud app's cached copy, or the other phone writing from an older copy. An item missing
           from the file without a tombstone wasn't deleted (deletions always leave one), and a file copy older than what
           both phones last agreed on (the base) is out of date: either way this phone's copy is kept, and the file is
           rewritten with it. */
        if (!absent(b) && (absent(r) || r!!.n("_u") < b!!.n("_u"))) return l
        if (same(l, b)) return r
        if (same(r, b)) return l
        /* changed on both phones */
        if (l == null || l == J.Null) return r
        if (r == null || r == J.Null) return l
        val rightNewer = r.n("_u") > l.n("_u")
        val o = merge3(l, r, b, rightNewer) as? J.O ?: return if (rightNewer) r else l
        if (MONTH_DOC.matches(key) && (l["moved"] != null || r["moved"] != null)) o.map["moved"] = addUp(l["moved"], r["moved"], b?.get("moved"))
        o.map["_u"] = J.N(maxOf(l.n("_u"), r.n("_u")))
        return o
    }

    /**
     * The merged data for this phone ([local]), the file ([remote], null when the file is empty) and the base (null
     * before the first sync). The result is what both this phone and the file should hold, and the next base.
     */
    fun reconcile(local: Map<String, J>, remote: Map<String, J>?, base: Map<String, J>?): Result {
        if (remote == null) { val d = LinkedHashMap(local); return Result(d, false, true) }
        val data: LinkedHashMap<String, J> = if (base == null) Backup.merge(local, remote).data else {
            val tomb = LinkedHashMap<String, Double>()
            for (t in listOf(local[TOMB], remote[TOMB])) (t as? J.O)?.map?.forEach { (k, v) -> tomb[k] = maxOf(tomb[k] ?: 0.0, num(v)) }
            val out = LinkedHashMap<String, J>()
            for (k in LinkedHashSet<String>().apply { addAll(local.keys); addAll(remote.keys); remove(TOMB) }) {
                val v = mergeDoc(k, local[k], remote[k], base[k]) ?: continue
                if (absent(v)) continue
                val t = tomb[k]; if (t != null && t != 0.0 && t >= v.n("_u")) continue
                out[k] = v
            }
            if (tomb.isNotEmpty()) out[TOMB] = J.O(LinkedHashMap(tomb.mapValues { J.N(it.value) }))
            out
        }
        val c = canon(J.O(data))
        return Result(data, c != canon(J.O(LinkedHashMap(local))), c != canon(J.O(LinkedHashMap(remote))))
    }
}
