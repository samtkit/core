package tools.samt.cli

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import tools.samt.common.*

internal class DiagnosticFormatter(
    private val diagnosticController: DiagnosticController,
    private val startTimestamp: Long,
    private val currentTimestamp: Long,
    private val terminalWidth: Int,
) {
    companion object {
        private const val CONTEXT_ROW_COUNT = 3

        fun format(controller: DiagnosticController, startTimestamp: Long, currentTimestamp: Long, terminalWidth: Int = Terminal().info.width): String {
            val formatter = DiagnosticFormatter(controller, startTimestamp, currentTimestamp, terminalWidth)
            return formatter.format()
        }

        private fun rowContextLowerBound(row: Int): Int = (row - CONTEXT_ROW_COUNT).coerceAtLeast(0)
        private fun rowContextUpperBound(row: Int, sourceFile: SourceFile): Int = (row + CONTEXT_ROW_COUNT).coerceAtMost(sourceFile.sourceLines.lastIndex)
    }

    private fun format(): String = buildString {

        // keep track of the number of errors and warnings
        val countsBySeverity = mutableMapOf<DiagnosticSeverity, Int>()
        countsBySeverity[DiagnosticSeverity.Error] = 0
        countsBySeverity[DiagnosticSeverity.Warning] = 0
        countsBySeverity[DiagnosticSeverity.Info] = 0

        // print global messages
        val globalMessages = diagnosticController.globalMessages
        globalMessages.forEachIndexed { index, message ->
            appendLine("─".repeat(terminalWidth))
            append(formatGlobalMessage(message))
            if (index != globalMessages.lastIndex) {
                appendLine()
            }

            countsBySeverity[message.severity] = countsBySeverity.getValue(message.severity) + 1
        }

        // print context messages
        for (context in diagnosticController.contexts) {

            // sort messages by severity and then by location (row)
            context.messages.sortWith(compareBy(
                { it.severity },
                { it.highlights.firstOrNull()?.location?.start?.row }
            ))

            context.messages.forEachIndexed { index, message ->
                appendLine("─".repeat(terminalWidth))
                append(formatMessage(message, context))
                if (index != context.messages.lastIndex) {
                    appendLine()
                }

                countsBySeverity[message.severity] = countsBySeverity.getValue(message.severity) + 1
            }
        }
        appendLine()

        // print summary
        val errorCount = countsBySeverity.getValue(DiagnosticSeverity.Error)
        val warningCount = countsBySeverity.getValue(DiagnosticSeverity.Warning)
        val duration = currentTimestamp - startTimestamp
        appendLine("─".repeat(terminalWidth))
        if (errorCount == 0) {
            append((green + bold)("SUCCESSFUL") + " in ${duration}ms")
        } else {
            append((red + bold)("FAILED") + " in ${duration}ms")
        }

        if (errorCount > 0 || warningCount > 0) {
            appendLine(" ($errorCount error(s), $warningCount warning(s))")
        } else {
            appendLine()
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

    private fun formatTextForSeverity(text: String, severity: DiagnosticSeverity, withBackground: Boolean = false, withBold: Boolean = false): String {
        val colorFormatted = if (withBackground) {
            (severityForegroundColor(severity) on severityBackgroundColor(severity))(text)
        } else {
            (severityBackgroundColor(severity))(text)
        }

        return if (withBold) {
            bold(colorFormatted)
        } else {
            colorFormatted
        }
    }

    private fun formatSeverityIndicator(severity: DiagnosticSeverity): String = when (severity) {
        DiagnosticSeverity.Error -> formatTextForSeverity("ERROR:", severity, withBold = true)
        DiagnosticSeverity.Warning -> formatTextForSeverity("WARNING:", severity, withBold = true)
        DiagnosticSeverity.Info -> formatTextForSeverity("INFO:", severity, withBold = true)
    }

    private fun formatSeverityAndMessage(severity: DiagnosticSeverity, message: String): String = buildString {
        // <severity>: <message>
        append(formatSeverityIndicator(severity))
        append(" ")

        val indentLength = when (severity) {
            DiagnosticSeverity.Error -> 7
            DiagnosticSeverity.Warning -> 9
            DiagnosticSeverity.Info -> 6
        }

        val lines = message.lines()
        if (lines.size > 1) {
            lines.forEachIndexed { index, it ->
                if (index > 0) {
                    append(" ".repeat(indentLength))
                }
                appendLine(it)
            }
        } else {
            appendLine(message)
        }
    }

    private fun formatGlobalMessage(message: DiagnosticGlobalMessage): String = buildString {
        append(formatSeverityAndMessage(message.severity, message.message))
    }

    private fun formatMessage(message: DiagnosticMessage, context: DiagnosticContext): String = buildString {
        append(formatSeverityAndMessage(message.severity, message.message))

        val errorSourceFilePath = if (message.highlights.isNotEmpty()) {
            message.highlights.first().location.source.path
        } else {
            context.source.path
        }

        // -----> <file path>:<location>
        append(gray(" ---> "))
        append(errorSourceFilePath.toString())
        if (message.highlights.isNotEmpty()) {
            val firstHighlight = message.highlights.first()
            val firstHighlightLocation = firstHighlight.location
            append(":")
            append(firstHighlightLocation.toString())
        }
        appendLine()

        if (message.highlights.isNotEmpty()) {
            appendLine()
            append(formatMessageHighlights(message))
        }

        // print highlight annotations
        if (message.annotations.isNotEmpty()) {
            appendLine()
            append(formatHighlightAnnotations(message.annotations))
        }
    }

    private fun formatMessageHighlights(message: DiagnosticMessage): String = buildString {
        val highlights = message.highlights
        require(highlights.isNotEmpty())

        // group highlights by source file
        val mainSourceFileAbsolutePath = highlights.first().location.source.path
        val highlightsBySourceFile = highlights.groupBy { it.location.source.path }

        // print the highlights for the main source file
        val mainSourceFileHighlights = highlightsBySourceFile.getValue(mainSourceFileAbsolutePath)
        append(formatSingleFileHighlights(mainSourceFileHighlights, message.severity))

        // print remaining highlights for other source files
        val remainingHighlights = highlightsBySourceFile.filterKeys { it != mainSourceFileAbsolutePath }
        for ((_, fileHighlights) in remainingHighlights) {
            append(formatSingleFileHighlights(fileHighlights, message.severity))
        }
    }

    private fun formatSingleFileHighlights(highlights: List<DiagnosticHighlight>, severity: DiagnosticSeverity): String = buildString {
        require(highlights.isNotEmpty())

        // split highlights into non overlapping groups
        // attempt to make the groups as big as possible
        //
        // this assures us that each highlight in the group is either on a different line or
        // if they are on the same line, they are not overlapping
        val nonOverlappingGroups = mutableListOf<MutableList<DiagnosticHighlight>>()
        for (highlight in highlights) {
            val matchingGroup = nonOverlappingGroups.find { group ->
                group.all { groupHighlight -> !groupHighlight.overlaps(highlight) }
            }
            if (matchingGroup != null) {
                matchingGroup.add(highlight)
            } else {
                nonOverlappingGroups.add(mutableListOf(highlight))
            }
        }

        // print each highlight group
        for (group in nonOverlappingGroups) {
            append(formatSingleFileHighlightGroup(group, severity))
        }
    }

    private fun formatSingleFileHighlightGroup(highlights: List<DiagnosticHighlight>, severity: DiagnosticSeverity): String = buildString {
        require(highlights.isNotEmpty())

        val source = highlights.first().location.source

        // group highlights by their line number
        val highlightsByLineNumber = highlights.groupBy { it.location.start.row }.toSortedMap()
        val groupList = highlightsByLineNumber.keys.toList().zip(highlightsByLineNumber.values.toList())

        groupList.forEachIndexed { index, (rowIndex, highlights) ->

            // print preceding context lines
            if (index == 0) {
                append(formatPrecedingContextLines(rowIndex, source))
            }

            // print highlights in the group
            if (highlights.size == 1 && highlights.single().location.isMultiLine()) {
                append(formatMultipleLineSingleHighlight(highlights.single(), severity))
            } else {
                append(formatSingleLineHighlights(highlights, severity))
            }

            val lastHighlightLastRow = highlights.last().location.end.row
            if (index != groupList.lastIndex) {
                append(formatIntermediateContextLines(lastHighlightLastRow, groupList[index + 1].first, source))
            } else {
                append(formatTrailingContextLines(lastHighlightLastRow, source))
            }
        }
    }

    private fun formatPrecedingContextLines(rowIndex: Int, source: SourceFile): String = buildString {
        val contextStartRow = rowContextLowerBound(rowIndex)
        for (row in contextStartRow until rowIndex) {
            appendLine(formatNonHighlightedSourceRow(row, source))
        }
    }

    private fun formatTrailingContextLines(rowIndex: Int, source: SourceFile): String = buildString {
        val contextEndRow = rowContextUpperBound(rowIndex, source)
        for (row in (rowIndex + 1)..contextEndRow) {
            appendLine(formatNonHighlightedSourceRow(row, source))
        }
    }

    private fun formatIntermediateContextLines(previousRowIndex: Int, nextRowIndex: Int, source: SourceFile): String = buildString {

        // case 1: lines are directly adjacent, no context lines need to be drawn
        if (previousRowIndex + 1 == nextRowIndex) {
            return@buildString
        }

        val previousContextEndRow = rowContextUpperBound(previousRowIndex, source)
        val nextContextStartRow = rowContextLowerBound(nextRowIndex)

        // case 2: their context areas overlap, so we can draw all lines between the two
        // we add one to the previous context end row as to not draw a line separator between two context areas
        // that are directly adjacent but do not overlap
        if (previousContextEndRow + 1 >= nextContextStartRow) {
            for (row in (previousRowIndex + 1) until nextRowIndex) {
                appendLine(formatNonHighlightedSourceRow(row, source))
            }
            return@buildString
        }

        // case 3: their context areas do not overlap, and we must draw a '...' separator between the two areas
        append(formatTrailingContextLines(previousRowIndex, source))
        appendLine("    ... ")
        append(formatPrecedingContextLines(nextRowIndex, source))
    }

    private fun formatSingleLineHighlights(highlights: List<DiagnosticHighlight>, severity: DiagnosticSeverity): String = buildString {
        val location = highlights.first().location
        val rowIndex = location.start.row
        val sourceLine = location.source.sourceLines[rowIndex]
        require(highlights.all { !it.location.isMultiLine() })
        require(highlights.all { it.location.start.row == rowIndex })

        append(formatHighlightedLineNumberSection(rowIndex, severity))

        // print each character of the source line and highlight it if it is part of a highlight
        for (colIndex in sourceLine.indices) {
            val char = sourceLine[colIndex]
            val highlight = highlights.firstOrNull { it.location.containsRowColumn(rowIndex, colIndex) }
            if (highlight != null && !highlight.highlightBeginningOnly) {
                append(formatTextForSeverity(char.toString(), severity, withBackground = true))
            } else {
                append(char)
            }
        }
        appendLine()

        // draw the highlight carets for each highlight section
        append(formatNonHighlightedEmptySection())
        for (colIndex in sourceLine.indices) {

            // are we past any possible highlights?
            if (highlights.all { colIndex >= it.location.end.col }) {
                break
            }

            // is this column part of a highlight?
            val highlight = highlights.firstOrNull { it.location.containsRowColumn(rowIndex, colIndex) }
            if (highlight != null) {
                append(formatTextForSeverity("^", severity))
            } else {
                append(" ")
            }
        }
        appendLine()

        // iteratively draw the message arrows and messages
        // remove highlights that do not have a message or change suggestion
        val highlightsRemainingStack = highlights.toMutableList()
        highlightsRemainingStack.removeAll { it.message == null && it.changeSuggestion == null }
        while (highlightsRemainingStack.isNotEmpty()) {
            repeat(2) { iterationStep ->
                append(formatNonHighlightedEmptySection())
                for (colIndex in sourceLine.indices) {

                    // are we past any possible highlights?
                    if (highlightsRemainingStack.all { colIndex > it.location.start.col }) {
                        break
                    }

                    val highlight = highlightsRemainingStack.lastOrNull { colIndex == it.location.start.col }
                    if (highlight != null) {
                        if (highlight == highlightsRemainingStack.last() && iterationStep == 1) {
                            val message = highlight.message
                            if (message != null) {
                                append(formatTextForSeverity(message, severity))
                            } else {
                                requireNotNull(highlight.changeSuggestion)
                                append(formatTextForSeverity("Did you mean '${highlight.changeSuggestion}'?", severity))
                            }
                            break
                        } else {
                            append(formatTextForSeverity("|", severity))
                        }
                    } else {
                        append(" ")
                    }
                }
                appendLine()

                if (iterationStep == 1) {
                    highlightsRemainingStack.removeLast()
                }
            }
        }

        appendLine(formatNonHighlightedEmptySection())
    }

    private fun formatMultipleLineSingleHighlight(highlight: DiagnosticHighlight, severity: DiagnosticSeverity): String = buildString {
        val location = highlight.location
        require(location.isMultiLine())

        val startRowIndex = location.start.row
        val endRowIndex = location.end.row

        // print initial row
        val firstRow = location.source.sourceLines[startRowIndex]
        val firstRowNonHighlightedPortion = firstRow.substring(0, location.start.col)
        val firstRowHighlightedPortion = firstRow.substring(location.start.col)
        append(formatHighlightedMultilineLineNumberSection(startRowIndex, severity))
        append(gray(firstRowNonHighlightedPortion))
        appendLine(formatTextForSeverity(firstRowHighlightedPortion, severity, withBackground = true))

        // print intermediate rows
        for (rowIndex in (startRowIndex + 1) until endRowIndex) {
            append(formatHighlightedMultilineLineNumberSection(rowIndex, severity))
            val sourceRow = location.source.sourceLines[rowIndex]
            appendLine(formatTextForSeverity(sourceRow, severity, withBackground = true))
        }

        // print final row
        val lastRow = location.source.sourceLines[endRowIndex]
        val lastRowHighlightedPortion = lastRow.substring(0, location.end.col)
        val lastRowNonHighlightedPortion = lastRow.substring(location.end.col)
        append(formatHighlightedMultilineLineNumberSection(endRowIndex, severity))
        append(formatTextForSeverity(lastRowHighlightedPortion, severity, withBackground = true))
        appendLine(gray(lastRowNonHighlightedPortion))

        // print optional highlight message
        val message = highlight.message
        if (message != null) {
            appendLine(formatHighlightedMultilineEmptySection(severity))
            append(formatTextForSeverity("+--------- ", severity))
            appendLine(formatTextForSeverity(message, severity))
            appendLine(formatNonHighlightedEmptySection())
        }
    }

    private fun formatHighlightAnnotations(annotations: List<DiagnosticAnnotation>): String = buildString {
        require(annotations.isNotEmpty())

        for (annotation in annotations) {
            when (annotation) {
                is DiagnosticAnnotationHelp -> {
                    append("        = ")
                    append(green("help:"))
                    append(" ")
                    appendLine(formatHighlightAnnotationsMessage(annotation.message, indentStart = 16))
                }
                is DiagnosticAnnotationInformation -> {
                    append("        = ")
                    append(blue("info:"))
                    append(" ")
                    appendLine(formatHighlightAnnotationsMessage(annotation.message, indentStart = 16))
                }
            }
        }
    }

    private fun formatHighlightAnnotationsMessage(message: String, indentStart: Int): String = buildString {
        val maxLineLength = (terminalWidth / 3) * 2

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
        append("        │ ")
    }

    private fun formatHighlightedLineNumberSection(row: Int, severity: DiagnosticSeverity): String = buildString {
        append("   ")
        append(formatTextForSeverity((row + 1).toString().padStart(4), severity, withBold = true))
        append(" │ ")
    }

    private fun formatHighlightedMultilineLineNumberSection(row: Int, severity: DiagnosticSeverity): String = buildString {
        append(formatTextForSeverity("|> ", severity))
        append(formatTextForSeverity((row + 1).toString().padStart(4), severity, withBold = true))
        append(" │ ")
    }

    private fun formatHighlightedMultilineEmptySection(severity: DiagnosticSeverity): String = buildString {
        append(formatTextForSeverity("|      ", severity))
        append(" │ ")
    }

    private fun formatNonHighlightedSourceRow(row: Int, source: SourceFile): String = buildString {
        append("   ")
        append(gray((row + 1).toString().padStart(4)))
        append(" │ ")
        append(gray(source.sourceLines[row]))
    }

    private fun DiagnosticHighlight.overlaps(other: DiagnosticHighlight): Boolean {
        if (location.isMultiLine() || other.location.isMultiLine()) {
            return contentRowsOverlapWith(other)
        }

        if (location.start.row != other.location.start.row) {
            return false
        }

        return contentColumnsOverlapWith(other)
    }

    private fun DiagnosticHighlight.contentColumnsOverlapWith(other: DiagnosticHighlight): Boolean {
        require(!location.isMultiLine() && !other.location.isMultiLine())
        require(location.start.row == other.location.start.row)

        val ownStart = location.start.col
        val ownEnd = location.end.col
        val otherStart = other.location.start.col
        val otherEnd = other.location.end.col
        return (otherStart in ownStart..ownEnd) || (otherEnd in ownStart..ownEnd)
    }

    private fun DiagnosticHighlight.contentRowsOverlapWith(other: DiagnosticHighlight): Boolean {
        val ownStart = location.start.row
        val ownEnd = location.end.row
        val otherStart = other.location.start.row
        val otherEnd = other.location.end.row
        return (otherStart in ownStart..ownEnd) || (otherEnd in ownStart..ownEnd)
    }
}
