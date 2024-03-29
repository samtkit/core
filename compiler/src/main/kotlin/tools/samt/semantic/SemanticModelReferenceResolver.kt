package tools.samt.semantic

import tools.samt.common.DiagnosticController
import tools.samt.parser.*

internal class SemanticModelReferenceResolver(
    private val controller: DiagnosticController,
    private val global: Package,
) {
    private val constraintBuilder = ConstraintBuilder(controller)

    fun resolveAndLinkExpression(
        scope: SemanticModelBuilder.FileScope,
        rootExpression: ExpressionNode,
    ): ResolvedTypeReference {
        val resolvedTypeReference = resolveExpressionInternal(scope, rootExpression)
        scope.filePackage.sourcePackage.linkType(resolvedTypeReference.typeNode, resolvedTypeReference.type)
        return resolvedTypeReference
    }

    private fun resolveExpressionInternal(
        scope: SemanticModelBuilder.FileScope,
        expression: ExpressionNode,
    ): ResolvedTypeReference {
        when (expression) {
            is IdentifierNode -> {
                scope.typeLookup[expression.name]?.let { type ->
                    return ResolvedTypeReference(type, expression)
                }

                expression.reportError(controller) {
                    message("Type '${expression.name}' could not be resolved")
                    highlight("unresolved type", expression.location)
                }
            }

            is BundleIdentifierNode -> {
                // Bundle identifiers with one component are treated like normal identifiers
                if (expression.components.size == 1) {
                    return resolveAndLinkExpression(scope, expression.components.first()).also {
                        scope.filePackage.sourcePackage.linkType(expression, it.type)
                    }
                }
                // Type is foo.bar.Baz
                // Resolve foo first, it must be a package
                when (val expectedPackageType = scope.typeLookup[expression.components.first().name]) {
                    is PackageType -> {
                        resolveType(
                            expression.components.subList(1, expression.components.size),
                            expectedPackageType.sourcePackage
                        )?.let { type ->
                            return ResolvedTypeReference(type, expression)
                        }
                    }

                    null -> {
                        expression.reportError(controller) {
                            message("Type '${expression.name}' could not be resolved")
                            highlight("unresolved type", expression.location)
                        }
                    }

                    else -> {
                        expression.reportError(controller) {
                            message("Type '${expression.components.first().name}' is not a package, cannot access sub-types")
                            highlight("not a package", expression.components.first().location)
                        }
                    }
                }
            }

            is CallExpressionNode -> {
                val baseType = resolveAndLinkExpression(scope, expression.base)
                val constraints = expression.arguments.mapNotNull { constraintBuilder.build(baseType.type, it) }
                if (baseType.constraints.isNotEmpty()) {
                    expression.reportError(controller) {
                        message("Cannot have nested constraints")
                        highlight("illegal nested constraint", expression.location)
                    }
                }
                for (constraintInstances in constraints.groupBy { it::class }.values) {
                    if (constraintInstances.size > 1) {
                        expression.reportError(controller) {
                            message("Cannot have multiple constraints of the same type")
                            highlight("first constraint", constraintInstances.first().node.location)
                            for (duplicateConstraints in constraintInstances.drop(1)) {
                                highlight("duplicate constraint", duplicateConstraints.node.location)
                            }
                        }
                    }
                }
                return baseType.copy(constraints = constraints, fullNode = expression)
            }

            is GenericSpecializationNode -> {
                val name = expression.base.let {
                    when (it) {
                        is IdentifierNode -> it.name
                        is BundleIdentifierNode -> it.name
                        else -> null
                    }
                }
                when (name) {
                    "List" -> {
                        if (expression.arguments.size == 1) {
                            return ResolvedTypeReference(
                                type = ListType(
                                    elementType = resolveAndLinkExpression(scope, expression.arguments[0]),
                                    node = expression,
                                ),
                                typeNode = expression.base,
                                fullNode = expression,
                            )
                        } else {
                            expression.reportError(controller) {
                                message("List must have exactly one type argument")
                                highlight("expected one type argument", expression.location)

                                if (expression.arguments.size == 2) {
                                    help("Did you mean to use Map<Key, Value> instead?")
                                }
                            }
                        }
                    }

                    "Map" -> {
                        if (expression.arguments.size == 2) {
                            val (keyTypeNode, valueTypeNode) = expression.arguments
                            val keyType = resolveAndLinkExpression(scope, keyTypeNode)
                            val valueType = resolveAndLinkExpression(scope, valueTypeNode)

                            return ResolvedTypeReference(
                                type = MapType(
                                    keyType = keyType,
                                    valueType = valueType,
                                    node = expression,
                                ),
                                typeNode = expression.base,
                                fullNode = expression,
                            )
                        } else {
                            expression.reportError(controller) {
                                message("Map must have exactly two type arguments")
                                highlight("expected two type arguments", expression.location)

                                if (expression.arguments.size == 1) {
                                    help("Did you mean to use List<Value> instead?")
                                }
                            }
                        }
                    }

                    else -> {
                        expression.reportError(controller) {
                            message("Unsupported generic type")
                            highlight(expression.location)
                            help("Valid generic types are List<Value> and Map<Key, Value>")
                        }
                    }
                }
            }

            is OptionalDeclarationNode -> {
                val baseType = resolveAndLinkExpression(scope, expression.base)
                if (baseType.isOptional) {
                    expression.reportWarning(controller) {
                        message("Type is already optional, ignoring '?'")
                        highlight("already optional", expression.base.location)
                    }
                }
                return baseType.copy(isOptional = true, fullNode = expression)
            }

            is BooleanNode,
            is NumberNode,
            is StringNode,
            -> expression.reportError(controller) {
                message("Cannot use literal value as type")
                highlight("not a type expression", expression.location)
            }

            is ObjectNode,
            is ArrayNode,
            is RangeExpressionNode,
            is WildcardNode,
            -> expression.reportError(controller) {
                message("Invalid type expression")
                highlight("not a type expression", expression.location)
            }
        }

        return ResolvedTypeReference(UnknownType, expression)
    }


    fun resolveType(bundleIdentifierNode: BundleIdentifierNode) = resolveType(bundleIdentifierNode.components)
    private fun resolveType(components: List<IdentifierNode>, start: Package = global): Type? {
        var currentPackage = start
        val iterator = components.listIterator()
        while (iterator.hasNext()) {
            val component = iterator.next()
            when (val resolvedType = currentPackage.resolveType(component)) {
                is PackageType -> {
                    currentPackage = resolvedType.sourcePackage
                }

                null -> {
                    component.reportError(controller) {
                        message("Could not resolve reference '${component.name}'")
                        highlight("unresolved reference", component.location)
                    }
                    return null
                }

                else -> {
                    if (iterator.hasNext()) {
                        // We resolved a non-package type but there are still components left

                        component.reportError(controller) {
                            message("Type '${component.name}' is not a package, cannot access sub-types")
                            highlight("must be a package", component.location)
                        }
                        return null
                    }
                    return resolvedType
                }
            }
        }

        return PackageType(currentPackage)
    }
}
