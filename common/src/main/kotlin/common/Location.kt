package common

data class FileOffset(val charIndex: Int, val row: Int, val col: Int) {
    override fun toString(): String = "$row:$col"
}

data class Location(
    val fullPath: String,
    val start: FileOffset,
    val end: FileOffset,
) {
    override fun toString(): String = "$fullPath ($start - $end)"
}
