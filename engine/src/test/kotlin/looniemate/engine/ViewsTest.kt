package looniemate.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The spreadsheet export and Last 6 months (docs/plans/polish-and-audit.md W6, W7). */
class ViewsTest {
    private fun store(): Store = Docs.toStore(mapOf(
        "settings/main" to J.obj("startYm" to "2026-05", "trackFrom" to "2026-05-01", "accounts" to listOf(J.obj("id" to "a", "name" to "Main", "type" to "chequing"))),
        "bills/r" to J.obj("name" to "Rent", "amount" to 1000, "day" to 1, "account" to "a", "category" to "housing"),
        "bills/x" to J.obj("name" to "=HACK, \"quoted\"", "amount" to 5, "day" to 2, "account" to "a"),
    ), "2026-10-15")

    private fun exp(date: String, amount: Double, cat: String) = J.obj("date" to date, "ym" to date.take(7), "amount" to amount, "category" to cat, "oneTime" to false)

    @Test fun spreadsheetCellsAreSafe() {
        assertEquals("plain", Views.csvCell("plain"))
        assertEquals("\"'=SUM(A1)\"", Views.csvCell("=SUM(A1)"))
        assertEquals("\"a,b\"", Views.csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Views.csvCell("say \"hi\""))
    }

    @Test fun billsAndSpendingAsSpreadsheets() {
        val st = store()
        val bills = Views.csv("bills", st, emptyList(), "2026-10-15").removePrefix("﻿").split("\r\n")
        assertTrue(bills[0].startsWith("Name,For,Type,Amount"))
        assertEquals(3, bills.size)
        assertTrue(bills.any { it.startsWith("\"'=HACK, \"\"quoted\"\"\"") }, "a risky name is quoted and defused")
        assertTrue(bills.any { it.startsWith("Rent,Adults,Rent & housing,1000.00,Monthly,Day 1,Main") })
        val sp = Views.csv("spending", st, listOf(exp("2026-10-03", 12.5, "groceries")), "2026-10-15").removePrefix("﻿").split("\r\n")
        assertEquals("2026-10-03,Groceries,12.50,,,,", sp[1])
    }

    @Test fun lastSixMonthsAndTheBiggestChange() {
        val st = store()
        val all = listOf(exp("2026-06-05", 60.0, "takeout"), exp("2026-07-05", 60.0, "takeout"), exp("2026-08-05", 60.0, "takeout"),
            exp("2026-09-05", 160.0, "takeout"), exp("2026-10-02", 20.0, "groceries"))
        val h = Views.history(st, all, "2026-10-15", "2026-10")
        assertEquals(listOf("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10"), h.rows.map { it.ym })
        assertTrue(h.rows.last().partial)
        assertEquals(1005.0, h.rows.first().fixed)
        assertEquals(160.0, h.rows.first { it.ym == "2026-09" }.food)
        assertNotNull(h.insight); assertTrue(h.insight!!.startsWith("Takeout & snacks was \$160.00 in September, up from about \$60.00"), h.insight)
    }
}
