package looniemate.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Two phones keeping one budget through one shared file, the way SyncManager does it. */
class SyncTest {
    private class Phone(var data: LinkedHashMap<String, J>, var base: Map<String, J>? = null) {
        fun set(k: String, v: J, u: Long) { data[k] = (v.copy() as J.O).also { it["_u"] = J.N(u.toDouble()) } }
        fun delete(k: String, u: Long) { data.remove(k); val t = (data["meta/tombstones"]?.copy() as? J.O) ?: J.O(); t["${k}"] = J.N(u.toDouble()); data["meta/tombstones"] = t }
    }
    private var file: Map<String, J>? = null
    private fun sync(p: Phone) {
        val r = Sync.reconcile(p.data, file, p.base)
        p.data = LinkedHashMap(r.data); if (r.changedRemote) file = LinkedHashMap(r.data); p.base = LinkedHashMap(r.data)
    }
    private fun two(): Pair<Phone, Phone> {
        val a = Phone(LinkedHashMap()); a.set("settings/main", J.obj("foodPct" to 40, "householdName" to "Test"), 1); a.set("bills/rent", J.obj("name" to "Rent", "amount" to 1450), 1)
        sync(a); val b = Phone(LinkedHashMap()); sync(b); return a to b
    }

    @Test fun theSecondPhoneGetsEverything() {
        val (a, b) = two(); assertEquals(Sync.canon(J.O(a.data)), Sync.canon(J.O(b.data)))
    }

    @Test fun editsOnBothPhonesAreKept() {
        val (a, b) = two()
        a.set("bills/phone", J.obj("name" to "Phone", "amount" to 45), 10)
        b.set("bills/hydro", J.obj("name" to "Hydro", "amount" to 84), 11)
        sync(a); sync(b); sync(a)
        for (p in listOf(a, b)) { assertTrue("bills/phone" in p.data); assertTrue("bills/hydro" in p.data) }
    }

    @Test fun differentFieldsOfOneDocumentBothSurvive() {
        val (a, b) = two()
        a.set("settings/main", J.obj("foodPct" to 55, "householdName" to "Test"), 10)
        b.set("settings/main", J.obj("foodPct" to 40, "householdName" to "Priors"), 11)
        sync(a); sync(b); sync(a)
        for (p in listOf(a, b)) { assertEquals(55.0, p.data["settings/main"].n("foodPct")); assertEquals("Priors", p.data["settings/main"].s("householdName")) }
    }

    @Test fun theNewerChangeWinsWhenBothChangeTheSameThing() {
        val (a, b) = two()
        a.set("bills/rent", J.obj("name" to "Rent", "amount" to 1500), 10)
        b.set("bills/rent", J.obj("name" to "Rent", "amount" to 1475), 12)
        sync(a); sync(b); sync(a)
        for (p in listOf(a, b)) assertEquals(1475.0, p.data["bills/rent"].n("amount"))
    }

    @Test fun undoingAPaidTickSticks() {
        val (a, b) = two()
        a.set("months/2026-11", J.obj("paid" to J.obj("rent@2026-11-01" to true)), 10); sync(a); sync(b)
        assertTrue(b.data["months/2026-11"]?.get("paid")?.get("rent@2026-11-01").let { truthy(it) })
        b.set("months/2026-11", J.obj("paid" to J.obj("rent@2026-11-01" to false)), 11); sync(b); sync(a)
        for (p in listOf(a, b)) assertFalse(truthy(p.data["months/2026-11"]?.get("paid")?.get("rent@2026-11-01")))
    }

    @Test fun ticksOnBothPhonesInTheSameMonthAreKept() {
        val (a, b) = two()
        a.set("months/2026-11", J.obj("paid" to J.obj("rent@2026-11-01" to true)), 10)
        b.set("months/2026-11", J.obj("paid" to J.obj("phone@2026-11-05" to true)), 11)
        sync(a); sync(b); sync(a)
        for (p in listOf(a, b)) { val paid = p.data["months/2026-11"]?.get("paid")
            assertTrue(truthy(paid?.get("rent@2026-11-01"))); assertTrue(truthy(paid?.get("phone@2026-11-05"))) }
    }

    @Test fun savingsTotalsAddUpFromBothPhones() {
        val (a, b) = two()
        a.set("months/2026-11", J.obj("moved" to J.obj("emergency" to 50)), 5); sync(a); sync(b)
        a.set("months/2026-11", J.obj("moved" to J.obj("emergency" to 150)), 10)
        b.set("months/2026-11", J.obj("moved" to J.obj("emergency" to 80)), 11)
        sync(a); sync(b); sync(a)
        for (p in listOf(a, b)) assertEquals(180.0, p.data["months/2026-11"]?.get("moved").n("emergency"))
    }

    @Test fun deletionsCarryOver() {
        val (a, b) = two()
        b.delete("bills/rent", 10); sync(b); sync(a)
        assertNull(a.data["bills/rent"])
        /* a deletion on one phone and an older edit on the other: the deletion wins */
        val (c, d) = two()
        c.set("bills/rent", J.obj("name" to "Rent", "amount" to 1500), 5); d.delete("bills/rent", 9)
        sync(c); sync(d); sync(c)
        assertNull(c.data["bills/rent"]); assertNull(d.data["bills/rent"])
    }

    /** The file goes back to an older copy (a cloud app's cache, or the other phone writing from an older copy). */
    @Test fun aStaleFileDoesNotDeleteNewItems() {
        val (a, b) = two()
        val old = file
        a.set("bills/phone", J.obj("name" to "Phone", "amount" to 45), 10); sync(a)
        file = old                          /* the file loses the new bill */
        sync(a); sync(b)
        for (p in listOf(a, b)) assertEquals(45.0, p.data["bills/phone"].n("amount"), "the new bill survives on both phones")
    }

    @Test fun aStaleFileDoesNotUndoEdits() {
        val (a, b) = two()
        val old = file
        a.set("bills/rent", J.obj("name" to "Rent", "amount" to 1500), 10); sync(a)
        file = old                          /* back to the old rent */
        sync(a); sync(b)
        for (p in listOf(a, b)) assertEquals(1500.0, p.data["bills/rent"].n("amount"))
        /* and when this phone changes something else in the same item meanwhile, the stale copy still doesn't win */
        val (c, d) = two(); val old2 = file
        c.set("bills/rent", J.obj("name" to "Rent", "amount" to 1500), 10); sync(c)
        file = old2; c.set("bills/rent", J.obj("name" to "Rent (new)", "amount" to 1500), 11)
        sync(c); sync(d)
        for (p in listOf(c, d)) { assertEquals(1500.0, p.data["bills/rent"].n("amount")); assertEquals("Rent (new)", p.data["bills/rent"].s("name")) }
    }

    @Test fun deletionsStillCarryOverWithTheStaleFileGuard() {
        val (a, b) = two()
        a.set("bills/phone", J.obj("name" to "Phone", "amount" to 45), 10); sync(a); sync(b)
        b.delete("bills/phone", 20); sync(b); sync(a)
        assertNull(a.data["bills/phone"]); assertNull(b.data["bills/phone"])
    }

    /** A phone whose budget file couldn't be read starts empty; sync brings everything back rather than deleting it everywhere. */
    @Test fun anEmptiedPhoneGetsItsBudgetBackInsteadOfWipingTheFile() {
        val (a, b) = two()
        a.data = LinkedHashMap(); sync(a); sync(b)
        for (p in listOf(a, b)) { assertEquals(1450.0, p.data["bills/rent"].n("amount")); assertEquals("Test", p.data["settings/main"].s("householdName")) }
    }

    @Test fun nothingChangedMeansNothingToWrite() {
        val (a, _) = two()
        val r = Sync.reconcile(a.data, file, a.base); assertFalse(r.changedLocal); assertFalse(r.changedRemote)
    }
}
