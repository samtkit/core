package tools.samt.cli

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import tools.samt.common.*

class DiagnosticFormatter(
    private val diagnosticController: DiagnosticController
) {
    // FIXME: this is a bit of a hack to get the terminal width
    //        it also means we're assuming this output will only ever be printed in a terminal
    //        i don't actually know what happens if it doesn't run in a tty setting
    private val terminalWidth = Terminal().info.width

    private fun format(): String = buildString {

        // print contextless messages
        val contextlessMessages = diagnosticController.contextlessMessages
        contextlessMessages.forEachIndexed { index, message ->
            append(formatContextlessMessage(message))
            if (index != contextlessMessages.lastIndex) {
                appendLine()
            }
        }

        // print file-specific messages
        for (context in diagnosticController.contexts) {
            context.messages.forEachIndexed { index, message ->
                append(formatMessage(message, context))
                if (index != context.messages.lastIndex) {
                    appendLine()
                }
            }
        }
    }

    private fun severityForegroundColor(severity: DiagnosticSeverity): TextColors = when (severity) {
        DiagnosticSeverity.Error -> white
        DiagnosticSeverity.Warning -> black
        DiagnosticSeverity.Info -> white
    }

    private fun severityBackgroundColor(severity: DiagnosticSeverity): TextColors = when (severity) {
        DiagnosticSeverity.Error -> red
        DiagnosticSeverity.Warning -> yellow
        DiagnosticSeverity.Info -> blue
    }

    private fun formatTextForSeverity(text: String, severity: DiagnosticSeverity, withBackground: Boolean): String {
        if (withBackground) {
            return (severityForegroundColor(severity) on severityBackgroundColor(severity))(text)
        } else {
            return (severityBackgroundColor(severity))(text)
        }
    }

    private fun formatSeverityIndicator(severity: DiagnosticSeverity): String = when (severity) {
        DiagnosticSeverity.Error -> formatTextForSeverity("ERROR:", severity, withBackground = false)
        DiagnosticSeverity.Warning -> formatTextForSeverity("WARNING:", severity, withBackground = false)
        DiagnosticSeverity.Info -> formatTextForSeverity("INFO:", severity, withBackground = false)
    }

    private fun formatFilePathRelativeToWorkingDirectory(filePath: String): String {
        val workingDirectory = diagnosticController.workingDirectoryAbsolutePath
        return filePath.removePrefix(workingDirectory).removePrefix("/")
    }

    private fun formatContextlessMessage(message: DiagnosticContextlessMessage): String = buildString {
        append(formatSeverityIndicator(message.severity))
        append(" ")
        append(message.message)
    }

    private fun formatMessage(message: DiagnosticMessage, context: DiagnosticContext): String = buildString {
        appendLine("⎯".repeat(terminalWidth))

        // <severity>: <message>
        append(formatSeverityIndicator(message.severity))
        append(" ")
        append(message.message)
        appendLine()

        val errorSourceFilePath = if (message.highlights.isNotEmpty()) {
            message.highlights.first().location.context.source.absolutePath
        } else {
            context.source.absolutePath
        }

        // -----> <file path>:<location>
        append(gray(" ---> "))
        append(formatFilePathRelativeToWorkingDirectory(errorSourceFilePath))
        if (message.highlights.isNotEmpty()) {
            val firstHighlight = message.highlights.first()
            val firstHighlightLocation = firstHighlight.location
            append(":")
            append(firstHighlightLocation.toString())
        }
        appendLine()
        appendLine()

        if (message.highlights.isNotEmpty()) {
            append(formatMessageHighlights(message))
        }
    }

    private fun formatMessageHighlights(message: DiagnosticMessage): String = buildString {
        val highlights = message.highlights
        require(highlights.isNotEmpty())

        // group highlights by source file
        val mainSourceFilePath = highlights.first().location.context.source.absolutePath
        val highlightsBySourceFile = highlights.groupBy { it.location.context.source.absolutePath }

        // print the highlights for the main source file
        val mainSourceFileHighlights = highlightsBySourceFile[mainSourceFilePath]!!
        append(formatSingleFileHighlights(mainSourceFileHighlights, message.severity))

        // print remaining highlights for other source files
        val remainingHighlights = highlightsBySourceFile.filterKeys { it != mainSourceFilePath }
        for ((_, highlights) in remainingHighlights) {
            append(formatSingleFileHighlights(highlights, message.severity))
        }
    }

    private fun formatSingleFileHighlights(highlights: List<DiagnosticHighlight>, severity: DiagnosticSeverity): String = buildString {
        require(highlights.isNotEmpty())

        // Case: Single highlight per file
        if (highlights.size == 1) {
            val highlight = highlights.first()
            append(formatSingleHighlight(highlight, severity))
            return@buildString
        }

        // collect all the highlights into groups of highlights whose context areas overlap with each other
        val overlappingHighlightGroups = mutableListOf<List<DiagnosticHighlight>>()
        var currentGroup = mutableListOf<DiagnosticHighlight>()
        overlappingHighlightGroups.add(currentGroup)
        for (highlight in highlights) {
            if (currentGroup.isEmpty()) {
                currentGroup.add(highlight)
                continue
            }

            val lastHighlight = currentGroup.last()
            if (!highlight.contextOverlapsWith(lastHighlight)) {
                currentGroup = mutableListOf()
                overlappingHighlightGroups.add(currentGroup)
            }

            currentGroup.add(highlight)
        }

        // print the groups of overlapping highlights
        overlappingHighlightGroups.forEachIndexed { index, group ->
            if (group.size == 1) {
                append(formatSingleHighlight(group.first(), severity))
            } else {
                append(formatMultipleOverlappingHighlights(group, severity))
            }

            if (index != overlappingHighlightGroups.lastIndex) {
                appendLine("  ... ")
            }
        }

        // For all cases:
        // - Draw sequence of '^' characters under the highlighted text
        // - Pad messages with one empty line above or below depending on which side of the source line the message is printed
        // - Draw fancy arrows pointing to the highlighted text
        // - Print the message associated with each highlight
        // - Print annotations associated with each highlight
    }

    private fun formatSingleHighlight(highlight: DiagnosticHighlight, severity: DiagnosticSeverity): String = buildString {
        val location = highlight.location

        val contentStartRow = location.start.row
        val contextStartRow = rowContextLowerBound(contentStartRow)
        val contentEndRow = location.end.row
        val contextEndRow = rowContextUpperBound(contentEndRow, location.context.source)

        // print above context lines
        for (row in contextStartRow until contentStartRow) {
            appendLine(formatNonHighlightedSourceRow(row, location.context.source))
        }

        if (location.isMultiLine()) {
            appendLine(formatSingleMultilineHighlight(highlight, severity))
        } else {
            val highlightSourceCodeItself = !highlight.highlightBeginningOnly
            val startCol = location.start.col
            val endCol = location.end.col

            // print line number section
            append(formatHighlightedLineNumberSection(contentStartRow, severity))

            val row = location.context.source.sourceLines[contentStartRow]
            val precedingSpan = row.substring(0, startCol)
            val highlightedSpan = row.substring(startCol, endCol)
            val trailingSpan = row.substring(endCol)

            append(precedingSpan)
            append(formatTextForSeverity(highlightedSpan, severity, withBackground = false))
            append(trailingSpan)
            appendLine()

            // print highlight carets and the optional highlight message
            append(formatNonHighlightedEmptySection())
            append(formatSingleLineHighlightCarets(startCol, highlightedSpan.length, severity, withMessageAttachment = highlight.message != null))
            if (highlight.message != null) {
                append(" ")
                append(formatTextForSeverity(highlight.message!!, severity, withBackground = false))
            }
            appendLine()
        }

        if (highlight.highlightBeginningOnly || !location.isMultiLine()) {
            // case: highlight only the beginning of the location

            // case: single line highlight
            //   - print highlight carets and highlight message

        } else {
            require(location.isMultiLine()) { "sanity check" }

            // case: multi-line highlight
            //   - highlight line numbers and add arrows in line number columns
            //   - print highlight message
        }

        // print below context lines
        for (row in (contentEndRow + 1) until (contextEndRow + 1)) {
            appendLine(formatNonHighlightedSourceRow(row, location.context.source))
        }

        // print highlight annotations
        if (highlight.annotations.isNotEmpty()) {
            appendLine(formatHighlightAnnotations(highlight.annotations))
        }
    }

    private fun formatSingleMultilineHighlight(highlight: DiagnosticHighlight, severity: DiagnosticSeverity): String = buildString {
        // TODO: implement
        append("TODO: formatSingleMultilineHighlight")
    }

    private fun formatMultipleOverlappingHighlights(highlights: List<DiagnosticHighlight>, severity: DiagnosticSeverity): String = buildString {
        appendLine(red("TODO: multiple overlapping highlights"))

        // Cases:
        // 4. Two single-line highlights on the same source line
        //  - print one message above and the other below
        //
        // 5. More than two single-line highlights on the same source line
        //  - print all messages below with long extending arrows leading from the message to the highlight area
        //
        // 6. Multi-line highlight overlaps with single single-line highlight
        //  - give up
        //
        // 7. Multiple overlapping multi-line highlights overlap multiple overlapping single-line highlights
        //  - give up
        //
        // 8. Overlapping highlights both have annotations
        //  - ???
        //
    }

    private fun formatHighlightAnnotations(annotations: List<DiagnosticAnnotation>): String = buildString {
        require(annotations.isNotEmpty())

        for (annotation in annotations) {
            when (annotation) {
                is DiagnosticAnnotationChangeSuggestion -> {
                    // ignore this since it is handled by the LSP only,
                    // not relevant for the diagnostic formatter
                    //
                    // ideally the code generating the diagnostic message has added a DiagnosticAnnotationHelp
                    // annotation that contains a human-readable version of the change suggestion
                    continue
                }
                is DiagnosticAnnotationHelp -> {
                    append("      = ")
                    append(yellow("help:"))
                    append(" ")
                    appendLine(formatHighlightAnnotationsMessage(annotation.message, indentStart = 14))
                }
                is DiagnosticAnnotationInformation -> {
                    append("      = ")
                    append(blue("info:"))
                    append(" ")
                    appendLine(formatHighlightAnnotationsMessage(annotation.message, indentStart = 14))
                }
            }
        }
    }

    private fun formatHighlightAnnotationsMessage(message: String, indentStart: Int): String = buildString {
        val maxLineLength = terminalWidth / 2

        // message fits into terminal width
        if (message.length <= maxLineLength) {
            append(message)
            return@buildString
        }

        // split message into multiple lines each no longer than the max line length
        val lines = message.splitToSequence(" ")
            .fold(mutableListOf<String>()) { lines, word ->
                if (lines.isEmpty()) {
                    lines.add(word)
                } else {
                    val lastLine = lines.last()
                    if (lastLine.length + word.length + 1 <= maxLineLength) {
                        lines[lines.lastIndex] = "$lastLine $word"
                    } else {
                        lines.add(word)
                    }
                }
                lines
            }

        lines.forEachIndexed { index, line ->
            append(line)

            if (index != lines.lastIndex) {
                appendLine()
                append(" ".repeat(indentStart))
            }
        }

    }

    private fun formatNonHighlightedEmptySection(): String = buildString {
        append("      ┃ ")
    }

    private fun formatHighlightedLineNumberSection(row: Int, severity: DiagnosticSeverity): String = buildString {
        append(" ")
        append(formatTextForSeverity((row + 1).toString().padStart(4), severity, withBackground = false))
        append(" ┃ ")
    }

    private fun formatNonHighlightedSourceRow(row: Int, source: SourceFile): String = buildString {
        append(formatNonHighlightedEmptySection())
        append(gray(source.sourceLines[row]))
    }

    private fun formatSingleLineHighlightCarets(colStart: Int, length: Int, severity: DiagnosticSeverity, withMessageAttachment: Boolean): String = buildString {
        require(length > 0)

        append(" ".repeat(colStart))

        if (length == 1) {
            append(formatTextForSeverity("^", severity, withBackground = false))
        } else {
            append(formatTextForSeverity("^".repeat(length), severity, withBackground = false))
        }
    }

    companion object {
        private const val CONTEXT_ROW_COUNT = 3

        fun format(controller: DiagnosticController): String {
            val formatter = DiagnosticFormatter(controller)
            return formatter.format()
        }

        private fun rowContextLowerBound(row: Int): Int = maxOf(0, row - CONTEXT_ROW_COUNT)
        private fun rowContextUpperBound(row: Int, sourceFile: SourceFile): Int = minOf(sourceFile.sourceLines.lastIndex, row + CONTEXT_ROW_COUNT)
    }

    private fun DiagnosticHighlight.contentOverlapsWith(other: DiagnosticHighlight): Boolean {
        val ownStart = location.start.row
        val ownEnd = location.end.row
        val otherStart = other.location.start.row
        val otherEnd = other.location.end.row
        return (otherStart >= ownStart && otherStart <= ownEnd) || (otherEnd >= ownStart && otherEnd <= ownEnd)
    }

    private fun DiagnosticHighlight.contextOverlapsWith(other: DiagnosticHighlight): Boolean {
        val ownStart = rowContextLowerBound(location.start.row)
        val ownEnd = rowContextUpperBound(location.end.row, location.context.source)
        val otherStart = rowContextLowerBound(other.location.start.row)
        val otherEnd = rowContextUpperBound(other.location.end.row, other.location.context.source)
        return (otherStart >= ownStart && otherStart <= ownEnd) || (otherEnd >= ownStart && otherEnd <= ownEnd)
    }
}
