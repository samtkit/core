package tools.samt.cli

import tools.samt.parser.*

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*

class ASTPrinter private constructor() {
    private fun dump(node: Node): String = buildString {
        val topLevelStyle = (red + bold + underline)
        val expressionStyle = (blue + bold)

        when (node) {
            is ExpressionNode -> append(expressionStyle(node.javaClass.simpleName))
            else -> append(topLevelStyle(node.javaClass.simpleName))
        }

        val info = dumpInfo(node)
        if (info != null) {
            append(" $info")
        }

        append(gray(" <${node.location}>"))
        append(System.lineSeparator())

        val childDumps: List<String> = buildList {
            iterateChildren(node) { child ->
                add(dump(child))
            }
        }

        childDumps.forEachIndexed { childIndex, child ->
            var firstLine = true
            child.lineSequence().forEach { line ->
                if (line.isNotEmpty()) {
                    if (childIndex != childDumps.lastIndex) {
                        if (firstLine) {
                            append("${white("├─")}$line")
                        } else {
                            append("${white("│ ")}$line")
                        }
                    } else {
                        if (firstLine) {
                            append("${white("└─")}$line")
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
        is FileNode -> gray(node.filePath)
        is RequestResponseOperationNode -> if (node.isAsync) red("async") else null
        is IdentifierNode -> yellow(node.name)
        is ImportBundleIdentifierNode -> yellow(node.name) + if (node.isWildcard) yellow(".*") else ""
        is BundleIdentifierNode -> yellow(node.name)
        is IntegerNode -> blue(node.value.toString())
        is FloatNode -> magenta(node.value.toString())
        is BooleanNode -> if (node.value) green("true") else red("false")
        is StringNode -> red("\"${node.value}\"")
        else -> null
    }

    private inline fun iterateChildren(node: Node, block: (Node) -> Unit) {
        when (node) {
            is FileNode -> {
                node.imports.forEach(block)
                block(node.packageDeclaration)
                node.statements.forEach(block)
            }

            is TypeImportNode -> {
                block(node.name)
                if (node.alias != null) {
                    block(node.alias!!)
                }
            }

            is WildcardImportNode -> {
                block(node.name)
            }

            is PackageDeclarationNode -> {
                block(node.name)
            }

            is RecordDeclarationNode -> {
                block(node.name)
                node.extends.forEach(block)
                node.fields.forEach(block)
                node.annotations.forEach(block)
            }

            is RecordFieldNode -> {
                block(node.name)
                block(node.type)
                node.annotations.forEach(block)
            }

            is EnumDeclarationNode -> {
                block(node.name)
                node.values.forEach(block)
                node.annotations.forEach(block)
            }

            is TypeAliasNode -> {
                block(node.name)
                block(node.type)
                node.annotations.forEach(block)
            }

            is ServiceDeclarationNode -> {
                block(node.name)
                node.operations.forEach(block)
                node.annotations.forEach(block)
            }

            is OperationParameterNode -> {
                block(node.name)
                block(node.type)
                node.annotations.forEach(block)
            }

            is RequestResponseOperationNode -> {
                block(node.name)
                node.parameters.forEach(block)
                if (node.returnType != null) {
                    block(node.returnType!!)
                }
                node.raises.forEach(block)
                node.annotations.forEach(block)
            }

            is OnewayOperationNode -> {
                block(node.name)
                node.parameters.forEach(block)
                node.annotations.forEach(block)
            }

            is ProviderDeclarationNode -> {
                block(node.name)
                node.implements.forEach(block)
                block(node.transport)
            }

            is ProviderImplementsNode -> {
                block(node.serviceName)
                node.serviceOperationNames.forEach(block)
            }

            is ProviderTransportNode -> {
                block(node.protocolName)
                if (node.configuration != null) {
                    block(node.configuration!!)
                }
            }

            is ConsumerDeclarationNode -> {
                block(node.providerName)
                node.usages.forEach(block)
            }

            is ConsumerUsesNode -> {
                block(node.serviceName)
                node.serviceOperationNames.forEach(block)
            }

            is AnnotationNode -> {
                block(node.name)
                node.arguments.forEach(block)
            }

            is CallExpressionNode -> {
                block(node.base)
                node.arguments.forEach(block)
            }

            is GenericSpecializationNode -> {
                block(node.base)
                node.arguments.forEach(block)
            }

            is OptionalDeclarationNode -> {
                block(node.base)
            }

            is RangeExpressionNode -> {
                block(node.left)
                block(node.right)
            }

            is ObjectNode -> {
                node.fields.forEach(block)
            }

            is ObjectFieldNode -> {
                block(node.name)
                block(node.value)
            }

            is ArrayNode -> {
                node.values.forEach(block)
            }

            is BundleIdentifierNode -> {
                node.components.forEach(block)
            }

            is WildcardNode,
            is IdentifierNode,
            is IntegerNode,
            is FloatNode,
            is BooleanNode,
            is StringNode -> Unit
        }
    }

    companion object {
        fun dump(node: FileNode): String {
            return ASTPrinter().dump(node)
        }
    }
}
