package tools.samt.parser

import tools.samt.common.DiagnosticConsole
import tools.samt.common.FileOffset
import tools.samt.common.Location
import tools.samt.lexer.*

class Parser private constructor(
    tokenStream: Sequence<Token>,
    private val diagnostics: DiagnosticConsole,
) {
    private val tokenStream: Iterator<Token> = tokenStream.iterator()

    private var current: Token? = null
    private var previous: Token? = null
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
                        reportInfo("Previously declared package here", packageDeclaration.location)
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

        return FileNode(locationFromStart(start), imports, packageDeclaration, statements)
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
                    current!!.location
                )
            }
        }

        is ImportToken -> parseImport()
        is PackageToken -> parsePackageDeclaration()
        is RecordToken -> parseRecordDeclaration()
        is EnumToken -> parseEnumDeclaration()
        is AliasToken -> parseTypeAlias()
        is ServiceToken -> parseServiceDeclaration()
        is ProvideToken -> parseProviderDeclaration()
        is ConsumeToken -> parseConsumerDeclaration()
        else -> reportFatalError("Expected some sort of a declaration", Location(currentStart))
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

        return ImportBundleIdentifierNode(locationFromStart(start), components, isWildcard)
    }

    private fun parseBundleIdentifier(): BundleIdentifierNode {
        val start = currentStart
        val components = buildList {
            add(parseIdentifier())

            while (skip<PeriodToken>()) {
                add(parseIdentifier())
            }
        }

        return BundleIdentifierNode(locationFromStart(start), components)
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

        val fields = if (skip<OpenBraceToken>()) {
            buildList {
                while (!skip<CloseBraceToken>()) {
                    add(parseRecordField())
                }
            }
        } else emptyList()

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
        val values = parseCommaSeparatedTokenTerminatedList<CloseBraceToken, _>(::parseIdentifier)
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

        val parameters = parseCommaSeparatedTokenTerminatedList<CloseParenthesisToken, _>(::parseOperationParameter)

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
            RequestResponseOperationNode(
                locationFromStart(start),
                name,
                parameters,
                returnType,
                raisesList,
                isAsync,
                annotations
            )
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
        expect<ProvideToken>()
        val name = parseIdentifier()
        expect<OpenBraceToken>()

        val implements = mutableListOf<ProviderImplementsNode>()
        var transport: ProviderTransportNode? = null

        while (!skip<CloseBraceToken>()) {
            val statementStart = currentStart
            when {
                check<ImplementsToken>() -> {
                    implements.add(parseProviderImplements())
                }

                check<TransportToken>() -> {
                    val previousDeclaration = transport
                    transport = parseProviderTransport()
                    if (previousDeclaration is ProviderTransportNode) {
                        reportError("Provider can only have one transport declaration", transport.location)
                        reportInfo("Previously declared here", previousDeclaration.location)
                    }
                }

                else -> reportFatalError(
                    "Expected 'implements' or 'transport' but found '${getHumanReadableTokenName(current!!.javaClass.kotlin)}'",
                    current!!.location
                )
            }
        }

        if (transport == null) {
            reportError("Provider is missing a transport declaration", locationFromStart(start))

            // The previously reported error would prevent any semantic checks from ever interacting with
            // this dummy node. This might be implemented in a different manner in the future
            transport =
                ProviderTransportNode(locationFromStart(start), IdentifierNode(locationFromStart(start), "DUMMY"), null)
        }

        return ProviderDeclarationNode(locationFromStart(start), name, implements, transport)
    }

    private fun parseProviderImplements(): ProviderImplementsNode {
        val start = currentStart
        expect<ImplementsToken>()
        val serviceName = parseBundleIdentifier()
        var serviceOperationNames: List<IdentifierNode> = emptyList()
        if (skip<OpenBraceToken>()) {
            serviceOperationNames = parseCommaSeparatedTokenTerminatedList<CloseBraceToken, _>(::parseIdentifier)

            if (serviceOperationNames.isEmpty()) {
                reportError("Expected at least one operation name in the implements clause", locationFromStart(start))
            }
        }

        return ProviderImplementsNode(locationFromStart(start), serviceName, serviceOperationNames)
    }

    private fun parseProviderTransport(): ProviderTransportNode {
        val start = currentStart
        expect<TransportToken>()
        val protocolName = parseIdentifier()
        val config = if (check<OpenBraceToken>()) {
            parseObjectNode()
        } else {
            null
        }
        return ProviderTransportNode(locationFromStart(start), protocolName, config)
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
        var serviceOperationNames: List<IdentifierNode> = emptyList()
        if (skip<OpenBraceToken>()) {
            serviceOperationNames = parseCommaSeparatedTokenTerminatedList<CloseBraceToken, _>(::parseIdentifier)

            if (serviceOperationNames.isEmpty()) {
                reportError("Expected at least one operation name in the uses clause", locationFromStart(start))
            }
        }

        return ConsumerUsesNode(locationFromStart(start), serviceName, serviceOperationNames)
    }

    private fun parseExpression(): ExpressionNode {
        val start = currentStart
        val leftSide = parseCallGenericOptionalExpression()
        if (skip<DoublePeriodToken>()) {
            val rightSide = parseExpression()
            return RangeExpressionNode(locationFromStart(start), leftSide, rightSide)
        }

        return leftSide
    }

    private fun parseCallGenericOptionalExpression(): ExpressionNode {
        val start = currentStart

        var target = parseLiteral()

        while (!isEnd) {
            when {
                skip<OpenParenthesisToken>() -> {
                    val arguments = parseCommaSeparatedTokenTerminatedList<CloseParenthesisToken, _>(::parseExpression)
                    target = CallExpressionNode(locationFromStart(start), target, arguments)
                }

                skip<LessThanSignToken>() -> {
                    val arguments = parseCommaSeparatedTokenTerminatedList<GreaterThanSignToken, _>(::parseExpression)
                    if (arguments.isEmpty()) {
                        reportError("Generic specialization requires at least one argument", locationFromStart(start))
                    }
                    target = GenericSpecializationNode(locationFromStart(start), target, arguments)
                }

                skip<QuestionMarkToken>() -> {
                    target = OptionalDeclarationNode(locationFromStart(start), target)
                }

                else -> break
            }
        }
        return target
    }

    private fun parseLiteral(): ExpressionNode {
        val start = currentStart

        return when {
            skip<IntegerToken>() -> {
                IntegerNode(locationFromStart(start), (previous as IntegerToken).value)
            }

            skip<FloatToken>() -> {
                FloatNode(locationFromStart(start), (previous as FloatToken).value)
            }

            check<IdentifierToken>() -> parseBundleIdentifier()
            check<StringToken>() -> parseString()
            skip<TrueToken>() -> BooleanNode(locationFromStart(start), true)
            skip<FalseToken>() -> BooleanNode(locationFromStart(start), false)

            skip<OpenParenthesisToken>() -> {
                val exp = parseExpression()
                expect<CloseParenthesisToken>()
                exp
            }

            check<OpenBracketToken>() -> parseArrayNode()
            check<OpenBraceToken>() -> parseObjectNode()

            skip<AsteriskToken>() -> WildcardNode(locationFromStart(start))

            else -> {
                reportFatalError("Expected an expression", locationFromStart(start))
            }
        }
    }

    private fun parseObjectNode(): ObjectNode {
        val start = currentStart
        expect<OpenBraceToken>()
        val fields = parseCommaSeparatedTokenTerminatedList<CloseBraceToken, _>(::parseObjectField)
        return ObjectNode(locationFromStart(start), fields)
    }

    private fun parseObjectField(): ObjectFieldNode {
        val start = currentStart
        val name = parseIdentifier()
        expect<ColonToken>()
        val value = parseExpression()
        return ObjectFieldNode(locationFromStart(start), name, value)
    }

    private fun parseArrayNode(): ArrayNode {
        val start = currentStart
        expect<OpenBracketToken>()
        val values = parseCommaSeparatedTokenTerminatedList<CloseBracketToken, _>(::parseExpression)
        return ArrayNode(locationFromStart(start), values)
    }

    private fun parseIdentifier() = expect<IdentifierToken>().let { IdentifierNode(it.location, it.value) }

    private fun parseString() = expect<StringToken>().let { StringNode(it.location, it.value) }

    private fun parseAnnotations(): List<AnnotationNode> = buildList {
        while (check<AtSignToken>()) {
            add(parseAnnotation())
        }
    }

    private fun parseAnnotation(): AnnotationNode {
        val start = currentStart
        expect<AtSignToken>()
        val name = parseIdentifier()
        val arguments = if (skip<OpenParenthesisToken>()) {
            parseCommaSeparatedTokenTerminatedList<CloseParenthesisToken, _>(::parseExpression)
        } else emptyList()
        return AnnotationNode(locationFromStart(start), name, arguments)
    }

    private inline fun <T : Node> parseCommaSeparatedList(parse: () -> T): List<T> = buildList {
        add(parse())
        while (skip<CommaToken>()) {
            add(parse())
        }
    }

    private inline fun <reified E : Token, T : Node> parseCommaSeparatedTokenTerminatedList(parse: () -> T): List<T> {
        if (!skip<E>()) {
            val res = parseCommaSeparatedList(parse)
            expect<E>()
            return res
        }
        return emptyList()
    }

    private fun next() {
        if (current != null) {
            previousEnd = current!!.location.end
        }

        previous = current
        current = tokenStream.next()
        currentStart = current!!.location.start
    }

    private val isEnd: Boolean
        get() = current is EndOfFileToken

    private inline fun <reified T : Token> expectOrNull(): T? {
        val ret = current as? T

        if (ret != null) {
            next()
        }

        return ret
    }

    private inline fun <reified T : Token> expect(): T {
        val token = expectOrNull<T>()
        if (token != null) {
            return token
        }

        val expectedString = getHumanReadableTokenName(T::class)
        val gotString = getHumanReadableTokenName(current!!.javaClass.kotlin)

        if (current is EndOfFileToken) {
            reportFatalError("Expected '${expectedString}' but reached end of file")
        }

        if (T::class == IdentifierToken::class && current is StaticToken) {
            reportFatalError("'${gotString}' is a reserved keyword, did you mean to escape it? (e.g. '^${gotString}')")
        }

        reportFatalError("Expected '${expectedString}' but got '${gotString}'")
    }

    private inline fun <reified T : Token> skip(): Boolean {
        if (current !is T) {
            return false
        }
        if (tokenStream.hasNext()) {
            next()
        }
        return true
    }

    private inline fun <reified T : Token> check(): Boolean {
        return current is T
    }

    private fun reportError(message: String, location: Location? = null) {
        diagnostics.reportError(message, location ?: current!!.location)
    }

    private fun reportInfo(message: String, location: Location? = null) {
        diagnostics.reportInfo(message, location ?: current!!.location)
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
