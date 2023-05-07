package tools.samt.common

import org.junit.jupiter.api.assertThrows
import java.net.URI
import kotlin.test.*

class DiagnosticsTest {

    @Test
    fun `global messages`() {
        val controller = DiagnosticController(URI("file:///tmp"))
        controller.reportGlobalError("some error")
        controller.reportGlobalWarning("some warning")
        controller.reportGlobalInfo("some information")

        assertTrue(controller.hasMessages())
        assertTrue(controller.hasErrors())
        assertTrue(controller.hasWarnings())
        assertTrue(controller.hasInfos())

        assertEquals(3, controller.globalMessages.size)
        assertEquals(DiagnosticSeverity.Error, controller.globalMessages[0].severity)
        assertEquals(DiagnosticSeverity.Warning, controller.globalMessages[1].severity)
        assertEquals(DiagnosticSeverity.Info, controller.globalMessages[2].severity)

        assertEquals("some error", controller.globalMessages[0].message)
        assertEquals("some warning", controller.globalMessages[1].message)
        assertEquals("some information", controller.globalMessages[2].message)
    }

    @Test
    fun `fatal error messages`() {
        val controller = DiagnosticController(URI("file:///tmp"))
        val sourcePath = URI("file:///tmp/sourceFile")
        val sourceCode = """
            import foo as bar
            package debug
        """.trimIndent()
        val sourceFile = SourceFile(sourcePath, sourceCode)
        val context = controller.getOrCreateContext(sourceFile)

        assertThrows<DiagnosticException>("some fatal error") {
            context.fatal {
                message("some fatal error")
            }
        }

        assertTrue(context.hasMessages())
        assertTrue(context.hasErrors())
        assertFalse(context.hasWarnings())
        assertFalse(context.hasInfos())

        val message = context.messages[0]
        assertEquals(DiagnosticSeverity.Error, message.severity)
        assertEquals("some fatal error", message.message)
        assertEquals(0, message.highlights.size)
        assertEquals(0, message.annotations.size)
    }

    @Test
    fun `message highlights and annotations`() {
        val controller = DiagnosticController(URI("file:///tmp"))
        val sourcePath = URI("file:///tmp/sourceFile")
        val sourceCode = """
            import foo as bar
            package debug
        """.trimIndent().trim()
        val sourceFile = SourceFile(sourcePath, sourceCode)
        val context = controller.getOrCreateContext(sourceFile)

        val importStatementStartOffset = FileOffset(0, 0, 0)
        val importStatementEndOffset = FileOffset(17, 0, 17)
        val importStatementLocation = Location(sourceFile, importStatementStartOffset, importStatementEndOffset)

        val packageStatementStartOffset = FileOffset(18, 1, 0)
        val packageStatementEndOffset = FileOffset(31, 1, 13)
        val packageStatementLocation = Location(sourceFile, packageStatementStartOffset, packageStatementEndOffset)

        context.info {
            message("import statement")
            highlight("import statement highlight", importStatementLocation)
            info("import info annotation")
            help("import help annotation")
        }

        context.info {
            message("package statement")
            highlight("package statement highlight", packageStatementLocation)
            info("package info annotation")
            help("package help annotation")
        }

        assertTrue(context.hasMessages())
        assertFalse(context.hasErrors())
        assertFalse(context.hasWarnings())
        assertTrue(context.hasInfos())

        assertEquals(2, context.messages.size)

        val importMessage = context.messages[0]
        assertEquals(1, importMessage.highlights.size)
        assertEquals(2, importMessage.annotations.size)

        val importHighlight = importMessage.highlights[0]
        assertEquals("import statement highlight", importHighlight.message)
        assertEquals(sourceFile, importHighlight.location.source)
        assertEquals(0, importHighlight.location.start.charIndex)
        assertEquals(0, importHighlight.location.start.row)
        assertEquals(0, importHighlight.location.start.col)
        assertEquals(17, importHighlight.location.end.charIndex)
        assertEquals(0, importHighlight.location.end.row)
        assertEquals(17, importHighlight.location.end.col)
        assertEquals(2, importMessage.annotations.size)
        assertIs<DiagnosticAnnotationInformation>(importMessage.annotations[0])
        assertEquals("import info annotation", (importMessage.annotations[0] as DiagnosticAnnotationInformation).message)
        assertIs<DiagnosticAnnotationHelp>(importMessage.annotations[1])
        assertEquals("import help annotation", (importMessage.annotations[1] as DiagnosticAnnotationHelp).message)

        val packageMessage = context.messages[1]
        assertEquals(1, packageMessage.highlights.size)
        assertEquals(2, packageMessage.annotations.size)

        val packageHighlight = packageMessage.highlights[0]
        assertEquals("package statement highlight", packageHighlight.message)
        assertEquals(sourceFile, packageHighlight.location.source)
        assertEquals(18, packageHighlight.location.start.charIndex)
        assertEquals(1, packageHighlight.location.start.row)
        assertEquals(0, packageHighlight.location.start.col)
        assertEquals(31, packageHighlight.location.end.charIndex)
        assertEquals(1, packageHighlight.location.end.row)
        assertEquals(13, packageHighlight.location.end.col)
        assertEquals(2, packageMessage.annotations.size)
        assertIs<DiagnosticAnnotationInformation>(packageMessage.annotations[0])
        assertEquals("package info annotation", (packageMessage.annotations[0] as DiagnosticAnnotationInformation).message)
        assertIs<DiagnosticAnnotationHelp>(packageMessage.annotations[1])
        assertEquals("package help annotation", (packageMessage.annotations[1] as DiagnosticAnnotationHelp).message)
    }
}
