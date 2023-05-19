package tools.samt.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SamtLinterConfiguration(
    val extends: String = "recommended",
    val rules: List<SamtRuleConfiguration> = emptyList(),
)

enum class DiagnosticSeverity {
    @SerialName("error")
    Error,

    @SerialName("warn")
    Warn,

    @SerialName("info")
    Info,

    @SerialName("off")
    Off,
}

@Serializable
sealed interface SamtRuleConfiguration {
    val level: DiagnosticSeverity?
}

@Serializable
@SerialName("split-model-and-providers")
data class SplitModelAndProvidersConfiguration(
    override val level: DiagnosticSeverity? = null,
) : SamtRuleConfiguration

@Serializable
@SerialName("naming-conventions")
data class NamingConventionsConfiguration(
    override val level: DiagnosticSeverity? = null,
    val record: NamingConventions? = null,
    val recordField: NamingConventions? = null,
    val enum: NamingConventions? = null,
    val enumValue: NamingConventions? = null,
    val typeAlias: NamingConventions? = null,
    val service: NamingConventions? = null,
    val serviceOperation: NamingConventions? = null,
    val serviceOperationParameter: NamingConventions? = null,
    val provider: NamingConventions? = null,
    @SerialName("package")
    val samtPackage: NamingConventions? = null,
    val fileName: NamingConventions? = null,
) : SamtRuleConfiguration {
    enum class NamingConventions {
        @SerialName("PascalCase")
        PascalCase,
        @SerialName("camelCase")
        CamelCase,
        @SerialName("snake_case")
        SnakeCase,
        @SerialName("kebab-case")
        KebabCase,
        @SerialName("SCREAMING_SNAKE_CASE")
        ScreamingSnakeCase,
    }
}
