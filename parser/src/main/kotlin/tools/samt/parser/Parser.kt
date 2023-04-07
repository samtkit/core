package tools.samt.parser

import tools.samt.common.*
import tools.samt.lexer.*
import kotlin.reflect.full.isSubclassOf

class Parser private constructor(
    private val source: SourceFile,
    tokenStream: Sequence<Token>,
    private val diagnostic: DiagnosticContext,
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
                        diagnostic.error {
                            message("Unexpected import statement")
                            highlight("must occur before package declaration", statement.location)
                            highlight("no imports allowed after this point", packageDeclaration!!.location)
                            info("Import targets are resolved in the global scope, while anything written after the package declaration is scoped to the package")
                        }

                    }
                    imports.add(statement)
                }

                is PackageDeclarationNode -> {
                    if (packageDeclaration != null) {
                        diagnostic.error {
                            message("Too many package declarations")
                            highlight("extraneous package declaration", statement.location)
                            highlight("initial package declaration", packageDeclaration!!.location)
                        }
                    }
                    packageDeclaration = statement
                }

                else -> {
                    if (packageDeclaration == null) {
                        diagnostic.error {
                            message("Unexpected statement")
                            highlight("must occur after package declaration", statement.location)
                            info("Statements can only be written after the package declaration")
                        }
                    }
                    statements.add(statement)
                }
            }
        }

        if (packageDeclaration == null) {
            diagnostic.fatal {
                message("Missing package declaration")
            }
        }

        return FileNode(locationFromStart(start), source.absolutePath, imports, packageDeclaration, statements)
    }

    private fun parseStatement(): StatementNode = when (current) {
        is AtSignToken -> {
            val annotations = parseAnnotations()
            when (current) {
                is RecordToken -> parseRecordDeclaration(annotations)
                is EnumToken -> parseEnumDeclaration(annotations)
                is AliasToken -> parseTypeAlias(annotations)
                is ServiceToken -> parseServiceDeclaration(annotations)

                else -> {
                    diagnostic.error {
                        message("Statement does not support annotations")
                        highlight(current!!.location, highlightBeginningOnly = true)
                        help("Annotations can be attached to: record, enum, alias, service")
                    }

                    // skip the annotation and try to parse the statement again
                    // this drops the annotation entirely, but it's better
                    // than failing to parse the entire file
                    parseStatement()
                }
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
        else -> diagnostic.fatal {
            message("Unexpected token '${current!!.getHumanReadableName()}', expected a statement")
            highlight(current!!.location)
            info("Valid statements start with: import, package, record, enum, alias, service, provide, consume")
        }
    }

    private fun parseImport(): ImportNode {
        val start = currentStart
        expect<ImportToken>()

        val importBundleIdentifier = parseImportBundleIdentifier()
        var alias: IdentifierNode? = null

        val startBeforeAs = currentStart
        if (skip<AsToken>()) {
            alias = parseIdentifier()
        }

        return if (importBundleIdentifier.isWildcard) {
            if (alias != null) {
                diagnostic.error {
                    message("Malformed import statement")
                    highlight("wildcard import cannot declare an alias", locationFromStart(startBeforeAs))
                }

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
                diagnostic.error {
                    message("Oneway operations cannot have a return type")
                    highlight(locationFromStart(returnTypeStart))
                }
            }
            returnType
        } else null

        val raisesStart = previousEnd
        val raisesList = if (skip<RaisesToken>()) {
            val raisesList = parseCommaSeparatedList(::parseExpression)
            if (isOneway) {
                diagnostic.error {
                    message("Oneway operations cannot raise exceptions")
                    highlight(locationFromStart(raisesStart))
                }
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
            when {
                check<ImplementsToken>() -> {
                    implements.add(parseProviderImplements())
                }

                check<TransportToken>() -> {
                    val previousDeclaration = transport
                    transport = parseProviderTransport()
                    if (previousDeclaration is ProviderTransportNode) {
                        diagnostic.error {
                            message("Too many transport declarations for provider '${name.name}'")
                            highlight("extraneous declaration", transport!!.location, highlightBeginningOnly = true)
                            highlight("previous declaration", previousDeclaration.location, highlightBeginningOnly = true)
                        }

                    }
                }

                else -> diagnostic.fatal {
                    message("Unexpected token '${current!!.getHumanReadableName()}', expected 'implements' or 'transport'")
                    highlight(current!!.location)
                }
            }
        }

        if (transport == null) {
            diagnostic.error {
                message("Provider is missing a transport declaration")
                highlight(locationFromStart(start), highlightBeginningOnly = true)
            }

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
        val braceStartLocation = currentStart
        if (skip<OpenBraceToken>()) {
            serviceOperationNames = parseCommaSeparatedTokenTerminatedList<CloseBraceToken, _>(::parseIdentifier)

            if (serviceOperationNames.isEmpty()) {
                diagnostic.error {
                    message("Expected at least one operation name in the implements clause")
                    highlight("expected at least one operation name", locationFromStart(braceStartLocation))
                    help("If you want to implement all operations, you can omit the braces and the operation names")
                }
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
                diagnostic.error {
                    message("Expected at least one operation name in the uses clause")
                    highlight(locationFromStart(start))
                    info("A valid uses clause looks like 'uses ServiceName { operation1, operation2 }'")
                    help("If you want to use all operations, you can omit the braces and the operation names")
                }
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
            val currentOperationStart = currentStart
            when {
                skip<OpenParenthesisToken>() -> {
                    val arguments = parseCommaSeparatedTokenTerminatedList<CloseParenthesisToken, _>(::parseExpression)
                    target = CallExpressionNode(locationFromStart(start), target, arguments)
                }

                skip<LessThanSignToken>() -> {
                    val arguments = parseCommaSeparatedTokenTerminatedList<GreaterThanSignToken, _>(::parseExpression)
                    if (arguments.isEmpty()) {
                        diagnostic.error {
                            message("Generic specialization requires at least one argument")
                            highlight(locationFromStart(currentOperationStart))
                            info("A valid generic specialization looks like 'List<String>' or 'Map<String, Int>'")
                        }

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
                diagnostic.fatal {
                    message("Expected an expression")
                    highlight(locationFromStart(start), highlightBeginningOnly = true)
                }
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
        get() = check<EndOfFileToken>()

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

        val expectedString = getHumanReadableName<T>()
        val gotString = current!!.getHumanReadableName()

        if (isEnd) {
            diagnostic.fatal {
                message("Expected '${expectedString}' but reached end of file")
                highlight(current!!.location, highlightBeginningOnly = true)
            }
        }

        if (T::class == IdentifierToken::class && check<StaticToken>()) {
            diagnostic.error {
                message("Unescaped identifier '${gotString}'")
                highlight(current!!.location, suggestChange = "^${gotString}")
                help("To use '${gotString}' as an identifier, escape it with a caret: '^${gotString}'")
            }

            // we can continue parsing the file treating the static token as an identifier
            val staticToken = expectOrNull<StaticToken>()
            require(staticToken != null)
            return IdentifierToken(staticToken.location, gotString) as T
        }

        if (T::class.isSubclassOf(StaticToken::class) || T::class.isSubclassOf(StructureToken::class)) {
            diagnostic.fatal {
                message("Unexpected token '${gotString}', expected '${expectedString}'")
                highlight(current!!.location, suggestChange = expectedString)
                help("Replace '${gotString}' with '${expectedString}'")
            }
        } else {
            diagnostic.fatal {
                message("Unexpected token '${gotString}', expected a token of type '${expectedString}'")
                highlight(current!!.location)
            }
        }
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

    private fun locationFromStart(start: FileOffset) = Location(diagnostic.source, start, previousEnd)

    companion object {
        fun parse(source: SourceFile, tokenStream: Sequence<Token>, context: DiagnosticContext): FileNode {
            return Parser(source, tokenStream, context).parseFile()
        }
    }
}
