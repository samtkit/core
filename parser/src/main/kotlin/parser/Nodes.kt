package parser

import common.Location

sealed class Node(val location: Location)

class ProgramNode(
    location: Location,
    val files: List<FileNode>,
) : Node(location)

class FileNode(
    location: Location,
    val imports: List<ImportNode>,
    val packageDeclaration: PackageDeclarationNode,
    val statements: List<StatementNode>,
) : Node(location)

sealed class StatementNode(
    location: Location,
) : Node(location)

sealed class ImportNode(
    location: Location,
) : StatementNode(location)

class TypeImportNode(
    location: Location,
    val name: BundleIdentifierNode,
    val alias: IdentifierNode?,
) : ImportNode(location)

class WildcardImportNode(
    location: Location,
    val name: BundleIdentifierNode,
) : ImportNode(location)

class PackageDeclarationNode(
    location: Location,
    val name: BundleIdentifierNode,
) : StatementNode(location)

class RecordDeclarationNode(
    location: Location,
    val name: IdentifierNode,
    val extends: List<BundleIdentifierNode> = emptyList(),
    val fields: List<RecordFieldNode>,
    val annotations: List<AnnotationNode>,
) : StatementNode(location)

class RecordFieldNode(
    location: Location,
    val name: IdentifierNode,
    val type: ExpressionNode,
    val annotations: List<AnnotationNode>,
) : Node(location)

class EnumDeclarationNode(
    location: Location,
    val name: IdentifierNode,
    val values: List<IdentifierNode>,
    val annotations: List<AnnotationNode>,
) : StatementNode(location)

class TypeAliasNode(
    location: Location,
    val name: IdentifierNode,
    val type: ExpressionNode,
    val annotations: List<AnnotationNode>,
) : StatementNode(location)

class ServiceDeclarationNode(
    location: Location,
    val name: IdentifierNode,
    val operations: List<OperationNode>,
    val annotations: List<AnnotationNode>,
) : StatementNode(location)

sealed class OperationNode(
    location: Location,
    val name: IdentifierNode,
    val parameter: List<OperationParameterNode>,
    val annotations: List<AnnotationNode>,
) : Node(location)

class OperationParameterNode(
    location: Location,
    val name: IdentifierNode,
    val type: ExpressionNode,
    val annotations: List<AnnotationNode>,
) : Node(location)

class RequestResponseOperationNode(
    location: Location,
    name: IdentifierNode,
    arguments: List<OperationParameterNode>,
    val returnType: ExpressionNode?,
    val raises: List<ExpressionNode>,
    val isAsync: Boolean,
    annotations: List<AnnotationNode>,
) : OperationNode(location, name, arguments, annotations)

class OnewayOperationNode(
    location: Location,
    name: IdentifierNode,
    arguments: List<OperationParameterNode>,
    annotations: List<AnnotationNode>,
) : OperationNode(location, name, arguments, annotations)

class ProviderDeclarationNode(
    location: Location,
    val name: IdentifierNode,
    val implements: List<ProviderImplementsNode>,
    val transport: ProviderTransportNode,
) : StatementNode(location)

class ProviderImplementsNode(
    location: Location,
    val serviceName: BundleIdentifierNode,
    val serviceMethodNames: List<IdentifierNode>,
) : Node(location)

class ProviderTransportNode(
    location: Location,
    val protocolName: IdentifierNode,
    val config: ObjectNode?,
) : Node(location)

class ConsumerDeclarationNode(
    location: Location,
    val providerName: BundleIdentifierNode,
    val usages: List<ConsumerUsesNode>,
) : StatementNode(location)

class ConsumerUsesNode(
    location: Location,
    val serviceName: BundleIdentifierNode,
    val serviceMethodNames: List<IdentifierNode>,
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
) : ExpressionNode(location)

class ImportBundleIdentifierNode(
    location: Location,
    components: List<IdentifierNode>,
    val isWildcard: Boolean,
) : BundleIdentifierNode(location, components)

sealed class NumberNode(
    location: Location,
) : ExpressionNode(location)

class IntegerNode(
    location: Location,
    val value: Long,
) : NumberNode(location)

class FloatNode(
    location: Location,
    val value: Double,
) : NumberNode(location)

class BooleanNode(
    location: Location,
    val value: Boolean,
) : ExpressionNode(location)

class StringNode(
    location: Location,
    val value: String,
) : ExpressionNode(location)
