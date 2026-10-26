@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package looniemate.app.ui.screens

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import looniemate.app.MainViewModel
import looniemate.app.notify.ReminderAlarm
import looniemate.app.ui.SectionCard
import looniemate.app.ui.toneColors

/** "8:30 a.m." the Canadian way, or "20:30" when the phone uses the 24-hour clock. */
fun clock(m: Int, h24: Boolean): String = if (h24) "%d:%02d".format(m / 60, m % 60)
    else "%d:%02d %s".format(((m / 60) + 11) % 12 + 1, m % 60, if (m < 720) "a.m." else "p.m.")

/** Minutes after the first reminder, counting past midnight, so a 1:00 a.m. follow-up comes after a 3:00 p.m. first one. */
private fun sinceFirst(first: Int, m: Int) = (m - first + 1440) % 1440

/** Reminder times must be in order through the waking day, at least 2 hours apart. Returns an error, or null. */
fun checkTimes(times: List<Int>): String? {
    if (times.isEmpty()) return null
    val rel = times.map { sinceFirst(times[0], it) }
    for (i in 1 until rel.size) if (rel[i] - rel[i - 1] < 120) return "Each reminder has to be at least 2 hours after the one before."
    return null
}

/**
 * Reminders for bills and money moves: on or off, how often, the times (and different ones on weekends), the heads-up
 * for automatic payments, and a test notification. This phone only. Stage 6 of docs/plans/reminders-and-updates.md.
 */
@Composable
fun RemindersCard(vm: MainViewModel, bare: Boolean = false) {
    val ctx = LocalContext.current; val p = vm.prefs; val h24 = DateFormat.is24HourFormat(ctx)
    fun parse(s: String) = s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..1439 }
    var on by remember { mutableStateOf(p.remindersOn) }
    var times by remember { mutableStateOf(parse(p.reminderTimes).ifEmpty { listOf(510, 1140) }) }
    var weekend by remember { mutableStateOf(parse(p.reminderWeekendTimes)) }
    var pad by remember { mutableIntStateOf(p.padDays) }
    var evening by remember { mutableStateOf(p.movesEveningBefore) }
    var err by remember { mutableStateOf<String?>(null) }; var note by remember { mutableStateOf<String?>(null) }
    val defaults = listOf(510, 1140, 1260)

    fun save(t: List<Int> = times, w: List<Int> = weekend): Boolean {
        val e = checkTimes(t) ?: checkTimes(w); err = e; if (e != null) return false
        times = t; weekend = w
        p.reminderTimes = t.joinToString(","); p.reminderWeekendTimes = w.joinToString(",")
        ReminderAlarm.schedule(ctx); return true
    }
    fun pick(list: List<Int>, i: Int, set: (List<Int>) -> Unit) {
        val m = list[i]
        TimePickerDialog(ctx, { _, h, mi -> set(list.toMutableList().also { it[i] = h * 60 + mi }) }, m / 60, m % 60, h24).show()
    }

    looniemate.app.ui.CardOrBare(bare, "Reminders", Icons.Rounded.NotificationsActive, "accent", trailing = "Only this phone") {
        SwitchRow("Remind me about bills and money moves", "Only when something needs doing. Nothing is sent on days with nothing due.", on) {
            on = it; p.remindersOn = it; ReminderAlarm.schedule(ctx) }
        if (on) {
            Spacer(Modifier.height(12.dp))
            Text("How often", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp))
            val counts = listOf(1 to "Once a day", 2 to "Twice", 3 to "Three times")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                counts.forEachIndexed { i, (n, l) -> SegmentedButton(selected = times.size == n, shape = SegmentedButtonDefaults.itemShape(i, counts.size), onClick = {
                    val t = if (n <= times.size) times.take(n) else times + defaults.drop(times.size).take(n - times.size)
                    val w = if (weekend.isEmpty()) weekend else if (n <= weekend.size) weekend.take(n) else weekend + defaults.drop(weekend.size).take(n - weekend.size)
                    save(t, w) }) { Text(l) } }
            }
            Text(if (times.size > 1) "The first one lists everything for the day. The others only come if something is still not done." else "One list a day.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            TimesRow(times, if (weekend.isEmpty()) "Times" else "Weekday times", h24) { i -> pick(times, i) { save(it, weekend) } }
            Spacer(Modifier.height(10.dp))
            SwitchRow("Different times on weekends", "For a later start on Saturday and Sunday.", weekend.isNotEmpty()) { w ->
                save(times, if (w) times.map { (it + 90) % 1440 } else emptyList()) }
            if (weekend.isNotEmpty()) { Spacer(Modifier.height(6.dp)); TimesRow(weekend, "Weekend times", h24) { i -> pick(weekend, i) { save(times, it) } } }
            err?.let { Spacer(Modifier.height(6.dp)); Text(it, color = toneColors("bad").first, fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(14.dp))
            Text("Automatic payments", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp))
            val pads = listOf(0 to "Off", 1 to "1 day ahead", 2 to "2 days ahead")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                pads.forEachIndexed { i, (n, l) -> SegmentedButton(selected = pad == n, shape = SegmentedButtonDefaults.itemShape(i, pads.size), onClick = {
                    pad = n; p.padDays = n }) { Text(l) } }
            }
            Text("A heads-up before a pre-authorized debit comes out, so the money is in the right account. Moved earlier when it would fall on a weekend or holiday.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (times.size > 1) { Spacer(Modifier.height(10.dp))
                SwitchRow("Money moves the evening before", "The last reminder of the day mentions money to move tomorrow.", evening) { evening = it; p.movesEveningBefore = it } }

            Spacer(Modifier.height(14.dp))
            FilledTonalButton(onClick = { note = if (ReminderAlarm.test(ctx)) "Sent. Check your notifications." else "Notifications are off for LoonieMate. Turn them on in Android's settings." },
                modifier = Modifier.testTag("test-reminder")) { Text("Send a test notification") }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun TimesRow(list: List<Int>, label: String, h24: Boolean, onPick: (Int) -> Unit) {
    Text(label, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEachIndexed { i, m -> OutlinedButton(onClick = { onPick(i) }) { Text((if (i == 0) "First " else "Then ") + clock(m, h24)) } }
    }
}
