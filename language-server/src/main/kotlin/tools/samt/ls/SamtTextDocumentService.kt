package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import tools.samt.common.SourceFile
import tools.samt.lexer.Token
import tools.samt.parser.FileNode
import tools.samt.parser.NamedDeclarationNode
import tools.samt.parser.OperationNode
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
        val workspace = workspaces.getByFile(path) ?: return

        workspace.set(fileInfo)
        workspace.buildSemanticModel()
        client.publishWorkspaceDiagnostics(workspace)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        logger.info("Closed document ${params.textDocument.uri}")
        val path = params.textDocument.uri.toPathUri()
        val workspace = workspaces.getByFile(path) ?: return
        workspace.set(readAndParseFile(path))
        workspace.buildSemanticModel()
        client.publishWorkspaceDiagnostics(workspace)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.info("Saved document ${params.textDocument.uri}")
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        CompletableFuture.supplyAsync {
            val path = params.textDocument.uri.toPathUri()
            val workspace = workspaces.getByFile(path)

            val fileInfo = workspace?.get(path) ?: return@supplyAsync Either.forRight(emptyList())

            val fileNode: FileNode = fileInfo.fileNode ?: return@supplyAsync Either.forRight(emptyList())
            val globalPackage: Package = workspace.samtPackage ?: return@supplyAsync Either.forRight(emptyList())

            val token = fileInfo.tokens.findAt(params.position) ?: return@supplyAsync Either.forRight(emptyList())

            val samtPackage = globalPackage.resolveSubPackage(fileNode.packageDeclaration.name)

            val typeLookup = SamtDeclarationLookup.analyze(fileNode, samtPackage)
            val type = typeLookup[token.location] ?: return@supplyAsync Either.forRight(emptyList())

            val definition = type.declaration
            val location = definition.location

            val targetLocation = when (definition) {
                is NamedDeclarationNode -> definition.name.location
                is OperationNode -> definition.name.location
                else -> error("Unexpected definition type")
            }
            val locationLink = LocationLink().apply {
                targetUri = location.source.path.toString()
                targetRange = location.toRange()
                targetSelectionRange = targetLocation.toRange()
            }
            return@supplyAsync Either.forRight(listOf(locationLink))
        }

    override fun references(params: ReferenceParams): CompletableFuture<List<Location>> =
        CompletableFuture.supplyAsync {
            val path = params.textDocument.uri.toPathUri()
            val workspace = workspaces.getByFile(path) ?: return@supplyAsync emptyList()

            val relevantFileInfo = workspace[path] ?: return@supplyAsync emptyList()
            val relevantFileNode = relevantFileInfo.fileNode ?: return@supplyAsync emptyList()
            val token = relevantFileInfo.tokens.findAt(params.position) ?: return@supplyAsync emptyList()

            val globalPackage: Package = workspace.samtPackage ?: return@supplyAsync emptyList()

            val typeLookup = SamtDeclarationLookup.analyze(relevantFileNode, globalPackage.resolveSubPackage(relevantFileInfo.fileNode.packageDeclaration.name))
            val type = typeLookup[token.location] ?: return@supplyAsync emptyList()

            val filesAndPackages = buildList {
                for (fileInfo in workspace) {
                    val fileNode: FileNode = fileInfo.fileNode ?: continue
                    val samtPackage = globalPackage.resolveSubPackage(fileNode.packageDeclaration.name)
                    add(fileNode to samtPackage)
                }
            }

            val typeReferencesLookup = SamtReferencesLookup.analyze(filesAndPackages)

            val references = typeReferencesLookup[type] ?: emptyList()

            return@supplyAsync references.map { Location(it.source.path.toString(), it.toRange()) }
        }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> =
        CompletableFuture.supplyAsync {
            val path = params.textDocument.uri.toPathUri()
            val workspace = workspaces.getByFile(path)

            val fileInfo = workspace?.get(path) ?: return@supplyAsync SemanticTokens(emptyList())

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
}
