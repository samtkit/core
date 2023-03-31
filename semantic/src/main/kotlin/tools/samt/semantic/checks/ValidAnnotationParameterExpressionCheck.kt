package tools.samt.semantic.checks

import tools.samt.common.DiagnosticContext
import tools.samt.parser.*
import tools.samt.semantic.SemanticCheck

internal class ValidAnnotationParameterExpressionCheck(private val diagnostics: DiagnosticContext) : SemanticCheck {
    override fun check(fileNode: FileNode) {
        fileNode.statements.forEach {
            when (it) {
                is RecordDeclarationNode -> {
                    checkAnnotations(it.annotations)
                    it.fields.forEach { field -> checkAnnotations(field.annotations) }
                }

                is TypeAliasNode -> checkAnnotations(it.annotations)
                is EnumDeclarationNode -> checkAnnotations(it.annotations)
                is ServiceDeclarationNode -> {
                    checkAnnotations(it.annotations)
                    it.operations.forEach { operation ->
                        checkAnnotations(operation.annotations)
                        operation.parameters.forEach { parameter -> checkAnnotations(parameter.annotations) }
                    }
                }

                is TypeImportNode,
                is WildcardImportNode,
                is ConsumerDeclarationNode,
                is PackageDeclarationNode,
                is ProviderDeclarationNode,
                -> Unit
            }
        }
    }

    private fun checkAnnotations(annotations: List<AnnotationNode>) {
        annotations.forEach { annotation ->
            annotation.arguments.forEach { argument ->
                checkLiteralExpression(argument)
            }
        }
    }

    private fun checkLiteralExpression(expression: ExpressionNode) {
        when (expression) {
            is BooleanNode,
            is NumberNode,
            is StringNode,
            -> Unit

            is ObjectNode,
            is ArrayNode,
            is RangeExpressionNode,
            is WildcardNode,
            is IdentifierNode,
            is BundleIdentifierNode,
            is OptionalDeclarationNode,
            is CallExpressionNode,
            is GenericSpecializationNode,
            -> diagnostics.error {
                message("Invalid annotation argument")
                highlight("illegal argument", expression.location)
                help("Annotation arguments must be literal expressions like strings, numbers or booleans")
            }
        }
    }
}
