package parser

import common.DiagnosticConsole
import common.FileOffset
import common.Location
import lexer.*

class Parser private constructor(
    tokenStream: Sequence<Token>,
    private val diagnostics: DiagnosticConsole,
) {
    private val tokenStream: Iterator<Token> = tokenStream.iterator()

    private var current: Token? = null
    private var currentStart = FileOffset(charIndex = 0, row = 0, col = 0)
    private var previousEnd = FileOffset(charIndex = 0, row = 0, col = 0)

    private fun parseFile(): FileNode {
        next()
        val start = currentStart

        val imports = mutableListOf<ImportNode>()
        var packageDeclaration: PackageDeclarationNode? = null
        val statements = mutableListOf<StatementNode>()

        while (!isEnd) {
            when (val statement = parseStatement()) {
                is ImportNode -> {
                    if (packageDeclaration != null) {
                        reportError(
                            "Import statements must be placed before the package declaration",
                            statement.location
                        )
                    }
                    imports.add(statement)
                }

                is PackageDeclarationNode -> {
                    if (packageDeclaration != null) {
                        reportError("Cannot have multiple package declarations per file", statement.location)
                    }
                    packageDeclaration = statement
                }

                else -> {
                    if (packageDeclaration == null) {
                        reportError("Expected a package declaration before any other statements", statement.location)
                    }
                    statements.add(statement)
                }
            }
        }

        if (packageDeclaration == null) {
            reportFatalError("Files must have at least one package declaration")
        }

        val location = locationFromStart(start)
        return FileNode(location, imports, packageDeclaration, statements)
    }

    private fun parseStatement(): StatementNode = when (current) {
        is AtSignToken -> {
            val annotations = parseAnnotations()
            when (current) {
                is RecordToken -> parseRecordDeclaration(annotations)
                is EnumToken -> parseEnumDeclaration(annotations)
                is AliasToken -> parseTypeAlias(annotations)
                is ServiceToken -> parseServiceDeclaration(annotations)
                else -> reportFatalError(
                    "Expected declaration with annotation support",
                    Location(currentStart, currentStart)
                )
            }
        }

        is ImportToken -> parseImport()
        is PackageToken -> parsePackageDeclaration()
        is RecordToken -> parseRecordDeclaration()
        is EnumToken -> parseEnumDeclaration()
        is AliasToken -> parseTypeAlias()
        is ServiceToken -> parseServiceDeclaration()
        is ProviderToken -> parseProviderDeclaration()
        is ConsumeToken -> parseConsumerDeclaration()
        else -> reportFatalError("Expected declaration but got $current", Location(currentStart, currentStart))
    }

    private fun parseImport(): ImportNode {
        val start = currentStart
        expect<ImportToken>()

        val importBundleIdentifier = parseImportBundleIdentifier()
        var alias: IdentifierNode? = null

        val startOfAlias = currentStart
        if (skip<AsToken>()) {
            alias = parseIdentifier()
        }

        return if (importBundleIdentifier.isWildcard) {
            if (alias != null) {
                reportError("Wildcard imports cannot have an alias", locationFromStart(startOfAlias))
            }
            WildcardImportNode(locationFromStart(start), importBundleIdentifier)
        } else {
            TypeImportNode(locationFromStart(start), importBundleIdentifier, alias)
        }
    }

    private fun parseImportBundleIdentifier(): ImportBundleIdentifierNode {
        val start = currentStart
        var isWildcard = false
        val components = buildList {
            add(parseIdentifier())

            while (skip<PeriodToken>()) {
                if (skip<AsteriskToken>()) {
                    isWildcard = true
                    break
                }
                add(parseIdentifier())
            }
        }
        val location = locationFromStart(start)
        return ImportBundleIdentifierNode(location, components, isWildcard)
    }

    private fun parseBundleIdentifier(): BundleIdentifierNode {
        val start = currentStart
        val components = buildList {
            add(parseIdentifier())

            while (skip<PeriodToken>()) {
                add(parseIdentifier())
            }
        }

        val location = locationFromStart(start)
        return BundleIdentifierNode(location, components)
    }

    private fun parsePackageDeclaration(): PackageDeclarationNode {
        val start = currentStart
        expect<PackageToken>()
        val name = parseBundleIdentifier()
        return PackageDeclarationNode(locationFromStart(start), name)
    }

    private fun parseRecordDeclaration(annotations: List<AnnotationNode> = emptyList()): RecordDeclarationNode {
        val start = currentStart
        expect<RecordToken>()
        val name = parseIdentifier()
        val extends = if (skip<ExtendsToken>()) {
            parseCommaSeparatedList(::parseBundleIdentifier)
        } else emptyList()

        expect<OpenBraceToken>()
        val fields = buildList {
            while (!skip<CloseBraceToken>()) {
                add(parseRecordField())
            }
        }

        return RecordDeclarationNode(locationFromStart(start), name, extends, fields, annotations)
    }

    private fun parseRecordField(): RecordFieldNode {
        val annotations = parseAnnotations()
        val start = currentStart
        val name = parseIdentifier()
        expect<ColonToken>()
        val type = parseExpression()
        return RecordFieldNode(locationFromStart(start), name, type, annotations)
    }

    private fun parseEnumDeclaration(annotations: List<AnnotationNode> = emptyList()): EnumDeclarationNode {
        val start = currentStart
        expect<EnumToken>()
        val name = parseIdentifier()
        expect<OpenBraceToken>()
        val values = buildList {
            while (!skip<CloseBraceToken>()) {
                add(parseIdentifier())
            }
        }

        return EnumDeclarationNode(locationFromStart(start), name, values, annotations)
    }

    private fun parseTypeAlias(annotations: List<AnnotationNode> = emptyList()): TypeAliasNode {
        val start = currentStart
        expect<AliasToken>()
        val name = parseIdentifier()
        expect<ColonToken>()
        val type = parseExpression()
        return TypeAliasNode(locationFromStart(start), name, type, annotations)
    }

    private fun parseServiceDeclaration(annotations: List<AnnotationNode> = emptyList()): ServiceDeclarationNode {
        val start = currentStart
        expect<ServiceToken>()
        val name = parseIdentifier()
        expect<OpenBraceToken>()
        val operations = buildList {
            while (!skip<CloseBraceToken>()) {
                add(parseOperation())
            }
        }

        return ServiceDeclarationNode(locationFromStart(start), name, operations, annotations)
    }

    private fun parseOperation(): OperationNode {
        val annotations = parseAnnotations()
        val start = currentStart

        var isOneway = false
        var isAsync = false

        if (skip<OnewayToken>()) {
            isOneway = true
        } else if (skip<AsyncToken>()) {
            isAsync = true
        }

        val name = parseIdentifier()

        expect<OpenParenthesisToken>()

        var parameters: List<OperationParameterNode> = emptyList()
        if (!skip<CloseParenthesisToken>()) {
            parameters = parseCommaSeparatedList(::parseOperationParameter)
            expect<CloseParenthesisToken>()
        }

        val returnTypeStart = previousEnd
        val returnType = if (skip<ColonToken>()) {
            val returnType = parseExpression()
            if (isOneway) {
                reportError("Oneway operations cannot have a return type", locationFromStart(returnTypeStart))
            }
            returnType
        } else null

        val raisesStart = previousEnd
        val raisesList = if (skip<RaisesToken>()) {
            val raisesList = parseCommaSeparatedList(::parseExpression)
            if (isOneway) {
                reportError("Oneway operations cannot raise exceptions", locationFromStart(raisesStart))
            }
            raisesList
        } else emptyList()

        return if (isOneway) {
            OnewayOperationNode(locationFromStart(start), name, parameters, annotations)
        } else {
            RequestResponseOperationNode(locationFromStart(start), name, parameters, returnType, raisesList, isAsync, annotations)
        }
    }

    private fun parseOperationParameter(): OperationParameterNode {
        val annotations = parseAnnotations()
        val start = currentStart
        val name = parseIdentifier()
        expect<ColonToken>()
        val type = parseExpression()
        return OperationParameterNode(locationFromStart(start), name, type, annotations)
    }

    private fun parseProviderDeclaration(): ProviderDeclarationNode {
        val start = currentStart
        expect<ProviderToken>()
        val name = parseIdentifier()
        expect<OpenBraceToken>()

        val implements = mutableListOf<ProviderImplementsNode>()
        var transport: ProviderTransportNode? = null

        while (!skip<CloseBraceToken>()) {
            when {
                skip<ImplementsToken>() -> {
                    if (transport != null) {
                        reportError("Provider 'implements' must come before 'transport'", locationFromStart(start))
                    }
                    implements.add(parseProviderImplements())
                }

                skip<TransportToken>() -> {
                    if (transport != null) {
                        reportFatalError("Provider can only have one transport", locationFromStart(start))
                    }
                    transport = parseProviderTransport()
                }

                else -> reportFatalError("Expected 'implements' or 'transport' keyword", locationFromStart(start))
            }
        }

        if (transport == null) {
            reportFatalError("Provider must have a transport", locationFromStart(start))
        }

        return ProviderDeclarationNode(locationFromStart(start), name, implements, transport)
    }

    private fun parseProviderImplements(): ProviderImplementsNode {
        val start = currentStart
        val serviceName = parseBundleIdentifier()
        val serviceMethodNames: List<IdentifierNode>
        if (skip<OpenBraceToken>() && !skip<CloseBraceToken>()) {
            serviceMethodNames = parseCommaSeparatedList(::parseIdentifier)
            expect<CloseBraceToken>()
        } else {
            serviceMethodNames = emptyList()
        }
        return ProviderImplementsNode(locationFromStart(start), serviceName, serviceMethodNames)
    }

    private fun parseProviderTransport(): ProviderTransportNode {
        val start = currentStart
        expect<TransportToken>()
        val protocolName = parseIdentifier()
        val config = if (current is OpenBraceToken) {
            parseObjectNode()
        } else {
            null
        }
        return ProviderTransportNode(locationFromStart(start), protocolName, config)
    }

    private fun parseObjectNode(): ObjectNode {
        val start = currentStart
        expect<OpenBraceToken>()

        val fields: List<ObjectFieldNode>
        if (!skip<CloseBraceToken>()) {
            fields = parseCommaSeparatedList(::parseObjectField)
            expect<CloseBraceToken>()
        } else {
            fields = emptyList()
        }
        return ObjectNode(locationFromStart(start), fields)
    }

    private fun parseObjectField(): ObjectFieldNode {
        val start = currentStart
        val name = parseIdentifier()
        expect<ColonToken>()
        val value = parseExpression()
        return ObjectFieldNode(locationFromStart(start), name, value)
    }

    private fun parseConsumerDeclaration(): ConsumerDeclarationNode {
        val start = currentStart
        expect<ConsumeToken>()
        val providerName = parseBundleIdentifier()
        expect<OpenBraceToken>()
        val usages = buildList {
            while (!skip<CloseBraceToken>()) {
                add(parseConsumerUses())
            }
        }
        return ConsumerDeclarationNode(locationFromStart(start), providerName, usages)
    }

    private fun parseConsumerUses(): ConsumerUsesNode {
        val start = currentStart
        expect<UsesToken>()
        val serviceName = parseBundleIdentifier()
        val serviceMethodNames: List<IdentifierNode>
        if (skip<OpenBraceToken>() && !skip<CloseBraceToken>()) {
            serviceMethodNames = parseCommaSeparatedList(::parseIdentifier)
            expect<CloseBraceToken>()
        } else {
            serviceMethodNames = emptyList()
        }
        return ConsumerUsesNode(locationFromStart(start), serviceName, serviceMethodNames)
    }

    private fun parseExpression(): ExpressionNode {
        TODO()
    }

    private fun parseIdentifier() = expect<IdentifierToken>().let { IdentifierNode(it.location, it.value) }

    private fun parseString() = expect<StringToken>().let { StringNode(it.location, it.value) }

    private fun parseAnnotations(): List<AnnotationNode> = buildList {
        while (current is AtSignToken) {
            add(parseAnnotation())
        }
    }

    private fun parseAnnotation(): AnnotationNode {
        val start = currentStart
        expect<AtSignToken>()
        val name = parseIdentifier()
        expect<OpenParenthesisToken>()
        val arguments: List<ExpressionNode>
        if (!skip<CloseParenthesisToken>()) {
            arguments = parseCommaSeparatedList(::parseExpression)
            expect<CloseParenthesisToken>()
        } else {
            arguments = emptyList()
        }
        return AnnotationNode(locationFromStart(start), name, arguments)
    }

    private inline fun <T : Node> parseCommaSeparatedList(parse: () -> T): List<T> = buildList {
        add(parse())
        while (skip<CommaToken>()) {
            add(parse())
        }
    }

    private fun next() {
        if (current != null) {
            previousEnd = current!!.location.end
        }

        if (tokenStream.hasNext()) {
            current = tokenStream.next()
            currentStart = current!!.location.start
        } else {
            reportFatalError("Unexpected end of file")
        }
    }

    private val isEnd: Boolean
        get() = !tokenStream.hasNext()

    private inline fun <reified T : Token> expectOrNull(): T? {
        val ret = current as? T

        if (!isEnd) {
            next()
        }
        return ret
    }

    private inline fun <reified T : Token> expect(): T {
        val token = expectOrNull<T>()
        if (token != null) {
            return token
        }

        reportFatalError("Expected ${T::class.java.simpleName}")
    }

    private inline fun <reified T : Token> skip(): Boolean {
        if (current !is T) {
            return false
        }
        if (!isEnd) {
            next()
        }
        return true
    }

    private fun reportError(message: String, location: Location? = null) {
        diagnostics.reportError(message, location ?: current!!.location)
    }

    private fun reportFatalError(message: String, location: Location? = null): Nothing {
        diagnostics.reportError(message, location)
        throw ParserException(message)
    }

    private fun locationFromStart(start: FileOffset) = Location(start, previousEnd)

    companion object {
        fun parse(tokenStream: Sequence<Token>, diagnostics: DiagnosticConsole): FileNode {
            return Parser(tokenStream, diagnostics).parseFile()
        }
    }
}
