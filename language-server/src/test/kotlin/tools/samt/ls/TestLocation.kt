package tools.samt.ls

import tools.samt.common.FileOffset
import tools.samt.common.Location
import tools.samt.common.SourceFile

data class TestLocation(val range: Pair<String, String>) {
    private val startRow = range.first.substringBefore(":").toInt()
    private val startCol = range.first.substringAfter(":").toInt()
    private val endRow = range.second.substringBefore(":").toInt()
    private val endCol = range.second.substringAfter(":").toInt()
    private fun countUntil(source: String, row: Int, col: Int): Int {
        var currentRow = 0
        var currentCol = 0
        var currentIndex = 0
        for (c in source) {
            if (currentRow == row && currentCol == col) {
                return currentIndex
            }
            currentIndex++
            if (c == '\n') {
                currentRow++
                currentCol = 0
            } else {
                currentCol++
            }
        }
        return -1
    }

    fun getLocation(file: SourceFile) = Location(
        source = file,
        start = FileOffset(
            charIndex = countUntil(file.content, startRow, startCol),
            row = startRow,
            col = startCol,
        ),
        end = FileOffset(
            charIndex = countUntil(file.content, endRow, endCol),
            row = endRow,
            col = endCol,
        ),
    )
}
