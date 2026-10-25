package looniemate.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import looniemate.app.BuildConfig
import looniemate.app.data.Prefs
import looniemate.engine.J
import looniemate.engine.Json
import looniemate.engine.n
import looniemate.engine.s
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Keeps LoonieMate up to date from the GitHub repository's Releases page.
 * Each release carries `update.json` (version, file name, SHA-256) next to the app file. The download
 * is checked against that SHA-256 and against the signing key of the installed app before Android's
 * own installer is asked to install it.
 */
class Updater(private val ctx: Context, private val prefs: Prefs) {

    class Release(val versionCode: Int, val versionName: String, val url: String, val sha256: String, val size: Long, val notes: String, val minSdk: Int)

    sealed class State {
        object Idle : State()
        object Checking : State()
        class UpToDate(val at: Long) : State()
        class Available(val r: Release) : State()
        class Downloading(val r: Release, val progress: Float) : State()
        class Ready(val r: Release, val file: File) : State()
        class NeedsPermission(val r: Release, val file: File) : State()
        class Installing(val r: Release) : State()
        class Failed(val message: String, val r: Release? = null) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    val enabled: Boolean get() = !BuildConfig.DEBUG && BuildConfig.UPDATE_REPO.contains('/')
    val repoUrl: String get() = "https://github.com/" + BuildConfig.UPDATE_REPO
    private val manifestUrl get() = "$repoUrl/releases/latest/download/update.json"
    private val dir get() = File(ctx.cacheDir, "updates").apply { mkdirs() }

    init { cleanOld() }

    /** Checks at most every 12 hours on its own; a tap on "Check now" always checks. */
    suspend fun checkIfDue() { if (enabled && prefs.autoUpdate && System.currentTimeMillis() - prefs.lastUpdateCheck > 12 * 3_600_000L) check(false) }

    suspend fun check(manual: Boolean) {
        if (!enabled) { _state.value = State.Failed("Updates come with the release version of LoonieMate."); return }
        if (_state.value is State.Downloading || _state.value is State.Installing) return
        _state.value = State.Checking
        val r = try { withContext(Dispatchers.IO) { fetchRelease() } } catch (e: Exception) {
            _state.value = if (manual) State.Failed("Couldn't reach GitHub. Check your connection and try again.") else State.Idle; return
        }
        prefs.lastUpdateCheck = System.currentTimeMillis()
        _state.value = when {
            r == null -> State.UpToDate(System.currentTimeMillis())
            r.versionCode <= BuildConfig.VERSION_CODE -> State.UpToDate(System.currentTimeMillis())
            r.minSdk > Build.VERSION.SDK_INT -> State.Failed("Version ${r.versionName} needs a newer version of Android than this phone has.")
            !manual && prefs.skippedVersion == r.versionCode -> State.Idle
            else -> existing(r)?.let { State.Ready(r, it) } ?: State.Available(r)
        }
    }

    fun skip(r: Release) { prefs.skippedVersion = r.versionCode; _state.value = State.Idle }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 30_000
        setRequestProperty("User-Agent", "LoonieMate/${BuildConfig.VERSION_NAME} (Android)"); setRequestProperty("Accept", "application/json, application/octet-stream")
    }

    private fun fetchRelease(): Release? {
        val c = open(manifestUrl)
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
            val j = Json.parse(c.inputStream.bufferedReader().readText())
            val code = j.n("versionCode").toInt(); val apk = j.s("apk")
            if (code <= 0 || apk.isEmpty()) return null
            val url = j.s("url").ifEmpty { "$repoUrl/releases/download/v${j.s("versionName")}/$apk" }
            return Release(code, j.s("versionName"), url, j.s("sha256").lowercase(), j.n("size").toLong(), j.s("notes"), j.n("minSdk").toInt())
        } finally { c.disconnect() }
    }

    private fun fileFor(r: Release) = File(dir, "LoonieMate-${r.versionCode}.apk")
    private fun existing(r: Release): File? = fileFor(r).takeIf { it.exists() && sha256(it) == r.sha256 }

    suspend fun download(r: Release) {
        _state.value = State.Downloading(r, 0f)
        val out = fileFor(r); val part = File(dir, out.name + ".part")
        try {
            withContext(Dispatchers.IO) {
                val c = open(r.url)
                try {
                    if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
                    val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
                    val md = MessageDigest.getInstance("SHA-256"); var got = 0L; var lastShown = 0f
                    c.inputStream.use { inp -> part.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) { val n = inp.read(buf); if (n < 0) break; o.write(buf, 0, n); md.update(buf, 0, n); got += n
                            if (total > 0) { val p = got.toFloat() / total; if (p - lastShown > 0.01f) { lastShown = p; _state.value = State.Downloading(r, p.coerceIn(0f, 1f)) } } }
                    } }
                    val hash = md.digest().joinToString("") { "%02x".format(it) }
                    if (r.sha256.isNotEmpty() && hash != r.sha256) { part.delete(); throw SecurityException("The download was damaged. Try again.") }
                    if (!sameSigner(part)) { part.delete(); throw SecurityException("That file isn't signed by LoonieMate, so it wasn't installed.") }
                    part.renameTo(out)
                } finally { c.disconnect() }
            }
            _state.value = State.Ready(r, out)
        } catch (e: SecurityException) { _state.value = State.Failed(e.message ?: "The download couldn't be checked.", r) }
        catch (e: Exception) { part.delete(); _state.value = State.Failed("The download stopped. Check your connection and try again.", r) }
    }

    @Suppress("DEPRECATION")
    private fun signers(pi: PackageInfo?): Set<String> {
        if (pi == null) return emptySet()
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pi.signingInfo?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory } else pi.signatures
        return sigs.orEmpty().map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }

    @Suppress("DEPRECATION")
    private fun sameSigner(apk: File): Boolean {
        val pm = ctx.packageManager
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val newer = pm.getPackageArchiveInfo(apk.absolutePath, flag) ?: return false
        if (newer.packageName != ctx.packageName) return false
        val mine = signers(pm.getPackageInfo(ctx.packageName, flag)); val theirs = signers(newer)
        return mine.isNotEmpty() && theirs.isNotEmpty() && mine.intersect(theirs).isNotEmpty()
    }

    fun canInstall(): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for LoonieMate. */
    fun permissionIntent(): Intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(r: Release, apk: File) {
        if (!canInstall()) { _state.value = State.NeedsPermission(r, apk); return }
        _state.value = State.Installing(r)
        try {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName); setSize(apk.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                apk.inputStream().use { inp -> session.openWrite("LoonieMate.apk", 0, apk.length()).use { o -> inp.copyTo(o); session.fsync(o) } }
                val intent = Intent(ctx, InstallReceiver::class.java).setAction(InstallReceiver.ACTION)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
            }
        } catch (e: Exception) { _state.value = State.Failed("Android couldn't start the install. Try again.", r) }
    }

    /** Called by InstallReceiver when Android reports back. */
    fun onInstallResult(ok: Boolean, message: String?) {
        val r = (state.value as? State.Installing)?.r
        _state.value = if (ok) State.Idle else State.Failed(message?.takeIf { it.isNotBlank() }?.let { "The update wasn't installed: $it" } ?: "The update wasn't installed.", r)
    }

    fun onPermissionMaybeGranted() { val s = state.value; if (s is State.NeedsPermission && canInstall()) install(s.r, s.file) }

    private fun cleanOld() { try { dir.listFiles()?.forEach { f -> val code = f.name.removePrefix("LoonieMate-").substringBefore('.').toIntOrNull(); if (code == null || code <= BuildConfig.VERSION_CODE) f.delete() } } catch (_: Exception) {} }

    private fun sha256(f: File): String { val md = MessageDigest.getInstance("SHA-256"); f.inputStream().use { i -> val b = ByteArray(65536); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) } }
}
