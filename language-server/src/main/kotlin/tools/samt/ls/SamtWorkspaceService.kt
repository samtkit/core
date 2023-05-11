package tools.samt.ls

import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService
import tools.samt.common.SourceFile
import java.net.URI
import kotlin.io.path.readText
import kotlin.io.path.toPath

class SamtWorkspaceService(private val workspaces: Map<URI, SamtWorkspace>) : WorkspaceService, LanguageClientAware {
    private lateinit var client: LanguageClient

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        TODO("Not yet implemented")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        val affectedWorkspaces = mutableSetOf<SamtWorkspace>()
        for (change in params.changes) {
            val uri = change.uri.toPathUri()
            val workspace = workspaces.getByFile(uri) ?: continue
            affectedWorkspaces.add(workspace)
            when (change.type) {
                FileChangeType.Created, FileChangeType.Changed -> {
                    val sourceFile = SourceFile(uri, uri.toPath().readText())
                    workspace.set(parseFile(sourceFile))
                }
                FileChangeType.Deleted -> {
                    workspace.remove(uri)
                }
                null -> error("Unexpected null value for change.type")
            }
        }
        for (workspace in affectedWorkspaces) {
            workspace.buildSemanticModel()
            client.publishWorkspaceDiagnostics(workspace)
        }
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }
}