package looniemate.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Motion stays quick (docs/plans/overhaul.md): no animation in the app runs longer than 400 ms, and nothing animates
 * forever (no pulsing or shifting colours). Progress while downloading is a Material indicator, not one of these.
 */
class MotionTest {
    @Test fun animationsAreShortAndEnd() {
        val src = File("src/main/java").takeIf { it.isDirectory } ?: File("app/src/main/java")
        val problems = mutableListOf<String>()
        src.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                Regex("""tween\((\d+)""").findAll(line).forEach { m -> if (m.groupValues[1].toInt() > 400) problems.add("${f.name}:${i + 1} lasts ${m.groupValues[1]} ms") }
                Regex("""durationMillis\s*=\s*(\d+)""").findAll(line).forEach { m -> if (m.groupValues[1].toInt() > 400) problems.add("${f.name}:${i + 1} lasts ${m.groupValues[1]} ms") }
                if (!line.trimStart().startsWith("import") && (line.contains("infiniteRepeatable(") || line.contains("rememberInfiniteTransition(")))
                    problems.add("${f.name}:${i + 1} animates forever")
            }
        }
        assertTrue("Animations that are too long or never stop:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
