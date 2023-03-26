package tools.samt.parser

class ASTPrinter private constructor() {
    private fun dump(node: Node): String = buildString {
        append(node.javaClass.simpleName)

        val info = dumpInfo(node)
        if (info != null) {
            append(" $info")
        }

        append(" <${node.location}>")
        append(System.lineSeparator())

        val childDumps: List<String> = buildList {
            iterateChildren(node) { child ->
                add(dump(child))
            }
        }

        childDumps.forEachIndexed { childIndex, child ->
            var firstLine = true
            child.lineSequence().forEach { line ->
                if (!line.isEmpty()) {
                    if (childIndex != childDumps.lastIndex) {
                        if (firstLine) {
                            append("├─$line")
                        } else {
                            append("│ $line")
                        }
                    } else {
                        if (firstLine) {
                            append("└─$line")
                        } else {
                            append("  $line")
                        }
                    }

                    append(System.lineSeparator())
                }

                firstLine = false
            }
        }

        append(System.lineSeparator())
    }

    private fun dumpInfo(node: Node): String? = when (node) {
        is RequestResponseOperationNode -> if (node.isAsync) "async" else null
        is IdentifierNode -> node.name
        is ImportBundleIdentifierNode -> if (node.isWildcard) "wildcard" else null
        is IntegerNode -> node.value.toString()
        is FloatNode -> node.value.toString()
        is BooleanNode -> node.value.toString()
        is StringNode -> "\"${node.value}\""
        else -> null
    }

    private inline fun iterateChildren(node: Node, body: (Node) -> Unit) {
        when (node) {
            is FileNode -> {
                node.imports.forEach(body)
                body(node.packageDeclaration)
                node.statements.forEach(body)
            }

            is TypeImportNode -> {
                body(node.name)
                if (node.alias != null) {
                    body(node.alias)
                }
            }

            is WildcardImportNode -> {
                body(node.name)
            }

            is PackageDeclarationNode -> {
                body(node.name)
            }

            is RecordDeclarationNode -> {
                body(node.name)
                node.extends.forEach(body)
                node.fields.forEach(body)
                node.annotations.forEach(body)
            }

            is RecordFieldNode -> {
                body(node.name)
                body(node.type)
                node.annotations.forEach(body)
            }

            is EnumDeclarationNode -> {
                body(node.name)
                node.values.forEach(body)
                node.annotations.forEach(body)
            }

            is TypeAliasNode -> {
                body(node.name)
                body(node.type)
                node.annotations.forEach(body)
            }

            is ServiceDeclarationNode -> {
                body(node.name)
                node.operations.forEach(body)
                node.annotations.forEach(body)
            }

            is OperationParameterNode -> {
                body(node.name)
                body(node.type)
                node.annotations.forEach(body)
            }

            is RequestResponseOperationNode -> {
                body(node.name)
                node.parameters.forEach(body)
                if (node.returnType != null) {
                    body(node.returnType)
                }
                node.raises.forEach(body)
                node.annotations.forEach(body)
            }

            is OnewayOperationNode -> {
                body(node.name)
                node.parameters.forEach(body)
                node.annotations.forEach(body)
            }

            is ProviderDeclarationNode -> {
                body(node.name)
                node.implements.forEach(body)
                body(node.transport)
            }

            is ProviderImplementsNode -> {
                body(node.serviceName)
                node.serviceOperationNames.forEach(body)
            }

            is ProviderTransportNode -> {
                body(node.protocolName)
                if (node.configuration != null) {
                    body(node.configuration)
                }
            }

            is ConsumerDeclarationNode -> {
                body(node.providerName)
                node.usages.forEach(body)
            }

            is ConsumerUsesNode -> {
                body(node.serviceName)
                node.serviceOperationNames.forEach(body)
            }

            is AnnotationNode -> {
                body(node.name)
                node.arguments.forEach(body)
            }

            is CallExpressionNode -> {
                body(node.base)
                node.arguments.forEach(body)
            }

            is GenericSpecializationNode -> {
                body(node.base)
                node.arguments.forEach(body)
            }

            is OptionalDeclarationNode -> {
                body(node.base)
            }

            is RangeExpressionNode -> {
                body(node.left)
                body(node.right)
            }

            is ObjectNode -> {
                node.fields.forEach(body)
            }

            is ObjectFieldNode -> {
                body(node.name)
                body(node.value)
            }

            is ArrayNode -> {
                node.values.forEach(body)
            }

            is BundleIdentifierNode -> {
                node.components.forEach(body)
            }

            else -> {}
        }
    }

    companion object {
        fun dump(node: Node): String {
            return ASTPrinter().dump(node)
        }
    }
}
