package cli

import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test

class CliCompilerTest {
    @Test
    fun `should not propagate exceptions`() {
        assertDoesNotThrow {
            parse(listOf("foo.samt"))
        }
    }
}
