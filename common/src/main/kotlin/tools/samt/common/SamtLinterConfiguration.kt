package tools.samt.common

data class SamtLinterConfiguration(
    val splitModelAndProviders: SplitModelAndProvidersConfiguration,
    val namingConventions: NamingConventionsConfiguration,
)

sealed interface SamtRuleConfiguration {
    val level: DiagnosticSeverity?
}

data class SplitModelAndProvidersConfiguration(
    override val level: DiagnosticSeverity?,
) : SamtRuleConfiguration

data class NamingConventionsConfiguration(
    override val level: DiagnosticSeverity?,
    val record: NamingConvention,
    val recordField: NamingConvention,
    val enum: NamingConvention,
    val enumValue: NamingConvention,
    val typeAlias: NamingConvention,
    val service: NamingConvention,
    val serviceOperation: NamingConvention,
    val serviceOperationParameter: NamingConvention,
    val provider: NamingConvention,
    val samtPackage: NamingConvention,
    val fileName: NamingConvention,
) : SamtRuleConfiguration {
    enum class NamingConvention {
        PascalCase,
        CamelCase,
        SnakeCase,
        KebabCase,
        ScreamingSnakeCase,
    }
}
