package tools.samt.ls

import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticMessage
import tools.samt.semantic.Package
import tools.samt.semantic.SemanticModelBuilder

class SamtWorkspace(private val parserController: DiagnosticController) : Iterable<FileInfo> {
    private val files = mutableMapOf<String, FileInfo>()
    private var samtPackage: Package? = null
    private var semanticController: DiagnosticController =
        DiagnosticController(parserController.workingDirectoryAbsolutePath)

    fun add(fileInfo: FileInfo) {
        files[fileInfo.sourceFile.absolutePath] = fileInfo
    }

    operator fun get(path: String): FileInfo? = files[path]

    override fun iterator(): Iterator<FileInfo> = files.values.iterator()

    operator fun contains(path: String) = path in files

    fun buildSemanticModel() {
        semanticController = DiagnosticController(parserController.workingDirectoryAbsolutePath)
        samtPackage = SemanticModelBuilder.build(mapNotNull { it.fileNode }, semanticController)
    }

    fun getMessages(path: String): List<DiagnosticMessage> {
        val fileInfo = files[path] ?: return emptyList()
        return fileInfo.diagnosticContext.messages +
                semanticController.getOrCreateContext(fileInfo.sourceFile).messages
    }

    fun getAllMessages() = files.keys.associateWith {
        getMessages(it)
    }
}