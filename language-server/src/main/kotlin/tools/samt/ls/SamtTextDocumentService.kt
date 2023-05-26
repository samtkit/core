package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import tools.samt.common.SourceFile
import tools.samt.lexer.*
import tools.samt.parser.FileNode
import tools.samt.parser.NamedDeclarationNode
import tools.samt.parser.OperationNode
import tools.samt.semantic.*
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.io.path.toPath

class SamtTextDocumentService(private val workspace: SamtWorkspace) : TextDocumentService,
    LanguageClientAware {
    private lateinit var client: LanguageClient
    private val logger = Logger.getLogger("SamtTextDocumentService")

    override fun didOpen(params: DidOpenTextDocumentParams) {
        logger.info("Opened document ${params.textDocument.uri}")
        val path = params.textDocument.uri.toPathUri()
        val text = params.textDocument.text

        if (!workspace.containsFile(path)) {
            val configPath = findSamtConfigs(path.toPath().parent).singleOrNull()
            configPath
                ?.let { SamtFolder.fromConfig(it.toUri()) }
                ?.let { workspace.addFolder(it) }
        }
        workspace.setFile(parseFile(SourceFile(path, text)))
        client.updateWorkspace(workspace)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        logger.info("Changed document ${params.textDocument.uri}")

        val path = params.textDocument.uri.toPathUri()
        val newText = params.contentChanges.single().text
        val fileInfo = parseFile(SourceFile(path, newText))

        workspace.setFile(fileInfo)
        client.updateWorkspace(workspace)
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        logger.info("Closed document ${params.textDocument.uri}")
        val path = params.textDocument.uri.toPathUri()

        workspace.setFile(readAndParseFile(path))
        client.updateWorkspace(workspace)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.info("Saved document ${params.textDocument.uri}")
    }

    override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        CompletableFuture.supplyAsync {
            val path = params.textDocument.uri.toPathUri()

            val fileInfo = workspace.getFile(path) ?: return@supplyAsync Either.forRight(emptyList())

            val fileNode: FileNode = fileInfo.fileNode ?: return@supplyAsync Either.forRight(emptyList())
            val semanticModel = workspace.getSemanticModel(path) ?: return@supplyAsync Either.forRight(emptyList())
            val globalPackage: Package = semanticModel.global

            val token = fileInfo.tokens.findAt(params.position) ?: return@supplyAsync Either.forRight(emptyList())

            val filePackage = globalPackage.resolveSubPackage(fileNode.packageDeclaration.name)

            val typeLookup = SamtDeclarationLookup.analyze(fileNode, filePackage, semanticModel.userMetadata)
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

            val relevantFileInfo = workspace.getFile(path) ?: return@supplyAsync emptyList()
            val relevantFileNode = relevantFileInfo.fileNode ?: return@supplyAsync emptyList()
            val token = relevantFileInfo.tokens.findAt(params.position) ?: return@supplyAsync emptyList()

            val (_, files, semanticModel) = workspace.getFolderSnapshot(path) ?: return@supplyAsync emptyList()
            if (semanticModel == null) return@supplyAsync emptyList()

            val globalPackage = semanticModel.global
            val typeLookup = SamtDeclarationLookup.analyze(
                relevantFileNode,
                globalPackage.resolveSubPackage(relevantFileInfo.fileNode.packageDeclaration.name),
                semanticModel.userMetadata
            )
            val type = typeLookup[token.location] ?: return@supplyAsync emptyList()

            val filesAndPackages = buildList {
                for (fileInfo in files) {
                    val fileNode: FileNode = fileInfo.fileNode ?: continue
                    val filePackage = globalPackage.resolveSubPackage(fileNode.packageDeclaration.name)
                    add(fileNode to filePackage)
                }
            }

            val typeReferencesLookup = SamtReferencesLookup.analyze(filesAndPackages, semanticModel.userMetadata)

            val references = typeReferencesLookup[type] ?: emptyList()

            return@supplyAsync references.map { Location(it.source.path.toString(), it.toRange()) }
        }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> =
        CompletableFuture.supplyAsync {
            val path = params.textDocument.uri.toPathUri()

            val fileInfo = workspace.getFile(path) ?: return@supplyAsync SemanticTokens(emptyList())

            val tokens: List<Token> = fileInfo.tokens
            val fileNode: FileNode = fileInfo.fileNode ?: return@supplyAsync SemanticTokens(emptyList())
            val semanticModel = workspace.getSemanticModel(path) ?: return@supplyAsync SemanticTokens(emptyList())
            val filePackage = semanticModel.global.resolveSubPackage(fileNode.packageDeclaration.name)

            val semanticTokens = SamtSemanticTokens.analyze(fileNode, filePackage, semanticModel.userMetadata)

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

    override fun hover(params: HoverParams): CompletableFuture<Hover> = CompletableFuture.supplyAsync {
        val path = params.textDocument.uri.toPathUri()

        val fileInfo = workspace.getFile(path) ?: return@supplyAsync null

        val fileNode: FileNode = fileInfo.fileNode ?: return@supplyAsync null
        val semanticModel = workspace.getSemanticModel(path) ?: return@supplyAsync null
        val globalPackage: Package = semanticModel.global

        val token = fileInfo.tokens.findAt(params.position) ?: return@supplyAsync null
        val filePackage = globalPackage.resolveSubPackage(fileNode.packageDeclaration.name)

        val typeLookup = SamtDeclarationLookup.analyze(fileNode, filePackage, semanticModel.userMetadata)
        val type = typeLookup[token.location] ?: return@supplyAsync null

        val description = buildString {
            appendLine("```samt")
            appendLine(type.peekDeclaration())
            appendLine("```")
            appendLine("---")
            appendLine(semanticModel.userMetadata.getDescription(type).orEmpty())
        }
        Hover().apply {
            contents = Either.forRight(MarkupContent(MarkupKind.MARKDOWN, description))
            range = token.location.toRange()
        }
    }

    private fun UserDeclared.peekDeclaration(): String {
        fun List<ServiceType.Operation.Parameter>.toParameterList(): String =
            joinToString(", ") { it.peekDeclaration() }

        return when (this) {
            is AliasType -> "${getHumanReadableName<TypealiasToken>()} $humanReadableName"
            is EnumType -> "${getHumanReadableName<EnumToken>()} $humanReadableName"
            is RecordType.Field -> "$name: ${type.humanReadableName}"
            is ServiceType.OnewayOperation -> "${getHumanReadableName<OnewayToken>()} $name(${parameters.toParameterList()})"
            is ServiceType.RequestResponseOperation -> buildString {
                if (isAsync) {
                    append(getHumanReadableName<AsyncToken>())
                    append(' ')
                }
                append(name)
                append('(')
                append(parameters.toParameterList())
                append(')')
                returnType?.let {
                    append(": ")
                    append(it.humanReadableName)
                }
                if (raisesTypes.isNotEmpty()) {
                    append(' ')
                    append(getHumanReadableName<RaisesToken>())
                    append(' ')
                    raisesTypes.joinTo(this, ", ") { it.humanReadableName }
                }
            }

            is ServiceType.Operation.Parameter -> "$name: ${type.humanReadableName}"
            is RecordType -> "${getHumanReadableName<RecordToken>()} $humanReadableName"
            is ServiceType -> "${getHumanReadableName<ServiceToken>()} $humanReadableName"
            is ConsumerType -> "${getHumanReadableName<ConsumeToken>()} $humanReadableName"
            is ProviderType -> "${getHumanReadableName<ProvideToken>()} $humanReadableName"
        }
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }
}
