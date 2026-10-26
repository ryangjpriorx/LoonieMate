package looniemate.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Notification channels and permission. Two channels so people can tune one without losing the other:
 * Reminders (bills to pay, money to move) and Updates (a new version of LoonieMate).
 * See docs/plans/reminders-and-updates.md.
 */
object Notifs {
    const val REMINDERS = "reminders"
    const val UPDATES = "updates"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Bills to pay and money to move. At most a few a day, and only when something needs doing." })
        nm.createNotificationChannel(NotificationChannel(UPDATES, "Updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A new version of LoonieMate is ready. Once per version." })
    }

    /** Android 13 and later ask the person before an app can show notifications. */
    fun needsPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    const val PERMISSION = Manifest.permission.POST_NOTIFICATIONS

    private fun granted(ctx: Context) = !needsPermission() || ContextCompat.checkSelfPermission(ctx, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** True when notifications from this channel will actually be shown. */
    fun enabled(ctx: Context, channel: String = REMINDERS): Boolean {
        if (!granted(ctx) || !NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = ctx.getSystemService(NotificationManager::class.java)?.getNotificationChannel(channel)
            if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    /** Android's notification settings for LoonieMate, for when the permission can't be asked for again. */
    fun settingsIntent(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
}
