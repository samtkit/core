package tools.samt.common

enum class DiagnosticSeverity {
    Error, Warning, Info
}

sealed interface DiagnosticAddition

data class PreviouslyDefined(val location: Location) : DiagnosticAddition
data class SeeAlso(val location: Location) : DiagnosticAddition
data class Explanation(val explanation: String) : DiagnosticAddition
data class Suggestion(val newValue: String) : DiagnosticAddition

class DiagnosticMessage(
    val message: String,
    val location: Location?,
    val severity: DiagnosticSeverity,
    val additions: MutableList<DiagnosticAddition> = mutableListOf(),
) {
    override fun toString() = buildString {
        append(severity)
        if (location != null) {
            append("<", location, ">")
        }
        append(": ")
        append(message)

        if (additions.isNotEmpty()) {
            append(" (")
            additions.forEachIndexed { index, addition ->
                if (index > 0) {
                    append(", ")
                }
                append(addition)
            }
            append(")")
        }
    }

    fun previousDefinedAt(location: Location): DiagnosticMessage = also {
        additions.add(PreviouslyDefined(location))
    }

    fun explanation(example: String): DiagnosticMessage = also {
        additions.add(Explanation(example))
    }

    fun suggestion(newValue: String): DiagnosticMessage = also {
        additions.add(Suggestion(newValue))
    }

    fun seeAlso(location: Location): DiagnosticMessage = also {
        additions.add(SeeAlso(location))
    }
}

data class DiagnosticContext(
    val sourcePath: String,
    val source: String,
)

class DiagnosticConsole(val context: DiagnosticContext) {
    private val mutableMessages: MutableList<DiagnosticMessage> = ArrayList()
    val messages: List<DiagnosticMessage> get() = mutableMessages

    fun reportInfo(message: String, location: Location?) = report(message, location, DiagnosticSeverity.Info)
    fun reportWarning(message: String, location: Location?) = report(message, location, DiagnosticSeverity.Warning)
    fun reportError(message: String, location: Location?) = report(message, location, DiagnosticSeverity.Error)

    private fun report(message: String, location: Location?, severity: DiagnosticSeverity): DiagnosticMessage {
        val msg = DiagnosticMessage(message, location, severity)
        mutableMessages.add(msg)
        return msg
    }

    fun hasErrors(): Boolean = mutableMessages.any { it.severity == DiagnosticSeverity.Error }
    fun hasWarnings(): Boolean = mutableMessages.any { it.severity == DiagnosticSeverity.Warning }
    fun hasMessages(): Boolean = mutableMessages.isNotEmpty()

    override fun toString() = buildString {
        if (messages.isNotEmpty()) {
            appendLine("${context.sourcePath}:")
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
