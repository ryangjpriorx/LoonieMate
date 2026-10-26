package looniemate.app.notify

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import looniemate.app.LoonieApp
import looniemate.app.MainActivity
import looniemate.app.R
import looniemate.app.update.Updater
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub for a new version in the background about twice a day (only with internet), and posts an
 * "update available" notification once per version. Obeys "Check for updates automatically".
 * Stage 3 of docs/plans/reminders-and-updates.md.
 */
class UpdateCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as LoonieApp; val up = app.updater; val prefs = app.prefs
        if (!up.enabled || !prefs.autoUpdate) return Result.success()
        up.check(false)
        val r = when (val s = up.state.value) { is Updater.State.Available -> s.r; is Updater.State.Ready -> s.r; else -> null } ?: return Result.success()
        if (r.versionCode == prefs.notifiedVersion || r.versionCode == prefs.skippedVersion) return Result.success()
        if (UpdateCheck.notify(applicationContext, r)) prefs.notifiedVersion = r.versionCode
        return Result.success()
    }
}

object UpdateCheck {
    const val EXTRA_OPEN = "open"; const val OPEN_UPDATE = "update"; const val OPEN_INSTALL = "install"
    private const val ID = 2

    /** Starts the twice-a-day background check (kept across restarts by WorkManager). */
    fun schedule(ctx: Context) {
        try {
            val req = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (_: Exception) { /* no WorkManager (some test setups): the check on opening the app still runs */ }
    }

    fun openIntent(ctx: Context, what: String = OPEN_UPDATE): PendingIntent = PendingIntent.getActivity(ctx, if (what == OPEN_INSTALL) 21 else 20,
        Intent(ctx, MainActivity::class.java).putExtra(EXTRA_OPEN, what).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** "LoonieMate X is available" with a Download button that opens the app to the update. Returns whether it was shown. */
    @SuppressLint("MissingPermission")
    fun notify(ctx: Context, r: Updater.Release): Boolean {
        if (Notifs.needsPermission() && ContextCompat.checkSelfPermission(ctx, Notifs.PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
        if (!Notifs.enabled(ctx, Notifs.UPDATES)) return false
        val first = r.notes.lineSequence().map { it.trim().trimStart('-', '*', ' ').replace("**", "") }.firstOrNull { it.isNotEmpty() } ?: "See what's new and update."
        val open = openIntent(ctx)
        val n = NotificationCompat.Builder(ctx, Notifs.UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("LoonieMate ${r.versionName} is available")
            .setContentText(first).setStyle(NotificationCompat.BigTextStyle().bigText(first))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .addAction(0, "Download", open)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID, n)
        return true
    }

    /** The download finished while LoonieMate wasn't on screen: "Ready to install" with an Install button. */
    @SuppressLint("MissingPermission")
    fun notifyReady(ctx: Context, r: Updater.Release): Boolean {
        if (Notifs.needsPermission() && ContextCompat.checkSelfPermission(ctx, Notifs.PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
        if (!Notifs.enabled(ctx, Notifs.UPDATES)) return false
        val open = openIntent(ctx, OPEN_INSTALL)
        val n = NotificationCompat.Builder(ctx, Notifs.UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("LoonieMate ${r.versionName} is ready to install")
            .setContentText("Downloaded and checked. Your budget stays exactly as it is.")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .addAction(0, "Install", open)
            .build()
        NotificationManagerCompat.from(ctx).notify(ID, n)
        return true
    }

    fun clear(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}
