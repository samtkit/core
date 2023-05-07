package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import tools.samt.common.SourceFile
import tools.samt.lexer.Token
import tools.samt.parser.FileNode
import tools.samt.semantic.Package
import java.net.URI
import java.util.concurrent.CompletableFuture
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

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> =
        CompletableFuture.supplyAsync {
            val path = params.textDocument.uri.toPathUri()
            val workspace = getWorkspace(path)

            val fileInfo = workspace[path] ?: return@supplyAsync SemanticTokens(emptyList())

            val tokens: List<Token> = fileInfo.tokens
            val fileNode: FileNode = fileInfo.fileNode ?: return@supplyAsync SemanticTokens(emptyList())
            val globalPackage: Package = workspace.samtPackage ?: return@supplyAsync SemanticTokens(emptyList())
            val samtPackage = globalPackage.resolveSubPackage(fileNode.packageDeclaration.name)

            val semanticTokens = SamtSemanticTokens.analyze(fileNode, samtPackage)

            var lastLine = 0
            var lastStartChar = 0

            val encodedData = buildList {
                for (token in tokens) {
                    val (tokenType, modifier) = semanticTokens[token.location] ?: continue
                    val (_, start, end) = token.location
                    val line = start.row
                    val deltaLine = line - lastLine
                    val startChar = start.col
                    val deltaStartChar = if (deltaLine == 0) startChar - lastStartChar else startChar
                    val length = end.charIndex - start.charIndex
                    add(deltaLine)
                    add(deltaStartChar)
                    add(length)
                    add(tokenType.ordinal)
                    add(modifier.bitmask)
                    lastLine = line
                    lastStartChar = startChar
                }
            }

            SemanticTokens(encodedData)
        }

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    private fun getWorkspace(filePath: URI): SamtWorkspace =
        workspaces.values.first { filePath in it }
}
