package tools.samt.ls

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import tools.samt.common.DiagnosticMessage
import tools.samt.semantic.SemanticModel
import java.net.URI

class SamtWorkspace {
    /**
     * Maps the path of the config file to the folder
     */
    private val foldersByConfigPath = mutableMapOf<URI, SamtFolder>()
    private val changedFolders = mutableSetOf<SamtFolder>()
    private val removedFiles = mutableSetOf<URI>()

    fun getFolderSnapshot(path: URI): FolderSnapshot? = getFolder(path)?.let { FolderSnapshot(it.sourcePath, it.toList(), it.semanticModel) }

    fun addFolder(folder: SamtFolder) {
        // folder is contained in other folder, ignore
        if (foldersByConfigPath.values.any { folder.sourcePath.startsWith(it.sourcePath) }) {
            return
        }
        // remove folders contained in new folder
        foldersByConfigPath.values.removeIf { it.sourcePath.startsWith(folder.sourcePath) }
        foldersByConfigPath[folder.configPath] = folder
        changedFolders.add(folder)
        removedFiles.removeAll(folder.map { it.path }.toSet())
    }

    fun removeFolder(configPath: URI) {
        foldersByConfigPath.remove(configPath)?.let { folder ->
            removedFiles.addAll(folder.map { it.path })
        }
    }

    fun getFile(path: URI): FileInfo? = getFolder(path)?.get(path)

    fun setFile(file: FileInfo) {
        val currentFile = getFile(file.path)
        if (file.content == currentFile?.content) return
        val folder = getFolder(file.path) ?: return
        folder.set(file)
        changedFolders.add(folder)
        removedFiles.remove(file.path)
    }

    fun removeFile(path: URI) {
        val folder = getFolder(path) ?: return
        folder.remove(path)
        changedFolders.add(folder)
        removedFiles.add(path)
    }

    fun containsFile(path: URI) = foldersByConfigPath.keys.any { path.startsWith(it) }

    fun removeDirectory(path: URI) {
        val folder = getFolder(path)
        val files = folder?.getFilesIn(path)?.ifEmpty { null } ?: return
        changedFolders.add(folder)
        for (file in files) {
            folder.remove(file)
            removedFiles.add(file)
        }
    }

    fun getSemanticModel(path: URI): SemanticModel? = getFolder(path)?.semanticModel

    fun getPendingMessages(): Map<URI, List<DiagnosticMessage>> = changedFolders.flatMap { folder ->
        folder.getAllMessages().toList()
    }.toMap() + removedFiles.associateWith { emptyList() }

    fun buildSemanticModel() {
        changedFolders.forEach { it.buildSemanticModel() }
    }

    fun clearChanges() {
        changedFolders.clear()
        removedFiles.clear()
    }

    private fun getFolder(path: URI): SamtFolder? =
        foldersByConfigPath[path] ?: foldersByConfigPath.values.singleOrNull { path.startsWith(it.sourcePath) }
}

data class FolderSnapshot(val sourcePath: URI, val files: List<FileInfo>, val semanticModel: SemanticModel?)


fun LanguageClient.updateWorkspace(workspace: SamtWorkspace) {
    workspace.buildSemanticModel()
    workspace.getPendingMessages().forEach { (path, messages) ->
        publishDiagnostics(
            PublishDiagnosticsParams(
                path.toString(),
                messages.mapNotNull { it.toDiagnostic() }
            )
        )
    }
    workspace.clearChanges()
}
