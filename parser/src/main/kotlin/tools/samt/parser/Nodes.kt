package tools.samt.parser

import tools.samt.common.Location
import tools.samt.common.SourceFile

sealed class Node(val location: Location)

sealed interface AnnotatedNode {
    val annotations: List<AnnotationNode>
}

class FileNode(
    location: Location,
    val sourceFile: SourceFile,
    val imports: List<ImportNode>,
    val packageDeclaration: PackageDeclarationNode,
    val statements: List<StatementNode>,
) : Node(location)

sealed class StatementNode(
    location: Location,
) : Node(location)

sealed class NamedDeclarationNode(
    val name: IdentifierNode,
    location: Location,
) : StatementNode(location)

sealed class ImportNode(
    location: Location,
    val name: BundleIdentifierNode,
) : StatementNode(location)

class TypeImportNode(
    location: Location,
    name: BundleIdentifierNode,
    val alias: IdentifierNode?,
) : ImportNode(location, name)

class WildcardImportNode(
    location: Location,
    name: BundleIdentifierNode,
) : ImportNode(location, name)

class PackageDeclarationNode(
    location: Location,
    val name: BundleIdentifierNode,
) : StatementNode(location)

class RecordDeclarationNode(
    location: Location,
    name: IdentifierNode,
    val extends: List<BundleIdentifierNode> = emptyList(),
    val fields: List<RecordFieldNode>,
    override val annotations: List<AnnotationNode>,
) : NamedDeclarationNode(name, location), AnnotatedNode

class RecordFieldNode(
    location: Location,
    val name: IdentifierNode,
    val type: ExpressionNode,
    override val annotations: List<AnnotationNode>,
) : Node(location), AnnotatedNode

class EnumDeclarationNode(
    location: Location,
    name: IdentifierNode,
    val values: List<IdentifierNode>,
    override val annotations: List<AnnotationNode>,
) : NamedDeclarationNode(name, location), AnnotatedNode

class TypeAliasNode(
    location: Location,
    name: IdentifierNode,
    val type: ExpressionNode,
    override val annotations: List<AnnotationNode>,
) : NamedDeclarationNode(name, location), AnnotatedNode

class ServiceDeclarationNode(
    location: Location,
    name: IdentifierNode,
    val operations: List<OperationNode>,
    override val annotations: List<AnnotationNode>,
) : NamedDeclarationNode(name, location), AnnotatedNode

sealed class OperationNode(
    location: Location,
    val name: IdentifierNode,
    val parameters: List<OperationParameterNode>,
    override val annotations: List<AnnotationNode>,
) : Node(location), AnnotatedNode

class OperationParameterNode(
    location: Location,
    val name: IdentifierNode,
    val type: ExpressionNode,
    override val annotations: List<AnnotationNode>,
) : Node(location), AnnotatedNode

class RequestResponseOperationNode(
    location: Location,
    name: IdentifierNode,
    parameters: List<OperationParameterNode>,
    val returnType: ExpressionNode?,
    val raises: List<ExpressionNode>,
    val isAsync: Boolean,
    annotations: List<AnnotationNode>,
) : OperationNode(location, name, parameters, annotations)

class OnewayOperationNode(
    location: Location,
    name: IdentifierNode,
    parameters: List<OperationParameterNode>,
    annotations: List<AnnotationNode>,
) : OperationNode(location, name, parameters, annotations)

class ProviderDeclarationNode(
    location: Location,
    name: IdentifierNode,
    val implements: List<ProviderImplementsNode>,
    val transport: ProviderTransportNode,
) : NamedDeclarationNode(name, location)

class ProviderImplementsNode(
    location: Location,
    val serviceName: BundleIdentifierNode,
    val serviceOperationNames: List<IdentifierNode>,
) : Node(location)

class ProviderTransportNode(
    location: Location,
    val protocolName: IdentifierNode,
    val configuration: ObjectNode?,
) : Node(location)

class ConsumerDeclarationNode(
    location: Location,
    val providerName: BundleIdentifierNode,
    val usages: List<ConsumerUsesNode>,
) : StatementNode(location)

class ConsumerUsesNode(
    location: Location,
    val serviceName: BundleIdentifierNode,
    val serviceOperationNames: List<IdentifierNode>,
) : Node(location)

class AnnotationNode(
    location: Location,
    val name: IdentifierNode,
    val arguments: List<ExpressionNode>,
) : Node(location)

sealed class ExpressionNode(
    location: Location,
) : Node(location)

class CallExpressionNode(
    location: Location,
    val base: ExpressionNode,
    val arguments: List<ExpressionNode>,
) : ExpressionNode(location)

class GenericSpecializationNode(
    location: Location,
    val base: ExpressionNode,
    val arguments: List<ExpressionNode>,
) : ExpressionNode(location)

class OptionalDeclarationNode(
    location: Location,
    val base: ExpressionNode,
) : ExpressionNode(location)

class RangeExpressionNode(
    location: Location,
    val left: ExpressionNode,
    val right: ExpressionNode,
) : ExpressionNode(location)

class ObjectNode(
    location: Location,
    val fields: List<ObjectFieldNode>,
) : ExpressionNode(location)

class ObjectFieldNode(
    location: Location,
    val name: IdentifierNode,
    val value: ExpressionNode,
) : Node(location)

class ArrayNode(
    location: Location,
    val values: List<ExpressionNode>,
) : ExpressionNode(location)

class WildcardNode(
    location: Location,
) : ExpressionNode(location)

class IdentifierNode(
    location: Location,
    val name: String,
) : ExpressionNode(location)

open class BundleIdentifierNode(
    location: Location,
    val components: List<IdentifierNode>,
) : ExpressionNode(location) {
    val name: String
        get() = components.joinToString(".") { it.name }
}

class ImportBundleIdentifierNode(
    location: Location,
    components: List<IdentifierNode>,
    val isWildcard: Boolean,
) : BundleIdentifierNode(location, components)

sealed class NumberNode(
    location: Location,
) : ExpressionNode(location) {
    abstract val value: Number
}

class IntegerNode(
    location: Location,
    override val value: Long,
) : NumberNode(location)

class FloatNode(
    location: Location,
    override val value: Double,
) : NumberNode(location)

class BooleanNode(
    location: Location,
    val value: Boolean,
) : ExpressionNode(location)

class StringNode(
    location: Location,
    val value: String,
) : ExpressionNode(location)
