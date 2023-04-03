package tools.samt.common

enum class DiagnosticSeverity {
    Error, Warning, Info
}

data class DiagnosticMessage(
    val severity: DiagnosticSeverity,
    val message: String,
    val highlights: List<DiagnosticHighlight>,
    val annotations: List<DiagnosticAnnotation>,
)

sealed interface DiagnosticAnnotation
class DiagnosticAnnotationHelp(val message: String): DiagnosticAnnotation
class DiagnosticAnnotationInformation(val message: String): DiagnosticAnnotation

// messages that do not belong to a specific source file
data class DiagnosticContextlessMessage(
    val severity: DiagnosticSeverity,
    val message: String,
)

data class DiagnosticHighlight(
    val message: String?,
    val location: Location,
    val changeSuggestion: String?,
)

data class SourceFile(
    val absolutePath: String,       // absolute path to the source file
    val content: String,            // the content of the source file as a string
    val sourceLines: List<String>,  // each line of the source file
)

class DiagnosticException(message: String) : RuntimeException(message)

class DiagnosticController(val workingDirectoryAbsolutePath: String) {

    /**
     * All diagnostic contexts, one for each source file.
     * */
    val contexts: MutableList<DiagnosticContext> = mutableListOf()

    /**
     * All contextless diagnostic messages.
     * */
    val contextlessMessages: MutableList<DiagnosticContextlessMessage> = mutableListOf()

    inline fun withSourceContext(source: SourceFile, block: (DiagnosticContext) -> Unit) {
        val context = DiagnosticContext(source)
        contexts.add(context)
        block(context)
    }

    fun reportContextlessError(message: String) = reportContextless(DiagnosticSeverity.Error, message)
    fun reportContextlessWarning(message: String) = reportContextless(DiagnosticSeverity.Warning, message)
    fun reportContextlessInfo(message: String) = reportContextless(DiagnosticSeverity.Info, message)
    fun reportContextless(severity: DiagnosticSeverity, message: String) {
        contextlessMessages.add(DiagnosticContextlessMessage(severity, message))
    }

    fun hasMessages(): Boolean = contexts.any { it.hasMessages() } or contextlessMessages.isNotEmpty()
    fun hasErrors(): Boolean = contexts.any { it.hasErrors() } or contextlessMessages.any { it.severity == DiagnosticSeverity.Error }
    fun hasWarnings(): Boolean = contexts.any { it.hasWarnings() } or contextlessMessages.any { it.severity == DiagnosticSeverity.Warning }
    fun hasInfos(): Boolean = contexts.any { it.hasInfos() } or contextlessMessages.any { it.severity == DiagnosticSeverity.Info }
}

class DiagnosticContext(
    val source: SourceFile,
) {
    /**
     * All diagnostic messages for this source file.
     * */
    val messages: MutableList<DiagnosticMessage> = mutableListOf()

    fun report(severity: DiagnosticSeverity, block: DiagnosticMessageBuilder.() -> Unit): DiagnosticMessage {
        val builder = DiagnosticMessageBuilder(severity)
        builder.block()
        val message = builder.build()
        messages.add(message)
        return message
    }

    fun fatal(block: DiagnosticMessageBuilder.() -> Unit): Nothing {
        throw DiagnosticException(report(DiagnosticSeverity.Error, block).message)
    }
    fun error(block: DiagnosticMessageBuilder.() -> Unit) = report(DiagnosticSeverity.Error, block)
    fun warn(block: DiagnosticMessageBuilder.() -> Unit) = report(DiagnosticSeverity.Warning, block)
    fun info(block: DiagnosticMessageBuilder.() -> Unit) = report(DiagnosticSeverity.Info, block)

    fun hasMessages(): Boolean = messages.isNotEmpty()
    fun hasErrors(): Boolean = messages.any { it.severity == DiagnosticSeverity.Error }
    fun hasWarnings(): Boolean = messages.any { it.severity == DiagnosticSeverity.Warning }
    fun hasInfos(): Boolean = messages.any { it.severity == DiagnosticSeverity.Info }
}

class DiagnosticMessageBuilder(
    val severity: DiagnosticSeverity,
) {
    var message: String? = null
    val highlights: MutableList<DiagnosticHighlight> = mutableListOf()
    val annotations: MutableList<DiagnosticAnnotation> = mutableListOf()

    fun message(message: String) {
        require(this.message == null)
        this.message = message
    }

    fun highlight(
        location: Location,
        suggestChange: String? = null,
        highlightBeginningOnly: Boolean = false,
    ) {
        val finalLocation = if (highlightBeginningOnly) location.copy(end = location.start) else location
        highlights.add(DiagnosticHighlight(null, finalLocation, suggestChange))
    }

    fun highlight(
        message: String,
        location: Location,
        suggestChange: String? = null,
        highlightBeginningOnly: Boolean = false,
    ) {
        val finalLocation = if (highlightBeginningOnly) location.copy(end = location.start) else location
        highlights.add(DiagnosticHighlight(message, finalLocation, suggestChange))
    }

    fun info(message: String) {
        annotations.add(DiagnosticAnnotationInformation(message))
    }

    fun help(message: String) {
        annotations.add(DiagnosticAnnotationHelp(message))
    }

    fun build(): DiagnosticMessage {
        requireNotNull(message)
        highlights.sortWith(compareBy({ it.location.start.row }, { it.location.start.col }))
        return DiagnosticMessage(severity, message!!, highlights, annotations)
    }
}
