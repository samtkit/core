package tools.samt.cli

import com.github.ajalt.mordant.rendering.TextColors.*
import tools.samt.lexer.*

internal object TokenPrinter {
    fun dump(tokens: Sequence<Token>): String = buildString {
        var currentRow = 0
        for (token in tokens) {
            if (token.location.start.row != currentRow) {
                appendLine()
                currentRow = token.location.start.row
            }
            val color = when (token) {
                is ValueToken -> green
                is StructureToken -> yellow
                is StaticToken -> blue
                is EndOfFileToken -> gray
            }
            append(color(token.getHumanReadableName()))
            append(' ')
            if (token is EndOfFileToken) {
                break
            }
        }
    }
}
