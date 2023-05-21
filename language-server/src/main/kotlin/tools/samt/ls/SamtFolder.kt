package tools.samt.ls

import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticMessage
import tools.samt.common.collectSamtFiles
import tools.samt.common.readSamtSource
import tools.samt.semantic.SemanticModel
import java.net.URI

class SamtFolder(val path: URI) : Iterable<FileInfo> {
    private val files = mutableMapOf<URI, FileInfo>()
    var semanticModel: SemanticModel? = null
        private set
    private var semanticController: DiagnosticController = DiagnosticController(path)

    fun set(fileInfo: FileInfo) {
        files[fileInfo.sourceFile.path] = fileInfo
    }

    fun remove(fileUri: URI) {
        files.remove(fileUri)
    }

    operator fun get(path: URI): FileInfo? = files[path]

    override fun iterator(): Iterator<FileInfo> = files.values.iterator()

    operator fun contains(path: URI) = path in files

    fun getFilesIn(directoryPath: URI): List<URI> {
        return files.keys.filter { it.startsWith(directoryPath) }
    }

    fun buildSemanticModel() {
        semanticController = DiagnosticController(path)
        semanticModel = SemanticModel.build(mapNotNull { it.fileNode }, semanticController)
    }

    private fun getMessages(path: URI): List<DiagnosticMessage> {
        val fileInfo = files[path] ?: return emptyList()
        return fileInfo.diagnosticContext.messages +
                semanticController.getOrCreateContext(fileInfo.sourceFile).messages
    }

    fun getAllMessages() = files.keys.associateWith {
        getMessages(it)
    }

    companion object {
        fun fromDirectory(path: URI): SamtFolder {
            val controller = DiagnosticController(path)
            val workspace = SamtFolder(path)
            val sourceFiles = collectSamtFiles(path).readSamtSource(controller)
            sourceFiles.forEach {
                workspace.set(parseFile(it))
            }
            return workspace
        }
    }
}
