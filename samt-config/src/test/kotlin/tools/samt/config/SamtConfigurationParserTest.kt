package tools.samt.config

import com.charleskorn.kaml.YamlException
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tools.samt.common.DiagnosticSeverity as CommonDiagnosticSeverity
import tools.samt.common.NamingConventionsConfiguration as CommonNamingConventionsConfiguration
import tools.samt.common.NamingConventionsConfiguration.NamingConvention as CommonNamingConvention
import tools.samt.common.SamtLinterConfiguration as CommonLinterConfiguration
import tools.samt.common.SplitModelAndProvidersConfiguration as CommonSplitModelAndProvidersConfiguration

class SamtConfigurationParserTest {
    private val testDirectory = Path("src/test/resources/test-files")

    @BeforeTest
    fun setup() {
        assertTrue(testDirectory.exists() && testDirectory.isDirectory(), "Test directory does not exist")
    }

    @Test
    fun `works for samt-full file`() {
        val samtConfiguration = SamtConfigurationParser.parseConfiguration(testDirectory.resolve("samt-full.yaml"))

        assertEquals(
            tools.samt.common.SamtConfiguration(
                source = testDirectory.resolve("some/other/src"),
                plugins = listOf(
                    tools.samt.common.SamtLocalPluginConfiguration(
                        path = testDirectory.resolve("path/to/plugin.jar")
                    ),
                    tools.samt.common.SamtMavenPluginConfiguration(
                        groupId = "com.example",
                        artifactId = "example-plugin",
                        version = "1.0.0",
                        repository = "https://repository.jboss.org/nexus/content/repositories/releases"
                    ),
                    tools.samt.common.SamtMavenPluginConfiguration(
                        groupId = "com.example",
                        artifactId = "example-plugin",
                        version = "1.0.0",
                        repository = "https://repo.spring.io/release"
                    )
                ),
                generators = listOf(
                    tools.samt.common.SamtGeneratorConfiguration(
                        name = "samt-kotlin-ktor",
                        output = testDirectory.resolve("some/other/out"),
                        options = mapOf(
                            "removePrefixFromSamtPackage" to "tools.samt",
                            "addPrefixToKotlinPackage" to "tools.samt.example.generated",
                        )
                    )
                )
            ), samtConfiguration
        )
    }

    @Test
    fun `works for samt-minimal file`() {
        val samtConfiguration = SamtConfigurationParser.parseConfiguration(testDirectory.resolve("samt-minimal.yaml"))

        assertEquals(
            tools.samt.common.SamtConfiguration(
                source = testDirectory.resolve("src"),
                plugins = emptyList(),
                generators = listOf(
                    tools.samt.common.SamtGeneratorConfiguration(
                        name = "samt-kotlin-ktor",
                        output = testDirectory.resolve("out"),
                        options = mapOf(
                            "addPrefixToKotlinPackage" to "com.company.samt.generated",
                        )
                    )
                )
            ),
            samtConfiguration
        )
    }

    @Test
    fun `throws for samt-invalid file`() {
        val exception = assertThrows<SamtConfigurationParser.ParseException> {
            SamtConfigurationParser.parseConfiguration(testDirectory.resolve("samt-invalid.yaml"))
        }

        assertEquals(
            "Unknown property 'generator'. Known properties are: generators, plugins, repositories, source",
            exception.message
        )
    }

    @Test
    fun `works for samtrc-recommended file`() {
        val samtLintConfiguration = SamtConfigurationParser.parseLinterConfiguration(testDirectory.resolve(".samtrc-recommended.yaml"))

        assertEquals(
            CommonLinterConfiguration(
                splitModelAndProviders = CommonSplitModelAndProvidersConfiguration(
                    level = null,
                ),
                namingConventions = CommonNamingConventionsConfiguration(
                    level = CommonDiagnosticSeverity.Info,
                    record = CommonNamingConvention.PascalCase,
                    recordField = CommonNamingConvention.CamelCase,
                    enum = CommonNamingConvention.CamelCase,
                    enumValue = CommonNamingConvention.PascalCase,
                    typeAlias = CommonNamingConvention.PascalCase,
                    service = CommonNamingConvention.PascalCase,
                    serviceOperation = CommonNamingConvention.CamelCase,
                    serviceOperationParameter = CommonNamingConvention.CamelCase,
                    provider = CommonNamingConvention.PascalCase,
                    samtPackage = CommonNamingConvention.ScreamingSnakeCase,
                    fileName = CommonNamingConvention.KebabCase,
                ),
            ), samtLintConfiguration
        )
    }

    @Test
    fun `works for samtrc-strict file`() {
        val samtConfiguration = SamtConfigurationParser.parseLinterConfiguration(testDirectory.resolve(".samtrc-strict.yaml"))

        assertEquals(
            strict,
            samtConfiguration
        )
    }

    @Test
    fun `throws when parsing samt-invalid file as linter configuration`() {
        val exception = assertThrows<YamlException> {
            SamtConfigurationParser.parseLinterConfiguration(testDirectory.resolve("samt-invalid.yaml"))
        }

        assertEquals(
            "Unknown property 'generator'. Known properties are: extends, rules",
            exception.message
        )
    }
}
