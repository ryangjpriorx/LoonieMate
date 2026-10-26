package looniemate.app.data

import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import looniemate.engine.J
import looniemate.engine.Json
import looniemate.engine.copy
import looniemate.engine.num
import java.io.File
import java.util.concurrent.Executors
import looniemate.engine.*

/**
 * The budget, saved on this phone as one JSON file of documents keyed by path
 * (settings/main, bills/<id>, months/<ym>, months/<ym>/expenses/<id>, …), the same layout as the
 * web version and its backups. Every write stamps `_u` with the time; deletions leave a tombstone
 * so combining two copies later knows the item was removed.
 */
class Repository(dir: File) {
    private val file = AtomicFile(File(dir, "budget.json"))
    private val writer = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private val _docs = MutableStateFlow<Map<String, J>>(emptyMap())
    val docs: StateFlow<Map<String, J>> = _docs

    /**
     * Something wrong with keeping the budget on this phone, shown on Today until it's sorted (docs/plans/polish-and-audit.md
     * S2, S3): "damaged" (the saved budget couldn't be read, so it started empty; a copy was kept) or "save" (the last
     * change couldn't be written, e.g. the phone is full). A later save that works clears "save".
     */
    val problem = MutableStateFlow<String?>(null)
    /** The copy kept of a budget file that couldn't be read. */
    var damagedCopy: File? = null; private set

    init { load() }

    private fun load() {
        val map = LinkedHashMap<String, J>()
        try {
            if (file.baseFile.exists()) {
                val root = Json.parse(String(file.readFully(), Charsets.UTF_8))
                (root["data"] as? J.O)?.map?.forEach { (k, v) -> map[k] = v }
            }
        } catch (e: Exception) {
            /* keep a copy of a damaged file instead of overwriting it, and say so */
            try { damagedCopy = file.baseFile.copyTo(File(file.baseFile.parentFile, "budget-damaged-${System.currentTimeMillis()}.json"), overwrite = true) } catch (_: Exception) {}
            problem.value = "damaged"
        }
        _docs.value = map
    }

    private fun persist(snapshot: Map<String, J>) {
        writer.execute {
            val text = Json.stringify(J.obj("app" to "loonie-mate", "schema" to 1, "data" to J.O(LinkedHashMap(snapshot))))
            val out = file.startWrite()
            try { out.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(out); if (problem.value == "save") problem.value = null }
            catch (e: Exception) { file.failWrite(out); if (problem.value != "damaged") problem.value = "save" }
        }
    }

    private inline fun edit(block: (LinkedHashMap<String, J>) -> Unit) {
        synchronized(lock) {
            val m = LinkedHashMap(_docs.value)
            block(m)
            _docs.value = m
            persist(m)
        }
    }

    fun get(path: String): J.O? = _docs.value[path] as? J.O

    /** Replaces a document. */
    fun set(path: String, doc: J.O) = edit { m -> m[path] = (doc.copy() as J.O).also { it["_u"] = J.N(now().toDouble()) } }

    /** Merges fields into a document (nested objects are merged, everything else replaced), or creates it. */
    fun update(path: String, patch: J.O) = edit { m ->
        val cur = m[path] as? J.O
        val next = if (cur == null) patch.copy() as J.O else deepMerge(cur, patch)
        next["_u"] = J.N(now().toDouble()); m[path] = next
    }

    fun delete(path: String) = edit { m ->
        m.remove(path)
        val t = (m["meta/tombstones"] as? J.O)?.copy() as? J.O ?: J.O()
        val n = now(); t[path] = J.N(n.toDouble())
        val cut = n - 400L * 86_400_000
        t.map.entries.removeAll { num(it.value) < cut }
        m["meta/tombstones"] = t
    }

    /**
     * Undo: puts documents back as they were. Each one put back is stamped as a new change (and loses its tombstone),
     * and one that didn't exist before gets a tombstone, so sync carries the Undo to the other phone instead of
     * reversing it (S4). The tombstones document itself isn't restored wholesale.
     */
    fun restoreAll(before: Map<String, J?>) = edit { m ->
        val t = (m["meta/tombstones"]?.copy() as? J.O) ?: J.O(); val n = now()
        for ((p, v) in before) { if (p == "meta/tombstones") continue
            if (v == null) { if (m.remove(p) != null) t[p] = J.N(n.toDouble()) }
            else { m[p] = (v.copy() as? J.O ?: continue).also { it["_u"] = J.N(n.toDouble()) }; t.map.remove(p) } }
        if (t.map.isNotEmpty() || m.containsKey("meta/tombstones")) m["meta/tombstones"] = t
    }

    /**
     * Replaces everything with [data]. [recordRemoved]: items on this phone that aren't in [data] get a tombstone, so
     * replacing the budget from a backup also removes them on a synced phone instead of sync bringing them back.
     */
    fun replaceAll(data: Map<String, J>, recordRemoved: Boolean = false) = edit { m ->
        val t = (data["meta/tombstones"]?.copy() as? J.O) ?: J.O()
        if (recordRemoved) { val n = now(); for (k in m.keys) if (k != "meta/tombstones" && !data.containsKey(k)) t[k] = J.N(n.toDouble()) }
        m.clear(); m.putAll(data); if (t.map.isNotEmpty()) m["meta/tombstones"] = t
        if (problem.value == "damaged") { problem.value = null; damagedCopy = null }
    }

    /** The person read the "couldn't read your budget" note and chose to carry on with what's here. */
    fun dismissProblem() { problem.value = null }

    /** Replaces everything with [data], but only if nothing changed since [expected] was read (used by sync). */
    fun replaceIfUnchanged(expected: Map<String, J>, data: Map<String, J>): Boolean = synchronized(lock) {
        if (_docs.value !== expected) return false
        val m = LinkedHashMap(data); _docs.value = m; persist(m); true
    }

    fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) + (1..7).map { "0123456789abcdefghijklmnopqrstuvwxyz"[rnd.nextInt(36)] }.joinToString("")

    private val rnd = java.security.SecureRandom()
    private var last = 0L
    /** milliseconds, never the same twice, so the newest change always wins a merge */
    private fun now(): Long { val t = maxOf(System.currentTimeMillis(), last + 1); last = t; return t }

    companion object {
        fun deepMerge(a: J.O, b: J.O): J.O {
            val o = a.copy() as J.O
            for ((k, v) in b.map) {
                val av = o.map[k]
                o.map[k] = if (v is J.O && av is J.O) deepMerge(av, v) else v.copy()
            }
            return o
        }
    }
}
