package tools.samt.semantic

import org.junit.jupiter.api.Nested
import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SemanticModelTest {

    @Nested
    inner class UniquenessChecks {
        @Test
        fun `cannot have duplicate enum value`() {
            val source = """
                package color

                enum Color {
                    Red,
                    Green,
                    Blue,
                    Red
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Enum value 'Red' is defined more than once"))
        }

        @Test
        fun `cannot have duplicate record field`() {
            val source = """
                package color

                record Color {
                    red: Int
                    green: Int
                    blue: Int
                    red: Int
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Record field 'red' is defined more than once"))
        }

        @Test
        fun `cannot have duplicate service operation`() {
            val source = """
                package color

                record Color

                service ColorService {
                    get(): Color
                    set(color: Color)
                    get()
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Operation 'get' is defined more than once"))
        }

        @Test
        fun `cannot have duplicate declarations with same name`() {
            val source = """
                package color

                record A
                record A

                record B
                enum B { }

                service C { }
                service D { }
                provide C {
                    implements D
                    transport HTTP
                }

                enum E { }
                typealias E = Int
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: 'A' is already declared",
                    "Error: 'B' is already declared",
                    "Error: 'C' is already declared",
                    "Error: 'E' is already declared",
                )
            )
        }

        @Test
        fun `cannot import same type multiple times`() {
            val foo = """
                import bar.B
                import bar.*

                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo.A
                import foo.A

                package bar

                record B
            """.trimIndent()
            parseAndCheck(
                foo to listOf(
                    "Error: Import 'B' conflicts with locally defined type with same name"
                ),
                bar to listOf(
                    "Error: Import 'A' conflicts with locally defined type with same name"
                ),
            )
        }

        @Test
        fun `cannot import type which is defined locally`() {
            val foo = """
                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo.A

                package bar

                enum A { }
            """.trimIndent()
            parseAndCheck(
                foo to emptyList(),
                bar to listOf(
                    "Error: Import 'A' conflicts with locally defined type with same name"
                ),
            )
        }

        @Test
        fun `can import type with same name if using import alias`() {
            val foo = """
                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo.A as OtherA

                package bar

                record A {
                    a: OtherA
                }
            """.trimIndent()
            parseAndCheck(
                foo to emptyList(),
                bar to emptyList(),
            )
        }

        @Test
        fun `can reference type with same name via package`() {
            val foo = """
                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo as fooAlias

                package bar

                record A {
                    a1: foo.A
                    a2: fooAlias.A
                }
            """.trimIndent()
            parseAndCheck(
                foo to emptyList(),
                bar to emptyList(),
            )
        }

        @Test
        fun `cannot import type through wildcard which is defined locally`() {
            val foo = """
                package foo

                record A
            """.trimIndent()
            val bar = """
                import foo.*

                package bar

                enum A { }
            """.trimIndent()
            parseAndCheck(
                foo to emptyList(),
                bar to listOf(
                    "Error: Import 'A' conflicts with locally defined type with same name"
                ),
            )
        }

        @Test
        fun `warn about user overriding built-in type`() {
            val source = """
                package foo

                record String
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Type 'String' shadows built-in type with same name"),
            )
        }
    }

    @Nested
    inner class OptionalTypes {
        @Test
        fun `warn about duplicate nullability`() {
            val source = """
                package color

                record Color {
                    red: Int?
                    green: Int??
                    blue: Int?
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Warning: Type is already optional, ignoring '?'"))
        }

        @Test
        fun `warn about indirect duplicate nullability`() {
            val source = """
                package color

                record Color {
                    red: Int? (range(1..100))?
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Warning: Type is already optional, ignoring '?'"))
        }
    }

    @Nested
    inner class Constraints {
        @Test
        fun `cannot use constraints on bad base type`() {
            val source = """
                package complex

                record Complex {
                    int: Int? (pattern("a-z"))
                    list: List<String> (pattern("a-z"))
                    int2: Int (size(1..100))
                    string: String (value(42))
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Constraint 'pattern(a-z)' is not allowed for type 'Int'",
                    "Error: Constraint 'pattern(a-z)' is not allowed for type 'List<String>'",
                    "Error: Constraint 'size(1..100)' is not allowed for type 'Int'",
                    "Error: Constraint 'value(42)' is not allowed for type 'String'",
                )
            )
        }

        @Test
        fun `cannot use same constraint multiple times`() {
            val source = """
                package tooManyConstraints

                record Complex {
                    string: String (pattern("a-z"), pattern("A-Z"))
                    float: Float (1..100, range(1.5..*))
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Cannot have multiple constraints of the same type",
                    "Error: Cannot have multiple constraints of the same type",
                )
            )
        }

        @Test
        fun `range must be valid`() {
            val source = """
                package complex

                record Complex {
                    int: Int (range(1 .. "2"))
                }

                service Foo {
                    bar(value: Float (range(* .. *)))
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Range constraint argument must be a valid number range",
                    "Error: Range constraint must have at least one valid number",
                )
            )
        }

        @Test
        fun `size must be valid`() {
            val source = """
                package complex

                record Complex {
                    list1: List<Int> (size(1..5.5))
                    list2: List<Int> (size(-10..*))
                    list3: List<Int> (size(0..-10))
                    list4: List<Int> (size(*..100))
                }

                service Foo {
                    bar(): String (size(5..4))
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Expected size constraint argument to be a positive whole number or wildcard",
                    "Error: Size constraint lower bound must be greater than or equal to 0",
                    "Error: Size constraint upper bound must be greater than or equal to 0",
                    "Warning: Size constraint lower bound should be '0' instead of '*' to avoid confusion",
                    "Error: Size constraint lower bound must be lower than or equal to the upper bound",
                )
            )
        }

        @Test
        fun `pattern must be valid`() {
            val source = """
                package complex

                record Foo {
                    name: String (pattern("fo/+++!hi"))
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Invalid regex pattern: 'Dangling meta character '+' near index 5${System.lineSeparator()}" +
                            "fo/+++!hi${System.lineSeparator()}" +
                            "     ^'"
                )
            )
        }

        @Test
        fun `cannot use non-existent constraints`() {
            val source = """
                package color

                record Color {
                    red: Int (max(100))
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Constraint with name 'max' does not exist"))
        }

        @Test
        fun `can use shorthand constraints`() {
            val source = """
                package color

                record Color {
                    red: Int (100)
                }
            """.trimIndent()
            parseAndCheck(source to emptyList())
        }

        @Test
        fun `cannot nest constraints`() {
            val source = """
                package color

                record Color {
                    red: Int (range(1..100)) (range(1..*))
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Cannot have nested constraints"))
        }

        @Test
        fun `cannot illegal arguments to constraints`() {
            val range = """
                package range

                record Range {
                    foo: Int? (range(1.."100"))
                }
            """.trimIndent()
            val size = """
                package size

                record Size {
                    foo: String (size(1, 100))
                }
            """.trimIndent()
            val pattern = """
                package pattern

                record Pattern {
                    foo: String (size(1..100), pattern("a", "-", "z"))
                }
            """.trimIndent()
            parseAndCheck(
                range to listOf("Error: Range constraint argument must be a valid number range"),
                size to listOf("Error: Size constraint must have exactly one size argument"),
                pattern to listOf("Error: Pattern constraint must have exactly one string argument"),
            )
        }
    }

    @Nested
    inner class Generics {
        @Test
        fun `can use nested generics in lists`() {
            val source = """
                package lists

                record Lists {
                    lists: List<List<Int> (size(1..100))> (1..*)
                }
            """.trimIndent()
            parseAndCheck(source to emptyList())
        }

        @Test
        fun `can use nested generics in map`() {
            val source = """
                package maps

                record Maps {
                    maps: Map<String, Map<String (pattern("a-z")), List<Int>>> (1..*)
                }
            """.trimIndent()
            parseAndCheck(source to emptyList())
        }

        @Test
        fun `cannot use unknown generics in map`() {
            val source = """
                package foo

                record Foo {
                    foo: Foo<String>
                    badOptionalList: List?<String>
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Unsupported generic type", "Error: Unsupported generic type"))
        }

        @Test
        fun `cannot pass too many or too few generic arguments`() {
            val source = """
                package foo

                record Foo {
                    twoArgList: List<Int, String>
                    oneArgMap: Map<String>
                    threeArgMap: Map<String, String, String>
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: List must have exactly one type argument",
                    "Error: Map must have exactly two type arguments",
                    "Error: Map must have exactly two type arguments",
                )
            )
        }

        @Test
        fun `cannot use non-string types as keys in maps`() {
            val source = """
                package foo

                record Foo {
                    arg: Map<Int, String>
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Map key type must be String",
                )
            )
        }

        @Test
        fun `can use typealiases of String as keys in maps`() {
            val source = """
                package foo

                typealias StringAlias = String
                typealias StringAliasAlias = StringAlias

                record Foo {
                    arg: Map<StringAliasAlias, String>
                }
            """.trimIndent()
            parseAndCheck(source to emptyList())
        }
    }

    @Nested
    inner class InvalidTypeExpression {
        @Test
        fun `cannot use literal value as type`() {
            val source = """
                package color

                record Color {
                    value: 42
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Cannot use literal value as type"))
        }

        @Test
        fun `cannot use object value as type`() {
            val source = """
                package color

                record Color {
                    value: { red: Int, green: Int, blue: Int }
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Invalid type expression"))
        }

        @Test
        fun `cannot use array value as type`() {
            val source = """
                package color

                record Color {
                    value: [Int]
                }
            """.trimIndent()
            parseAndCheck(source to listOf("Error: Invalid type expression"))
        }
    }

    @Nested
    inner class Aliases {

        @Test
        fun `can use type aliases`() {
            val source = """
                package color

                typealias UShort = Int (0..256)

                record Color {
                    r: UShort
                    g: UShort
                    b: UShort
                }
            """.trimIndent()
            parseAndCheck(
                source to emptyList()
            )
        }

        @Test
        fun `can use service alias`() {
            val source = """
                package color

                typealias Foo = MyService

                service MyService {
                    foo(): String
                }

                provide MyEndpoint {
                    implements Foo { foo }
                    
                    transport HTTP
                }
            """.trimIndent()
            parseAndCheck(
                source to emptyList()
            )
        }

        @Test
        fun `cannot use service alias in type model`() {
            val source = """
                package color

                typealias Foo = MyService

                service MyService {
                    foo(): Foo
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Type alias refers to 'MyService', which cannot be used in this context")
            )
        }

        @Test
        fun `cannot use package in alias`() {
            val source = """
                package color

                typealias myColor = color
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Type alias cannot reference package")
            )
        }

        @Test
        fun `duplicate optional markers within alias definitions are reported`() {
            val source = """
                package people

                typealias OptionalName = String? ("a-z")
                typealias OptionalDeepName = OptionalName
                typealias OptionalDeeperName = OptionalDeepName?
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Warning: Type is already optional, ignoring '?'",
                )
            )
        }

        @Test
        fun `duplicate optional markers when referencing alias types are reported`() {
            val source = """
                package people

                typealias OptionalName = String? ("a-z")
                typealias OptionalDeepName = OptionalName

                record Human {
                    firstName: OptionalName
                    lastName: OptionalName?
                    middleName: OptionalDeepName?
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Warning: Type alias refers to type which is already optional, ignoring '?'",
                    "Warning: Type alias refers to type which is already optional, ignoring '?'",
                )
            )
        }

        @Test
        fun `cannot use type aliases with cyclic references`() {
            val source = """
                package color

                typealias A = Int
                typealias B = A // Int
                typealias C = B // Int
                typealias D = F // Cycle!
                typealias E = D // Cycle!
                typealias F = E // Cycle!
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Could not resolve type alias 'D', are there circular references?",
                    "Error: Could not resolve type alias 'E', are there circular references?",
                    "Error: Could not resolve type alias 'F', are there circular references?",
                )
            )
        }
    }

    @Nested
    inner class NotImplementedFeatures {
        @Test
        fun `cannot use extends keyword`() {
            val source = """
                package color

                record Color extends Int
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Record extends are not yet supported")
            )
        }
    }

    @Nested
    inner class PostProcessor {
        @Test
        fun `cannot use service within record fields`() {
            val source = """
                package color

                service ColorService {
                    get(): Int
                }

                record Color {
                    ^service: ColorService
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Cannot use service 'ColorService' as type")
            )
        }

        @Test
        fun `cannot use record within consumer`() {
            val source = """
                package color

                service ColorService {
                    get(): Int
                }

                record Color {
                    value: String
                }

                provide FooEndpoint {
                    implements ColorService
                    implements Color
                    transport HTTP
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Expected a service but got 'Color'")
            )
        }

        @Test
        fun `cannot provide operations which do not exist in service`() {
            val source = """
                package color

                service ColorService {
                    get(): Int
                }

                provide FooEndpoint {
                    implements ColorService { get, post }
                    transport HTTP
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf("Error: Operation 'post' not found in service 'ColorService'")
            )
        }

        @Test
        fun `cannot use operations which do not exist in service or are not implemented by provider`() {
            val source = """
                package color

                service ColorService {
                    get(): Int
                    post()
                }

                service FooService {
                    bar(): String
                    baz(arg: Float)
                }

                provide FooEndpoint {
                    implements ColorService { get }
                    implements FooService
                    transport HTTP
                }

                consume FooEndpoint {
                    uses ColorService { get, post, put }
                    uses FooService { bar, baz, qux }
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf(
                    "Error: Operation 'post' in service 'ColorService' is not implemented by provider 'FooEndpoint'",
                    "Error: Operation 'put' not found in service 'ColorService'",
                    "Error: Operation 'qux' not found in service 'FooService'",
                )
            )
        }

        @Test
        fun `can use and implement operations which do exist in service`() {
            val consumer = """
                import providers.FooEndpoint as Provider
                import services.FooService as Service

                package consumers

                consume Provider {
                    uses Service { foo }
                }
            """.trimIndent()
            val provider = """
                import services.*
                package providers

                provide FooEndpoint {
                    implements FooService
                    transport HTTP
                }
            """.trimIndent()
            val service = """
                package services

                service FooService {
                    foo(): String
                    baz(arg: Float)
                }
            """.trimIndent()
            parseAndCheck(
                consumer to emptyList(),
                provider to emptyList(),
                service to emptyList(),
            )
        }

        @Test
        fun `cannot use or implement same service multiple times`() {
            val consumer = """
                import providers.FooEndpoint as Provider
                import services.FooService as Service

                package consumers

                consume Provider {
                    uses Service { foo }
                    uses services.FooService
                }
            """.trimIndent()
            val provider = """
                import services.*
                package providers

                provide FooEndpoint {
                    implements FooService
                    implements FooService
                    transport HTTP
                }
            """.trimIndent()
            val service = """
                package services

                service FooService {
                    oneway foo()
                }
            """.trimIndent()
            parseAndCheck(
                consumer to listOf("Error: Service 'FooService' already used"),
                provider to listOf("Error: Service 'FooService' already implemented"),
                service to emptyList(),
            )
        }

        @Test
        fun `cannot have cyclic records`() {
            val source = """
                package cycles

                record Recursive {
                    recursive: Recursive
                }

                record IndirectA {
                    b: IndirectB
                }

                record IndirectB {
                    a: IndirectA
                }
                
                record ReferencesAll {
                    r: Recursive
                    a: IndirectA
                    b: IndirectB
                }
            """.trimIndent()
            parseAndCheck(
                source to List(3) { "Error: Required record fields must not be cyclical, because they cannot be serialized" }
            )
        }

        @Test
        fun `cannot have cyclic records with typealiases`() {
            val source = """
                package cycles

                record A {
                    b: B
                }
                
                record B {
                    c: C
                }

                typealias C = A
            """.trimIndent()
            parseAndCheck(
                source to List(2) { "Error: Required record fields must not be cyclical, because they cannot be serialized" }
            )
        }

        @Test
        fun `can have List or Map of same type`() {
            val source = """
                package cycles

                record A {
                    children: List<A>
                    childrenByName: Map<String, A>
                }
            """.trimIndent()
            parseAndCheck(
                source to emptyList()
            )
        }

        @Test
        fun `cycle with optional type is warning`() {
            val source = """
                package cycles

                record A {
                    b: B?
                }
                
                record B {
                    a: A
                }
                
                record Recursive {
                    recursive: R
                }
                
                typealias R = Recursive?
            """.trimIndent()
            parseAndCheck(
                source to List(3) { "Warning: Record fields should not be cyclical, because they might not be serializable" }
            )
        }
    }

    @Nested
    inner class Annotations {
        @Test
        fun `can retrieve descriptions`() {
            val source = """
                package annotations
                
                @Description("enum description")
                enum UserType {
                    ADMIN, USER
                }
                
                @Description("typealias description")
                typealias Id = Long(1..*)
                
                @Description("record description")
                record User {
                    @Description("field description")
                    id: Id
                }
                
                @Description("service description")
                service UserService {
                    @Description("operation description")
                    get(@Description("parameter description") id: Id): User
                }
            """.trimIndent()
            val model = parseAndCheck(
                source to emptyList()
            )
            val samtPackage = model.global.subPackages.single()
            val metadata = model.userMetadata
            assertEquals("enum description", metadata.getDescription(samtPackage.enums.single()))
            assertEquals("typealias description", metadata.getDescription(samtPackage.aliases.single()))
            val record = samtPackage.records.single()
            assertEquals("record description", metadata.getDescription(record))
            assertEquals("field description", metadata.getDescription(record.fields.single()))
            val service = samtPackage.services.single()
            assertEquals("service description", metadata.getDescription(service))
            val operation = service.operations.single()
            assertEquals("operation description", metadata.getDescription(operation))
            val parameter = operation.parameters.single()
            assertEquals("parameter description", metadata.getDescription(parameter))
        }

        @Test
        fun `can retrieve deprecations`() {
            val source = """
                package annotations
                
                @Deprecated("enum deprecation")
                enum UserType {
                    ADMIN, USER
                }
                
                @Deprecated("typealias deprecation")
                typealias Id = Long(1..*)
                
                @Deprecated("record deprecation")
                record User {
                    @Deprecated("field description")
                    id: Id
                    @Deprecated
                    type: UserType
                }
                
                @Deprecated("service deprecation")
                service UserService {
                    @Deprecated("operation deprecation")
                    async get(@Deprecated("parameter deprecation") id: Id): User
                }
            """.trimIndent()
            val model = parseAndCheck(
                source to emptyList()
            )
            val samtPackage = model.global.subPackages.single()
            val metadata = model.userMetadata
            assertEquals(UserMetadata.Deprecation("enum deprecation"), metadata.getDeprecation(samtPackage.enums.single()))
            assertEquals(UserMetadata.Deprecation("typealias deprecation"), metadata.getDeprecation(samtPackage.aliases.single()))
            val record = samtPackage.records.single()
            assertEquals(UserMetadata.Deprecation("record deprecation"), metadata.getDeprecation(record))
            assertEquals(listOf(UserMetadata.Deprecation("field description"), UserMetadata.Deprecation(null)), record.fields.map { metadata.getDeprecation(it) })
            val service = samtPackage.services.single()
            assertEquals(UserMetadata.Deprecation("service deprecation"), metadata.getDeprecation(service))
            val operation = service.operations.single()
            assertEquals(UserMetadata.Deprecation("operation deprecation"), metadata.getDeprecation(operation))
            val parameter = operation.parameters.single()
            assertEquals(UserMetadata.Deprecation("parameter deprecation"), metadata.getDeprecation(parameter))
        }

        @Test
        fun `@Description without argument is an error`() {
            val source = """
                package annotations
                
                @Description
                enum UserType {
                    ADMIN, USER
                }
                
                @Description
                typealias Id = Long(1..*)
                
                @Description
                record User {
                    @Description
                    id: Id
                }
                
                @Description
                service UserService {
                    @Description
                    get(@Description id: Id): User
                }
            """.trimIndent()
            parseAndCheck(
                source to List(7) { "Error: Missing argument for @Description" }
            )
        }

        @Test
        fun `wrong argument type for @Description is an error`() {
            val source = """
                package annotations
                
                @Description(1)
                enum UserType {
                    ADMIN, USER
                }
                
                @Description(true)
                typealias Id = Long(1..*)
                
                @Description({})
                record User {
                    @Description(1)
                    id: Id
                }
                
                @Description([])
                service UserService {
                    @Description(1.5)
                    get(@Description(String) id: Id): User
                }
            """.trimIndent()
            parseAndCheck(
                source to List(7) { "Error: Argument for @Description must be a string" }
            )
        }

        @Test
        fun `wrong argument type for @Deprecated is an error`() {
            val source = """
                package annotations
                
                @Deprecated(1)
                enum UserType {
                    ADMIN, USER
                }
                
                @Deprecated(true)
                typealias Id = Long(1..*)
                
                @Deprecated({})
                record User {
                    @Deprecated(1)
                    id: Id
                }
                
                @Deprecated([])
                service UserService {
                    @Deprecated(1.5)
                    get(@Deprecated(String) id: Id): User
                }
            """.trimIndent()
            parseAndCheck(
                source to List(7) { "Error: Argument for @Deprecated must be a string" }
            )
        }

        @Test
        fun `extraneous arguments for @Description are an error`() {
            val source = """
                package annotations
                
                @Description("enum description", "")
                enum UserType {
                    ADMIN, USER
                }
                
                @Description("typealias description", "")
                typealias Id = Long(1..*)
                
                @Description("record description", "")
                record User {
                    @Description("field description", "")
                    id: Id
                }
                
                @Description("service description", "")
                service UserService {
                    @Description("operation description", "")
                    get(@Description("parameter description", "") id: Id): User
                }
            """.trimIndent()
            parseAndCheck(
                source to List(7) { "Error: @Description expects exactly one string argument" }
            )
        }

        @Test
        fun `extraneous arguments for @Deprecated are an error`() {
            val source = """
                package annotations
                
                @Deprecated("enum deprecation", "")
                enum UserType {
                    ADMIN, USER
                }
                
                @Deprecated("typealias deprecation", "")
                typealias Id = Long(1..*)
                
                @Deprecated("record deprecation", "")
                record User {
                    @Deprecated("field deprecation", "")
                    id: Id
                }
                
                @Deprecated("service deprecation", "")
                service UserService {
                    @Deprecated("operation deprecation", "")
                    get(@Deprecated("parameter deprecation", "") id: Id): User
                }
            """.trimIndent()
            parseAndCheck(
                source to List(7) { "Error: @Deprecated expects at most one string argument" }
            )
        }

        @Test
        fun `unknown annotations are an error`() {
            val source = """
                package annotations
                
                @Deprescription
                enum UserType {
                    ADMIN, USER
                }
                
                @Deprescription
                typealias Id = Long(1..*)
                
                @Deprescription
                record User {
                    @Deprescription
                    id: Id
                }
                
                @Deprescription
                service UserService {
                    @Deprescription
                    get(@Deprescription id: Id): User
                }
            """.trimIndent()
            parseAndCheck(
                source to List(7) { "Error: Unknown annotation @Deprescription, allowed annotations are @Description and @Deprecated" }
            )
        }

        @Test
        fun `duplicate annotations are an error`() {
            val deprecated = """
                package annotations
                
                @Deprecated
                @Deprecated
                enum UserType {
                    ADMIN, USER
                }
                
                @Deprecated
                @Deprecated
                typealias Id = Long(1..*)
                
                @Deprecated
                @Deprecated
                record User {
                    @Deprecated
                    @Deprecated
                    id: Id
                }
                
                @Deprecated
                @Deprecated
                service UserService {
                    @Deprecated
                    @Deprecated
                    get(@Deprecated @Deprecated id: Id): User
                }
            """.trimIndent()
            parseAndCheck(
                deprecated to List(7) { "Error: Duplicate @Deprecated annotation" }
            )
            val description = """
                package annotations
                
                @Description("test")
                @Description("test")
                enum UserType {
                    ADMIN, USER
                }
                
                @Description("test")
                @Description("test")
                typealias Id = Long(1..*)
                
                @Description("test")
                @Description("test")
                record User {
                    @Description("test")
                    @Description("test")
                    id: Id
                }
                
                @Description("test")
                @Description("test")
                service UserService {
                    @Description("test")
                    @Description("test")
                    get(@Description("test") @Description("test") id: Id): User
                }
            """
            parseAndCheck(
                description to List(7) { "Error: Duplicate @Description annotation" }
            )
        }
    }

    @Nested
    inner class FileSeparation {
        @Test
        fun `provider in file with other types is warning`()  {
            val source = """
                package separation
                
                record A {}
                record B {}
                record C {}
                record D {}
                record E {}
                record F {}
                record G {}
                record H {}
                record I {}
                
                service TestService {}
                
                provide TestProvider {
                    implements TestService
                
                    transport http
                }
            """.trimIndent()
            parseAndCheck(
                source to listOf("Warning: Provider declaration should be in its own file")
            )
        }

        @Test
        fun `consumer in file with other types is warning`() {
            val source = """
                package separation
                
                record A {}
                record B {}
                record C {}
                record D {}
                record E {}
                record F {}
                record G {}
                record H {}
                record I {}
                
                service TestService {}
                
                consume TestProvider {
                    uses TestService
                }
            """.trimIndent()
            val providerSource = """
                package separation
                
                provide TestProvider {
                    implements TestService
                
                    transport http
                }
            """.trimIndent()

            parseAndCheck(
                source to listOf("Warning: Consumer declaration should be in its own file"),
                providerSource to emptyList()
            )
        }
    }

    private fun parseAndCheck(
        vararg sourceAndExpectedMessages: Pair<String, List<String>>,
    ): SemanticModel {
        val diagnosticController = DiagnosticController(URI("file:///tmp"))
        val fileTree = sourceAndExpectedMessages.mapIndexed { index, (source) ->
            val filePath = URI("file:///tmp/SemanticModelTest-${index}.samt")
            val sourceFile = SourceFile(filePath, source)
            val parseContext = diagnosticController.getOrCreateContext(sourceFile)
            val stream = Lexer.scan(source.reader(), parseContext)
            val fileTree = Parser.parse(sourceFile, stream, parseContext)
            assertFalse(parseContext.hasErrors(), "Expected no parse errors, but had errors: ${parseContext.messages}}")
            fileTree
        }

        val parseMessageCount = diagnosticController.contexts.associate { it.source.content to it.messages.size }

        val semanticModel = SemanticModel.build(fileTree, diagnosticController)

        for ((source, expectedMessages) in sourceAndExpectedMessages) {
            val messages = diagnosticController.contexts
                .first { it.source.content == source }
                .messages
                .drop(parseMessageCount.getValue(source))
                .map { "${it.severity}: ${it.message}" }
            assertEquals(expectedMessages, messages)
        }
        return semanticModel
    }
}
