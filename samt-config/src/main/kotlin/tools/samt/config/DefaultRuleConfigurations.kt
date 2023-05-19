package tools.samt.config

import tools.samt.common.DiagnosticSeverity as CommonDiagnosticSeverity
import tools.samt.common.NamingConventionsConfiguration.NamingConvention as CommonNamingConvention

val recommended = tools.samt.common.SamtLinterConfiguration(
    splitModelAndProviders = tools.samt.common.SplitModelAndProvidersConfiguration(
        level = CommonDiagnosticSeverity.Info,
    ),
    namingConventions = tools.samt.common.NamingConventionsConfiguration(
        level = CommonDiagnosticSeverity.Warning,
        record = CommonNamingConvention.PascalCase,
        recordField = CommonNamingConvention.CamelCase,
        enum = CommonNamingConvention.PascalCase,
        enumValue = CommonNamingConvention.ScreamingSnakeCase,
        typeAlias = CommonNamingConvention.PascalCase,
        service = CommonNamingConvention.PascalCase,
        serviceOperation = CommonNamingConvention.CamelCase,
        serviceOperationParameter = CommonNamingConvention.CamelCase,
        provider = CommonNamingConvention.PascalCase,
        samtPackage = CommonNamingConvention.SnakeCase,
        fileName = CommonNamingConvention.KebabCase,
    ),
)

val strict = recommended.copy(
    splitModelAndProviders = recommended.splitModelAndProviders.copy(level = CommonDiagnosticSeverity.Warning),
    namingConventions = recommended.namingConventions.copy(level = CommonDiagnosticSeverity.Error),
)
