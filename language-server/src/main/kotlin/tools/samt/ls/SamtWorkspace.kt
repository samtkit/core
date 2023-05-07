package tools.samt.ls

import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticMessage
import tools.samt.semantic.Package
import tools.samt.semantic.SemanticModelBuilder
import java.net.URI

class SamtWorkspace(private val parserController: DiagnosticController) : Iterable<FileInfo> {
    private val files = mutableMapOf<URI, FileInfo>()
    var samtPackage: Package? = null
        private set
    private var semanticController: DiagnosticController =
        DiagnosticController(parserController.workingDirectory)

    fun add(fileInfo: FileInfo) {
        files[fileInfo.sourceFile.path] = fileInfo
    }

    operator fun get(path: URI): FileInfo? = files[path]

    override fun iterator(): Iterator<FileInfo> = files.values.iterator()

    operator fun contains(path: URI) = path in files

    fun buildSemanticModel() {
        semanticController = DiagnosticController(parserController.workingDirectory)
        samtPackage = SemanticModelBuilder.build(mapNotNull { it.fileNode }, semanticController)
    }

    private fun getMessages(path: URI): List<DiagnosticMessage> {
        val fileInfo = files[path] ?: return emptyList()
        return fileInfo.diagnosticContext.messages +
                semanticController.getOrCreateContext(fileInfo.sourceFile).messages
    }

    fun getAllMessages() = files.keys.associateWith {
        getMessages(it)
    }
}
