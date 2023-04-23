package tools.samt.semantic

import tools.samt.common.DiagnosticContext
import tools.samt.parser.FileNode
import tools.samt.semantic.checks.ValidAnnotationParameterExpressionCheck

internal interface SemanticCheck {
    fun check(fileNode: FileNode)
}

class SemanticChecker private constructor(private val fileNode: FileNode, diagnostics: DiagnosticContext) {
    private val checks: List<SemanticCheck> = listOf(
        ValidAnnotationParameterExpressionCheck(diagnostics),
    )

    fun checkFile() {
        checks.forEach { it.check(fileNode) }
    }

    companion object {
        fun check(fileNode: FileNode, diagnostics: DiagnosticContext) {
            SemanticChecker(fileNode, diagnostics).checkFile()
        }
    }
}
