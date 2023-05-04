package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.*
import tools.samt.common.*
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.system.exitProcess

class SamtLanguageServer : LanguageServer, LanguageClientAware, Closeable {
    private lateinit var client: LanguageClient
    private val logger = Logger.getLogger("SamtLanguageServer")
    private val workspaces = mutableMapOf<String, SamtWorkspace>()
    private val textDocumentService = SamtTextDocumentService(workspaces)

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> =
        CompletableFuture.supplyAsync {
            buildSamtModel(params)
            val capabilities = ServerCapabilities().apply {
                setTextDocumentSync(TextDocumentSyncKind.Full)
            }
            InitializeResult(capabilities)
        }

    override fun initialized(params: InitializedParams) {
        pushDiagnostics()
    }

    override fun setTrace(params: SetTraceParams?) {
        // TODO
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
        params.workspaceFolders.forEach {
            val path = it.uri.uriToPath()
            workspaces[path] = buildWorkspace(path)
        }
    }

    private fun buildWorkspace(workspacePath: String): SamtWorkspace {
        val diagnosticController = DiagnosticController(workspacePath)
        val sourceFiles = collectSamtFiles(workspacePath).readSamtSource(diagnosticController)
        val workspace = SamtWorkspace(diagnosticController)
        sourceFiles.asSequence().map(::parseFile).forEach(workspace::add)
        workspace.buildSemanticModel()
        return workspace
    }

    private fun pushDiagnostics() {
        workspaces.values.flatMap { workspace ->
            workspace.getAllMessages().map { (path, messages) ->
                PublishDiagnosticsParams(
                    path.pathToUri(),
                    messages.map { it.toDiagnostic() }
                )
            }
        }.forEach(client::publishDiagnostics)
    }
}
