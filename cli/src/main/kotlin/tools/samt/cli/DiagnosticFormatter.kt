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

    private fun formatTextForSeverity(text: String, severity: DiagnosticSeverity, withBackground: Boolean = false): String {
        if (withBackground) {
            return (severityForegroundColor(severity) on severityBackgroundColor(severity))(text)
        } else {
            return (severityBackgroundColor(severity))(text)
        }
    }

    private fun formatSeverityIndicator(severity: DiagnosticSeverity): String = when (severity) {
        DiagnosticSeverity.Error -> formatTextForSeverity("ERROR:", severity)
        DiagnosticSeverity.Warning -> formatTextForSeverity("WARNING:", severity)
        DiagnosticSeverity.Info -> formatTextForSeverity("INFO:", severity)
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
        val mainSourceFileAbsolutePath = highlights.first().location.context.source.absolutePath
        val highlightsBySourceFile = highlights.groupBy { it.location.context.source.absolutePath }

        // print the highlights for the main source file
        val mainSourceFileHighlights = highlightsBySourceFile[mainSourceFileAbsolutePath]!!
        append(formatSingleFileHighlights(mainSourceFileHighlights, message.severity))

        // print remaining highlights for other source files
        val remainingHighlights = highlightsBySourceFile.filterKeys { it != mainSourceFileAbsolutePath }
        for ((_, highlights) in remainingHighlights) {
            append(formatSingleFileHighlights(highlights, message.severity))
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

        val source = highlights.first().location.context.source

        // group highlights by their line number
        val highlightsByLineNumber = highlights.groupBy { it.location.start.row }.toSortedMap()
        val groupList = highlightsByLineNumber.keys.toList().zip(highlightsByLineNumber.values.toList())

        groupList.forEachIndexed { index, (rowIndex, highlights) ->

            // print preceding context lines
            if (index == 0) {
                val contextStartRow = rowContextLowerBound(rowIndex)
                for (row in contextStartRow until rowIndex) {
                    appendLine(formatNonHighlightedSourceRow(row, source))
                }
            }

            // print highlights in the group
            if (highlights.size == 1 && highlights.single().location.isMultiLine()) {
                append(formatMultipleLineSingleHighlight(highlights.single(), severity))
            } else {
                append(formatSingleLineHighlights(highlights, severity))
            }

            val lastHighlightLastRow = highlights.last().location.end.row
            val lastContextRow = rowContextUpperBound(lastHighlightLastRow, source)
            if (index == groupList.lastIndex) {
                // print trailing context lines
                for (row in (lastHighlightLastRow + 1)..lastContextRow) {
                    appendLine(formatNonHighlightedSourceRow(row, source))
                }
            } else {
                // print intermediate context lines
                val nextHighlightFirstRow = groupList[index + 1].first
                val actualLastContextRow = minOf(lastContextRow, nextHighlightFirstRow - 1)
                for (row in (lastHighlightLastRow + 1)..actualLastContextRow) {
                    appendLine(formatNonHighlightedSourceRow(row, source))
                }
            }
        }
    }

    private fun formatSingleLineHighlights(highlights: List<DiagnosticHighlight>, severity: DiagnosticSeverity): String = buildString {
        val location = highlights.first().location
        val rowIndex = location.start.row
        val sourceLine = location.context.source.sourceLines[rowIndex]
        require(highlights.all { !it.location.isMultiLine() })
        require(highlights.all { it.location.start.row == rowIndex })

        appendLine(formatNonHighlightedEmptySection())
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
            val char = sourceLine[colIndex]
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
        while (highlightsRemainingStack.isNotEmpty()) {
            repeat(2) {
                append(formatNonHighlightedEmptySection())
                for (colIndex in sourceLine.indices) {
                    val highlight = highlightsRemainingStack.lastOrNull { it.location.end.col == colIndex + 1 }
                    if (highlight != null) {
                        if (highlight == highlightsRemainingStack.last() && it == 1) {
                            append(formatTextForSeverity(highlight.message!!, severity))
                        } else {
                            append(formatTextForSeverity("|", severity))
                        }
                    } else {
                        append(" ")
                    }
                }
                appendLine()

                if (it == 1) {
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
        val firstRow = location.context.source.sourceLines[startRowIndex]
        val firstRowNonHighlightedPortion = firstRow.substring(0, location.start.col)
        val firstRowHighlightedPortion = firstRow.substring(location.start.col)
        append(formatHighlightedMultilineLineNumberSection(startRowIndex, severity))
        append(gray(firstRowNonHighlightedPortion))
        appendLine(formatTextForSeverity(firstRowHighlightedPortion, severity, withBackground = true))

        // print intermediate rows
        for (rowIndex in (startRowIndex + 1)..(endRowIndex - 1)) {
            append(formatHighlightedMultilineLineNumberSection(rowIndex, severity))
            val sourceRow = location.context.source.sourceLines[rowIndex]
            appendLine(formatTextForSeverity(sourceRow, severity, withBackground = true))
        }

        // print final row
        val lastRow = location.context.source.sourceLines[endRowIndex]
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
        append("        ┃ ")
    }

    private fun formatHighlightedLineNumberSection(row: Int, severity: DiagnosticSeverity): String = buildString {
        append("   ")
        append(formatTextForSeverity((row + 1).toString().padStart(4), severity))
        append(" ┃ ")
    }

    private fun formatHighlightedMultilineLineNumberSection(row: Int, severity: DiagnosticSeverity): String = buildString {
        append(formatTextForSeverity("|> ", severity))
        append(formatTextForSeverity((row + 1).toString().padStart(4), severity))
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
        private const val CONTEXT_ROW_COUNT = 5

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
        return (otherStart >= ownStart && otherStart <= ownEnd) || (otherEnd >= ownStart && otherEnd <= ownEnd)
    }

    private fun DiagnosticHighlight.contentRowsOverlapWith(other: DiagnosticHighlight): Boolean {
        val ownStart = location.start.row
        val ownEnd = location.end.row
        val otherStart = other.location.start.row
        val otherEnd = other.location.end.row
        return (otherStart >= ownStart && otherStart <= ownEnd) || (otherEnd >= ownStart && otherEnd <= ownEnd)
    }
}
