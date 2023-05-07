package tools.samt.cli

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.FileNode
import tools.samt.parser.Parser
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ASTPrinterTest {
    @Test
    fun `correctly formats an AST dump`() {
        val fileNode = parse("""
            import foo.bar.baz.*

            package test.stuff

            record A {
              name: String(10..20, pattern("hehe"))
              age: Integer(0..150)
            }

            record B {}

            service MyService {
              testmethod(foo: A): B
            }
        """.trimIndent())

        val dump = ASTPrinter.dump(fileNode)
        val dumpWithoutColorCodes = dump.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            FileNode /tmp/ASTPrinterTest.samt <1:1>
            ├─WildcardImportNode <1:1>
            │ └─ImportBundleIdentifierNode foo.bar.baz.* <1:8>
            │   ├─IdentifierNode foo <1:8>
            │   ├─IdentifierNode bar <1:12>
            │   └─IdentifierNode baz <1:16>
            ├─PackageDeclarationNode <3:1>
            │ └─BundleIdentifierNode test.stuff <3:9>
            │   ├─IdentifierNode test <3:9>
            │   └─IdentifierNode stuff <3:14>
            ├─RecordDeclarationNode <5:1>
            │ ├─IdentifierNode A <5:8>
            │ ├─RecordFieldNode <6:3>
            │ │ ├─IdentifierNode name <6:3>
            │ │ └─CallExpressionNode <6:9>
            │ │   ├─BundleIdentifierNode String <6:9>
            │ │   │ └─IdentifierNode String <6:9>
            │ │   ├─RangeExpressionNode <6:16>
            │ │   │ ├─IntegerNode 10 <6:16>
            │ │   │ └─IntegerNode 20 <6:20>
            │ │   └─CallExpressionNode <6:24>
            │ │     ├─BundleIdentifierNode pattern <6:24>
            │ │     │ └─IdentifierNode pattern <6:24>
            │ │     └─StringNode "hehe" <6:32>
            │ └─RecordFieldNode <7:3>
            │   ├─IdentifierNode age <7:3>
            │   └─CallExpressionNode <7:8>
            │     ├─BundleIdentifierNode Integer <7:8>
            │     │ └─IdentifierNode Integer <7:8>
            │     └─RangeExpressionNode <7:16>
            │       ├─IntegerNode 0 <7:16>
            │       └─IntegerNode 150 <7:19>
            ├─RecordDeclarationNode <10:1>
            │ └─IdentifierNode B <10:8>
            └─ServiceDeclarationNode <12:1>
              ├─IdentifierNode MyService <12:9>
              └─RequestResponseOperationNode <13:3>
                ├─IdentifierNode testmethod <13:3>
                ├─OperationParameterNode <13:14>
                │ ├─IdentifierNode foo <13:14>
                │ └─BundleIdentifierNode A <13:19>
                │   └─IdentifierNode A <13:19>
                └─BundleIdentifierNode B <13:23>
                  └─IdentifierNode B <13:23>
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
