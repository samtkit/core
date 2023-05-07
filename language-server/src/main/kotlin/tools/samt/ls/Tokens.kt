package tools.samt.ls

import org.eclipse.lsp4j.Position
import tools.samt.lexer.StructureToken
import tools.samt.lexer.Token

/**
 * Finds a non-structure token at the given position.
 * A token is considered to be at the given position if the position is within the token's location.
 *
 * For example, given the following source code:
 *
 * ```samt
 *package foo.bar.baz
 *```
 *
 * Any position within the token `package` will return that token, including bordering positions (before the p or after the e).
 *
 * @return the token at the given position, or null if there is no token at the given position
 */
fun List<Token>.findAt(position: Position): Token? {
    val relevantTokens = this.filter { it !is StructureToken }
    val tokenIndex = relevantTokens.binarySearch {
        when {
            it.location.end.row < position.line -> -1
            it.location.start.row > position.line -> 1
            it.location.end.col < position.character -> -1
            it.location.start.col > position.character -> 1
            else -> 0
        }
    }
    if (tokenIndex < 0) return null
    return relevantTokens[tokenIndex]
}
