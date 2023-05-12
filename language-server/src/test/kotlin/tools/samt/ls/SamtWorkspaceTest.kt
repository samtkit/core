package tools.samt.ls

import tools.samt.common.DiagnosticController
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class SamtWorkspaceTest {
    private val dir1 = "file:///foo/bar".toPathUri()
    private val dir2 = "file:///foo/baz".toPathUri()
    private val workspace1 = SamtWorkspace(DiagnosticController(dir1))
    private val workspace2 = SamtWorkspace(DiagnosticController(dir2))
    private val workspaces = mapOf(
        dir1 to workspace1,
        dir2 to workspace2,
    )

    @Test
    fun `getByFile returns correct workspace`() {
        assertSame(workspace1, workspaces.getByFile("file:///foo/bar/greeter.samt".toPathUri()))
        assertSame(workspace2, workspaces.getByFile("file:///foo/baz/greeter.samt".toPathUri()))
    }

    @Test
    fun `getByFile returns null if file is outside of workspaces`() {
        assertNull(workspaces.getByFile("file:///foo/bars/greeter.samt".toPathUri()))
    }
}
