package tools.samt.common

data class FileOffset(val charIndex: Int, val row: Int, val col: Int) {
    override fun toString(): String = "$row:$col"
}

data class Location(
    val start: FileOffset,
    val end: FileOffset,
) {
    init {
        require(start.row <= end.row) { "Location end row (${end.row}) cannot be before start row ${start.row}" }
    }

    override fun toString(): String = start.toString()
}
