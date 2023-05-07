package tools.samt.ls

import org.eclipse.lsp4j.Position
import tools.samt.common.DiagnosticContext
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TokensTest {
    @Test
    fun `finds token at position`() {
        val source = """
            package foo.bar.baz

            record Foo {
                bar: Int
            }
        """.trimIndent()
        val tokens = Lexer.scan(source.reader(), DiagnosticContext(SourceFile(URI("file:///tmp/test.samt"), source))).toList()

        assertEquals(tokens[0], tokens.findAt(Position(0, 0)))
        assertEquals(tokens[0], tokens.findAt(Position(0, 3)))
        assertEquals(tokens[0], tokens.findAt(Position(0, 7)))
        assertEquals(tokens[1], tokens.findAt(Position(0, 8)))
        assertEquals(tokens[3], tokens.findAt(Position(0, 12)))
        assertEquals(tokens[3], tokens.findAt(Position(0, 15)))
        assertEquals(tokens[5], tokens.findAt(Position(0, 19)))
        assertEquals(tokens[11], tokens.findAt(Position(3, 10)))

        assertNull(tokens.findAt(Position(1, 0)))
        assertNull(tokens.findAt(Position(2, 11)))
    }
}
