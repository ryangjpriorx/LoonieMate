package looniemate.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class IncomeSetupTest {
    @Test fun weekdaysCountFromSundayLikeJavaScript() {
        assertEquals(0, IncomeSetup.weekday("2026-10-25")); assertEquals(1, IncomeSetup.weekday("2026-10-26")); assertEquals(6, IncomeSetup.weekday("2026-10-31"))
    }

    @Test fun pickingHowOftenSetsTheWebDefaults() {
        assertEquals("""{"type":"monthly","day":1}""", Json.stringify(IncomeSetup.schedFor("monthly", "2026-10-25")))
        assertEquals("""{"type":"biweekly","anchor":"2026-10-26"}""", Json.stringify(IncomeSetup.schedFor("biweekly", "2026-10-25")))
        assertEquals("""{"type":"lastbiz"}""", Json.stringify(IncomeSetup.schedFor("lastbiz", "2026-10-25")))
    }

    @Test fun changingTheWeekdayMovesTheAnchorForward() {
        val s = J.obj("type" to "biweekly", "anchor" to "2026-10-26")
        assertEquals("2026-10-30", IncomeSetup.withWeekday(s, 5, "2026-10-25").s("anchor"))
        assertEquals("2026-10-26", IncomeSetup.withWeekday(s, 1, "2026-10-25").s("anchor"))
    }

    @Test fun newIncomeRolesFollowTheWeb() {
        assertEquals("main", IncomeSetup.newIncome(emptyList(), "i1").s("role"))
        assertEquals("other", IncomeSetup.newIncome(listOf(J.obj("role" to "main")), "i2").s("role"))
        val a = IncomeSetup.newAssist(emptyList(), "as1", "odsp"); assertEquals("rent", a.s("role")); assertEquals("odsp", a["sched"].s("type"))
        assertEquals(true, IncomeSetup.newCredit("ccb-1", "ccb").t("protect"))
    }

    @Test fun reportRemindersUseTheProgramsDay() {
        assertEquals(7.0, IncomeSetup.reportCfg(J.obj("assist" to "odsp")).n("day"))
        assertEquals(false, IncomeSetup.reportCfg(J.obj("name" to "Job")).t("on"))
    }
}
