package tools.samt.common

import kotlin.test.*

class DiagnosticsTest {
    private lateinit var diagnostics: DiagnosticConsole

    private val dummyContext = DiagnosticContext("DiagnosticsTest.samt", "source")
    private val dummyStart = FileOffset(charIndex = 0, row = 0, col = 0)
    private val dummyEnd = FileOffset(charIndex = 1, row = 0, col = 1)
    private val dummyLocation = Location(dummyContext, dummyStart, dummyEnd)

    @BeforeTest
    fun setup() {
        diagnostics = DiagnosticConsole(dummyContext)
    }

    @AfterTest
    fun teardown() {
        diagnostics.messages.forEach { println(it) }
    }

    @Test
    fun `info, warnings and errors are added to messages`() {
        diagnostics.reportInfo("Info", dummyLocation)
        diagnostics.reportWarning("Warning", null)
        diagnostics.reportError("Error", dummyLocation).explanation("Dummy")
        assertTrue(diagnostics.hasErrors())
        assertTrue(diagnostics.hasWarnings())
        assertEquals(3, diagnostics.messages.size)
        val strings = diagnostics.messages.map { it.toString() }
        assertContains(strings, "Info<$dummyLocation>: Info")
        assertContains(strings, "Warning: Warning")
        assertContains(strings, "Error<$dummyLocation>: Error (Explanation(explanation=Dummy))")
    }
}
