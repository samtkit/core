package tools.samt.semantic.checks

import tools.samt.common.DiagnosticContext
import tools.samt.parser.*
import tools.samt.semantic.SemanticCheck

internal class ValidTypeExpressionCheck(private val diagnostics: DiagnosticContext) : SemanticCheck {
    override fun check(fileNode: FileNode) {
        fileNode.statements.forEach {
            when (it) {
                is RecordDeclarationNode -> it.fields.forEach { field -> checkTypeExpression(field.type) }
                is TypeAliasNode -> checkTypeExpression(it.type)
                is ServiceDeclarationNode -> it.operations.forEach { operation -> checkOperationType(operation) }
                is TypeImportNode,
                is WildcardImportNode,
                is EnumDeclarationNode,
                is ConsumerDeclarationNode,
                is PackageDeclarationNode,
                is ProviderDeclarationNode,
                -> Unit
            }
        }
    }

    private fun checkOperationType(operation: OperationNode) {
        operation.parameters.forEach { parameter -> checkTypeExpression(parameter.type) }
        when (operation) {
            is OnewayOperationNode -> Unit
            is RequestResponseOperationNode -> {
                operation.returnType?.let { checkTypeExpression(it) }
            }
        }
    }

    private fun checkTypeExpression(expression: ExpressionNode, optionalAllowed: Boolean = true) {
        when (expression) {
            is IdentifierNode,
            is BundleIdentifierNode,
            -> Unit

            is OptionalDeclarationNode -> {
                if (optionalAllowed) {
                    checkTypeExpression(expression.base, false)
                } else {
                    diagnostics.error {
                        message("Cannot nest optional types")
                        highlight("type is already optional", expression.location)
                    }
                }
            }

            is CallExpressionNode -> {
                checkConstraintBaseTypeExpression(expression.base)
                for (argument in expression.arguments) {
                    checkConstraintArgumentExpression(argument)
                }
            }

            is GenericSpecializationNode -> diagnostics.error {
                message("Generic types are currently not supported")
                highlight(expression.location)
            }

            is BooleanNode,
            is NumberNode,
            is StringNode,
            -> diagnostics.error {
                message("Cannot use literal value as type")
                highlight("not a type expression", expression.location)
            }

            is ObjectNode,
            is ArrayNode,
            is RangeExpressionNode,
            is WildcardNode,
            -> diagnostics.error {
                message("Invalid type expression")
                highlight("not a type expression", expression.location)
            }
        }
    }

    private fun checkConstraintBaseTypeExpression(expression: ExpressionNode, optionalAllowed: Boolean = true) {
        when (expression) {
            is IdentifierNode,
            is BundleIdentifierNode,
            -> Unit

            is OptionalDeclarationNode -> {
                if (optionalAllowed) {
                    checkConstraintBaseTypeExpression(expression.base, false)
                } else {
                    diagnostics.error {
                        message("Cannot nest optional types")
                        highlight("type is already optional", expression.location)
                    }
                }
            }

            is CallExpressionNode -> diagnostics.error {
                message("Cannot nest constraint types")
                highlight("type already has constraints", expression.location)
            }

            is GenericSpecializationNode -> diagnostics.error {
                message("Generic type are currently not supported")
                highlight(expression.location)
            }

            is BooleanNode,
            is NumberNode,
            is StringNode,
            is ObjectNode,
            is ArrayNode,
            is RangeExpressionNode,
            is WildcardNode,
            -> diagnostics.error {
                message("Base type for constraint is not valid")
                highlight("not a type expression", expression.location)
            }
        }
    }

    private fun checkConstraintArgumentExpression(expression: ExpressionNode) {
        when (expression) {
            is CallExpressionNode -> {
                if (expression.base !is BundleIdentifierNode) {
                    diagnostics.error {
                        message("Constraint argument name must be an identifier")
                        highlight("is not an identifier", expression.base.location)
                    }
                }
                for (argument in expression.arguments) {
                    checkLiteralExpression(argument)
                }
            }

            is RangeExpressionNode -> checkRangeExpression(expression)

            is BooleanNode,
            is NumberNode,
            is StringNode,
            is IdentifierNode,
            is BundleIdentifierNode,
            is OptionalDeclarationNode,
            is ObjectNode,
            is ArrayNode,
            is WildcardNode,
            is GenericSpecializationNode,
            -> diagnostics.error {
                message("Invalid constraint construct")
                highlight("cannot be used as a constraint parameter", expression.location)
                help("Regular constraints have a name and arguments, e.g. 'size(1..*)', 'pattern(\"a-z\")'")
                help("Shorthand constraints are used without arguments and supported for size checks, e.g. 1..100")
            }
        }
    }

    private fun checkLiteralExpression(expression: ExpressionNode) {
        when (expression) {
            is BooleanNode,
            is NumberNode,
            is StringNode,
            -> Unit

            is ObjectNode -> diagnostics.error {
                message("Cannot use object literal as constraint argument")
                highlight("illegal object literal", expression.location)
            }

            is ArrayNode -> diagnostics.error {
                message("Cannot use array literal as constraint argument")
                highlight("illegal array literal", expression.location)
            }

            is RangeExpressionNode -> checkRangeExpression(expression)

            is WildcardNode -> diagnostics.error {
                message("Cannot use wildcard as constraint argument")
                highlight("illegal wildcard", expression.location)
            }

            is IdentifierNode,
            is BundleIdentifierNode,
            is OptionalDeclarationNode,
            is CallExpressionNode,
            is GenericSpecializationNode,
            -> diagnostics.error {
                message("Invalid type expression")
                highlight("invalid expression", expression.location)
            }
        }
    }

    private fun checkRangeExpression(range: RangeExpressionNode) {
        when {
            range.left is NumberNode && range.right is NumberNode ||
                    range.left is WildcardNode && range.right is NumberNode ||
                    range.left is NumberNode && range.right is WildcardNode -> Unit

            range.left is WildcardNode && range.right is WildcardNode -> diagnostics.error {
                message("Cannot use two wildcards in a range")
                highlight("illegal range", range.location)
            }

            else -> diagnostics.error {
                message("Range must be between two numbers")
                highlight("invalid range", range.location)
                help("Example: 1..100, 1..*, *..5")
            }
        }
    }
}
