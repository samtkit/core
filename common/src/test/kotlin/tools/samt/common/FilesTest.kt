package tools.samt.common

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FilesTest {
    private val testDirectory = Path("src/test/resources/test-files")
    @BeforeTest
    fun setup() {
        assertTrue(testDirectory.exists() && testDirectory.isDirectory(), "Test directory does not exist")
    }

    @Test
    fun `collectSamtFiles only returns files with samt extension`() {
        val samtFiles = collectSamtFiles(testDirectory.toUri())

        val relativeFilePaths = samtFiles.map { testDirectory.toUri().relativize(it.toPath().toUri()).toString() }.sorted()
        assertEquals(listOf("foo.samt", "src/bar.samt", "src/baz.samt"), relativeFilePaths)
    }
    @Test
    fun `readSamtSource only returns files with samt extension`() {
        val files = listOf(
            testDirectory.resolve("foo.samt").toFile(),
            testDirectory.resolve("dummy.samt.txt").toFile(),
            testDirectory.resolve("src/bar.samt").toFile(),
            testDirectory.resolve("src/baz.samt").toFile(),
            testDirectory.resolve("src/missing.samt").toFile(),
            testDirectory.resolve(".samt").toFile(),
        )
        val controller = DiagnosticController(testDirectory.toUri())
        val samtFiles = files.readSamtSource(controller)

        val relativeFilePaths = samtFiles.map { testDirectory.toUri().relativize(it.path).toString() }.sorted()
        assertEquals(listOf("foo.samt", "src/bar.samt", "src/baz.samt"), relativeFilePaths)

        assertEquals(listOf(
            "File '${testDirectory.resolve("dummy.samt.txt").toFile().path}' must end in .samt",
            "File '${testDirectory.resolve("src/missing.samt").toFile().path}' does not exist",
            "'${testDirectory.resolve(".samt").toFile().path}' is not a file",
        ), controller.globalMessages.filter { it.severity == DiagnosticSeverity.Error }.map { it.message })
    }
}
