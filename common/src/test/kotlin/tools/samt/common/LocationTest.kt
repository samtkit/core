package tools.samt.common

import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.net.URI
import kotlin.test.*

class LocationTest {
    private val dummySource = SourceFile(
        URI("file://locations.samt"), """
        import foo
        import bar

        package tools.samt.common

        record Baz
    """.trimIndent()
    )

    @Test
    fun `cannot have row outside of source`() {
        assertThrows<IllegalArgumentException> {
            Location(dummySource, FileOffset(0, 0, 0), FileOffset(50, 50, 0))
        }
    }

    @Test
    fun `cannot have greater end row than start`() {
        assertThrows<IllegalArgumentException> {
            Location(dummySource, FileOffset(0, 1, 0), FileOffset(1, 0, 1))
        }
    }

    @Test
    fun `cannot have greater end col than start on same row`() {
        assertDoesNotThrow {
            Location(dummySource, FileOffset(10, 0, 10), FileOffset(11, 1, 0))
        }
        assertThrows<IllegalArgumentException> {
            Location(dummySource, FileOffset(10, 0, 10), FileOffset(9, 0, 9))
        }
    }

    @Test
    fun `single row location behaves as expected`() {
        val firstChar = Location(dummySource, FileOffset(0, 0, 0), FileOffset(1, 0, 1))
        assertTrue(firstChar.containsRow(0))
        assertTrue(firstChar.containsRowColumn(0, 0))
        assertFalse(firstChar.containsRowColumn(0, 1))
    }

    @Test
    fun `multiline location behaves as expected`() {
        val firstChar = Location(dummySource, FileOffset(5, 1, 1), FileOffset(25, 3, 15))
        assertFalse(firstChar.containsRow(0))
        assertTrue(firstChar.containsRow(1))
        assertTrue(firstChar.containsRow(2))
        assertTrue(firstChar.containsRow(3))
        assertFalse(firstChar.containsRow(4))
        assertFalse(firstChar.containsRowColumn(0, 0))
        assertFalse(firstChar.containsRowColumn(0, 1))
        assertFalse(firstChar.containsRowColumn(1, 0))
        assertTrue(firstChar.containsRowColumn(1, 1))
        assertTrue(firstChar.containsRowColumn(2, 0))
        assertTrue(firstChar.containsRowColumn(2, 45))
        assertTrue(firstChar.containsRowColumn(3, 14))
        assertFalse(firstChar.containsRowColumn(3, 15))
        assertFalse(firstChar.containsRowColumn(4, 0))
    }
}