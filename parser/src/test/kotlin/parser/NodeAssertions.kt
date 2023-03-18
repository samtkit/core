package parser

import kotlin.test.*

inline fun <T : Node> assertNodes(nodes: List<T>, assertBlock: AssertNodesContext<T>.() -> Unit = {}) {
    val context = AssertNodesContext(ArrayDeque(nodes))
    context.assertBlock()
    assertTrue(context.nodes.isEmpty(), "Not all nodes were asserted")
}

inline fun <T : Node> assertNode(node: T, assertBlock: AssertNodesContext<T>.() -> Unit = {}) =
    assertNodes(mutableListOf(node), assertBlock)

data class AssertNodesContext<T>(val nodes: ArrayDeque<T>)
typealias AssertStatementContext = AssertNodesContext<StatementNode>
typealias AssertExpressionContext = AssertNodesContext<ExpressionNode>
typealias AssertAnnotationsContext = AssertNodesContext<AnnotationNode>
typealias AssertImportContext = AssertNodesContext<ImportNode>
typealias AssertOperationContext = AssertNodesContext<OperationNode>
typealias AssertRecordFieldContext = AssertNodesContext<RecordFieldNode>
typealias AssertObjectFieldContext = AssertNodesContext<ObjectFieldNode>
typealias AssertParameterContext = AssertNodesContext<OperationParameterNode>
typealias AssertIdentifierContext = AssertNodesContext<IdentifierNode>

inline fun <reified T : Node> AssertNodesContext<in T>.next(block: T.() -> Unit) {
    assertTrue(
        nodes.isNotEmpty(),
        "Expected node of type ${T::class.simpleName}, but no more nodes were found"
    )
    val node = nodes.removeFirst()
    assertIs<T>(
        node,
        "Expected node of type ${T::class.simpleName}, but got ${node?.javaClass?.simpleName}"
    )
    node.block()
}

// Statements

inline fun AssertStatementContext.record(
    expectedName: String,
    expectedExtends: List<String> = emptyList(),
    expectedAnnotations: AssertAnnotationsContext.() -> Unit = {},
    expectedFields: AssertRecordFieldContext.() -> Unit = {},
) =
    next<RecordDeclarationNode> {
        assertIdentifier(expectedName, name)
        assertNodes<ExpressionNode>(extends) {
            for (expectedExtend in expectedExtends) {
                bundleIdentifier(expectedExtend)
            }
        }
        assertNodes(fields, expectedFields)
        assertNodes(annotations, expectedAnnotations)
    }

inline fun AssertStatementContext.enum(
    expectedName: String,
    expectedAnnotations: AssertAnnotationsContext.() -> Unit = {},
    expectedValues: List<String> = emptyList(),
) =
    next<EnumDeclarationNode> {
        assertIdentifier(expectedName, name)
        assertNodes(annotations) {
            expectedAnnotations()
        }
        assertNodes(values) {
            for (expectedValue in expectedValues) {
                identifier(expectedValue)
            }
        }
    }

inline fun AssertStatementContext.alias(
    expectedName: String,
    expectedAnnotations: AssertAnnotationsContext.() -> Unit = {},
    expectedType: AssertExpressionContext.() -> Unit,
) = next<TypeAliasNode> {
    assertIdentifier(expectedName, name)
    assertNodes(annotations, expectedAnnotations)
    assertNode(type, expectedType)
}

inline fun AssertStatementContext.service(
    expectedName: String,
    expectedAnnotations: AssertAnnotationsContext.() -> Unit = {},
    expectedOperations: AssertOperationContext.() -> Unit = {},
) = next<ServiceDeclarationNode> {
    assertIdentifier(expectedName, name)
    assertNodes(annotations, expectedAnnotations)
    assertNodes(operations, expectedOperations)
}

fun AssertAnnotationsContext.annotation(expectedName: String, expectedArguments: AssertExpressionContext.() -> Unit = {}) = next {
    assertIdentifier(expectedName, name)
    assertNodes(arguments, expectedArguments)
}

// Operations

inline fun AssertOperationContext.requestReplyOperation(
    expectedName: String,
    expectedAnnotations: AssertAnnotationsContext.() -> Unit = {},
    expectedRaises: List<String> = emptyList(),
    expectedParameters: AssertParameterContext.() -> Unit = {},
    expectedIsAsync: Boolean = false,
    hasReturnType: Boolean = true,
    expectedReturnType: (AssertExpressionContext.() -> Unit) = {},
) = next<RequestResponseOperationNode> {
    assertIdentifier(expectedName, name)
    assertNodes(annotations, expectedAnnotations)
    assertNodes(raises) {
        for (expectedRaise in expectedRaises) {
            bundleIdentifier(expectedRaise)
        }
    }
    assertNodes(parameters, expectedParameters)
    if (hasReturnType) {
        assertNotNull(returnType, "Expected return type, but got none")
        assertNode(returnType!!, expectedReturnType)
    } else {
        assertNull(returnType, "Expected no return type, but got one")
    }
    assertEquals(expectedIsAsync, isAsync)
}

inline fun AssertOperationContext.onewayOperation(
    expectedName: String,
    expectedAnnotations: AssertAnnotationsContext.() -> Unit = {},
    expectedParameters: AssertParameterContext.() -> Unit = {},
) = next<OnewayOperationNode> {
    assertIdentifier(expectedName, name)
    assertNodes(annotations, expectedAnnotations)
    assertNodes(parameters, expectedParameters)
}

// Operation Parameters

inline fun AssertParameterContext.parameter(
    expectedName: String,
    expectedAnnotations: AssertAnnotationsContext.() -> Unit = {},
    expectedType: AssertExpressionContext.() -> Unit,
) = next {
    assertIdentifier(expectedName, name)
    assertNodes(annotations, expectedAnnotations)
    assertNode(type, expectedType)
}

// Imports

fun AssertImportContext.typeImport(expectedName: String, expectedAlias: String? = null) = next<TypeImportNode> {
    assertBundleIdentifier(expectedName, name)
    assertIdentifier(expectedAlias, alias)
}

fun AssertImportContext.wildcardImport(expectedName: String) = next<WildcardImportNode> {
    assertBundleIdentifier(expectedName, name)
}

// Identifiers

fun AssertIdentifierContext.identifier(expectedName: String) = next {
    assertIdentifier(expectedName, this)
}

// Expressions

fun AssertExpressionContext.bundleIdentifier(expectedName: String) = next<BundleIdentifierNode> {
    assertBundleIdentifier(expectedName, this)
}

inline fun AssertExpressionContext.callExpression(
    expectedBase: AssertExpressionContext.() -> Unit = {},
    expectedArguments: AssertExpressionContext.() -> Unit = {},
) =
    next<CallExpressionNode> {
        assertNode(base, expectedBase)
        assertNodes(arguments, expectedArguments)
    }

inline fun AssertExpressionContext.rangeExpression(
    expectedLeft: AssertExpressionContext.() -> Unit = {},
    expectedRight: AssertExpressionContext.() -> Unit = {},
) =
    next<RangeExpressionNode> {
        assertNode(left, expectedLeft)
        assertNode(right, expectedRight)
    }

inline fun AssertExpressionContext.optional(expectedBase: AssertExpressionContext.() -> Unit = {}) =
    next<OptionalDeclarationNode> {
        assertNode(base, expectedBase)
    }

fun AssertExpressionContext.integer(expectedValue: Long) = next<IntegerNode> {
    assertEquals(expectedValue, value)
}

fun AssertExpressionContext.float(expectedValue: Double) = next<FloatNode> {
    assertEquals(expectedValue, value)
}

fun AssertExpressionContext.string(expectedValue: String) = next<StringNode> {
    assertEquals(expectedValue, value)
}

fun AssertExpressionContext.boolean(expectedValue: Boolean) = next<BooleanNode> {
    assertEquals(expectedValue, value)
}

fun AssertExpressionContext.wildcard() = next<WildcardNode> {}

inline fun AssertExpressionContext.genericSpecialization(expectedBase: AssertExpressionContext.() -> Unit, expectedArguments: AssertExpressionContext.() -> Unit = {}) =
    next<GenericSpecializationNode> {
        assertNode(base, expectedBase)
        assertNodes(arguments, expectedArguments)
    }

inline fun AssertExpressionContext.array(expectedElements: AssertExpressionContext.() -> Unit = {}) = next<ArrayNode> {
    assertNodes(values, expectedElements)
}

inline fun AssertExpressionContext.objectLiteral(expectedFields: AssertObjectFieldContext.() -> Unit = {}) = next<ObjectNode> {
    assertNodes(fields, expectedFields)
}

fun AssertObjectFieldContext.field(expectedName: String, expectedValue: AssertExpressionContext.() -> Unit = {}) = next {
    assertIdentifier(expectedName, name)
    assertNode(value, expectedValue)
}

// Record Fields

inline fun AssertRecordFieldContext.field(
    expectedName: String,
    expectedAnnotations: AssertAnnotationsContext.() -> Unit = {},
    expectedType: AssertExpressionContext.() -> Unit = {},
) =
    next {
        assertIdentifier(expectedName, name)
        assertNode(type) {
            expectedType()
        }
        assertNodes(annotations) {
            expectedAnnotations()
        }
    }
