package tools.samt.common

enum class DiagnosticSeverity {
    Error, Warning, Info
}

data class DiagnosticMessage(val message: String, val location: Location?, val severity: DiagnosticSeverity)

data class DiagnosticContext(
    val sourcePath: String,
    val source: String,
)

class DiagnosticConsole(private val diagnosticContext: DiagnosticContext) {
    private val mutableMessages: MutableList<DiagnosticMessage> = ArrayList()
    val messages: List<DiagnosticMessage> get() = mutableMessages

    fun reportInfo(message: String, location: Location?) = report(message, location, DiagnosticSeverity.Info)
    fun reportWarning(message: String, location: Location?) = report(message, location, DiagnosticSeverity.Warning)
    fun reportError(message: String, location: Location?) = report(message, location, DiagnosticSeverity.Error)

    private fun report(message: String, location: Location?, severity: DiagnosticSeverity) {
        mutableMessages.add(DiagnosticMessage(message, location, severity))
    }

    fun hasErrors(): Boolean = mutableMessages.any { it.severity == DiagnosticSeverity.Error }
    fun hasWarnings(): Boolean = mutableMessages.any { it.severity == DiagnosticSeverity.Warning }

    override fun toString() = buildString {
        if (messages.isNotEmpty()) {
            appendLine("${diagnosticContext.sourcePath}:")
            for (message in messages) {
                append(message.severity)
                append(": ")
                append(message.message)
                if (message.location != null) {
                    append(" at ")
                    append(message.location)
                }
                appendLine()
            }
        }
    }
}
