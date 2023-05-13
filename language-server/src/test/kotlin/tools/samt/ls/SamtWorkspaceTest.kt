package tools.samt.ls

import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import java.net.URI
import kotlin.test.*

class SamtWorkspaceTest {
    @Test
    fun `getByFile returns correct workspace`() {
        val workspaces = createWorkspaces()
        val workspace1 = assertNotNull(workspaces["file:///foo/bar".toPathUri()])
        val workspace2 = assertNotNull(workspaces["file:///foo/baz".toPathUri()])
        assertSame(workspace1, workspaces.getByFile("file:///foo/bar/greeter.samt".toPathUri()))
        assertSame(workspace2, workspaces.getByFile("file:///foo/baz/greeter.samt".toPathUri()))
    }

    @Test
    fun `getByFile returns null if file is outside of workspaces`() {
        val workspaces = createWorkspaces()
        assertNull(workspaces.getByFile("file:///foo/bars/greeter.samt".toPathUri()))
    }

    @Test
    fun `file can be retrieved with URI after set`() {
        val workspace = SamtWorkspace(DiagnosticController("file:///tmp/test".toPathUri()))
        val uri = "file:///tmp/test/model.samt".toPathUri()
        val fileInfo = parseFile(SourceFile(uri, "package foo.bar"))
        workspace.set(fileInfo)
        assertSame(fileInfo, workspace[uri])
        assertTrue(uri in workspace)
        assertContains(workspace as Iterable<FileInfo>, fileInfo)
    }

    @Test
    fun `file cannot be retrieved after removal`() {
        val workspace = SamtWorkspace(DiagnosticController("file:///tmp/test".toPathUri()))
        val uri = "file:///tmp/test/model.samt".toPathUri()
        val fileInfo = parseFile(SourceFile(uri, "package foo.bar"))
        workspace.set(fileInfo)
        workspace.remove(uri)
        assertNull(workspace[uri])
        assertFalse(uri in workspace)
        assertFalse(fileInfo in workspace)
    }

    @Test
    fun `getFilesIn returns files in directory`() {
        val workspace = SamtWorkspace(DiagnosticController("file:///tmp/test".toPathUri()))
        val file1 = "file:///tmp/test/dir/foo.samt".toPathUri()
        val file2 = "file:///tmp/test/dir/bar.samt".toPathUri()
        val file3 = "file:///tmp/test/baz.samt".toPathUri()
        val fileInfo1 = parseFile(SourceFile(file1, "package foo.bar"))
        val fileInfo2 = parseFile(SourceFile(file2, "package foo.bar"))
        val fileInfo3 = parseFile(SourceFile(file3, "package foo.bar"))
        workspace.set(fileInfo1)
        workspace.set(fileInfo2)
        workspace.set(fileInfo3)
        assertEquals(setOf(file1, file2), workspace.getFilesIn("file:///tmp/test/dir".toPathUri()).toSet())
    }

    @Test
    fun `getMessages includes parser and semantic messages`() {
        val workspace = SamtWorkspace(DiagnosticController("file:///tmp/test".toPathUri()))
        val uri1 = "file:///tmp/test/record.samt".toPathUri()
        val uri2 = "file:///tmp/test/service.samt".toPathUri()
        val fileInfo1 = parseFile(SourceFile(uri1, "package foo.bar recrd Foo { "))
        val fileInfo2 = parseFile(SourceFile(uri2, "package foo.bar service FooService { getService(): Foo }"))
        workspace.set(fileInfo1)
        workspace.set(fileInfo2)
        workspace.buildSemanticModel()
        val messages = workspace.getAllMessages()
        assertEquals(2, messages.size)
    }

    private fun createWorkspaces(): Map<URI, SamtWorkspace> {
        val dir1 = "file:///foo/bar".toPathUri()
        val dir2 = "file:///foo/baz".toPathUri()
        val workspace1 = SamtWorkspace(DiagnosticController(dir1))
        val workspace2 = SamtWorkspace(DiagnosticController(dir2))
        return mapOf(
            dir1 to workspace1,
            dir2 to workspace2,
        )
    }
}
