package tools.samt.cli

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.lexer.Token
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TokenPrinterTest {
    @Test
    fun `correctly formats a token dump`() {
        val tokenStream = parse("""
            import foo.bar.baz.*

            package test.stuff

            record A {
              name: String(10..20, pattern("hehe"))
              age: Int(0..150)
            }

            record B {}

            service MyService {
              testmethod(foo: A): B
            }
        """.trimIndent())

        val dump = TokenPrinter.dump(tokenStream)
        val dumpWithoutColorCodes = dump.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            import foo . bar . baz . * 
            package test . stuff 
            record A { 
            name : String ( 10 .. 20 , pattern ( "hehe" ) ) 
            age : Int ( 0 .. 150 ) 
            } 
            record B { } 
            service MyService { 
            testmethod ( foo : A ) : B 
            } EOF
        """.trimIndent().trim(), dumpWithoutColorCodes.trimIndent().trim())
    }

    private fun parse(source: String): Sequence<Token> {
        val filePath = URI("file:///tmp/TokenPrinterTest.samt")
        val sourceFile = SourceFile(filePath, source)
        val diagnosticController = DiagnosticController(URI("file:///tmp"))
        val diagnosticContext = diagnosticController.getOrCreateContext(sourceFile)
        val stream = Lexer.scan(source.reader(), diagnosticContext)
        assertFalse(diagnosticContext.hasErrors(), "Expected no errors, but had errors")
        return stream
    }
}
