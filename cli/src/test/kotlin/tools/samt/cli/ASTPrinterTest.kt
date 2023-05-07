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
            import foo.bar.baz.A as B

            package test.stuff

            record A {
              name: String(10..20, pattern("hehe"))
              age: Integer(0..150)
            }

            enum E { A, B, C }

            @Description("This is a service")
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
            ├─TypeImportNode <2:1>
            │ ├─ImportBundleIdentifierNode foo.bar.baz.A <2:8>
            │ │ ├─IdentifierNode foo <2:8>
            │ │ ├─IdentifierNode bar <2:12>
            │ │ ├─IdentifierNode baz <2:16>
            │ │ └─IdentifierNode A <2:20>
            │ └─IdentifierNode B <2:25>
            ├─PackageDeclarationNode <4:1>
            │ └─BundleIdentifierNode test.stuff <4:9>
            │   ├─IdentifierNode test <4:9>
            │   └─IdentifierNode stuff <4:14>
            ├─RecordDeclarationNode <6:1>
            │ ├─IdentifierNode A <6:8>
            │ ├─RecordFieldNode <7:3>
            │ │ ├─IdentifierNode name <7:3>
            │ │ └─CallExpressionNode <7:9>
            │ │   ├─BundleIdentifierNode String <7:9>
            │ │   │ └─IdentifierNode String <7:9>
            │ │   ├─RangeExpressionNode <7:16>
            │ │   │ ├─IntegerNode 10 <7:16>
            │ │   │ └─IntegerNode 20 <7:20>
            │ │   └─CallExpressionNode <7:24>
            │ │     ├─BundleIdentifierNode pattern <7:24>
            │ │     │ └─IdentifierNode pattern <7:24>
            │ │     └─StringNode "hehe" <7:32>
            │ └─RecordFieldNode <8:3>
            │   ├─IdentifierNode age <8:3>
            │   └─CallExpressionNode <8:8>
            │     ├─BundleIdentifierNode Integer <8:8>
            │     │ └─IdentifierNode Integer <8:8>
            │     └─RangeExpressionNode <8:16>
            │       ├─IntegerNode 0 <8:16>
            │       └─IntegerNode 150 <8:19>
            ├─EnumDeclarationNode <11:1>
            │ ├─IdentifierNode E <11:6>
            │ ├─IdentifierNode A <11:10>
            │ ├─IdentifierNode B <11:13>
            │ └─IdentifierNode C <11:16>
            └─ServiceDeclarationNode <14:1>
              ├─IdentifierNode MyService <14:9>
              ├─RequestResponseOperationNode <15:3>
              │ ├─IdentifierNode testmethod <15:3>
              │ ├─OperationParameterNode <15:14>
              │ │ ├─IdentifierNode foo <15:14>
              │ │ └─BundleIdentifierNode A <15:19>
              │ │   └─IdentifierNode A <15:19>
              │ └─BundleIdentifierNode B <15:23>
              │   └─IdentifierNode B <15:23>
              └─AnnotationNode <13:1>
                ├─IdentifierNode Description <13:2>
                └─StringNode "This is a service" <13:14>
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
