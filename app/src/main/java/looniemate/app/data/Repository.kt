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

    init { load() }

    private fun load() {
        val map = LinkedHashMap<String, J>()
        try {
            if (file.baseFile.exists()) {
                val root = Json.parse(String(file.readFully(), Charsets.UTF_8))
                (root["data"] as? J.O)?.map?.forEach { (k, v) -> map[k] = v }
            }
        } catch (e: Exception) {
            /* keep a copy of a damaged file instead of overwriting it */
            try { file.baseFile.copyTo(File(file.baseFile.parentFile, "budget-damaged-${System.currentTimeMillis()}.json"), overwrite = true) } catch (_: Exception) {}
        }
        _docs.value = map
    }

    private fun persist(snapshot: Map<String, J>) {
        writer.execute {
            val text = Json.stringify(J.obj("app" to "loonie-mate", "schema" to 1, "data" to J.O(LinkedHashMap(snapshot))))
            val out = file.startWrite()
            try { out.write(text.toByteArray(Charsets.UTF_8)); file.finishWrite(out) } catch (e: Exception) { file.failWrite(out) }
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

    /** Puts back exactly what a document was before (used by Undo). */
    fun restore(path: String, before: J?) = edit { m -> if (before == null) m.remove(path) else m[path] = before.copy() }

    fun replaceAll(data: Map<String, J>) = edit { m -> m.clear(); m.putAll(data) }

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
