package tools.samt.ls

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import tools.samt.common.SourceFile
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileInfoTest {
    @Test
    fun `parseFile conserves path`() {
        val sourceFile = SourceFile("file:///tmp/test.samt".toPathUri(), "package foo.bar")
        val fileInfo = parseFile(sourceFile)
        assertEquals(sourceFile.path, fileInfo.path)
    }

    @Test
    fun `parseFile handles fatal lexer errors`() {
        val sourceFile = SourceFile("file:///tmp/test.samt".toPathUri(), "package foo.bar;")
        assertDoesNotThrow {
            val fileInfo = parseFile(sourceFile)
            assertEquals(emptyList(), fileInfo.tokens)
            assertNull(fileInfo.fileNode)
            assertTrue(fileInfo.diagnosticContext.hasErrors())
        }
    }

    @Test
    fun `parseFile handles non fatal lexer errors`() {
        val sourceFile = SourceFile("file:///tmp/test.samt".toPathUri(), "\"foo")
        val fileInfo = parseFile(sourceFile)
        assertTrue(fileInfo.tokens.isNotEmpty())
        assertNull(fileInfo.fileNode)
        assertTrue(fileInfo.diagnosticContext.hasErrors())
    }

    @Test
    fun `parseFile handles fatal parser errors`() {
        val sourceFile = SourceFile("file:///tmp/test.samt".toPathUri(), "package foo.bar record Greeter { } }")
        assertDoesNotThrow {
            val fileInfo = parseFile(sourceFile)
            assertNull(fileInfo.fileNode)
            assertTrue(fileInfo.diagnosticContext.hasErrors())
        }
    }

    @Test
    fun `parseFile returns tokens and AST`() {
        val sourceFile = SourceFile("file:///tmp/test.samt".toPathUri(), "package foo.bar")
        val fileInfo = parseFile(sourceFile)
        assertEquals(emptyList(),  fileInfo.diagnosticContext.messages)
        assertTrue(fileInfo.tokens.isNotEmpty())
        assertNotNull(fileInfo.fileNode)
    }
}
