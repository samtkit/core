package tools.samt.parser

import tools.samt.common.Location
import tools.samt.common.SourceFile

sealed interface Node {
    val location: Location
}

sealed interface AnnotatedNode : Node {
    val annotations: List<AnnotationNode>
}

class FileNode(
    override val location: Location,
    val sourceFile: SourceFile,
    val imports: List<ImportNode>,
    val packageDeclaration: PackageDeclarationNode,
    val statements: List<StatementNode>,
) : Node

sealed interface StatementNode : Node

sealed interface NamedDeclarationNode : StatementNode {
    val name: IdentifierNode
}

sealed interface ImportNode : StatementNode {
    val name: BundleIdentifierNode
}

class TypeImportNode(
    override val location: Location,
    override val name: BundleIdentifierNode,
    val alias: IdentifierNode?,
) : ImportNode

class WildcardImportNode(
    override val location: Location,
    override val name: BundleIdentifierNode,
) : ImportNode

class PackageDeclarationNode(
    override val location: Location,
    val name: BundleIdentifierNode,
) : StatementNode

class RecordDeclarationNode(
    override val location: Location,
    override val name: IdentifierNode,
    val extends: List<BundleIdentifierNode> = emptyList(),
    val fields: List<RecordFieldNode>,
    override val annotations: List<AnnotationNode>,
) : NamedDeclarationNode, AnnotatedNode

class RecordFieldNode(
    override val location: Location,
    val name: IdentifierNode,
    val type: ExpressionNode,
    override val annotations: List<AnnotationNode>,
) : Node, AnnotatedNode

class EnumDeclarationNode(
    override val location: Location,
    override val name: IdentifierNode,
    val values: List<IdentifierNode>,
    override val annotations: List<AnnotationNode>,
) : NamedDeclarationNode, AnnotatedNode

class TypeAliasNode(
    override val location: Location,
    override val name: IdentifierNode,
    val type: ExpressionNode,
    override val annotations: List<AnnotationNode>,
) : NamedDeclarationNode, AnnotatedNode

class ServiceDeclarationNode(
    override val location: Location,
    override val name: IdentifierNode,
    val operations: List<OperationNode>,
    override val annotations: List<AnnotationNode>,
) : NamedDeclarationNode, AnnotatedNode

sealed interface OperationNode : Node, AnnotatedNode {
    val name: IdentifierNode
    val parameters: List<OperationParameterNode>
}

class OperationParameterNode(
    override val location: Location,
    val name: IdentifierNode,
    val type: ExpressionNode,
    override val annotations: List<AnnotationNode>,
) : Node, AnnotatedNode

class RequestResponseOperationNode(
    override val location: Location,
    override val name: IdentifierNode,
    override val parameters: List<OperationParameterNode>,
    val returnType: ExpressionNode?,
    val raises: List<ExpressionNode>,
    val isAsync: Boolean,
    override val annotations: List<AnnotationNode>,
) : OperationNode

class OnewayOperationNode(
    override val location: Location,
    override val name: IdentifierNode,
    override val parameters: List<OperationParameterNode>,
    override val annotations: List<AnnotationNode>,
) : OperationNode

class ProviderDeclarationNode(
    override val location: Location,
    override val name: IdentifierNode,
    val implements: List<ProviderImplementsNode>,
    val transport: ProviderTransportNode,
) : NamedDeclarationNode

class ProviderImplementsNode(
    override val location: Location,
    val serviceName: BundleIdentifierNode,
    val serviceOperationNames: List<IdentifierNode>,
) : Node

class ProviderTransportNode(
    override val location: Location,
    val protocolName: IdentifierNode,
    val configuration: ObjectNode?,
) : Node

class ConsumerDeclarationNode(
    override val location: Location,
    val providerName: BundleIdentifierNode,
    val usages: List<ConsumerUsesNode>,
) : StatementNode

class ConsumerUsesNode(
    override val location: Location,
    val serviceName: BundleIdentifierNode,
    val serviceOperationNames: List<IdentifierNode>,
) : Node

class AnnotationNode(
    override val location: Location,
    val name: IdentifierNode,
    val arguments: List<ExpressionNode>,
) : Node

sealed interface ExpressionNode : Node

class CallExpressionNode(
    override val location: Location,
    val base: ExpressionNode,
    val arguments: List<ExpressionNode>,
) : ExpressionNode

class GenericSpecializationNode(
    override val location: Location,
    val base: ExpressionNode,
    val arguments: List<ExpressionNode>,
) : ExpressionNode

class OptionalDeclarationNode(
    override val location: Location,
    val base: ExpressionNode,
) : ExpressionNode

class RangeExpressionNode(
    override val location: Location,
    val left: ExpressionNode,
    val right: ExpressionNode,
) : ExpressionNode

class ObjectNode(
    override val location: Location,
    val fields: List<ObjectFieldNode>,
) : ExpressionNode

class ObjectFieldNode(
    override val location: Location,
    val name: IdentifierNode,
    val value: ExpressionNode,
) : Node

class ArrayNode(
    override val location: Location,
    val values: List<ExpressionNode>,
) : ExpressionNode

class WildcardNode(
    override val location: Location,
) : ExpressionNode

class IdentifierNode(
    override val location: Location,
    val name: String,
) : ExpressionNode

open class BundleIdentifierNode(
    override val location: Location,
    val components: List<IdentifierNode>,
) : ExpressionNode {
    val name: String
        get() = components.joinToString(".") { it.name }
}

class ImportBundleIdentifierNode(
    location: Location,
    components: List<IdentifierNode>,
    val isWildcard: Boolean,
) : BundleIdentifierNode(location, components)

sealed interface NumberNode : ExpressionNode {
    val value: Number
}

class IntegerNode(
    override val location: Location,
    override val value: Long,
) : NumberNode

class FloatNode(
    override val location: Location,
    override val value: Double,
) : NumberNode

class BooleanNode(
    override val location: Location,
    val value: Boolean,
) : ExpressionNode

class StringNode(
    override val location: Location,
    val value: String,
) : ExpressionNode
