package tools.samt.ls

import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticMessage
import tools.samt.common.collectSamtFiles
import tools.samt.common.readSamtSource
import tools.samt.config.SamtConfigurationParser
import tools.samt.semantic.SemanticModel
import java.net.URI
import kotlin.io.path.toPath

class SamtFolder(val configPath: URI, val sourcePath: URI) : Iterable<FileInfo> {
    private val files = mutableMapOf<URI, FileInfo>()
    var semanticModel: SemanticModel? = null
        private set
    private var semanticController: DiagnosticController = DiagnosticController(sourcePath)

    init {
        require(configPath.toPath().fileName == SAMT_CONFIG_FILE_NAME)
    }

    fun set(fileInfo: FileInfo) {
        require(fileInfo.path.startsWith(sourcePath))
        files[fileInfo.path] = fileInfo
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
        semanticController = DiagnosticController(sourcePath)
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
        fun fromConfig(configPath: URI): SamtFolder? {
            val folder =
                try {
                    configPath.toPath().let {
                        val config = SamtConfigurationParser.parseConfiguration(it)
                        SamtFolder(configPath.normalize(), it.resolveSibling(config.source).normalize().toUri())
                    }
                } catch (e: SamtConfigurationParser.ParseException) {
                    return null
                }
            val sourceFiles = collectSamtFiles(folder.sourcePath).readSamtSource(DiagnosticController(folder.sourcePath))
            for (file in sourceFiles) {
                folder.set(parseFile(file))
            }
            return folder
        }
    }
}
