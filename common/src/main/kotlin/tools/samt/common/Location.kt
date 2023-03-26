package tools.samt.common

data class FileOffset(val charIndex: Int, val row: Int, val col: Int) {
    override fun toString(): String = "${row + 1}:${col + 1}"
}

data class Location(
    var start: FileOffset,
    var end: FileOffset = start,
) {
    init {
        if (start.row > end.row) {
            end = start
        }

        // FIXME: There is some bug with this enabled
        //        I removed it temporarily to be able to implement the AST dumper
        // require(start.row <= end.row) { "Location end row (${end.row}) cannot be before start row ${start.row}" }
    }

    override fun toString(): String = start.toString()
}
