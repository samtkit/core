package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService
import tools.samt.common.DiagnosticController
import tools.samt.common.collectSamtFiles
import tools.samt.common.readSamtSource
import java.net.URI
import kotlin.io.path.isDirectory
import kotlin.io.path.toPath

class SamtWorkspaceService(private val workspaces: MutableMap<URI, SamtWorkspace>) : WorkspaceService, LanguageClientAware {
    private lateinit var client: LanguageClient

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        TODO("Not yet implemented")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) = updateWorkspaces {
        for (change in params.changes) {
            val uri = change.uri.toPathUri()
            val workspace = workspaces.getByFile(uri) ?: continue
            if (uri.toPath().isDirectory()) {
                when (change.type) {
                    FileChangeType.Created -> {
                        val sourceFiles = collectSamtFiles(uri).readSamtSource(DiagnosticController(workspace.workingDirectory))
                        sourceFiles.forEach {
                            workspace.set(parseFile(it))
                        }
                    }
                    FileChangeType.Changed -> {}
                    FileChangeType.Deleted -> {
                        workspace.getFilesIn(uri).forEach { removeFile(workspace, it) }
                    }
                    null -> error("Unexpected null value for change.type")
                }
                yield(workspace)
            } else if (change.uri.endsWith(".samt")) {
                when (change.type) {
                    FileChangeType.Created, FileChangeType.Changed -> workspace.set(readAndParseFile(uri))
                    FileChangeType.Deleted -> removeFile(workspace, uri)
                    null -> error("Unexpected null value for change.type")
                }
                yield(workspace)
            }
        }
    }

    override fun didCreateFiles(params: CreateFilesParams) = updateWorkspaces {
        for (file in params.files) {
            val uri = file.uri.toPathUri()
            val workspace = workspaces.getByFile(uri) ?: continue
            workspace.set(readAndParseFile(uri))
            yield(workspace)
        }
    }

    override fun didRenameFiles(params: RenameFilesParams) = updateWorkspaces {
        for (file in params.files) {
            val oldUri = file.oldUri.toPathUri()
            val newUri = file.newUri.toPathUri()
            val oldWorkspace = workspaces.getByFile(oldUri)
            val newWorkspace = workspaces.getByFile(newUri)
            if (file.oldUri.endsWith(".samt")) {
                oldWorkspace?.let {
                    removeFile(it, oldUri)
                    yield(it)
                }
                newWorkspace?.let {
                    it.set(readAndParseFile(newUri))
                    yield(it)
                }
            } else {
                oldWorkspace?.let { workspace ->
                    workspace.getFilesIn(oldUri).forEach { removeFile(workspace, it) }
                    yield(workspace)
                }
                newWorkspace?.let { workspace ->
                    val sourceFiles = collectSamtFiles(newUri).readSamtSource(DiagnosticController(workspace.workingDirectory))
                    sourceFiles.forEach {
                        workspace.set(parseFile(it))
                    }
                    yield(workspace)
                }
            }
        }
    }

    override fun didDeleteFiles(params: DeleteFilesParams) = updateWorkspaces {
        for (file in params.files) {
            val uri = file.uri.toPathUri()
            val workspace = workspaces.getByFile(uri) ?: continue
            if (file.uri.endsWith(".samt")) {
                removeFile(workspace, uri)
            } else {
                workspace.getFilesIn(uri).forEach { removeFile(workspace, it) }
            }
            yield(workspace)
        }
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        val event = params.event
        for (added in event.added) {
            val folder = added.uri.toPathUri()
            val workspace = SamtWorkspace.createFromDirectory(folder)
            workspaces[folder] = workspace
            workspace.buildSemanticModel()
            client.publishWorkspaceDiagnostics(workspace)
        }
        for (removed in event.removed) {
            val workspace = workspaces.remove(removed.uri.toPathUri())
            workspace?.forEach {
                client.publishDiagnostics(PublishDiagnosticsParams(it.path.toString(), emptyList()))
            }
        }
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    private fun updateWorkspaces(block: suspend SequenceScope<SamtWorkspace>.() -> Unit): Unit =
        sequence(block).toSet().forEach {
            it.buildSemanticModel()
            client.publishWorkspaceDiagnostics(it)
        }

    private fun removeFile(workspace: SamtWorkspace, file: URI) {
        workspace.remove(file)
        client.publishDiagnostics(PublishDiagnosticsParams(file.toString(), emptyList()))
    }
}
