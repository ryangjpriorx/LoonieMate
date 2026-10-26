package looniemate.app

import looniemate.app.data.Repository
import looniemate.engine.J
import looniemate.engine.n
import looniemate.engine.num
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

/** Keeping the budget on the phone: Undo, replacing from a backup, and a damaged file (docs/plans/polish-and-audit.md S3, S4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private fun dir(): File = Files.createTempDirectory("repo").toFile()
    private fun tomb(r: Repository, p: String): Double? = (r.docs.value["meta/tombstones"] as? J.O)?.map?.get(p)?.let { num(it) }

    @Test fun undoingADeletePutsItBackNewerThanItsTombstone() {
        val r = Repository(dir())
        r.set("bills/rent", J.obj("name" to "Rent", "amount" to 1450)); val before = r.docs.value["bills/rent"]
        r.delete("bills/rent"); assertNotNull(tomb(r, "bills/rent"))
        Thread.sleep(2); r.restoreAll(mapOf("bills/rent" to before, "meta/tombstones" to null))
        val back = r.docs.value["bills/rent"]; assertNotNull(back)
        assertEquals(1450.0, back.n("amount"), 0.0)
        assertNull("the tombstone goes, so sync doesn't delete it again", tomb(r, "bills/rent"))
        assertTrue("stamped as a new change", back.n("_u") > before.n("_u"))
    }

    @Test fun undoingAnAddLeavesATombstone() {
        val r = Repository(dir())
        r.set("bills/phone", J.obj("name" to "Phone", "amount" to 45))
        r.restoreAll(mapOf("bills/phone" to null))
        assertNull(r.docs.value["bills/phone"]); assertNotNull(tomb(r, "bills/phone"))
    }

    @Test fun replacingFromABackupRecordsWhatWasRemoved() {
        val r = Repository(dir())
        r.set("bills/old", J.obj("name" to "Old", "amount" to 5))
        r.replaceAll(mapOf("bills/new" to J.obj("name" to "New", "amount" to 9)), recordRemoved = true)
        assertNull(r.docs.value["bills/old"]); assertNotNull(tomb(r, "bills/old")); assertNotNull(r.docs.value["bills/new"])
    }

    @Test fun aDamagedFileIsKeptAndReported() {
        val d = dir(); File(d, "budget.json").writeText("{\"app\":\"loonie-mate\",\"data\":{\"bills/x\":")
        val r = Repository(d)
        assertEquals("damaged", r.problem.value)
        assertTrue(r.docs.value.isEmpty())
        assertTrue("a copy of the damaged file is kept", d.listFiles()!!.any { it.name.startsWith("budget-damaged-") })
    }
}
