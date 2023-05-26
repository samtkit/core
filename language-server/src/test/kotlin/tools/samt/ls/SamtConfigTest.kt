package tools.samt.ls

import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SamtConfigTest {
    private val testDirectory = Path("src/test/resources/path-test")
    @BeforeTest
    fun setup() {
        assertTrue(testDirectory.exists() && testDirectory.isDirectory(), "Test directory does not exist")
    }

    @Test
    fun `no samt yaml`() {
        val workDir = testDirectory.resolve("no-yaml")
        val configs = findSamtConfigs(workDir)
        assertEquals(emptyList(), configs)
    }

    @Test
    fun `samt yaml in workdir`() {
        val workDir = testDirectory.resolve("yaml-in-workdir")
        val configs = findSamtConfigs(workDir)
        assertEquals(listOf(workDir.resolve("samt.yaml")), configs)
    }

    @Test
    fun `samt yaml in subdir`() {
        val workDir = testDirectory.resolve("yaml-in-subdir")
        val configs = findSamtConfigs(workDir)
        assertEquals(listOf(workDir.resolve("sub/samt.yaml")), configs)
    }

    @Test
    fun `samt yaml in parent dir`() {
        val workDir = testDirectory.resolve("yaml-in-parentdir/parent/work")
        val configs = findSamtConfigs(workDir)
        assertEquals(listOf(workDir.resolveSibling("samt.yaml")), configs)
    }
}