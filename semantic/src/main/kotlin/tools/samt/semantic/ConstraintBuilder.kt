package tools.samt.semantic

import tools.samt.common.DiagnosticController
import tools.samt.parser.*

internal class ConstraintBuilder(private val controller: DiagnosticController) {
    private fun createRange(expression: RangeExpressionNode): ResolvedTypeReference.Constraint.Range? {
        fun resolveSide(expressionNode: ExpressionNode): Number? = when (expressionNode) {
            is NumberNode -> expressionNode.value
            is WildcardNode -> null
            else -> {
                controller.createContext(expressionNode.location.source).error {
                    message("Range constraint argument must be a valid number range")
                    highlight("neither a number nor '*'", expressionNode.location)
                    help("A valid constraint would be range(1..10.5) or range(1..*)")
                }
                null
            }
        }

        val lower = resolveSide(expression.left)
        val higher = resolveSide(expression.right)

        if (lower == null && higher == null) {
            controller.createContext(expression.location.source).error {
                message("Range constraint must have at least one valid number")
                highlight("invalid constraint", expression.location)
                help("A valid constraint would be range(1..10.5) or range(1..*)")
            }
            return null
        }

        if (lower is Double && higher is Double && lower > higher ||
            lower is Long && higher is Long && lower > higher
        ) {
            controller.createContext(expression.location.source)
                .error {
                    message("Range constraint must have a lower bound lower than the upper bound")
                    highlight("invalid constraint", expression.location)
                }
            return null
        }

        return ResolvedTypeReference.Constraint.Range(
            definition = expression,
            lowerBound = lower,
            upperBound = higher,
        )
    }

    private fun createSize(expression: RangeExpressionNode): ResolvedTypeReference.Constraint.Size? {
        fun resolveSide(expressionNode: ExpressionNode): Long? = when (expressionNode) {
            is IntegerNode -> expressionNode.value
            is WildcardNode -> null

            else -> {
                controller.createContext(expressionNode.location.source).error {
                    message("Expected size constraint argument to be a whole number or wildcard")
                    highlight("expected whole number or wildcard '*'", expressionNode.location)
                    help("A valid constraint would be size(1..10), size(1..*) or size(*..10)")
                }
                null
            }
        }

        val lower = resolveSide(expression.left)
        val higher = resolveSide(expression.right)

        if (lower == null && higher == null) {
            controller.createContext(expression.location.source).error {
                message("Constraint parameters cannot both be wildcards")
                highlight("invalid constraint", expression.location)
                help("A valid constraint would be range(1..10.5) or range(1..*)")
            }
            return null
        }

        if (lower != null && higher != null && lower > higher) {
            controller.createContext(expression.location.source).error {
                message("Size constraint lower bound must be lower than or equal to the upper bound")
                highlight("invalid constraint", expression.location)
            }
            return null
        }

        return ResolvedTypeReference.Constraint.Size(
            definition = expression,
            lowerBound = lower,
            upperBound = higher,
        )
    }

    private fun createPattern(expression: StringNode): ResolvedTypeReference.Constraint.Pattern {
        // We will validate the pattern here in the future
        return ResolvedTypeReference.Constraint.Pattern(expression, expression.value)
    }

    private fun createValue(expression: ExpressionNode): ResolvedTypeReference.Constraint.Value? {
        return when (expression) {
            is StringNode -> ResolvedTypeReference.Constraint.Value(expression, expression.value)
            is NumberNode -> ResolvedTypeReference.Constraint.Value(expression, expression.value)
            is BooleanNode -> ResolvedTypeReference.Constraint.Value(expression, expression.value)
            else -> {
                controller.createContext(expression.location.source).error {
                    message("Value constraint must be a string, integer, float or boolean")
                    highlight("invalid constraint", expression.location)
                    help("A valid constraint would be value(\"foo\"), value(42) or value(false)")
                }
                null
            }
        }
    }

    private fun buildConstraint(baseType: Type, expression: ExpressionNode): ResolvedTypeReference.Constraint? {
        when (expression) {
            is CallExpressionNode -> {
                val name = expression.base.let {
                    when (it) {
                        is IdentifierNode -> it.name
                        is BundleIdentifierNode -> it.name
                        else -> null
                    }
                }
                when (name) {
                    "range" -> {
                        if (expression.arguments.size != 1 || expression.arguments.firstOrNull() !is RangeExpressionNode) {
                            controller.createContext(expression.location.source).error {
                                message("Range constraint must have exactly one range argument")
                                highlight("invalid constraint", expression.location)
                                help("A valid constraint would be range(1..10.5)")
                            }
                            return null
                        }
                        return createRange(expression.arguments.first() as RangeExpressionNode)
                    }

                    "size" -> {
                        if (expression.arguments.size != 1 || expression.arguments.firstOrNull() !is RangeExpressionNode) {
                            controller.createContext(expression.location.source).error {
                                message("Size constraint must have exactly one size argument")
                                highlight("invalid constraint", expression.location)
                                help("A valid constraint would be size(1..10)")
                            }
                            return null
                        }
                        return createSize(expression.arguments.first() as RangeExpressionNode)
                    }

                    "pattern" -> {
                        if (expression.arguments.size != 1 || expression.arguments.firstOrNull() !is StringNode) {
                            controller.createContext(expression.location.source).error {
                                message("Pattern constraint must have exactly one string argument")
                                highlight("invalid constraint", expression.location)
                                help("A valid constraint would be pattern(\"a-z\")")
                            }
                            return null
                        }
                        return createPattern(expression.arguments.first() as StringNode)
                    }

                    "value" -> {
                        if (expression.arguments.size != 1) {
                            controller.createContext(expression.location.source).error {
                                message("value constraint must have exactly one argument")
                                highlight("invalid constraint", expression.location)
                            }
                            return null
                        }
                        return createValue(expression.arguments.first())
                    }

                    is String -> {
                        controller.createContext(expression.location.source).error {
                            message("Constraint with name '${name}' does not exist")
                            highlight("unknown constraint", expression.base.location)
                            help("A valid constraint would be range(1..10.5), size(1..10), pattern(\"a-z\") or value(\"foo\")")
                        }
                        return null
                    }
                }
            }
            // It might make sense to limit shorthand constraints to only the first argument
            is RangeExpressionNode -> return if (baseType is StringType || baseType is ListType || baseType is MapType) createSize(
                expression
            ) else createRange(expression)

            is NumberNode -> {
                return if (expression is IntegerNode && (baseType is StringType || baseType is ListType || baseType is MapType)) {
                    ResolvedTypeReference.Constraint.Size(
                        definition = expression,
                        lowerBound = null,
                        upperBound = expression.value
                    )
                } else {
                    ResolvedTypeReference.Constraint.Range(
                        definition = expression,
                        lowerBound = null,
                        upperBound = expression.value
                    )
                }
            }

            is StringNode -> return createPattern(expression)
            else -> Unit
        }
        controller.createContext(expression.location.source).error {
            message("Invalid constraint")
            highlight("invalid constraint", expression.location)
        }
        return null
    }

    private fun validateConstraintMatches(constraint: ResolvedTypeReference.Constraint, baseType: Type): Boolean {
        return when (constraint) {
            is ResolvedTypeReference.Constraint.Pattern -> baseType is StringType
            is ResolvedTypeReference.Constraint.Range -> baseType is NumberType
            is ResolvedTypeReference.Constraint.Size -> baseType is StringType || baseType is ListType || baseType is MapType
            is ResolvedTypeReference.Constraint.Value -> when (constraint.value) {
                is String -> baseType is StringType
                is Number -> baseType is NumberType
                is Boolean -> baseType is BooleanType
                else -> false
            }
        }
    }

    fun build(baseType: Type, expression: ExpressionNode): ResolvedTypeReference.Constraint? {
        val constraint = buildConstraint(baseType, expression) ?: return null

        return if (validateConstraintMatches(constraint, baseType)) {
            constraint
        } else {
            controller.createContext(expression.location.source).error {
                message("Constraint '${constraint.humanReadableName}' is not allowed for type '${baseType.humanReadableName}'")
                highlight("illegal constraint", expression.location)

                // applicable constraints
                // string: pattern, size, value
                // number: range, value
                // boolean: value
                // list: size
                // map: size

                val applicableConstraints = when (baseType) {
                    is StringType -> "pattern, size or value"
                    is NumberType -> "range or value"
                    is BooleanType -> "value"
                    is ListType -> "size"
                    is MapType -> "size"
                    else -> ""
                }

                help("Applicable constraints for type are $applicableConstraints")
            }
            null
        }

    }
}