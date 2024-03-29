package tools.samt.ls

import org.eclipse.lsp4j.*
import tools.samt.common.DiagnosticMessage
import tools.samt.common.DiagnosticSeverity
import tools.samt.common.Location as SamtLocation

fun DiagnosticMessage.toDiagnostic(): Diagnostic? {
    val diagnostic = Diagnostic()
    val primaryHighlight = this.highlights.firstOrNull() ?: return null
    diagnostic.range = primaryHighlight.location.toRange()
    diagnostic.severity = severity.toLspSeverity()
    diagnostic.source = "samt"
    diagnostic.message = message
    diagnostic.relatedInformation = highlights.filter { it.message != null }.map {
        DiagnosticRelatedInformation(
            Location(it.location.source.path.toString(), it.location.toRange()),
            it.message
        )
    }
    return diagnostic
}

fun DiagnosticSeverity.toLspSeverity(): org.eclipse.lsp4j.DiagnosticSeverity = when (this) {
    DiagnosticSeverity.Error -> org.eclipse.lsp4j.DiagnosticSeverity.Error
    DiagnosticSeverity.Warning -> org.eclipse.lsp4j.DiagnosticSeverity.Warning
    DiagnosticSeverity.Info -> org.eclipse.lsp4j.DiagnosticSeverity.Information
}

fun SamtLocation.toRange(): Range = Range(
    Position(start.row, start.col),
    Position(end.row, end.col)
)
