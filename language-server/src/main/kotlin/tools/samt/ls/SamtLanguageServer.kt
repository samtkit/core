package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.*
import java.io.Closeable
import java.net.URI
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import kotlin.system.exitProcess

class SamtLanguageServer : LanguageServer, LanguageClientAware, Closeable {
    private lateinit var client: LanguageClient
    private val logger = Logger.getLogger("SamtLanguageServer")
    private val workspaces = mutableMapOf<URI, SamtWorkspace>()
    private val textDocumentService = SamtTextDocumentService(workspaces)
    private val workspaceService = SamtWorkspaceService(workspaces)

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
                workspace = WorkspaceServerCapabilities().apply {
                    workspaceFolders = WorkspaceFoldersOptions().apply {
                        supported = true
                        changeNotifications = Either.forRight(true)
                    }
                    fileOperations = FileOperationsServerCapabilities().apply {
                        val samtFilter = FileOperationFilter().apply {
                            pattern = FileOperationPattern().apply {
                                glob = "**/*.samt"
                                matches = FileOperationPatternKind.File
                            }
                        }
                        val folderFilter = FileOperationFilter().apply {
                            pattern = FileOperationPattern().apply {
                                glob = "**"
                                matches = FileOperationPatternKind.Folder
                            }
                        }
                        didCreate = FileOperationOptions().apply {
                            filters = listOf(samtFilter)
                        }
                        FileOperationOptions().apply {
                            filters = listOf(samtFilter, folderFilter)
                        }.let {
                            didRename = it
                            didDelete = it
                        }
                    }
                }
                definitionProvider = Either.forLeft(true)
                referencesProvider = Either.forLeft(true)
            }
            InitializeResult(capabilities)
        }

    override fun initialized(params: InitializedParams) {
        registerFileWatchCapability()
        publishAllDiagnostics()
    }

    override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(null)

    override fun exit() {
        exitProcess(0)
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    override fun connect(client: LanguageClient) {
        this.client = client
        textDocumentService.connect(client)
        workspaceService.connect(client)
        logger.info("Connected to client")
    }

    override fun close() {
        shutdown().get()
    }

    private fun buildSamtModel(params: InitializeParams) {
        val folders = params.workspaceFolders?.map { it.uri.toPathUri() }.orEmpty()
        for (folder in folders) {
            // if the folder is contained within another folder ignore it
            if (folders.any { folder != it && folder.path.startsWith(it.path) }) continue
            workspaces[folder] = SamtWorkspace.createFromDirectory(folder)
        }
    }

    private fun registerFileWatchCapability() {
        val capability = "workspace/didChangeWatchedFiles"
        client.registerCapability(RegistrationParams(listOf(
            Registration(UUID.randomUUID().toString(), capability, DidChangeWatchedFilesRegistrationOptions().apply {
                watchers = listOf(
                    FileSystemWatcher().apply {
                        globPattern = Either.forLeft("**/*.samt")
                    },
                    FileSystemWatcher().apply {
                        globPattern = Either.forLeft("**/")
                        kind = WatchKind.Create or WatchKind.Delete
                    },
                )
            })
        )))
    }

    private fun publishAllDiagnostics() {
        workspaces.values.forEach(client::publishWorkspaceDiagnostics)
    }
}
