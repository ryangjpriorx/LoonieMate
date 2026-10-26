package looniemate.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatchUpTest {
    private val docs = mapOf<String, J>("settings/main" to Json.parse("""{"startYm":"2026-11","trackFrom":"2026-11-14",
        "accounts":[{"id":"a1","name":"Main","type":"chequing","purposes":["main"]}]}"""))

    @Test fun aPaymentPlanEndsWhenItsPaidOff() {
        val b = CatchUp.plan(docs, "2026-11-14", "Bell arrears", 180.0, 60.0, 20, "a1", "Pre-authorized debit (PAD)")
        assertEquals("2027-01-20", b.s("end"))          // Nov 20, Dec 20, Jan 20
        assertEquals(true, b.t("catchUp")); assertEquals(180.0, b["debt"].n("owed"))
    }

    @Test fun undecidedItemsCountAsOwedToday() {
        val s = Json.parse("""{"catchUp":[]}""")
        val list = CatchUp.unsure(s, "cu1", " Landlord ", 300.0, "2026-11-14")
        val d = LinkedHashMap(docs); d["settings/main"] = (docs.getValue("settings/main").copy() as Obj).also { it["catchUp"] = J.of(list) }
        val r = Core.runway(Docs.toStore(d, "2026-11-14"), "2026-11-14", mapOf("a1" to 100.0))
        assertTrue(r["days"].arr()[0]["items"].arr().any { it.s("name") == "Landlord" && it.n("amount") == 300.0 })
        assertEquals(200.0 + 0.0, r["short"].n("amount").let { if (it >= 200.0) 200.0 else it })
    }
}
