package tools.samt.common

/**
 * row and column information are 0-indexed.
 * */
data class FileOffset(val charIndex: Int, val row: Int, val col: Int) {
    override fun toString(): String = "${row + 1}:${col + 1}"
}

/**
 * start and end file offsets of a location.
 * file offset in [start] is inclusive
 * file offset in [end] is exclusive.
 * */
data class Location(
    val context: DiagnosticContext,
    val start: FileOffset,
    val end: FileOffset = start,
) {
    init {
        require(start.row <= end.row) { "Location end row (${end.row}) cannot be before start row ${start.row}" }
        require(end.row <= context.source.sourceLines.lastIndex) {
            "Location end row (${end.row}) cannot be after last source file row ${context.source.sourceLines.lastIndex}"
        }
    }

    fun isMultiLine(): Boolean = start.row != end.row

    fun containsRow(row: Int): Boolean = start.row <= row && row <= end.row

    fun containsRowColumn(row: Int, col: Int): Boolean {
        if (!containsRow(row)) {
            return false
        }

        if (!isMultiLine()) {
            return col >= start.col && col < end.col
        }

        if (row == start.row) {
            return col >= start.col
        }

        if (row == end.row) {
            return col < end.col
        }

        return true
    }

    override fun toString(): String = start.toString()
}
