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
            val changeType = checkNotNull(change.type)
            when {
                path.isDirectory() -> when (changeType) {
                    FileChangeType.Created -> parseFilesInDirectory(uri).forEach(workspace::setFile)
                    FileChangeType.Changed -> error("Directory changes should not be watched")
                    FileChangeType.Deleted -> workspace.removeDirectory(uri)
                }
                path.fileName == SAMT_CONFIG_FILE_NAME -> when (changeType) {
                    FileChangeType.Created, FileChangeType.Changed -> {
                        workspace.removeFolder(uri)
                        workspace.addFolder(SamtFolder.fromConfig(uri))
                    }
                    FileChangeType.Deleted -> workspace.removeFolder(uri)
                }
                path.extension == "samt" -> when (changeType) {
                    FileChangeType.Created, FileChangeType.Changed -> workspace.setFile(readAndParseFile(uri))
                    FileChangeType.Deleted -> workspace.removeFile(uri)
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
            findSamtConfigs(path).forEach { workspace.addFolder(SamtFolder.fromConfig(it.toUri())) }
        }
        for (removed in event.removed) {
            val path = removed.uri.toPathUri().toPath()
            findSamtConfigs(path)
                .forEach { workspace.removeFolder(it.toUri()) }
        }
        client.updateWorkspace(workspace)
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    private fun parseFilesInDirectory(path: URI): List<FileInfo> {
        val folderPath  = checkNotNull(workspace.getFolderSnapshot(path)).sourcePath
        val sourceFiles = collectSamtFiles(path).readSamtSource(DiagnosticController(folderPath))
        return sourceFiles.map(::parseFile)
    }
}
