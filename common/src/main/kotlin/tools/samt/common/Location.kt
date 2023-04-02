package tools.samt.common

data class FileOffset(val charIndex: Int, val row: Int, val col: Int) {
    override fun toString(): String = "${row + 1}:${col + 1}"
}

/**
 * start and end file offsets of a location. [start] is inclusive, [end] is exclusive.
 * */
data class Location(
    val context: DiagnosticContext,
    val start: FileOffset,
    val end: FileOffset = start,
) {
    init {
        require(start.row <= end.row) { "Location end row (${end.row}) cannot be before start row ${start.row}" }
    }

    override fun toString(): String = start.toString()
}
