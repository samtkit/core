package tools.samt.cli

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.FileNode
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModelBuilder
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TypePrinterTest {
    @Test
    fun `correctly formats an AST dump`() {
        val stuffPackage = parse("""
            package test.stuff

            record A {
              name: String (10..20, pattern("hehe"))
              age: Int (0..150)
            }

            enum E { A, B, C }

            @Description("This is a service")
            service MyService {
              testmethod(foo: A): E
            }

            provide MyEndpoint {
              implements MyService
              transport HTTP
            }
        """.trimIndent())
        val consumerPackage = parse("""
            package test.other.company

            record Empty

            consume test.stuff.MyEndpoint {
              uses test.stuff.MyService
            }
        """.trimIndent())

        val controller = DiagnosticController(URI("file:///tmp"))
        val samtPackage = SemanticModelBuilder.build(listOf(stuffPackage, consumerPackage), controller)
        assertFalse(controller.hasErrors())

        val dump = TypePrinter.dump(samtPackage)
        val dumpWithoutColorCodes = dump.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            <root>
            └─test
              ├─stuff
              │  enum E
              │  record A
              │  service MyService
              │  provider MyEndpoint
              └─other
                └─company
                   record Empty
                   consumer for MyEndpoint
        """.trimIndent().trim(), dumpWithoutColorCodes.trimIndent().trim())
    }

    private fun parse(source: String): FileNode {
        val filePath = URI("file:///tmp/ASTPrinterTest.samt")
        val sourceFile = SourceFile(filePath, source)
        val diagnosticController = DiagnosticController(URI("file:///tmp"))
        val diagnosticContext = diagnosticController.getOrCreateContext(sourceFile)
        val stream = Lexer.scan(source.reader(), diagnosticContext)
        val fileTree = Parser.parse(sourceFile, stream, diagnosticContext)
        assertFalse(diagnosticContext.hasErrors(), "Expected no errors, but had errors")
        return fileTree
    }
}
