package tools.samt.ls

import org.junit.jupiter.api.assertThrows
import tools.samt.common.SourceFile
import kotlin.test.*

class SamtFolderTest {
    @Test
    fun `file can be retrieved with URI after set`() {
        val folder = SamtFolder("file:///tmp/test".toPathUri())
        val uri = "file:///tmp/test/model.samt".toPathUri()
        val fileInfo = parseFile(SourceFile(uri, "package foo.bar"))
        folder.set(fileInfo)
        assertSame(fileInfo, folder[uri])
        assertTrue(uri in folder)
        assertContains(folder as Iterable<FileInfo>, fileInfo)
    }

    @Test
    fun `file cannot be retrieved after removal`() {
        val folder = SamtFolder("file:///tmp/test".toPathUri())
        val uri = "file:///tmp/test/model.samt".toPathUri()
        val fileInfo = parseFile(SourceFile(uri, "package foo.bar"))
        folder.set(fileInfo)
        folder.remove(uri)
        assertNull(folder[uri])
        assertFalse(uri in folder)
        assertFalse(fileInfo in folder)
    }

    @Test
    fun `getFilesIn returns files in directory`() {
        val folder = SamtFolder("file:///tmp/test".toPathUri())
        val file1 = "file:///tmp/test/dir/foo.samt".toPathUri()
        val file2 = "file:///tmp/test/dir/bar.samt".toPathUri()
        val file3 = "file:///tmp/test/baz.samt".toPathUri()
        val fileInfo1 = parseFile(SourceFile(file1, "package foo.bar"))
        val fileInfo2 = parseFile(SourceFile(file2, "package foo.bar"))
        val fileInfo3 = parseFile(SourceFile(file3, "package foo.bar"))
        folder.set(fileInfo1)
        folder.set(fileInfo2)
        folder.set(fileInfo3)
        assertEquals(setOf(file1, file2), folder.getFilesIn("file:///tmp/test/dir".toPathUri()).toSet())
    }

    @Test
    fun `getMessages includes parser and semantic messages`() {
        val folder = SamtFolder("file:///tmp/test".toPathUri())
        val uri1 = "file:///tmp/test/record.samt".toPathUri()
        val uri2 = "file:///tmp/test/service.samt".toPathUri()
        val fileInfo1 = parseFile(SourceFile(uri1, "package foo.bar record Foo { "))
        val fileInfo2 = parseFile(SourceFile(uri2, "package foo.bar service FooService { getService(): Foo }"))
        folder.set(fileInfo1)
        folder.set(fileInfo2)
        folder.buildSemanticModel()
        val messages = folder.getAllMessages()
        assertEquals(2, messages.size)
    }

    @Test
    fun `setting file with path outside of folder throws IllegalArgumentException`() {
        val folder = SamtFolder("file:///tmp/test".toPathUri())
        val uri = "file:///tmp/other/model.samt".toPathUri()
        val fileInfo = parseFile(SourceFile(uri, "package foo.bar"))
        assertThrows<IllegalArgumentException> {
            folder.set(fileInfo)
        }
    }
}
