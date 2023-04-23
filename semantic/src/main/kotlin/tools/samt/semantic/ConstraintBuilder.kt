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
            lowerBound = lower,
            upperBound = higher,
        )
    }

    private fun createSize(expression: RangeExpressionNode): ResolvedTypeReference.Constraint.Size? {
        fun resolveSide(expressionNode: ExpressionNode): Long? = when (expressionNode) {
            is IntegerNode -> expressionNode.value
            is WildcardNode -> null
            is FloatNode -> {
                controller.createContext(expressionNode.location.source).error {
                    message("Size constraint argument '${expressionNode.value}' is not a whole number")
                    highlight("illegal floating point number", expressionNode.location)
                }
                null
            }
            else -> {
                controller.createContext(expressionNode.location.source).error {
                    message("Range constraint argument must be a valid integer range")
                    highlight("neither a number nor '*'", expressionNode.location)
                    help("A valid constraint would be size(1..10) or size(1..*)")
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

        if (lower != null && higher != null && lower > higher) {
            controller.createContext(expression.location.source)
                .error {
                    message("Size constraint must have a lower bound lower than the upper bound")
                    highlight("invalid constraint", expression.location)
                }
            return null
        }

        return ResolvedTypeReference.Constraint.Size(
            lowerBound = lower,
            upperBound = higher,
        )
    }

    private fun createPattern(expression: StringNode): ResolvedTypeReference.Constraint.Pattern {
        // We will validate the pattern here in the future
        return ResolvedTypeReference.Constraint.Pattern(expression.value)
    }

    private fun createValue(expression: ExpressionNode): ResolvedTypeReference.Constraint.Value? {
        return when (expression) {
            is StringNode -> ResolvedTypeReference.Constraint.Value(expression.value)
            is NumberNode -> ResolvedTypeReference.Constraint.Value(expression.value)
            is BooleanNode -> ResolvedTypeReference.Constraint.Value(expression.value)
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

    fun build(constraint: ExpressionNode): ResolvedTypeReference.Constraint? {
        when (constraint) {
            is CallExpressionNode -> {
                val name = constraint.base.let {
                    when (it) {
                        is IdentifierNode -> it.name
                        is BundleIdentifierNode -> it.name
                        else -> null
                    }
                }
                when (name) {
                    "range" -> {
                        if (constraint.arguments.size != 1 || constraint.arguments.firstOrNull() !is RangeExpressionNode) {
                            controller.createContext(constraint.location.source).error {
                                message("Range constraint must have exactly one range argument")
                                highlight("invalid constraint", constraint.location)
                                help("A valid constraint would be range(1..10.5)")
                            }
                            return null
                        }
                        return createRange(constraint.arguments.first() as RangeExpressionNode)
                    }

                    "size" -> {
                        if (constraint.arguments.size != 1 || constraint.arguments.firstOrNull() !is RangeExpressionNode) {
                            controller.createContext(constraint.location.source).error {
                                message("Size constraint must have exactly one size argument")
                                highlight("invalid constraint", constraint.location)
                                help("A valid constraint would be size(1..10)")
                            }
                            return null
                        }
                        return createSize(constraint.arguments.first() as RangeExpressionNode)
                    }

                    "pattern" -> {
                        if (constraint.arguments.size != 1 || constraint.arguments.firstOrNull() !is StringNode) {
                            controller.createContext(constraint.location.source).error {
                                message("Pattern constraint must have exactly one string argument")
                                highlight("invalid constraint", constraint.location)
                                help("A valid constraint would be pattern(\"a-z\")")
                            }
                            return null
                        }
                        return createPattern(constraint.arguments.first() as StringNode)
                    }

                    "value" -> {
                        if (constraint.arguments.size != 1) {
                            controller.createContext(constraint.location.source).error {
                                message("value constraint must have exactly one argument")
                                highlight("invalid constraint", constraint.location)
                            }
                            return null
                        }
                        return createValue(constraint.arguments.first())
                    }

                    is String -> {
                        controller.createContext(constraint.location.source).error {
                            message("Constraint with name '${name}' does not exist")
                            highlight("unknown constraint", constraint.base.location)
                            help("A valid constraint would be range(1..10.5), size(1..10), pattern(\"a-z\") or value(\"foo\")")
                        }
                        return null
                    }
                }
            }
            // It might make sense to limit shorthand constraints to only the first argument
            is RangeExpressionNode -> return createRange(constraint)
            is NumberNode -> {
                // We should probably create a Range or Size constraint depending on the base type
                return ResolvedTypeReference.Constraint.Range(
                    lowerBound = null,
                    upperBound = constraint.value
                )
            }

            is StringNode -> return createPattern(constraint)
            else -> Unit
        }
        controller.createContext(constraint.location.source).error {
            message("Invalid constraint")
            highlight("invalid constraint", constraint.location)
        }
        return null
    }
}