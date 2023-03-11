package parser

import common.DiagnosticConsole
import common.DiagnosticContext
import lexer.Lexer
import kotlin.test.*

class ParserUnitTest {
    @Test
    fun emptySource() {
        val source = """
            package tools.samt.parser.foo
        """
        val fileTree = parse(source)
        assertIsEmpty(fileTree.imports)
        assertIsEmpty(fileTree.statements)
        assertBundleIdentifier("tools.samt.parser.foo", fileTree.packageDeclaration.name)
    }

    @Test
    fun emptyRecord() {
        val source = """
            package twoRecords

            record A {}
        """
        val fileTree = parse(source)
        assertIsEmpty(fileTree.imports)
        assertBundleIdentifier("twoRecords", fileTree.packageDeclaration.name)
        val recordA = fileTree.statements.single()
        assertIs<RecordDeclarationNode>(recordA)
        assertIdentifier("A", recordA.name)
        assertIsEmpty(recordA.annotations)
        assertIsEmpty(recordA.extends)
        assertIsEmpty(recordA.fields)
    }

    private fun parse(source: String): FileNode {
        val diagnostics = DiagnosticConsole(DiagnosticContext("ParserTest.samt", source))
        val stream = Lexer.scan(source.reader(), diagnostics)
        val fileTree = Parser.parse(stream, diagnostics)
        diagnostics.messages.forEach { println(it) }
        assertFalse(diagnostics.hasErrors())
        return fileTree
    }
    private fun assertIdentifier(expected: String, actual: IdentifierNode) {
        assertEquals(expected, actual.name)
    }

    private fun <T> assertIsEmpty(collection: Collection<T>) {
        assertEquals(0, collection.size, "Expected empty collection, but was $collection")
    }

    private fun assertBundleIdentifier(expected: String, actual: BundleIdentifierNode) {
        val expectedParts = expected.split(".")
        assertEquals(expectedParts.size, actual.components.size)
        for (i in expectedParts.indices) {
            assertEquals(expectedParts[i], actual.components[i].name)
        }
    }
}
