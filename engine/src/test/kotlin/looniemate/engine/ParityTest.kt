package looniemate.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ParityTest {
    @Test
    fun matchesTheWebVersion() {
        val f = File(System.getProperty("fixtures") ?: "../parity/fixtures.json")
        val diffs = Parity.run(f)
        assertTrue(diffs.isEmpty(), "The app engine differs from the web version:\n" + diffs.joinToString("\n"))
    }
}
