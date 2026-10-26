package looniemate.app.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import looniemate.app.LoonieApp
import looniemate.app.MainActivity
import looniemate.app.R
import looniemate.engine.Docs
import looniemate.engine.Fmt
import looniemate.engine.Reminders
import looniemate.engine.addDays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Bill and money-move reminders: one alarm at a time for the next reminder time; when it fires, the reminder
 * is worked out from the budget on the phone and posted (or nothing, when nothing needs doing), and the next
 * alarm is set. Stage 5a of docs/plans/reminders-and-updates.md.
 */
object ReminderAlarm {
    private const val ID = 1
    private const val EXTRA_DAY = "day"; private const val EXTRA_SLOT = "slot"; private const val EXTRA_SLOTS = "slots"

    fun config(ctx: Context): Reminders.Config = (ctx.applicationContext as LoonieApp).prefs.reminderConfig()

    class Next(val at: LocalDateTime, val day: String, val slot: Int, val slots: Int)

    /** The next reminder after [now]. A follow-up earlier in the clock than the first reminder fires after midnight. */
    fun next(cfg: Reminders.Config, now: LocalDateTime): Next? {
        val today = now.toLocalDate().toString(); var best: Next? = null
        for (k in -1..2) { val d = addDays(today, k); val times = Reminders.timesFor(cfg, d)
            times.forEachIndexed { i, m ->
                val date = if (i > 0 && m < times[0]) LocalDate.parse(addDays(d, 1)) else LocalDate.parse(d)
                val at = date.atTime(m / 60, m % 60)
                if (at.isAfter(now) && (best == null || at.isBefore(best!!.at))) best = Next(at, d, i, times.size)
            } }
        return best
    }

    private fun pending(ctx: Context, n: Next?): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction("looniemate.REMIND")
        if (n != null) i.putExtra(EXTRA_DAY, n.day).putExtra(EXTRA_SLOT, n.slot).putExtra(EXTRA_SLOTS, n.slots)
        return PendingIntent.getBroadcast(ctx, 10, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Sets the alarm for the next reminder time. Safe to call any time; it replaces the previous alarm. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val cfg = config(ctx)
        val n = if (cfg.times.isEmpty()) null else next(cfg, LocalDateTime.now())
        if (n == null) { am.cancel(pending(ctx, null)); return }
        val ms = n.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        (ctx.applicationContext as LoonieApp).prefs.reminderDueAt = ms
        /* fires while the phone is idle, without the exact-alarm permission; a few minutes late is fine */
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pending(ctx, n))
    }

    /** The alarm went off: post the reminder for that waking day and slot, then set the next alarm. */
    fun fire(ctx: Context, intent: Intent) {
        val day = intent.getStringExtra(EXTRA_DAY); val slot = intent.getIntExtra(EXTRA_SLOT, 0); val slots = intent.getIntExtra(EXTRA_SLOTS, 1)
        val prefs = (ctx.applicationContext as LoonieApp).prefs
        prefs.reminderFiredAt = System.currentTimeMillis()
        if (day != null) { prefs.reminderShown = "$day|$slot|$slots"; post(ctx, day, slot, slots, alert = true) }
        schedule(ctx)
    }

    /** After something is ticked off: update the reminder on screen silently, or remove it when nothing is left. */
    fun refreshShown(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.activeNotifications.none { it.id == ID }) return
        val p = (ctx.applicationContext as LoonieApp).prefs.reminderShown?.split("|") ?: return
        if (p.size == 3) post(ctx, p[0], p[1].toInt(), p[2].toInt(), alert = false)
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, day: String, slot: Int, slots: Int, alert: Boolean) {
        val nm = NotificationManagerCompat.from(ctx)
        val app = ctx.applicationContext as LoonieApp
        val d = Reminders.digest(Docs.toStore(app.repo.docs.value, LocalDate.now().toString()), day, slot, slots, config(ctx))
        if (d.isEmpty) { nm.cancel(ID); return }
        if (Notifs.needsPermission() && ContextCompat.checkSelfPermission(ctx, Notifs.PERMISSION) != PackageManager.PERMISSION_GRANTED) return
        val lines = d.todo.map { (if (it.overdue) "Overdue: " else "") + (if (it.kind == "move") it.name else "Pay ${it.name} ${Fmt.money(it.amount)}") } +
            d.pads.map { Reminders.padLine(it) } + d.tomorrowMoves.map { "Tomorrow: ${it.name}" }
        val open = PendingIntent.getActivity(ctx, 11, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = Reminders.summary(d)
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title).also { s -> lines.take(6).forEach { s.addLine(it) }; if (lines.size > 6) s.setSummaryText("and ${lines.size - 6} more") }
        /* on the lock screen: no names or amounts */
        val public = NotificationCompat.Builder(ctx, Notifs.REMINDERS).setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle("LoonieMate")
            .setContentText(if (d.todo.isNotEmpty()) "Things to do today" else "A payment is coming out soon").build()
        val n = NotificationCompat.Builder(ctx, Notifs.REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome).setContentTitle(title).setContentText(lines.first()).setStyle(style)
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setSilent(!alert)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        nm.notify(ID, n)
    }
}

/** Receives the reminder alarm, and sets it again after the phone restarts or LoonieMate is updated. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            "looniemate.REMIND" -> ReminderAlarm.fire(ctx, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> ReminderAlarm.schedule(ctx)
        }
    }
}
