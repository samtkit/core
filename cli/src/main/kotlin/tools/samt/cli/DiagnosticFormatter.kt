package tools.samt.cli

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import tools.samt.common.*

class DiagnosticFormatter(
    private val diagnosticController: DiagnosticController
) {
    // FIXME: this is a bit of a hack to get the terminal width
    //        it also means we're assuming this output will only ever be printed in a terminal
    //        i don't actually know what happens if it doesn't run in a tty setting
    private val terminalWidth: Int = Terminal().info.width

    private fun format(): String = buildString {

        // print context-less messages
        val globalMessages = diagnosticController.globalMessages
        globalMessages.forEachIndexed { index, message ->
            append(formatGlobalMessage(message))
            if (index != globalMessages.lastIndex) {
                appendLine()
            }
        }

        // print file-specific messages
        for (context in diagnosticController.contexts) {

            // sort messages by severity and then by location (row)
            context.messages.sortWith(compareBy(
                { it.severity },
                { it.highlights.firstOrNull()?.location?.start?.row }
            ))

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

    private fun formatFilePathRelativeToWorkingDirectory(filePath: String): String {
        val workingDirectory = diagnosticController.workingDirectoryAbsolutePath
        return filePath.removePrefix(workingDirectory).removePrefix("/")
    }

    private fun formatGlobalMessage(message: DiagnosticGlobalMessage): String = buildString {
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
            message.highlights.first().location.source.absolutePath
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

        // print highlight annotations
        if (message.annotations.isNotEmpty()) {
            appendLine()
            appendLine(formatHighlightAnnotations(message.annotations))
        }
    }

    private fun formatMessageHighlights(message: DiagnosticMessage): String = buildString {
        val highlights = message.highlights
        require(highlights.isNotEmpty())

        // group highlights by source file
        val mainSourceFileAbsolutePath = highlights.first().location.source.absolutePath
        val highlightsBySourceFile = highlights.groupBy { it.location.source.absolutePath }

        // print the highlights for the main source file
        val mainSourceFileHighlights = highlightsBySourceFile[mainSourceFileAbsolutePath]!!
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
        sourceHighlightLoop@ for (highlight in highlights) {

            // check if the highlight fits into any existing groups
            groupLoop@ for (group in nonOverlappingGroups) {

                // compare with each highlight in the group to see if they overlap
                for (otherHighlight in group) {
                    if (highlight.overlaps(otherHighlight)) {
                        continue@groupLoop
                    }
                }

                // no conflicts were found, add the highlight to this group
                group.add(highlight)
                continue@sourceHighlightLoop
            }

            // could not find a group to fit this highlight in, create a new group
            nonOverlappingGroups.add(mutableListOf(highlight))
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

        // case 3: their context areas do not overlap and we must draw a '...' separator between the two areas
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
            if (highlight != null) {
                append(formatTextForSeverity(char.toString(), severity, withBackground = true))
            } else {
                append(char)
            }
        }
        appendLine()

        // draw the highlight carets for each highlight section
        append(formatNonHighlightedEmptySection())
        for (colIndex in sourceLine.indices) {
            val highlight = highlights.firstOrNull { it.location.containsRowColumn(rowIndex, colIndex) }
            if (highlight != null) {
                append(formatTextForSeverity("^", severity))
            } else {
                append(" ")
            }
        }
        appendLine()

        // iteratively draw the message arrow lines and message texts
        val highlightsRemainingStack = highlights.toMutableList()

        // remove highlights that do not have a message or change suggestion
        highlightsRemainingStack.removeAll { it.message == null && it.changeSuggestion == null }

        while (highlightsRemainingStack.isNotEmpty()) {
            repeat(2) { iterationCount ->
                append(formatNonHighlightedEmptySection())
                for (colIndex in sourceLine.indices) {
                    val highlight = highlightsRemainingStack.lastOrNull { it.location.end.col == colIndex + 1 }
                    if (highlight != null) {
                        if (highlight == highlightsRemainingStack.last() && iterationCount == 1) {
                            if (highlight.message != null) {
                                append(formatTextForSeverity(highlight.message!!, severity))
                            } else {
                                require(highlight.changeSuggestion != null)
                                append(formatTextForSeverity("Did you mean '${highlight.changeSuggestion}'?", severity))
                            }
                        } else {
                            append(formatTextForSeverity("|", severity))
                        }
                    } else {
                        append(" ")
                    }
                }
                appendLine()

                if (iterationCount == 1) {
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
        if (highlight.message != null) {
            appendLine(formatHighlightedMultilineEmptySection(severity))
            append(formatTextForSeverity("+--------- ", severity))
            appendLine(formatTextForSeverity(highlight.message!!, severity))
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
        append("        ┃ ")
    }

    private fun formatHighlightedLineNumberSection(row: Int, severity: DiagnosticSeverity): String = buildString {
        append("   ")
        append(formatTextForSeverity((row + 1).toString().padStart(4), severity, withBold = true))
        append(" ┃ ")
    }

    private fun formatHighlightedMultilineLineNumberSection(row: Int, severity: DiagnosticSeverity): String = buildString {
        append(formatTextForSeverity("|> ", severity))
        append(formatTextForSeverity((row + 1).toString().padStart(4), severity, withBold = true))
        append(" ┃ ")
    }

    private fun formatHighlightedMultilineEmptySection(severity: DiagnosticSeverity): String = buildString {
        append(formatTextForSeverity("|      ", severity))
        append(" ┃ ")
    }

    private fun formatNonHighlightedSourceRow(row: Int, source: SourceFile): String = buildString {
        append("   ")
        append(gray((row + 1).toString().padStart(4)))
        append(" ┃ ")
        append(gray(source.sourceLines[row]))
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

    private fun DiagnosticHighlight.overlaps(other: DiagnosticHighlight): Boolean {
        if (location.isMultiLine() || other.location.isMultiLine()) {
            return contentRowsOverlapWith(other)
        }

        require(!(location.isMultiLine() || other.location.isMultiLine()))

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
