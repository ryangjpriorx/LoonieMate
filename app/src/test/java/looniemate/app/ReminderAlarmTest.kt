package looniemate.app

import looniemate.app.notify.ReminderAlarm
import looniemate.engine.Reminders
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderAlarmTest {
    private fun at(s: String) = LocalDateTime.parse(s)

    @Test fun nextReminderIsLaterTodayOrTomorrowMorning() {
        val cfg = Reminders.Config()
        val a = ReminderAlarm.next(cfg, at("2026-11-02T08:00"))!!; assertEquals(at("2026-11-02T08:30"), a.at); assertEquals(0, a.slot)
        val b = ReminderAlarm.next(cfg, at("2026-11-02T12:00"))!!; assertEquals(at("2026-11-02T19:00"), b.at); assertEquals(1, b.slot)
        val c = ReminderAlarm.next(cfg, at("2026-11-02T20:00"))!!; assertEquals(at("2026-11-03T08:30"), c.at); assertEquals("2026-11-03", c.day)
    }

    @Test fun afterMidnightFollowUpBelongsToTheDayBefore() {
        val night = Reminders.Config(times = listOf(15 * 60, 60))
        val n = ReminderAlarm.next(night, at("2026-11-02T23:00"))!!
        assertEquals(at("2026-11-03T01:00"), n.at); assertEquals("2026-11-02", n.day); assertEquals(1, n.slot)
    }

    @Test fun weekendTimesApplyOnSaturday() {
        val cfg = Reminders.Config(weekendTimes = listOf(10 * 60))
        val n = ReminderAlarm.next(cfg, at("2026-10-30T20:00"))!!   // Friday evening → Saturday 10:00
        assertEquals(at("2026-10-31T10:00"), n.at)
    }
}
