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
import looniemate.app.data.Ticks
import looniemate.engine.Docs
import looniemate.engine.Fmt
import looniemate.engine.Reminders
import looniemate.engine.Views
import looniemate.engine.Core
import looniemate.engine.diffDays
import looniemate.engine.s
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

    /** Reset LoonieMate: no more reminder alarms and no reminders on screen. */
    fun cancelAll(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx, null))
        NotificationManagerCompat.from(ctx).cancelAll()
    }

    /** The alarm went off: post the reminder for that waking day and slot, then set the next alarm. */
    fun fire(ctx: Context, intent: Intent) {
        val day = intent.getStringExtra(EXTRA_DAY); val slot = intent.getIntExtra(EXTRA_SLOT, 0); val slots = intent.getIntExtra(EXTRA_SLOTS, 1)
        val prefs = (ctx.applicationContext as LoonieApp).prefs
        val now = System.currentTimeMillis(); val due = prefs.reminderDueAt
        prefs.reminderFiredAt = now
        /* a phone that puts apps to sleep can hold alarms back for hours; count it so Today can explain the fix */
        if (due > 0 && now - due > 3_600_000L) prefs.reminderLateCount = prefs.reminderLateCount + 1
        if (day != null) {
            /* a new waking day: rows still showing "Marked paid · Undo" from before are cleared */
            if (prefs.reminderShown?.substringBefore('|') != day) { prefs.reminderUndo.forEach { NotificationManagerCompat.from(ctx).cancel(childId(it)) }; prefs.reminderUndo = emptySet() }
            prefs.reminderShown = "$day|$slot|$slots"; post(ctx, day, slot, slots, alert = true) }
        schedule(ctx)
    }

    /** After something is ticked off: update the reminder on screen silently, or remove it when nothing is left. */
    fun refreshShown(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.activeNotifications.none { it.id == ID || it.groupKey?.endsWith(GROUP) == true }) return
        val p = (ctx.applicationContext as LoonieApp).prefs.reminderShown?.split("|") ?: return
        if (p.size == 3) post(ctx, p[0], p[1].toInt(), p[2].toInt(), alert = false)
    }

    /* ---------- the bundled notification: one summary that alerts, one silent row per item with its own buttons ---------- */

    private const val GROUP = "looniemate.reminders"
    private const val A_DONE = "looniemate.DONE"; private const val A_UNDO = "looniemate.UNDO"; private const val A_LATER = "looniemate.LATER"
    private const val A_LATER_FIRE = "looniemate.LATER_FIRE"; private const val A_GOTIT = "looniemate.GOTIT"; private const val A_FORGET = "looniemate.FORGET"
    val ACTIONS = setOf(A_DONE, A_UNDO, A_LATER, A_LATER_FIRE, A_GOTIT, A_FORGET)

    private fun childId(key: String) = 1000 + (key.hashCode() and 0x7fffffff) % 900_000
    private fun prefs(ctx: Context) = (ctx.applicationContext as LoonieApp).prefs

    /** One row in the bundle. [kind] is bill, move, pad (an automatic payment heads-up) or tomorrow (a move due tomorrow). */
    private class Row(val kind: String, val key: String, val ym: String, val title: String, val text: String, val sav: String, val amount: Double, val locked: Boolean,
                      val arg: String = "")

    private fun rows(d: Reminders.Digest): List<Row> =
        d.todo.map { Row(it.kind, it.key, it.ym, if (it.kind == "move") it.name else "Pay ${it.name} ${Fmt.money(it.amount)}",
            (if (it.overdue) "Overdue since ${Fmt.dShort(it.date)}" else if (it.kind == "move") "From ${it.account}" else "From ${it.account}"), it.sav, it.amount, it.locked) } +
        d.pads.map { g -> Row("pad", "pad:" + g.account, "", "Payment coming out of ${g.account}", Reminders.padLine(g), "", g.total, false) } +
        d.tomorrowMoves.map { Row("tomorrow", it.key, it.ym, "Tomorrow: ${it.name}", "From ${it.account}", it.sav, it.amount, it.locked) }

    /**
     * Ride-along rows for the first reminder of the day, spaced out so they don't nag: reporting income (3 days before,
     * on the day, then every 3 days while late), tax season (Mondays), an old backup and the weekly balance check (Sundays).
     */
    private fun extraRows(app: LoonieApp, day: String): List<Row> {
        val docs = app.repo.docs.value; val st = Docs.toStore(docs, LocalDate.now().toString()); val s = st.settings
        val date = LocalDate.parse(day); val dow = date.dayOfWeek.value; val dom = date.dayOfMonth
        val out = mutableListOf<Row>()
        for (r in Views.reminders(st, day, app.prefs.lastBackup)) when (r.action) {
            "reported" -> { val src = Core.incomesOf(s).find { it.s("id") == r.arg } ?: continue
                val due = minOf(28, maxOf(1, Views.reportCfg(src).second))
                if (dom == due || dom == due - 3 || (dom > due && (dom - due) % 3 == 0))
                    out.add(Row("report", "note:report:${r.arg}:${r.arg2}", r.arg2, r.title, "Tap Reported once it's done.", "", 0.0, false, r.arg)) }
            "tax-filed" -> if (dow == 1) out.add(Row("tax", "note:tax:${r.arg}", "", r.title, "Benefits depend on filing every year, even with no income.", "", 0.0, false, r.arg))
            "backup" -> if (dow == 7) out.add(Row("note", "note:backup", "", r.title, "Open LoonieMate and make a backup in Settings.", "", 0.0, false))
        }
        val ck = Docs.checkins(docs).firstOrNull()
        if (dow == 7 && Core.accountsOf(s).isNotEmpty() && (ck == null || diffDays(ck.s("date"), day) >= 7))
            out.add(Row("note", "note:balances", "", "Time for a balance check", "Type in what your accounts hold so LoonieMate can spot missing money.", "", 0.0, false))
        return out
    }

    private fun broadcast(ctx: Context, action: String, req: Int, fill: Intent.() -> Unit): PendingIntent =
        PendingIntent.getBroadcast(ctx, req, Intent(ctx, ReminderReceiver::class.java).setAction(action).apply(fill), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun openApp(ctx: Context) = PendingIntent.getActivity(ctx, 11, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** a button that asks to unlock the phone first, so nobody else can tick off bills from the lock screen */
    private fun action(label: String, pi: PendingIntent) = NotificationCompat.Action.Builder(0, label, pi).setAuthenticationRequired(true).build()

    /** what shows on the lock screen instead: no names or amounts */
    private fun public(ctx: Context, text: String) = NotificationCompat.Builder(ctx, Notifs.REMINDERS).setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle("LoonieMate").setContentText(text).build()

    private fun base(ctx: Context) = NotificationCompat.Builder(ctx, Notifs.REMINDERS).setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setGroup(GROUP).setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY).setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(openApp(ctx))

    private fun extras(i: Intent, day: String, r: Row) { i.putExtra("day", day).putExtra("kind", r.kind).putExtra("key", r.key).putExtra("ym", r.ym)
        .putExtra("sav", r.sav).putExtra("amount", r.amount).putExtra("title", r.title).putExtra("arg", r.arg) }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, day: String, slot: Int, slots: Int, alert: Boolean) {
        val nm = NotificationManagerCompat.from(ctx); val pr = prefs(ctx); val now = System.currentTimeMillis()
        val app = ctx.applicationContext as LoonieApp
        val d = Reminders.digest(Docs.toStore(app.repo.docs.value, LocalDate.now().toString()), day, slot, slots, config(ctx))
        val gotIt = pr.reminderGotIt.filter { it.startsWith("$day|") }.map { it.substringAfter('|') }.toSet()
        val snoozed = pr.reminderSnoozed.mapNotNull { s -> s.split('|').takeIf { it.size == 2 && (it[1].toLongOrNull() ?: 0) > now }?.get(0) }.toSet()
        val undo = pr.reminderUndo
        val list = (rows(d) + if (slot == 0) extraRows(app, day) else emptyList()).filter { it.key !in gotIt && it.key !in snoozed && it.key !in undo }
        val shown = list.map { childId(it.key) }.toSet() + undo.map { childId(it) }
        /* rows that are no longer needed go away */
        pr.reminderChildren.mapNotNull { it.toIntOrNull() }.filter { it !in shown }.forEach { nm.cancel(it) }
        pr.reminderChildren = shown.map { it.toString() }.toSet()
        if (list.isEmpty() && undo.isEmpty()) { nm.cancel(ID); return }
        if (Notifs.needsPermission() && ContextCompat.checkSelfPermission(ctx, Notifs.PERMISSION) != PackageManager.PERMISSION_GRANTED) return

        for (r in list) { val id = childId(r.key)
            val b = base(ctx).setContentTitle(r.title).setContentText(r.text).setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
                .setSilent(true).setOnlyAlertOnce(true).setAutoCancel(true)
                .setPublicVersion(public(ctx, if (r.kind == "pad") "A payment is coming out soon" else "Something to do today"))
            when {
                r.kind == "note" -> b.addAction(action("Got it", broadcast(ctx, A_GOTIT, id * 8 + 1) { extras(this, day, r) }))
                r.kind == "report" || r.kind == "tax" -> b.addAction(action(if (r.kind == "report") "Reported" else "We've filed", broadcast(ctx, A_DONE, id * 8 + 2) { extras(this, day, r) }))
                r.kind == "pad" -> b.addAction(action("Got it", broadcast(ctx, A_GOTIT, id * 8 + 1) { extras(this, day, r) }))
                r.locked -> {}
                else -> {
                    b.addAction(action(if (r.kind == "bill") "Paid" else "Moved it", broadcast(ctx, A_DONE, id * 8 + 2) { extras(this, day, r) }))
                    b.addAction(action("In 1 hour", broadcast(ctx, A_LATER, id * 8 + 3) { extras(this, day, r); putExtra("hours", 1) }))
                    b.addAction(action("In 3 hours", broadcast(ctx, A_LATER, id * 8 + 4) { extras(this, day, r); putExtra("hours", 3) }))
                }
            }
            nm.notify(id, b.build())
        }
        val title = if (list.isEmpty()) "All done for now" else Reminders.summary(Reminders.without(d, gotIt + snoozed + undo)).ifEmpty { list.first().title }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title).also { st -> list.take(6).forEach { st.addLine(it.title) }; if (list.size > 6) st.setSummaryText("and ${list.size - 6} more") }
        val summary = base(ctx).setContentTitle(title).setContentText(list.firstOrNull()?.title ?: "Nothing left to do").setStyle(style)
            .setGroupSummary(true).setOnlyAlertOnce(true).setSilent(!alert).setAutoCancel(true)
            .setPublicVersion(public(ctx, if (d.todo.isNotEmpty()) "Things to do today" else "A payment is coming out soon"))
        nm.notify(ID, summary.build())
    }

    /** "Send a test notification" in Settings: shows what a reminder looks like. Returns false when notifications are off. */
    @SuppressLint("MissingPermission")
    fun test(ctx: Context): Boolean {
        if (Notifs.needsPermission() && ContextCompat.checkSelfPermission(ctx, Notifs.PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
        if (!Notifs.enabled(ctx)) return false
        NotificationManagerCompat.from(ctx).notify(3, NotificationCompat.Builder(ctx, Notifs.REMINDERS).setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("This is a LoonieMate reminder").setContentText("On a real one, each bill and money move gets its own Paid or Moved it button.")
            .setContentIntent(openApp(ctx)).setAutoCancel(true).build())
        return true
    }

    /** Paid, Moved it, Reported or We've filed (or undoing one). The same saved fields as Today uses. */
    private fun mark(app: LoonieApp, i: Intent, done: Boolean) {
        val key = i.getStringExtra("key") ?: return; val ym = i.getStringExtra("ym") ?: ""; val arg = i.getStringExtra("arg") ?: ""
        when (i.getStringExtra("kind")) {
            "bill" -> Ticks.setPaid(app.repo, ym, key, done)
            "report" -> app.repo.update("months/$ym", looniemate.engine.J.obj("reported" to looniemate.engine.J.obj(arg to done)))
            "tax" -> app.repo.update("settings/main", looniemate.engine.J.obj("taxFiled" to looniemate.engine.J.obj(arg to done)))
            else -> Ticks.setMoved(app.repo, ym, key, i.getStringExtra("sav") ?: "", i.getDoubleExtra("amount", 0.0), done)
        }
    }

    /** A button on a row was pressed. */
    @SuppressLint("MissingPermission")
    fun onAction(ctx: Context, i: Intent) {
        val pr = prefs(ctx); val app = ctx.applicationContext as LoonieApp; val nm = NotificationManagerCompat.from(ctx)
        val key = i.getStringExtra("key") ?: return; val ym = i.getStringExtra("ym") ?: ""; val kind = i.getStringExtra("kind") ?: ""
        val id = childId(key)
        when (i.action) {
            A_DONE -> {
                mark(app, i, true)
                pr.reminderUndo = pr.reminderUndo + key
                val title = i.getStringExtra("title") ?: ""
                val n = base(ctx).setContentTitle(when (kind) { "bill" -> "Marked paid"; "report" -> "Marked reported"; "tax" -> "Marked filed"; else -> "Marked done" }).setContentText(title.removePrefix("Pay "))
                    .setSilent(true).setOnlyAlertOnce(true)
                    .addAction(action("Undo", broadcast(ctx, A_UNDO, id * 8 + 5) { putExtras(i) }))
                    .setDeleteIntent(broadcast(ctx, A_FORGET, id * 8 + 6) { putExtras(i) })
                    .setPublicVersion(public(ctx, "Marked done"))
                if (Notifs.enabled(ctx)) nm.notify(id, n.build())
            }
            A_UNDO -> {
                mark(app, i, false)
                pr.reminderUndo = pr.reminderUndo - key
            }
            A_FORGET -> pr.reminderUndo = pr.reminderUndo - key
            A_GOTIT -> { pr.reminderGotIt = pr.reminderGotIt.filter { it.substringBefore('|') >= addDays(LocalDate.now().toString(), -3) }.toSet() + "${i.getStringExtra("day")}|$key"; nm.cancel(id) }
            A_LATER -> {
                val at = System.currentTimeMillis() + i.getIntExtra("hours", 1) * 3_600_000L
                pr.reminderSnoozed = pr.reminderSnoozed.filter { !it.startsWith("$key|") }.toSet() + "$key|$at"
                nm.cancel(id)
                ctx.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at,
                    broadcast(ctx, A_LATER_FIRE, id * 8 + 7) { putExtras(i) })
            }
            A_LATER_FIRE -> {
                pr.reminderSnoozed = pr.reminderSnoozed.filter { !it.startsWith("$key|") }.toSet()
                val p = pr.reminderShown?.split("|")
                if (p != null && p.size == 3) { post(ctx, p[0], p[1].toInt(), p[2].toInt(), alert = true); return }
            }
        }
        refreshShown(ctx)
    }
}

/** Receives the reminder alarm, and sets it again after the phone restarts or LoonieMate is updated. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            "looniemate.REMIND" -> ReminderAlarm.fire(ctx, intent)
            in ReminderAlarm.ACTIONS -> ReminderAlarm.onAction(ctx, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> ReminderAlarm.schedule(ctx)
        }
    }
}
