package lexer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class TokensTest {
    @Test
    fun `getHumanReadableTokenName is configured for every token`() {
        val relevantTypes = Token::class.sealedSubclasses
            .flatMap { it.sealedSubclasses }
            .filter { it.isData }
        for (sealedSubclass in relevantTypes) {
            assertDoesNotThrow { getHumanReadableTokenName(sealedSubclass) }
        }
    }
}
