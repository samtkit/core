package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.WorkspaceService
import tools.samt.common.DiagnosticController
import tools.samt.common.collectSamtFiles
import tools.samt.common.readSamtSource
import java.net.URI
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.toPath

class SamtWorkspaceService(private val workspace: SamtWorkspace) : WorkspaceService, LanguageClientAware {
    private lateinit var client: LanguageClient

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        TODO("Not yet implemented")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        for (change in params.changes) {
            val uri = change.uri.toPathUri()
            val path = uri.toPath()
            if (path.isDirectory()) {
                when (change.type) {
                    FileChangeType.Created -> parseFilesInDirectory(uri).forEach(workspace::setFile)
                    FileChangeType.Changed -> error("Directory changes should not be watched")
                    FileChangeType.Deleted -> workspace.removeDirectory(uri)
                    null -> error("Unexpected null value for change.type")
                }
            } else if (path.extension == "samt") {
                when (change.type) {
                    FileChangeType.Created, FileChangeType.Changed -> workspace.setFile(readAndParseFile(uri))
                    FileChangeType.Deleted -> workspace.removeFile(uri)
                    null -> error("Unexpected null value for change.type")
                }
            }
        }
        client.updateWorkspace(workspace)
    }

    override fun didCreateFiles(params: CreateFilesParams) {
        for (file in params.files) {
            val path = file.uri.toPathUri()
            workspace.setFile(readAndParseFile(path))
        }
        client.updateWorkspace(workspace)
    }

    override fun didRenameFiles(params: RenameFilesParams)  {
        for (file in params.files) {
            val oldUri = file.oldUri.toPathUri()
            val newUri = file.newUri.toPathUri()
            val newPath = newUri.toPath()
            if (newPath.isDirectory()) {
                workspace.removeDirectory(oldUri)
                parseFilesInDirectory(newUri).forEach(workspace::setFile)
            } else {
                workspace.removeFile(oldUri)
                if (newPath.extension == "samt") {
                    workspace.setFile(readAndParseFile(newUri))
                }
            }
        }
        client.updateWorkspace(workspace)
    }

    override fun didDeleteFiles(params: DeleteFilesParams) {
        for (file in params.files) {
            val path = file.uri.toPathUri()
            if (path.toPath().isDirectory()) {
                workspace.removeDirectory(path)
            } else {
                workspace.removeFile(path)
            }
        }
        client.updateWorkspace(workspace)
    }

    override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
        val event = params.event
        for (added in event.added) {
            val path = added.uri.toPathUri().toPath()
            path.findSamtRoots()
                .ifEmpty { listOf(path) }
                .forEach { workspace.addFolder(SamtFolder.fromDirectory(it.toUri())) }
        }
        for (removed in event.removed) {
            val path = removed.uri.toPathUri().toPath()
            path.findSamtRoots()
                .ifEmpty { listOf(path) }
                .forEach { workspace.removeFolder(it.toUri()) }
        }
        client.updateWorkspace(workspace)
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    private fun parseFilesInDirectory(path: URI): List<FileInfo> {
        val folderPath  = checkNotNull(workspace.getFolderSnapshot(path)).path
        val sourceFiles = collectSamtFiles(path).readSamtSource(DiagnosticController(folderPath))
        return sourceFiles.map(::parseFile)
    }
}
