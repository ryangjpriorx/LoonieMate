package looniemate.app.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import looniemate.app.LoonieApp
import looniemate.app.data.Prefs
import looniemate.app.data.Repository
import looniemate.engine.Backup
import looniemate.engine.J
import looniemate.engine.Json
import looniemate.engine.Sync
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Keeps this phone and the sync file (picked with Android's file picker, usually in Google Drive) the same.
 * One sync: read the file → decrypt → three-way merge with this phone's data and the base → apply here if anything
 * changed → write the file if it differs → save the new base. docs/plans/sync.md, stage 2.
 */
class SyncManager(private val ctx: Context, private val repo: Repository, private val prefs: Prefs) {
    sealed class State {
        object Off : State()
        object Syncing : State()
        class Ok(val at: Long) : State()
        /** what went wrong: "password" (the file's password changed), "unreachable" (file moved, deleted or offline), "notfile" */
        class Error(val kind: String, val message: String, val at: Long) : State()
    }
    private val _state = MutableStateFlow<State>(if (prefs.syncUri == null) State.Off else if (prefs.syncLast > 0) State.Ok(prefs.syncLast) else State.Ok(0))
    val state: StateFlow<State> = _state
    private val mutex = Mutex()
    private var key: Backup.Key? = null
    /** the data this phone holds right after a sync applied the file's changes (so that change doesn't trigger another sync) */
    @Volatile var lastApplied: Map<String, J>? = null; private set

    val on: Boolean get() = prefs.syncUri != null
    private val baseFile = AtomicFile(File(ctx.filesDir, "sync-base.json"))

    private fun readBase(): Map<String, J>? = try { if (!baseFile.baseFile.exists()) null else (Json.parse(String(baseFile.readFully(), Charsets.UTF_8)) as? J.O)?.map } catch (_: Exception) { null }
    private fun writeBase(d: Map<String, J>) { val o = baseFile.startWrite(); try { o.write(Json.stringify(J.O(LinkedHashMap(d))).toByteArray(Charsets.UTF_8)); baseFile.finishWrite(o) } catch (e: Exception) { baseFile.failWrite(o) } }

    fun displayName(uri: Uri): String = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) { null } ?: "the sync file"

    private fun readText(uri: Uri): String = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
    private fun writeText(uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        /* "wt" truncates; some cloud providers only accept "w" (which replaces the whole file for them) */
        val out = try { ctx.contentResolver.openOutputStream(uri, "wt") } catch (_: Exception) { null } ?: ctx.contentResolver.openOutputStream(uri, "w")!!
        out.use { it.write(bytes) }
    }

    /**
     * Turns sync on with [uri]. [create]: a new, empty file (this phone's budget goes in it). Otherwise an existing sync
     * file: it's opened with [password] and combined with this phone's budget. Returns an error message, or null.
     */
    suspend fun setUp(uri: Uri, password: String, create: Boolean): String? = withContext(Dispatchers.IO) {
        try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {}
        val text = try { readText(uri) } catch (e: Exception) { return@withContext "LoonieMate couldn't open that file. Try choosing it again." }
        if (!create && text.isNotBlank()) {
            try { val (_, k) = Backup.decrypt(text, password); key = k }
            catch (e: Backup.BackupException) { return@withContext if (e.problem == Backup.Problem.PASSWORD) "That password doesn't open this sync file." else "That isn't a LoonieMate sync file." }
        } else key = null
        if (create && text.isNotBlank()) {
            /* a file that already has something in it: only replace it if it isn't someone's budget */
            if (runCatching { Backup.envelope(text) }.isSuccess) return@withContext "That file already holds a LoonieMate budget. Choose \"Use an existing sync file\" instead, or pick a new name."
        }
        prefs.syncUri = uri.toString(); prefs.syncPass = SecretStore.seal(password); prefs.syncName = displayName(uri)
        baseFile.delete()
        val err = sync(force = true)
        if (err == null) schedule(ctx) else if (create) stop()
        err
    }

    /** Stops syncing on this phone. The file stays where it is. */
    fun stop() {
        prefs.syncUri?.let { u -> try { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(u), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {} }
        prefs.syncUri = null; prefs.syncPass = null; prefs.syncName = null; prefs.syncLast = 0; key = null; baseFile.delete()
        try { WorkManager.getInstance(ctx).cancelUniqueWork(WORK) } catch (_: Exception) {}
        _state.value = State.Off
    }

    /** A new password after the file was locked with a different one on another phone. */
    suspend fun newPassword(password: String): String? { prefs.syncPass = SecretStore.seal(password); key = null; return sync(force = true) }

    /** Syncs once. Returns an error message, or null when it worked (or was skipped because a sync is already running). */
    suspend fun sync(force: Boolean = false): String? {
        val u = prefs.syncUri ?: return null
        if (!force && System.currentTimeMillis() - prefs.syncLast < 20_000) return null
        if (!mutex.tryLock()) return null
        try {
            _state.value = State.Syncing
            return withContext(Dispatchers.IO) { once(Uri.parse(u)) }
        } finally { mutex.unlock() }
    }

    private fun fail(kind: String, msg: String): String { _state.value = State.Error(kind, msg, System.currentTimeMillis()); return msg }

    private fun once(uri: Uri): String? {
        val text = try { readText(uri) } catch (e: Exception) { return fail("unreachable", "LoonieMate can't reach ${prefs.syncName ?: "the sync file"}. It may have been moved or deleted, or this phone is offline.") }
        val password = SecretStore.open(prefs.syncPass)
        var remote: Map<String, J>? = null
        if (text.isNotBlank()) {
            try { val (p, k) = Backup.decrypt(text, password, key); key = k; remote = (p["data"] as J.O).map }
            catch (e: Backup.BackupException) {
                return if (e.problem == Backup.Problem.PASSWORD) fail("password", "The sync file's password was changed on another phone. Enter the new one to keep syncing.")
                    else fail("notfile", "The sync file isn't a LoonieMate budget any more.")
            }
        }
        /* merge, then apply here only if nothing changed on this phone meanwhile (otherwise the next sync picks it up) */
        val local = repo.docs.value
        val r = Sync.reconcile(local, remote, readBase())
        if (r.changedLocal) { if (!repo.replaceIfUnchanged(local, r.data)) { _state.value = State.Ok(prefs.syncLast); return null }; lastApplied = repo.docs.value }
        if (r.changedRemote) try {
            val k = key ?: Backup.deriveKey(password ?: return fail("password", "Enter the sync password again."), Backup.newSalt()).also { key = it }
            writeText(uri, Backup.encrypt(Backup.payload(r.data, java.time.Instant.now().toString()), k))
        } catch (e: Exception) { return fail("unreachable", "LoonieMate couldn't save to ${prefs.syncName ?: "the sync file"}. Check that this phone is online.") }
        writeBase(r.data)
        val now = System.currentTimeMillis(); prefs.syncLast = now
        /* an encrypted copy off this phone counts as a backup */
        prefs.lastBackup = java.time.Instant.now().toString()
        _state.value = State.Ok(now)
        if (r.changedLocal) looniemate.app.notify.ReminderAlarm.refreshShown(ctx)
        return null
    }

    companion object {
        private const val WORK = "sync"
        /** every 6 hours in the background, with internet */
        fun schedule(ctx: Context) {
            try {
                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
            } catch (_: Exception) {}
        }
    }
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result { (applicationContext as LoonieApp).sync.sync(force = true); return Result.success() }
}
