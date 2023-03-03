# SAMT Grammar

This grammar describes the basic syntax and structure of a SAMT file.
Some language constructs (like comments, eof, newlines, etc) are not specified in the grammar, as they are handled
differently by the parser.
We plan on writing our parser by hand from scratch, using this grammar only as a reference.

Syntactic problems like left-recursion are also not removed from the grammar,
because they are handled in our parsing algorithm.

Semantic concerns and limitations are also not present in the grammar, they are handled either directly
inside the parser or in a subsequent semantic checking step.

## EBNF

The below grammatic still misses some features, it's a WIP.

TODO
- Comments
- Faults / Raises (exception stuff in general)
- Providers
- Consumers
- Custom annotation declarations
- Inheritance / Polymorphism

```ebnf
File = { Statement };

Statement = PackageDeclaration
            | ImportStatement
            | StructDeclaration
            | EnumDeclaration
            | TypeAliasDeclaration
            | ServiceDeclaration;

PackageDeclaration = "package", BundleIdentifier, "{", { Statement }, "}";

ImportStatement = "import", [BundleIdentifier, "from"], BundleIdentifier;

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

Expression = Identifier | Integer | Float | Boolean | String | RegEx | Type | Range | ("(", Expression, ")");

Range = Expression, "..", Expression;

ExpressionList = [ Expression, { ",", Expression } ];

Identifier = Letter, { Letter | Digit };
Integer = Digit, { Digit };
Float = (Digit, { Digit }), ".", (Digit, { Digit });
Boolean = "true" | "false";
Letter = ? A - Z | a - z | _ ?;
Digit = ? 0 - 9 ?;
String = '"', ?utf8 codepoints or escaped special characters?, '"';
RegEx = ?regex expression?;
```
