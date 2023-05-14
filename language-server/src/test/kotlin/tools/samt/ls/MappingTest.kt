package tools.samt.ls

import org.eclipse.lsp4j.DiagnosticRelatedInformation
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import tools.samt.common.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import tools.samt.common.Location as SamtLocation

class MappingTest {

    @Test
    fun `toDiagnostic returns null if no highlights`() {
        val message = DiagnosticMessage(
                DiagnosticSeverity.Error,
                "message",
                listOf(),
                listOf()
        )
        val diagnostic = message.toDiagnostic()
        assertNull(diagnostic)
    }

    @Test
    fun `toDiagnostic converts to LSP diagnostic`() {
        val location = SamtLocation(
                SourceFile("file:///tmp/test.samt".toPathUri(), "package foo.bar\nrecord Foo {}"),
                FileOffset(0, 0, 2),
                FileOffset(10, 1, 3)
        )
        val highlight = DiagnosticHighlight(
                "highlight message",
                location,
                "suggestion",
                false
        )
        val message = DiagnosticMessage(
                DiagnosticSeverity.Error,
                "message",
                listOf(highlight),
                listOf()
        )
        val diagnostic = message.toDiagnostic()
        assertNotNull(diagnostic)
        assertEquals("message", diagnostic.message)
        assertEquals(org.eclipse.lsp4j.DiagnosticSeverity.Error, diagnostic.severity)
        assertEquals("samt", diagnostic.source)
        assertEquals(Range(
                Position(0, 2),
                Position(1, 3)
        ), diagnostic.range)
        assertEquals(listOf(DiagnosticRelatedInformation(
                Location("file:///tmp/test.samt", Range(
                        Position(0, 2),
                        Position(1, 3)
                )),
                "highlight message"
        )), diagnostic.relatedInformation)
    }

    @Test
    fun `toLspSeverity converts to LSP severity`() {
        val expected = mapOf(
                DiagnosticSeverity.Error to org.eclipse.lsp4j.DiagnosticSeverity.Error,
                DiagnosticSeverity.Warning to org.eclipse.lsp4j.DiagnosticSeverity.Warning,
                DiagnosticSeverity.Info to org.eclipse.lsp4j.DiagnosticSeverity.Information
        )
        assertEquals(expected, DiagnosticSeverity.values().associateWith { it.toLspSeverity() })
    }

    @Test
    fun `toRange converts to LSP range`() {
        val sourceFile = SourceFile("file:///tmp/test.samt".toPathUri(), "package foo.bar\nrecord Foo {}")
        val start = FileOffset(0, 0, 2)
        val end = FileOffset(10, 1, 3)
        val location = SamtLocation(
                sourceFile,
                start,
                end
        )
        val range = location.toRange()
        assertEquals(Range(
                Position(0, 2),
                Position(1, 3)
        ), range)
    }
}
