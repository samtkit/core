package tools.samt.ls

import tools.samt.common.DiagnosticSeverity
import tools.samt.common.SourceFile
import kotlin.test.*

class SamtWorkspaceTest {
    @Test
    fun `getFile retrieves file`() {
        val folder = SamtFolder("file:///tmp/test".toPathUri())
        val workspace = SamtWorkspace()
        val uri = "file:///tmp/test/model.samt".toPathUri()
        workspace.addFolder(folder)
        val fileInfo = parseFile(SourceFile (uri, "package foo.bar"))
        folder.set(fileInfo)
        assertSame(fileInfo, workspace.getFile(uri))
    }

    @Test
    fun `file is in folder snapshot`() {
        val folder = SamtFolder("file:///tmp/test".toPathUri())
        val workspace = SamtWorkspace()
        val uri = "file:///tmp/test/model.samt".toPathUri()
        workspace.addFolder(folder)
        val fileInfo = parseFile(SourceFile (uri, "package foo.bar"))
        folder.set(fileInfo)
        val snapshot = workspace.getFolderSnapshot("file:///tmp/test".toPathUri())
        assertEquals(listOf(fileInfo), snapshot?.files)
    }

    @Test
    fun `folder which is already contained in other folder is ignored`() {
        val outer = SamtFolder("file:///tmp/test".toPathUri())
        val inner = SamtFolder("file:///tmp/test/inner".toPathUri())
        val workspace = SamtWorkspace()
        workspace.addFolder(outer)
        workspace.addFolder(inner)
        val snapshot = workspace.getFolderSnapshot("file:///tmp/test/inner".toPathUri())
        assertEquals(outer.path, snapshot?.path)
    }

    @Test
    fun `folder which contains other folder overwrites it`() {
        val inner = SamtFolder("file:///tmp/test/inner".toPathUri())
        val outer = SamtFolder("file:///tmp/test".toPathUri())
        val workspace = SamtWorkspace()
        workspace.addFolder(inner)
        workspace.addFolder(outer)
        val snapshot = workspace.getFolderSnapshot("file:///tmp/test/inner".toPathUri())
        assertEquals(outer.path, snapshot?.path)
    }

    @Test
    fun `getPendingMessages includes messages from new files`() {
        val workspace = SamtWorkspace()
        workspace.addFolder(SamtFolder("file:///tmp/test".toPathUri()))
        val uri = "file:///tmp/test/model.samt".toPathUri()
        val fileInfo = parseFile(SourceFile (uri, "package foo.bar record Foo {"))
        workspace.setFile(fileInfo)
        val messages = workspace.getPendingMessages()[uri]
        assertEquals(DiagnosticSeverity.Error, messages?.single()?.severity)
    }

    @Test
    fun `getPendingMessages includes emptyList for removed file`() {
        val workspace = SamtWorkspace()
        workspace.addFolder(SamtFolder("file:///tmp/test".toPathUri()))
        val uri = "file:///tmp/test/model.samt".toPathUri()
        val fileInfo = parseFile(SourceFile (uri, "package foo.bar record Foo {"))
        workspace.setFile(fileInfo)
        workspace.removeFile(uri)
        val messages = workspace.getPendingMessages()
        assertEquals(mapOf(uri to emptyList()), messages)
    }

    @Test
    fun `getPendingMessages includes empty list for every file in removed folder`() {
        val workspace = SamtWorkspace()
        workspace.addFolder(SamtFolder("file:///tmp/test".toPathUri()))
        val file1 = parseFile(SourceFile ("file:///tmp/test/foo.samt".toPathUri(), "package foo.bar record Foo {"))
        val file2 = parseFile(SourceFile ("file:///tmp/test/bar.samt".toPathUri(), "package foo.bar record Bar {"))
        workspace.setFile(file1)
        workspace.setFile(file2)
        workspace.removeFolder("file:///tmp/test".toPathUri())
        val messages = workspace.getPendingMessages()
        assertEquals(mapOf(file1.path to emptyList(), file2.path to emptyList()), messages)
    }

    @Test
    fun `getPendingMessages is empty after clearing changes`() {
        val workspace = SamtWorkspace()
        workspace.addFolder(SamtFolder("file:///tmp/test".toPathUri()))
        val uri = "file:///tmp/test/model.samt".toPathUri()
        val fileInfo = parseFile(SourceFile (uri, "package foo.bar record Foo {"))
        workspace.setFile(fileInfo)
        workspace.clearChanges()
        val messages = workspace.getPendingMessages()
        assertEquals(emptyMap(), messages)
    }

    @Test
    fun `removing file triggers semantic error in dependent file`() {
        val workspace = SamtWorkspace()
        workspace.addFolder(SamtFolder("file:///tmp/test".toPathUri()))
        val file1 = parseFile(SourceFile ("file:///tmp/test/foo.samt".toPathUri(), "package foo.bar record Foo {}"))
        val file2 = parseFile(SourceFile ("file:///tmp/test/bar.samt".toPathUri(), "package foo.bar record Bar { foo: Foo }"))
        workspace.setFile(file1)
        workspace.setFile(file2)
        workspace.buildSemanticModel()
        val messagesBefore = workspace.getPendingMessages()
        assertEquals(mapOf(file1.path to emptyList(), file2.path to emptyList()), messagesBefore)
        workspace.removeFile(file1.path)
        workspace.buildSemanticModel()
        val messagesAfter = workspace.getPendingMessages()
        assertEquals(emptyList(), messagesAfter[file1.path])
        assertNotNull(messagesAfter[file2.path]).single().let {
            assertEquals(DiagnosticSeverity.Error, it.severity)
            assertEquals("Type 'Foo' could not be resolved", it.message)
        }
    }

    @Test
    fun `getPendingMessages includes empty list for every file in removed directory`() {
        val workspace = SamtWorkspace()
        workspace.addFolder(SamtFolder("file:///tmp/test".toPathUri()))
        val file1 = parseFile(SourceFile ("file:///tmp/test/subfolder/foo.samt".toPathUri(), "package foo.bar record Foo {"))
        val file2 = parseFile(SourceFile ("file:///tmp/test/subfolder/bar.samt".toPathUri(), "package foo.bar record Bar {"))
        workspace.setFile(file1)
        workspace.setFile(file2)
        workspace.removeDirectory("file:///tmp/test/subfolder".toPathUri())
        val messages = workspace.getPendingMessages()
        assertEquals(mapOf(file1.path to emptyList(), file2.path to emptyList()), messages)
    }

    @Test
    fun `semanticModel can be found after buildSemanticModel`() {
        val workspace = SamtWorkspace()
        workspace.addFolder(SamtFolder("file:///tmp/test".toPathUri()))
        val file1 = parseFile(SourceFile ("file:///tmp/test/foo.samt".toPathUri(), "package foo.bar record Foo {}"))
        workspace.setFile(file1)
        assertNull(workspace.getSemanticModel(file1.path))
        workspace.buildSemanticModel()
        val semanticModel = workspace.getSemanticModel(file1.path)
        assertNotNull(semanticModel)
    }

    @Test
    fun `if file has not changed pending messages don't change`() {
        val workspace = SamtWorkspace()
        workspace.addFolder(SamtFolder("file:///tmp/test".toPathUri()))
        val sourceFile = SourceFile("file:///tmp/test/foo.samt".toPathUri(), "package foo.bar record Foo {")
        workspace.setFile(parseFile(sourceFile))
        assertEquals(1, workspace.getPendingMessages().size)
        workspace.clearChanges()
        workspace.setFile(parseFile(sourceFile))
        assertEquals(emptyMap(), workspace.getPendingMessages())
    }
}
