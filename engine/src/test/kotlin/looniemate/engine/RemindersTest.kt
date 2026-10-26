package looniemate.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemindersTest {
    /* 2026-11-02 is a Monday; 2026-10-30 the Friday before */
    private fun docs(extra: Map<String, J> = emptyMap()): Map<String, J> = linkedMapOf<String, J>(
        "settings/main" to Json.parse("""{"startYm":"2026-10","trackFrom":"2026-10-01","accounts":[
            {"id":"a1","name":"Main Chequing","type":"chequing","purposes":["main"]},{"id":"a2","name":"Everyday Card","type":"chequing","purposes":["everyday"]}]}"""),
        "bills/bell" to Json.parse("""{"name":"Bell","group":"adults","amount":95,"freq":"monthly","day":2,"account":"a1","method":"Pre-authorized debit (PAD)","active":true}"""),
        "bills/gym" to Json.parse("""{"name":"Gym","group":"adults","amount":30,"freq":"monthly","day":2,"account":"a2","method":"Debit card","active":true}"""),
    ).also { it.putAll(extra) }

    private fun st(d: Map<String, J>, today: String) = Docs.toStore(d, today)
    private val cfg = Reminders.Config()

    @Test fun automaticPaymentHeadsUpMovesBackOverTheWeekend() {
        assertEquals("2026-10-30", Reminders.headsUpDay("2026-11-02", 2))
        assertEquals("2026-10-30", Reminders.headsUpDay("2026-11-02", 1))
        assertEquals("2026-11-03", Reminders.headsUpDay("2026-11-05", 2))
    }

    @Test fun headsUpOnlyInTheFirstReminder() {
        val s = st(docs(), "2026-10-30")
        val first = Reminders.digest(s, "2026-10-30", 0, 2, cfg)
        assertEquals(1, first.pads.size); assertEquals("Main Chequing", first.pads[0].account); assertEquals(95.0, first.pads[0].total)
        assertTrue(Reminders.padLine(first.pads[0]).startsWith("Coming out of Main Chequing by Monday: Bell \$95.00"))
        assertTrue(Reminders.digest(s, "2026-10-30", 1, 2, cfg).isEmpty)
    }

    @Test fun automaticPaymentsAreNotToDosButOtherBillsAre() {
        val d = Reminders.digest(st(docs(), "2026-11-02"), "2026-11-02", 0, 2, cfg)
        assertEquals(listOf("Gym"), d.todo.map { it.name }); assertEquals(false, d.todo[0].overdue)
        val late = Reminders.digest(st(docs(), "2026-11-03"), "2026-11-03", 1, 2, cfg)
        assertEquals(listOf("Gym"), late.todo.map { it.name }); assertEquals(true, late.todo[0].overdue)
    }

    @Test fun somethingTickedOffIsNeverSent() {
        val d = Reminders.digest(st(docs(), "2026-11-02"), "2026-11-02", 0, 2, cfg)
        val key = d.todo[0].key
        val paid = docs(mapOf("months/2026-11" to J.obj("paid" to J.obj(key to true))))
        assertTrue(Reminders.digest(st(paid, "2026-11-02"), "2026-11-02", 1, 2, cfg).isEmpty)
    }

    @Test fun nothingDueMeansNoNotification() {
        assertTrue(Reminders.digest(st(docs(), "2026-10-27"), "2026-10-27", 0, 2, cfg).isEmpty)
    }

    @Test fun anAfterMidnightFollowUpBelongsToThePreviousDay() {
        val night = Reminders.Config(times = listOf(15 * 60, 60))
        assertEquals("2026-11-02", Reminders.wakingDay(night, "2026-11-03", 60))
        assertEquals("2026-11-03", Reminders.wakingDay(night, "2026-11-03", 15 * 60))
        assertEquals("2026-11-03", Reminders.wakingDay(cfg, "2026-11-03", 19 * 60))
    }

    @Test fun summaryCountsThings() {
        val d = Reminders.digest(st(docs(), "2026-11-03"), "2026-11-03", 0, 2, cfg)
        assertEquals("1 thing to do today · 1 overdue", Reminders.summary(d))
    }
}
