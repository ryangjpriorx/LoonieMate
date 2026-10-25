package looniemate.engine

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypted backups, byte-for-byte compatible with the web version:
 * AES-256-GCM, with the key made from the password by PBKDF2-SHA256 (600,000 rounds).
 * The same file is used for export, import and Google Drive sync.
 */
object Backup {
    const val FORMAT = "family-budget-backup"
    const val ITERATIONS = 600_000
    private const val NOTE = "Encrypted budget backup. It can only be opened in the budget app with the password it was made with."

    enum class Problem { NOT_BACKUP, PASSWORD }
    class BackupException(val problem: Problem, msg: String) : Exception(msg)

    /** The key for a password and salt. Kept so repeat saves (sync) don't redo the slow step. */
    class Key(val bytes: ByteArray, val salt: ByteArray, val iterations: Int)

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    /** PBKDF2 with HMAC-SHA256, written out so every phone does it exactly the same way. */
    fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): Key {
        val mac = Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val u = ByteArray(32); val t = ByteArray(32)
        mac.update(salt); mac.update(byteArrayOf(0, 0, 0, 1)); mac.doFinal(u, 0)
        System.arraycopy(u, 0, t, 0, 32)
        for (i in 1 until iterations) { mac.update(u); mac.doFinal(u, 0); for (j in 0 until 32) t[j] = (t[j].toInt() xor u[j].toInt()).toByte() }
        return Key(t, salt, iterations)
    }

    private val b64e = java.util.Base64.getEncoder()
    private fun b64d(s: String): ByteArray = java.util.Base64.getDecoder().decode(s.filterNot { it.isWhitespace() })

    /** The backup contents, like the web version's backupPayload(). */
    fun payload(docs: Map<String, J>, exportedAt: String): Obj =
        J.obj("app" to "family-budget", "schema" to 1, "exportedAt" to exportedAt, "data" to J.O(LinkedHashMap(docs)))

    fun encrypt(payload: J, key: Key): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(128, iv))
        val ct = c.doFinal(Json.stringify(payload).toByteArray(Charsets.UTF_8))
        return Json.stringify(J.obj("format" to FORMAT, "version" to 1, "note" to NOTE, "cipher" to "AES-256-GCM", "kdf" to "PBKDF2-SHA256", "iterations" to key.iterations,
            "salt" to b64e.encodeToString(key.salt), "iv" to b64e.encodeToString(iv), "data" to b64e.encodeToString(ct)))
    }

    fun encrypt(payload: J, password: String): String = encrypt(payload, deriveKey(password, newSalt()))

    class Envelope(val salt: ByteArray, val iv: ByteArray, val data: ByteArray, val iterations: Int)

    fun envelope(text: String): Envelope {
        val env = try { Json.parse(text.trim()) } catch (e: Exception) { throw BackupException(Problem.NOT_BACKUP, "not JSON") }
        if (env.s("format") != FORMAT || !env.t("data")) throw BackupException(Problem.NOT_BACKUP, "not a budget backup")
        return try { Envelope(b64d(env.s("salt")), b64d(env.s("iv")), b64d(env.s("data")), env.n("iterations").toInt().takeIf { it > 0 } ?: ITERATIONS) }
        catch (e: IllegalArgumentException) { throw BackupException(Problem.NOT_BACKUP, "damaged file") }
    }

    /** Opens a backup. Pass a key from an earlier open to skip the slow step when the salt matches. */
    fun decrypt(text: String, password: String?, known: Key? = null): Pair<Obj, Key> {
        val env = envelope(text)
        val key = if (known != null && known.salt.contentEquals(env.salt) && known.iterations == env.iterations) known
            else deriveKey(password ?: throw BackupException(Problem.PASSWORD, "password needed"), env.salt, env.iterations)
        val pt = try {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(128, env.iv)); c.doFinal(env.data)
        } catch (e: Exception) { throw BackupException(Problem.PASSWORD, "wrong password") }
        val obj = try { Json.parse(String(pt, Charsets.UTF_8)) } catch (e: Exception) { throw BackupException(Problem.NOT_BACKUP, "unreadable") }
        if (obj.s("app") != "family-budget" || obj["data"] !is J.O) throw BackupException(Problem.NOT_BACKUP, "not a budget backup")
        return (obj as Obj) to key
    }

    class Merged(val data: LinkedHashMap<String, J>, val added: Int, val updated: Int, val removed: Int)

    private val MONTH_DOC = Regex("^months/\\d{4}-\\d{2}$")

    /** Combines two copies: newest change wins for each item, "paid" and "done" ticks from both are kept, deletions carry over. */
    fun merge(a: Map<String, J>, b: Map<String, J>): Merged {
        val out = LinkedHashMap<String, J>(); val ta = a["meta/tombstones"] as? Obj; val tb = b["meta/tombstones"] as? Obj
        val tomb = LinkedHashMap<String, Double>()
        for (k in (ta?.map?.keys ?: emptySet()) + (tb?.map?.keys ?: emptySet())) tomb[k] = maxOf(num(ta?.get(k)), num(tb?.get(k)))
        val keys = LinkedHashSet<String>().apply { addAll(a.keys); addAll(b.keys); remove("meta/tombstones") }
        var added = 0; var updated = 0; var removed = 0
        for (k in keys) {
            val x = a[k]?.takeIf { truthy(it) }; val y = b[k]?.takeIf { truthy(it) }; val v: J?
            if (x != null && y == null) v = x
            else if (x == null && y != null) { v = y; added++ }
            else if (x != null && y != null) {
                val ux = x.n("_u"); val uy = y.n("_u")
                if (MONTH_DOC.matches(k)) {
                    val w = (if (uy > ux) y else x).copy() as? Obj ?: Obj()
                    for (f in listOf("paid", "moves")) {
                        val o = LinkedHashMap<String, J>(); (x[f] as? Obj)?.map?.forEach { (kk, vv) -> o[kk] = vv }
                        (y[f] as? Obj)?.map?.forEach { (kk, vv) -> o[kk] = J.B(truthy(o[kk]) || truthy(vv)) }
                        if (o.isNotEmpty()) w[f] = J.O(o)
                    }
                    w["_u"] = J.N(maxOf(ux, uy)); v = w
                } else v = if (uy > ux) y else x
                if (Json.stringify(v) != Json.stringify(x)) updated++
            } else continue
            val t = tomb[k]
            if (t != null && t != 0.0 && t >= v.n("_u")) { if (x != null) removed++ else if (y != null) added--; continue }
            out[k] = v
        }
        if (tomb.isNotEmpty()) out["meta/tombstones"] = J.O(LinkedHashMap(tomb.mapValues { J.N(it.value) }))
        return Merged(out, added, updated, removed)
    }
}
