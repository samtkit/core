package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.*
import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlin.system.exitProcess

class SamtLanguageServer : LanguageServer, LanguageClientAware, Closeable {
    private lateinit var client: LanguageClient

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> = CompletableFuture.supplyAsync {
        val capabilities = ServerCapabilities().apply {

        }
        InitializeResult(capabilities)
    }

    override fun shutdown(): CompletableFuture<Any> = CompletableFuture.completedFuture(null)

    override fun exit() {
        exitProcess(0)
    }

    override fun getTextDocumentService(): TextDocumentService? = null

    override fun getWorkspaceService(): WorkspaceService? = null

    override fun connect(client: LanguageClient) {
        this.client = client
    }

    override fun close() {
        shutdown().get()
    }

}
