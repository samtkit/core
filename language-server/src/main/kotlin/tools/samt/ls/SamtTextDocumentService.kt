package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import tools.samt.common.SourceFile
import java.net.URI
import java.util.logging.Logger

class SamtTextDocumentService(private val workspaces: Map<URI, SamtWorkspace>) : TextDocumentService,
    LanguageClientAware {
    private lateinit var client: LanguageClient
    private val logger = Logger.getLogger("SamtTextDocumentService")

    override fun didOpen(params: DidOpenTextDocumentParams) {
        logger.info("Opened document ${params.textDocument.uri}")
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        logger.info("Changed document ${params.textDocument.uri}")

        val path = params.textDocument.uri.toPathUri()
        val newText = params.contentChanges.single().text
        val fileInfo = parseFile(SourceFile(path, newText))
        val workspace = getWorkspace(path)

        workspace.add(fileInfo)
        workspace.buildSemanticModel()
        workspace.getAllMessages().forEach { (path, messages) ->
            client.publishDiagnostics(
                PublishDiagnosticsParams(
                    path.toString(),
                    messages.map { it.toDiagnostic() },
                    params.textDocument.version
                )
            )
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

    private fun getWorkspace(filePath: URI): SamtWorkspace =
        workspaces.values.first { filePath in it }
}
