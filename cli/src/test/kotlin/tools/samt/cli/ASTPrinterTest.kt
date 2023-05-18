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

            typealias B : E

            @Description("This is a service")
            service MyService {
              testmethod(foo: A): B
            }

            provide MyEndpoint {
              implements MyService
              transport HTTP
            }

            consume MyEndpoint {
              uses MyService
            }
        """.trimIndent())

        val dump = ASTPrinter.dump(fileNode)
        val dumpWithoutColorCodes = dump.replace(Regex("\u001B\\[[;\\d]*m"), "")

        assertEquals("""
            FileNode file:///tmp/ASTPrinterTest.samt <1:1>
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
            ├─TypeAliasNode <13:1>
            │ ├─IdentifierNode B <13:11>
            │ └─BundleIdentifierNode E <13:15>
            │   └─IdentifierNode E <13:15>
            ├─ServiceDeclarationNode <16:1>
            │ ├─IdentifierNode MyService <16:9>
            │ ├─RequestResponseOperationNode <17:3>
            │ │ ├─IdentifierNode testmethod <17:3>
            │ │ ├─OperationParameterNode <17:14>
            │ │ │ ├─IdentifierNode foo <17:14>
            │ │ │ └─BundleIdentifierNode A <17:19>
            │ │ │   └─IdentifierNode A <17:19>
            │ │ └─BundleIdentifierNode B <17:23>
            │ │   └─IdentifierNode B <17:23>
            │ └─AnnotationNode <15:1>
            │   ├─IdentifierNode Description <15:2>
            │   └─StringNode "This is a service" <15:14>
            ├─ProviderDeclarationNode <20:1>
            │ ├─IdentifierNode MyEndpoint <20:9>
            │ ├─ProviderImplementsNode <21:3>
            │ │ └─BundleIdentifierNode MyService <21:14>
            │ │   └─IdentifierNode MyService <21:14>
            │ └─ProviderTransportNode <22:3>
            │   └─IdentifierNode HTTP <22:13>
            └─ConsumerDeclarationNode <25:1>
              ├─BundleIdentifierNode MyEndpoint <25:9>
              │ └─IdentifierNode MyEndpoint <25:9>
              └─ConsumerUsesNode <26:3>
                └─BundleIdentifierNode MyService <26:8>
                  └─IdentifierNode MyService <26:8>
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
