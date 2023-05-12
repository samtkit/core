package tools.samt.ls

import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticMessage
import tools.samt.semantic.Package
import tools.samt.semantic.SemanticModelBuilder
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.toPath

class SamtWorkspace(private val parserController: DiagnosticController) : Iterable<FileInfo> {
    private val files = mutableMapOf<URI, FileInfo>()
    var samtPackage: Package? = null
        private set
    private var semanticController: DiagnosticController =
        DiagnosticController(parserController.workingDirectory)
    val workingDirectory = parserController.workingDirectory

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
        val path = directoryPath.toPath()
        return files.keys.filter { it.toPath().startsWith(path) }
    }

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

fun LanguageClient.publishWorkspaceDiagnostics(workspace: SamtWorkspace) {
    workspace.getAllMessages().forEach { (path, messages) ->
        publishDiagnostics(
            PublishDiagnosticsParams(
                path.toString(),
                messages.map { it.toDiagnostic() }
            )
        )
    }
}

fun Map<*, SamtWorkspace>.getByFile(filePath: Path): SamtWorkspace? =
    values.firstOrNull {  filePath.startsWith(it.workingDirectory.toPath()) }
fun Map<*, SamtWorkspace>.getByFile(fileUri: URI): SamtWorkspace? = getByFile(fileUri.toPath())
