package tools.samt.ls

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UriTest {
    @Test
    fun `correctly transforms encoded URIs`() {
        assertEquals("file:///c:/test/directory", "file:///c%3A/test/directory".toPathUri().toString())
    }

    @Test
    fun `startsWith returns true if URI is contained in folder`() {
        assertTrue("file:///c:/test/directory".toPathUri().startsWith("file:///c:/test".toPathUri()))
    }

    @Test
    fun `startsWith returns false if URI is just a prefix`() {
        assertFalse("file:///c:/testtest".toPathUri().startsWith("file:///c:/test".toPathUri()))
    }

    @Test
    fun `startsWith returns true if URIs are identical`() {
        assertTrue("file:///c:/test".toPathUri().startsWith("file:///c:/test".toPathUri()))
    }
}
