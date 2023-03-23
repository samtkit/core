package tools.samt.common

import kotlin.test.*

class DiagnosticsTest {
    private lateinit var diagnostics: DiagnosticConsole

    private val dummyStart = FileOffset(charIndex = 0, row = 0, col = 0)
    private val dummyEnd = FileOffset(charIndex = 1, row = 0, col = 1)
    private val dummyLocation = Location(dummyStart, dummyEnd)

    @BeforeTest
    fun setup() {
        diagnostics = DiagnosticConsole(DiagnosticContext("DiagnosticsTest.samt", "source"))
    }

    @AfterTest
    fun teardown() {
        diagnostics.messages.forEach { println(it) }
    }

    @Test
    fun `info, warnings and errors are added to messages`() {
        diagnostics.reportInfo("Info", dummyLocation)
        diagnostics.reportWarning("Warning", dummyLocation)
        diagnostics.reportError("Error", dummyLocation)
        assertTrue(diagnostics.hasErrors())
        assertTrue(diagnostics.hasWarnings())
        assertEquals(3, diagnostics.messages.size)
        assertContains(diagnostics.messages, DiagnosticMessage("Info", dummyLocation, DiagnosticSeverity.Info))
        assertContains(diagnostics.messages, DiagnosticMessage("Warning", dummyLocation, DiagnosticSeverity.Warning))
        assertContains(diagnostics.messages, DiagnosticMessage("Error", dummyLocation, DiagnosticSeverity.Error))
    }
}
