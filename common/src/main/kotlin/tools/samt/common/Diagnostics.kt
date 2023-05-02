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
data class DiagnosticGlobalMessage(
    val severity: DiagnosticSeverity,
    val message: String,
)

data class DiagnosticHighlight(
    val message: String?,
    val location: Location,
    val changeSuggestion: String?,
    val highlightBeginningOnly: Boolean,
)

data class SourceFile(
    // absolute path to the source file
    val absolutePath: String,

    // the content of the source file as a string
    val content: String,
) {
    // each line of the source file
    val sourceLines: List<String> = content.lines()
}

class DiagnosticException(message: DiagnosticMessage) : RuntimeException(message.message)

class DiagnosticController(val workingDirectoryAbsolutePath: String) {

    /**
     * All diagnostic contexts, one for each source file.
     * */
    val contexts: MutableList<DiagnosticContext> = mutableListOf()

    /**
     * All global diagnostic messages.
     * */
    val globalMessages: MutableList<DiagnosticGlobalMessage> = mutableListOf()

    /**
     * Creates a new diagnostic context for the given source file or returns already existing one.
     * */
    fun createContext(source: SourceFile): DiagnosticContext {
        val foundContext = contexts.find { it.source == source}
        if (foundContext != null) return foundContext
        return DiagnosticContext(source).also { contexts.add(it) }
    }

    fun reportGlobalError(message: String) = reportGlobal(DiagnosticSeverity.Error, message)
    fun reportGlobalWarning(message: String) = reportGlobal(DiagnosticSeverity.Warning, message)
    fun reportGlobalInfo(message: String) = reportGlobal(DiagnosticSeverity.Info, message)
    private fun reportGlobal(severity: DiagnosticSeverity, message: String) {
        globalMessages.add(DiagnosticGlobalMessage(severity, message))
    }

    fun hasMessages(): Boolean = contexts.any { it.hasMessages() } or globalMessages.isNotEmpty()
    fun hasErrors(): Boolean = contexts.any { it.hasErrors() } or globalMessages.any { it.severity == DiagnosticSeverity.Error }
    fun hasWarnings(): Boolean = contexts.any { it.hasWarnings() } or globalMessages.any { it.severity == DiagnosticSeverity.Warning }
    fun hasInfos(): Boolean = contexts.any { it.hasInfos() } or globalMessages.any { it.severity == DiagnosticSeverity.Info }
}

class DiagnosticContext(
    val source: SourceFile,
) {
    /**
     * All diagnostic messages for this source file.
     * */
    val messages: MutableList<DiagnosticMessage> = mutableListOf()

    inline fun report(severity: DiagnosticSeverity, block: DiagnosticMessageBuilder.() -> Unit): DiagnosticMessage {
        val builder = DiagnosticMessageBuilder(severity)
        builder.block()
        val message = builder.build()
        messages.add(message)
        return message
    }

    inline fun fatal(block: DiagnosticMessageBuilder.() -> Unit): Nothing {
        throw DiagnosticException(report(DiagnosticSeverity.Error, block))
    }
    inline fun error(block: DiagnosticMessageBuilder.() -> Unit) = report(DiagnosticSeverity.Error, block)
    inline fun warn(block: DiagnosticMessageBuilder.() -> Unit) = report(DiagnosticSeverity.Warning, block)
    inline fun info(block: DiagnosticMessageBuilder.() -> Unit) = report(DiagnosticSeverity.Info, block)

    fun hasMessages(): Boolean = messages.isNotEmpty()
    fun hasErrors(): Boolean = messages.any { it.severity == DiagnosticSeverity.Error }
    fun hasWarnings(): Boolean = messages.any { it.severity == DiagnosticSeverity.Warning }
    fun hasInfos(): Boolean = messages.any { it.severity == DiagnosticSeverity.Info }
}

class DiagnosticMessageBuilder(
    private val severity: DiagnosticSeverity,
) {
    private var message: String? = null
    private val highlights: MutableList<DiagnosticHighlight> = mutableListOf()
    private val annotations: MutableList<DiagnosticAnnotation> = mutableListOf()

    fun message(message: String) {
        require(this.message == null)
        this.message = message
    }

    fun highlight(
        location: Location,
        suggestChange: String? = null,
        highlightBeginningOnly: Boolean = false,
    ) {
        val finalLocation = if (highlightBeginningOnly) {
            location.copy(end = location.start.copy(charIndex = location.start.charIndex + 1, col = location.start.col + 1))
        } else {
            location
        }
        highlights.add(DiagnosticHighlight(null, finalLocation, suggestChange, highlightBeginningOnly))
    }

    fun highlight(
        message: String,
        location: Location,
        suggestChange: String? = null,
        highlightBeginningOnly: Boolean = false,
    ) {
        val finalLocation = if (highlightBeginningOnly) {
            location.copy(end = location.start.copy(charIndex = location.start.charIndex + 1, col = location.start.col + 1))
        } else {
            location
        }
        highlights.add(DiagnosticHighlight(message, finalLocation, suggestChange, highlightBeginningOnly))
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
