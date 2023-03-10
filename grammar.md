```ebnf
File = { ImportStatement }, PackageDeclaration, { Statement };

ImportStatement = { Annotation }, "import", BundleIdentifier, [(".", "*") | ("as", Identifier)];

PackageDeclaration = "package", BundleIdentifier;

Statement = StructDeclaration
                            | EnumDeclaration
                            | TypeAliasDeclaration
                            | ServiceDeclaration
                            | ProviderDeclaration
                            | ConsumerDeclaration;

StructDeclaration = { Annotation }, "struct", Identifier, ["extends", BundleIdentifier, {",", BundleIdentifier}], "{", { StructField }, "}";

StructField = { Annotation }, Identifier, ":", Expression;

EnumDeclaration = { Annotation }, "enum", Identifier, "{", [IdentifierList], "}";

TypeAliasDeclaration = { Annotation }, "alias", Identifier, "=", Expression;

ServiceDeclaration = { Annotation }, "service", Identifier, "{", { OperationDeclaration | OnewayOperationDeclaration }, "}";

OperationDeclaration = { Annotation }, [ "async" ], Identifier, "(", ArgumentList, ")", [ ":", Expression ], ["raises", ExpressionList];

OnewayOperationDeclaration = { Annotation }, "oneway", Identifier, "(", ArgumentList, ")";

ArgumentList = [ ArgumentListEntry, { ",", ArgumentListEntry } ];

ArgumentListEntry = Identifier, ":", Expression;

ProviderDeclaration = "provide", Identifier, "{", {ProviderDeclarationStatement}, "}";

ProviderDeclarationStatement = ProviderImplementsStatement
                              | ProviderTransportStatement
                              | ProviderTargetStatement;

ProviderImplementsStatement = "with", BundleIdentifier, ["{", [IdentifierList], "}"];

ProviderTransportStatement = "transport", Identifier, [Object];

ProviderTargetStatement = "target", Identifier, [Object];

ConsumerDeclaration = "consume", BundleIdentifier, "{", {ConsumerDeclarationStatement}, "}";

ConsumerDeclarationStatement = ConsumerUsesStatement
                              | ConsumerTargetStatement;

ConsumerUsesStatement = "uses", BundleIdentifier, ["{", [IdentifierList], "}"];

ConsumerTargetStatement = "target", Identifier, [Object];

Annotation = "@", Identifier, [ "(", [ExpressionList], ")" ];

Expression = BundleIdentifier
            | Number
            | Boolean
            | String
            | RegEx
            | Range
            | Object
            | CallExpression
            | GenericSpecialization
            | OptionalPostOperatorT
            | ("(", Expression, ")");

BundleIdentifier = Identifier, { ".", Identifier};

CallExpression = Expression, "(", [ExpressionList], ")";

GenericSpecialization = Expression, "<", ExpressionList, ">";

OptionalPostOperator = Expression, "?";

Range = Expression, "..", Expression;

Object = "{", [ObjectFieldDeclaration, {",", ObjectFieldDeclaration}] "}";

ObjectFieldDeclaration = Identifier, ":", Expression;

ExpressionList = Expression, { ",", Expression };

IdentifierList = Identifier, { ",", Identifier };

Letter = ? A - Z | a - z | _ ?;
Identifier = Letter, { Letter | Digit };
Number = ["-"], (Integer | Float);
Integer = Digit, { Digit };
Float = (Digit, { Digit }), ".", (Digit, { Digit });
Boolean = "true" | "false";
Digit = ? 0 - 9 ?;
String = '"', ?utf8 codepoints or escaped special characters?, '"';
RegEx = ?regex expression?;
```
