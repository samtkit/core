package tools.samt.ls

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import tools.samt.common.DiagnosticMessage
import tools.samt.semantic.Package
import java.net.URI

class SamtWorkspace {
    private val folders = mutableMapOf<URI, SamtFolder>()
    private val changedFolders = mutableSetOf<SamtFolder>()
    private val removedFiles = mutableSetOf<URI>()

    fun getFolderSnapshot(path: URI): FolderSnapshot? = getFolder(path)?.let { FolderSnapshot(it.path, it.toList(), it.globalPackage) }

    fun addFolder(folder: SamtFolder) {
        val newPath = folder.path
        // folder is contained in other folder, ignore
        if (folders.keys.any { newPath.startsWith(it) }) {
            return
        }
        // remove folders contained in new folder
        folders.keys.removeIf { it.startsWith(newPath) }
        folders[folder.path] = folder
        changedFolders.add(folder)
    }

    fun removeFolder(path: URI) {
        folders.remove(path)?.let { folder ->
            removedFiles.addAll(folder.map { it.path })
        }
    }

    fun getFile(path: URI): FileInfo? = getFolder(path)?.get(path)

    fun setFile(file: FileInfo) {
        val folder = getFolder(file.path) ?: return
        folder.set(file)
        changedFolders.add(folder)
    }

    fun removeFile(path: URI) {
        val folder = getFolder(path) ?: return
        folder.remove(path)
        changedFolders.add(folder)
        removedFiles.add(path)
    }

    fun removeDirectory(path: URI) {
        val folder = getFolder(path)
        val files = folder?.getFilesIn(path)?.ifEmpty { null } ?: return
        changedFolders.add(folder)
        for (file in files) {
            folder.remove(file)
            removedFiles.add(file)
        }
    }

    fun getRootPackage(path: URI): Package? = getFolder(path)?.globalPackage

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

    private fun getFolder(path: URI): SamtFolder? = folders[path] ?: folders.values.singleOrNull { path.startsWith(it.path) }
}

data class FolderSnapshot(val path: URI, val files: List<FileInfo>, val globalPackage: Package?)


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
