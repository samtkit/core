package parser

import common.DiagnosticConsole
import common.DiagnosticContext
import lexer.Lexer
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class ParserUnitTest {
    @Test
    fun `without package`() {
        val source = """
            import foo
        """.trimIndent()
        val exception = parseWithFatalError(source)
        assertEquals("Files must have at least one package declaration", exception.message)
    }

    @Test
    fun `only package`() {
        val source = """
            package tools.samt.parser.foo
        """
        val fileTree = parse(source)
        assertPackage("tools.samt.parser.foo", fileTree.packageDeclaration)
        assertNodes(fileTree.imports)
        assertNodes(fileTree.statements)
    }

    @Test
    fun `multiple packages`() {
        val source = """
            package a
            package b
        """
        val (fileTree, diagnostics) = parseRecoverableError(source)
        assertEquals("Cannot have multiple package declarations per file", diagnostics.messages.single().message)
        assertPackage("b", fileTree.packageDeclaration)
        assertNodes(fileTree.imports)
        assertNodes(fileTree.statements)
    }

    @Test
    fun `empty record`() {
        val source = """
            package emptyRecord

            record A {}
        """
        val fileTree = parse(source)
        assertPackage("emptyRecord", fileTree.packageDeclaration)
        assertNodes(fileTree.imports)
        assertNodes(fileTree.statements) {
            assertNext<RecordDeclarationNode> {
                assertIdentifier("A", it.name)
                assertNodes(it.annotations)
                assertNodes(it.extends)
                assertNodes(it.fields)
            }
        }
    }

    @Test
    fun `empty record without braces`() {
        val source = """
            package emptyRecord

            record A
            record B extends com.test.C
        """
        val fileTree = parse(source)
        assertPackage("emptyRecord", fileTree.packageDeclaration)
        assertNodes(fileTree.imports)
        assertNodes(fileTree.statements) {
            assertNext<RecordDeclarationNode> { recordA ->
                assertIdentifier("A", recordA.name)
                assertNodes(recordA.annotations)
                assertNodes(recordA.extends)
                assertNodes(recordA.fields)
            }
            assertNext<RecordDeclarationNode> { recordB ->
                assertIdentifier("B", recordB.name)
                assertNodes(recordB.annotations)
                assertNodes(recordB.extends) {
                    assertNext<BundleIdentifierNode> {
                        assertBundleIdentifier("com.test.C", it)
                    }
                }
                assertNodes(recordB.fields)
            }
        }
    }

    @Test
    fun `simple and wildcard imports`() {
        val source = """
            import tools.samt.*
            import library.foo.bar.Baz
            import library.foo.bar.Baz as BazAlias

            package imports
        """
        val fileTree = parse(source)
        assertPackage("imports", fileTree.packageDeclaration)
        assertNodes(fileTree.imports) {
            assertNext<WildcardImportNode> {
                assertBundleIdentifier("tools.samt", it.name)
            }
            assertNext<TypeImportNode> {
                assertBundleIdentifier("library.foo.bar.Baz", it.name)
                assertNull(it.alias)
            }
            assertNext<TypeImportNode> {
                assertBundleIdentifier("library.foo.bar.Baz", it.name)
                assertNotNull(it.alias)
                assertIdentifier("BazAlias", it.alias!!)
            }
        }
        assertNodes(fileTree.statements)
    }

    @Test
    fun `imports must be before package`() {
        val source = """
            import tools.samt.*

            package badImports

            import library.foo.bar.Baz
        """
        val (fileTree, diagnostics) = parseRecoverableError(source)
        assertEquals("Import statements must be placed before the package declaration", diagnostics.messages.single().message)
        assertPackage("badImports", fileTree.packageDeclaration)
        assertNodes(fileTree.imports) {
            assertNext<WildcardImportNode> {
                assertBundleIdentifier("tools.samt", it.name)
            }
            assertNext<TypeImportNode> {
                assertBundleIdentifier("library.foo.bar.Baz", it.name)
                assertNull(it.alias)
            }
        }
        assertNodes(fileTree.statements)
    }

    @Test
    fun `record must be after package`() {
        val source = """
            record Foo {}

            package recordBeforePackage
        """
        val (fileTree, diagnostics) = parseRecoverableError(source)
        assertEquals("Expected a package declaration before any other statements", diagnostics.messages.single().message)
        assertPackage("recordBeforePackage", fileTree.packageDeclaration)
        assertNodes(fileTree.imports)
        assertNodes(fileTree.statements) {
            assertNext<RecordDeclarationNode> {
                assertIdentifier("Foo", it.name)
            }
        }
    }

    @Test
    fun `simple enum must be after package`() {
        val source = """
            package ^enum

            enum Foo {
                A B C
                D
            }
        """
        val fileTree = parse(source)
        assertPackage("enum", fileTree.packageDeclaration)
        assertNodes(fileTree.imports)
        assertNodes(fileTree.statements) {
            assertNext<EnumDeclarationNode> { enum ->
                assertIdentifier("Foo", enum.name)
                assertNodes(enum.values) {
                    assertNext<IdentifierNode> { assertIdentifier("A", it) }
                    assertNext<IdentifierNode> { assertIdentifier("B", it) }
                    assertNext<IdentifierNode> { assertIdentifier("C", it) }
                    assertNext<IdentifierNode> { assertIdentifier("D", it) }
                }
            }
        }
    }

    private fun parse(source: String): FileNode {
        val diagnostics = DiagnosticConsole(DiagnosticContext("ParserTest.samt", source))
        val stream = Lexer.scan(source.reader(), diagnostics)
        val fileTree = Parser.parse(stream, diagnostics)
        diagnostics.messages.forEach { println(it) }
        assertFalse(diagnostics.hasErrors(), "Expected no errors, but had errors")
        return fileTree
    }

    private fun parseRecoverableError(source: String): Pair<FileNode, DiagnosticConsole> {
        val diagnostics = DiagnosticConsole(DiagnosticContext("ParserTest.samt", source))
        val stream = Lexer.scan(source.reader(), diagnostics)
        val fileTree = Parser.parse(stream, diagnostics)
        diagnostics.messages.forEach { println(it) }
        assertTrue(diagnostics.hasErrors(), "Expected errors, but had no errors")
        return Pair(fileTree, diagnostics)
    }

    private fun parseWithFatalError(source: String): ParserException {
        val diagnostics = DiagnosticConsole(DiagnosticContext("ParserTest.samt", source))
        val stream = Lexer.scan(source.reader(), diagnostics)
        val ex = assertThrows<ParserException> { Parser.parse(stream, diagnostics) }
        diagnostics.messages.forEach { println(it) }
        assertTrue(diagnostics.hasErrors(), "Expected errors, but had no errors")
        return ex
    }

    private fun assertPackage(expectedPackageIdentifier: String, packageDeclaration: PackageDeclarationNode) {
        assertBundleIdentifier(expectedPackageIdentifier, packageDeclaration.name)
    }

    data class AssertNodesContext(val nodes: MutableList<Node>)

    private inline fun <reified T : Node> AssertNodesContext.assertNext(block: (node: T) -> Unit) {
        assertTrue(
            nodes.isNotEmpty(),
            "Expected node of type ${T::class.simpleName}, but no more nodes were found"
        )
        val node = nodes.removeFirst()
        assertIs<T>(
            node,
            "Expected node of type ${T::class.simpleName}, but got ${node::class.simpleName}"
        )
        block(node)
    }

    private fun assertNodes(nodes: List<Node>, assertBlock: AssertNodesContext.() -> Unit = {}) {
        val context = AssertNodesContext(nodes.toMutableList())
        context.assertBlock()
        assertTrue(context.nodes.isEmpty(), "Not all nodes were asserted")
    }

    private fun assertIdentifier(expected: String, actual: IdentifierNode) {
        assertEquals(expected, actual.name)
    }

    private fun assertBundleIdentifier(expected: String, actual: BundleIdentifierNode) {
        val expectedParts = expected.split(".")
        assertEquals(expectedParts.size, actual.components.size)
        for (i in expectedParts.indices) {
            assertEquals(expectedParts[i], actual.components[i].name)
        }
    }
}
