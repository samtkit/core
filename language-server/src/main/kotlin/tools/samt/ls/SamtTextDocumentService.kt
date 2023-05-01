package tools.samt.ls

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.TextDocumentService
import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticException
import tools.samt.common.DiagnosticMessage
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModelBuilder
import java.util.logging.Logger
import kotlin.io.path.Path
import kotlin.io.path.readText
import tools.samt.common.DiagnosticSeverity as SamtSeverity
import tools.samt.common.Location as SamtLocation

class SamtTextDocumentService : TextDocumentService, LanguageClientAware {
    private lateinit var client: LanguageClient
    private val logger = Logger.getLogger("SamtTextDocumentService")

    override fun didOpen(params: DidOpenTextDocumentParams) {
        logger.info("Opened document ${params.textDocument.uri}")

        val uri = params.textDocument.uri.removePrefix("file://")
        val messages = compile(uri, Path(uri).readText())

        client.publishDiagnostics(
            PublishDiagnosticsParams(
                params.textDocument.uri,
                messages.mapNotNull { it.toDiagnostic() })
        )
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        logger.info("Changed document ${params.textDocument.uri}")

        val uri = params.textDocument.uri.removePrefix("file://")
        val newText = params.contentChanges.firstOrNull()?.text ?: Path(uri).readText()
        val messages = compile(uri, newText)

        client.publishDiagnostics(
            PublishDiagnosticsParams(
                params.textDocument.uri,
                messages.mapNotNull { it.toDiagnostic() })
        )
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        logger.info("Closed document ${params.textDocument.uri}")
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        logger.info("Saved document ${params.textDocument.uri}")
    }

    private fun SamtLocation.toRange(): Range {
        return Range(
            Position(start.row, start.col),
            Position(start.row, end.col)
        )
    }

    private fun DiagnosticMessage.toDiagnostic(): Diagnostic? {
        val diagnostic = Diagnostic()
        val primaryHighlight = this.highlights.firstOrNull()
        if (primaryHighlight == null) {
            logger.warning("Diagnostic message without location, how do I convert this???")
            return null
        }
        // TODO consider highlightBeginningOnly
        diagnostic.range = primaryHighlight.location.toRange()
        diagnostic.severity = when (severity) {
            SamtSeverity.Error -> DiagnosticSeverity.Error
            SamtSeverity.Warning -> DiagnosticSeverity.Warning
            SamtSeverity.Info -> DiagnosticSeverity.Information
        }
        diagnostic.source = "samt"
        diagnostic.message = message
        diagnostic.relatedInformation = highlights.filter { it.message != null }.map {
            DiagnosticRelatedInformation(
                Location("file://${it.location.source.absolutePath}", it.location.toRange()),
                it.message
            )
        }
        return diagnostic
    }

    private fun compile(uri: String, content: String): List<DiagnosticMessage> {
        val source = SourceFile(uri, content)
        val controller = DiagnosticController("todo")
        val context = controller.createContext(source)

        val tokenStream = Lexer.scan(source.content.reader(), context)

        if (context.hasErrors()) {
            return context.messages
        }

        val fileNode = try {
            Parser.parse(source, tokenStream, context)
        } catch (e: DiagnosticException) {
            // error message is added to the diagnostic console, so it can be ignored here
            return context.messages
        }

        SemanticModelBuilder.build(listOf(fileNode), controller)

        return context.messages
    }

    override fun connect(client: LanguageClient) {
        this.client = client
    }
}
