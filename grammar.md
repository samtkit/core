File = ImportList, PackageDeclaration, { Statement };

ImportList = { ImportStatement };

ImportStatement = "import", BundleIdentifier, [(".", "*") | ("as", Identifier)];

PackageDeclaration = "package", BundleIdentifier;

Statement = StructDeclaration
            | EnumDeclaration
            | TypeAliasDeclaration
            | ServiceDeclaration;

StructDeclaration = "struct", Identifier, ["extends", BundleIdentifier, {"and", BundleIdentifier}], "{", { StructField }, "}";

StructField = { Annotation }, Identifier, ":", Type;

EnumDeclaration = "enum", Identifier, "{", { Identifier }, "}";

TypeAliasDeclaration = "alias", Identifier, "=", Type;

ServiceDeclaration = { Annotation }, "service", Identifier, "{", { OperationDeclaration | OnewayOperationDeclaration }, "}";

OperationDeclaration = { Annotation }, [ "async" ], Identifier, "(", ArgumentList, ")", [ ":", Type ];

OnewayOperationDeclaration = { Annotation }, "oneway", Identifier, "(", ArgumentList, ")";

ArgumentList = [ ArgumentListEntry, { ",", ArgumentListEntry } ];

ArgumentListEntry = Identifier, ":", Type;

Annotation = "@", Identifier, [ "(", ExpressionList, ")" ];

Type = BundleIdentifier, [ GenericSpecialization ], [ Constraint ], [ "?" ];

GenericSpecialization = "<", Type, { ",", Type }, ">";

Constraint = "(", Expression, { ",", Expression }, ")";

BundleIdentifier = Identifier, { ".", Identifier};

Expression = Identifier | Number | Boolean | String | RegEx | Type | Range | ("(", Expression, ")");

Range = Expression, "..", Expression;

ExpressionList = [ Expression, { ",", Expression } ];

Letter = ? A - Z | a - z | _ ?;
Identifier = Letter, { Letter | Digit };
Number = ["-"], (Integer | Float);
Integer = Digit, { Digit };
Float = (Digit, { Digit }), ".", (Digit, { Digit });
Boolean = "true" | "false";
Digit = ? 0 - 9 ?;
String = '"', ?utf8 codepoints or escaped special characters?, '"';
RegEx = ?regex expression?;
