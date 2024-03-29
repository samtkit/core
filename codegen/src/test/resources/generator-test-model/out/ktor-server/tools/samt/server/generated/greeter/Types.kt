@file:Suppress("RemoveRedundantQualifierName", "unused", "UnusedImport", "LocalVariableName", "FunctionName", "ConvertTwoComparisonsToRangeCheck", "ReplaceSizeCheckWithIsNotEmpty", "NAME_SHADOWING", "UNUSED_VARIABLE", "NestedLambdaShadowedImplicitParameter", "KotlinRedundantDiagnosticSuppress")

/*
 * This file is generated by SAMT, manual changes will be overwritten.
 * Visit the SAMT GitHub for more details: https://github.com/samtkit/core
 */

package tools.samt.server.generated.greeter

data class Greeting(
    val message: String,
)

data class Person(
    val id: tools.samt.server.generated.greeter.ID = null,
    val name: String,
    val age: Int,
)

enum class GreetingType {
    /** Default value used when the enum could not be parsed */
    FAILED_TO_PARSE,
    HELLO,
    HI,
    HEY,
}
typealias ID = String?
interface Greeter {
    fun greet(
        id: tools.samt.server.generated.greeter.ID = null,
        name: String,
        type: tools.samt.server.generated.greeter.GreetingType? = null,
    ): tools.samt.server.generated.greeter.Greeting
    fun greetAll(
        names: List<String?>,
    ): Map<String, tools.samt.server.generated.greeter.Greeting?>
    fun greeting(
        who: tools.samt.server.generated.greeter.Person,
    ): String
    fun allTheTypes(
        long: Long,
        float: Float,
        double: Double,
        decimal: java.math.BigDecimal,
        boolean: Boolean,
        date: java.time.LocalDate,
        dateTime: java.time.LocalDateTime,
        duration: java.time.Duration,
    )
    fun fireAndForget(
        deleteWorld: Boolean,
    )
    suspend fun legacy(
    )
}
