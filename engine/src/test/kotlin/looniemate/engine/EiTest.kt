package looniemate.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Employment Insurance and how assistance counts each kind of income (docs/plans/income-and-assistance.md). */
class EiTest {
    private val ei = J.obj("id" to "e", "name" to "EI", "kind" to "ei", "amount" to 400, "sched" to J.obj("type" to "biweekly", "anchor" to "2026-11-04"), "end" to "2026-12-16")

    @Test fun eachKindCountsItsOwnWay() {
        assertEquals("full", Core.countsAs(ei))
        assertEquals("spouse", Core.countsAs(J.obj("kind" to "pay")))
        assertEquals("dis", Core.countsAs(J.obj("kind" to "pay", "odsp" to "dis")))
        assertEquals("full", Core.countsAs(J.obj("kind" to "other", "odsp" to "full")))
        assertEquals("", Core.countsAs(J.obj("kind" to "other")))
        assertEquals("", Core.countsAs(J.obj("assist" to "odsp", "kind" to "pay")))
    }

    @Test fun eiStopsAfterItsLastPayment() {
        assertTrue(Core.inRange(ei, "2026-12-16"))
        assertTrue(!Core.inRange(ei, "2026-12-30"))
    }

    @Test fun eiReportEveryTwoWeeks() {
        /* reports count from 3 days before a payday: Nov 1, 15, 29, ... */
        assertEquals("2026-11-15", Views.eiReportDay(ei, "2026-11-15"))
        assertEquals("2026-11-15", Views.eiReportDay(ei, "2026-11-21"))
        assertNull(Views.eiReportDay(ei, "2026-11-22"))
        assertEquals("2026-11-29", Views.eiReportDay(ei, "2026-11-29"))
        /* nothing long after EI ends */
        assertNull(Views.eiReportDay(ei, "2027-02-07"))
        /* a chosen report day wins */
        val own = ei.copy() as Obj; own["report"] = J.obj("on" to true, "every" to 14, "anchor" to "2026-11-08")
        assertEquals("2026-11-22", Views.eiReportDay(own, "2026-11-23"))
        own["report"] = J.obj("on" to false)
        assertNull(Views.eiReportDay(own, "2026-11-23"))
    }
}
