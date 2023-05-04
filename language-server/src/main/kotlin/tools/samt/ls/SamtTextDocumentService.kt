package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import tools.samt.common.SourceFile
import java.util.logging.Logger

class SamtTextDocumentService(private val workspaces: Map<String, SamtWorkspace>) : TextDocumentService,
    LanguageClientAware {
    private lateinit var client: LanguageClient
    private val logger = Logger.getLogger("SamtTextDocumentService")

    override fun didOpen(params: DidOpenTextDocumentParams) {
        logger.info("Opened document ${params.textDocument.uri}")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        logger.info("Changed document ${params.textDocument.uri}")

        val path = params.textDocument.uri.uriToPath()
        val newText = params.contentChanges.single().text
        val fileInfo = parseFile(SourceFile(path, newText))
        val workspaces = getWorkspaces(path)

        workspaces.forEach { workspace ->
            workspace.add(fileInfo)
            workspace.buildSemanticModel()
            workspace.getAllMessages().forEach { (path, messages) ->
                client.publishDiagnostics(PublishDiagnosticsParams(
                    path.pathToUri(),
                    messages.map { it.toDiagnostic() },
                    params.textDocument.version
                ))
            }
        }
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        logger.info("Closed document ${params.textDocument.uri}")
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.info("Saved document ${params.textDocument.uri}")
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    private fun getWorkspaces(filePath: String): List<SamtWorkspace> =
        workspaces.values.filter { filePath in it }
}
