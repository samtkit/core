package tools.samt.config

import com.charleskorn.kaml.*
import kotlinx.serialization.SerializationException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import tools.samt.common.DiagnosticSeverity as CommonDiagnosticSeverity
import tools.samt.common.NamingConventionsConfiguration as CommonNamingConventionsConfiguration
import tools.samt.common.NamingConventionsConfiguration.NamingConvention as CommonNamingConvention
import tools.samt.common.SamtConfiguration as CommonSamtConfiguration
import tools.samt.common.SamtGeneratorConfiguration as CommonGeneratorConfiguration
import tools.samt.common.SamtLinterConfiguration as CommonLinterConfiguration
import tools.samt.common.SamtLocalPluginConfiguration as CommonLocalPluginConfiguration
import tools.samt.common.SamtMavenPluginConfiguration as CommonMavenPluginConfiguration
import tools.samt.common.SplitModelAndProvidersConfiguration as CommonSplitModelAndProvidersConfiguration

object SamtConfigurationParser {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            encodeDefaults = false,
            polymorphismStyle = PolymorphismStyle.Property,
            singleLineStringStyle = SingleLineStringStyle.Plain,
        )
    )

    class ParseException(exception: Throwable) : RuntimeException(exception.message, exception)

    fun parseConfiguration(path: Path): CommonSamtConfiguration {
        val parsedConfiguration: SamtConfiguration = if (path.exists()) {
            try {
                yaml.decodeFromStream(path.inputStream())
            } catch (exception: SerializationException) {
                throw ParseException(exception)
            }
        } else {
            SamtConfiguration()
        }

        val projectDirectory = path.parent

        return CommonSamtConfiguration(
            source = projectDirectory.resolve(parsedConfiguration.source).normalize(),
            plugins = parsedConfiguration.plugins.map { plugin ->
                when (plugin) {
                    is SamtLocalPluginConfiguration -> CommonLocalPluginConfiguration(
                        path = projectDirectory.resolve(plugin.path).normalize()
                    )

                    is SamtMavenPluginConfiguration -> CommonMavenPluginConfiguration(
                        groupId = plugin.groupId,
                        artifactId = plugin.artifactId,
                        version = plugin.version,
                        repository = plugin.repository ?: parsedConfiguration.repositories.maven
                    )

                    is SamtGradlePluginConfiguration -> CommonMavenPluginConfiguration(
                        groupId = plugin.dependency.split(':')[0],
                        artifactId = plugin.dependency.split(':')[1],
                        version = plugin.dependency.split(':')[2],
                        repository = plugin.repository ?: parsedConfiguration.repositories.maven
                    )
                }
            },
            generators = parsedConfiguration.generators.map { generator ->
                CommonGeneratorConfiguration(
                    name = generator.name,
                    output = projectDirectory.resolve(generator.output).normalize(),
                    options = generator.options
                )
            }
        )
    }

    fun parseLinterConfiguration(path: Path): CommonLinterConfiguration {
        val parsedLinterConfiguration: SamtLinterConfiguration = if (path.exists()) {
            yaml.decodeFromStream(path.inputStream())
        } else {
            SamtLinterConfiguration()
        }

        val base = when (parsedLinterConfiguration.extends) {
            "recommended" -> recommended
            "strict" -> strict
            else -> error("TODO")
        }

        val userSplitModelAndProvidersConfiguration = parsedLinterConfiguration.rules.filterIsInstance<SplitModelAndProvidersConfiguration>().singleOrNull()
        val userNamingConventionsConfiguration = parsedLinterConfiguration.rules.filterIsInstance<NamingConventionsConfiguration>().singleOrNull()

        return CommonLinterConfiguration(
            splitModelAndProviders = CommonSplitModelAndProvidersConfiguration(
                level = userSplitModelAndProvidersConfiguration?.level.toLevelOrDefault(base.splitModelAndProviders.level),
            ),
            namingConventions = CommonNamingConventionsConfiguration(
                level = userNamingConventionsConfiguration?.level.toLevelOrDefault(base.namingConventions.level),
                record = userNamingConventionsConfiguration?.record.toNamingConventionOrDefault(base.namingConventions.record),
                recordField = userNamingConventionsConfiguration?.recordField.toNamingConventionOrDefault(base.namingConventions.recordField),
                enum = userNamingConventionsConfiguration?.enum.toNamingConventionOrDefault(base.namingConventions.enum),
                enumValue = userNamingConventionsConfiguration?.enumValue.toNamingConventionOrDefault(base.namingConventions.enumValue),
                typeAlias = userNamingConventionsConfiguration?.typeAlias.toNamingConventionOrDefault(base.namingConventions.typeAlias),
                service = userNamingConventionsConfiguration?.service.toNamingConventionOrDefault(base.namingConventions.service),
                serviceOperation = userNamingConventionsConfiguration?.serviceOperation.toNamingConventionOrDefault(base.namingConventions.serviceOperation),
                serviceOperationParameter = userNamingConventionsConfiguration?.serviceOperationParameter.toNamingConventionOrDefault(base.namingConventions.serviceOperationParameter),
                provider = userNamingConventionsConfiguration?.provider.toNamingConventionOrDefault(base.namingConventions.provider),
                samtPackage = userNamingConventionsConfiguration?.samtPackage.toNamingConventionOrDefault(base.namingConventions.samtPackage),
                fileName = userNamingConventionsConfiguration?.fileName.toNamingConventionOrDefault(base.namingConventions.fileName),
            ),
        )
    }

    private fun DiagnosticSeverity?.toLevelOrDefault(default: CommonDiagnosticSeverity?): CommonDiagnosticSeverity? = when (this) {
        null -> default
        DiagnosticSeverity.Error -> CommonDiagnosticSeverity.Error
        DiagnosticSeverity.Warn -> CommonDiagnosticSeverity.Warning
        DiagnosticSeverity.Info -> CommonDiagnosticSeverity.Info
        DiagnosticSeverity.Off -> null
    }

    private fun NamingConventionsConfiguration.NamingConventions?.toNamingConventionOrDefault(default: CommonNamingConvention): CommonNamingConvention = when (this) {
        null -> default
        NamingConventionsConfiguration.NamingConventions.PascalCase -> CommonNamingConvention.PascalCase
        NamingConventionsConfiguration.NamingConventions.CamelCase -> CommonNamingConvention.CamelCase
        NamingConventionsConfiguration.NamingConventions.SnakeCase -> CommonNamingConvention.SnakeCase
        NamingConventionsConfiguration.NamingConventions.KebabCase -> CommonNamingConvention.KebabCase
        NamingConventionsConfiguration.NamingConventions.ScreamingSnakeCase -> CommonNamingConvention.ScreamingSnakeCase
    }
}
