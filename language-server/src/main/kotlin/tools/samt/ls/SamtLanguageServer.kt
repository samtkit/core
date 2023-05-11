package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import tools.samt.common.DiagnosticController
import tools.samt.common.collectSamtFiles
import tools.samt.common.readSamtSource
import java.io.Closeable
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.system.exitProcess

class SamtLanguageServer : LanguageServer, LanguageClientAware, Closeable {
    private lateinit var client: LanguageClient
    private val logger = Logger.getLogger("SamtLanguageServer")
    private val workspaces = mutableMapOf<URI, SamtWorkspace>()
    private val textDocumentService = SamtTextDocumentService(workspaces)

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> =
        CompletableFuture.supplyAsync {
            buildSamtModel(params)
            val capabilities = ServerCapabilities().apply {
                textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
                semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
                    legend = SamtSemanticTokens.legend
                    range = Either.forLeft(false)
                    full = Either.forLeft(true)
                }
                definitionProvider = Either.forLeft(true)
                referencesProvider = Either.forLeft(true)
            }
            InitializeResult(capabilities)
        }

    override fun initialized(params: InitializedParams) {
        workspaces.values.forEach(client::publishWorkspaceDiagnostics)
    }

    override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(null)

    override fun exit() {
        exitProcess(0)
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService? = null

    override fun connect(client: LanguageClient) {
        this.client = client
        textDocumentService.connect(client)
        logger.info("Connected to client")
    }

    override fun close() {
        shutdown().get()
    }

    private fun buildSamtModel(params: InitializeParams) {
        val folders = params.workspaceFolders.map { it.uri.toPathUri() }
        for (folder in folders) {
            // if the folder is contained within another folder ignore it
            if (folders.any { folder != it && folder.path.startsWith(it.path) }) continue
            workspaces[folder] = buildWorkspace(folder)
        }
    }

    private fun buildWorkspace(workspacePath: URI): SamtWorkspace {
        val diagnosticController = DiagnosticController(workspacePath)
        val sourceFiles = collectSamtFiles(workspacePath).readSamtSource(diagnosticController)
        val workspace = SamtWorkspace(diagnosticController)
        sourceFiles.asSequence().map(::parseFile).forEach(workspace::add)
        workspace.buildSemanticModel()
        return workspace
    }
}
