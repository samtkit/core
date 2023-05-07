package tools.samt.ls

import kotlin.test.Test
import kotlin.test.assertEquals

class UriTest {
    @Test
    fun `correctly transforms encoded URIs`() {
        assertEquals("file:///c:/test/directory", "file:///c%3A/test/directory".toPathUri().toString())
    }
}
