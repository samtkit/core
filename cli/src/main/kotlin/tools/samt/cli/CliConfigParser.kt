package tools.samt.cli

import tools.samt.common.DiagnosticController
import tools.samt.common.SamtConfiguration
import tools.samt.common.SamtLinterConfiguration
import tools.samt.config.SamtConfigurationParser
import java.nio.file.InvalidPathException
import kotlin.io.path.Path
import kotlin.io.path.notExists

internal object CliConfigParser {
    fun readConfig(file: String, controller: DiagnosticController): Pair<SamtConfiguration, SamtLinterConfiguration>? {
        val configFile = try {
            Path(file)
        } catch (e: InvalidPathException) {
            controller.reportGlobalError("Invalid path '${file}': ${e.message}")
            return null
        }
        if (configFile.notExists()) {
            controller.reportGlobalInfo("Configuration file '${configFile.toUri()}' does not exist, using default configuration")
        }
        val configuration = try {
            SamtConfigurationParser.parseConfiguration(configFile)
        } catch (e: Exception) {
            controller.reportGlobalError("Failed to parse configuration file '${configFile.toUri()}': ${e.message}")
            return null
        }
        val samtLintConfigFile = configFile.resolveSibling(".samtrc.yaml")
        if (samtLintConfigFile.notExists()) {
            controller.reportGlobalInfo("Lint configuration file '${samtLintConfigFile.toUri()}' does not exist, using default lint configuration")
        }
        val linterConfiguration = try {
            SamtConfigurationParser.parseLinterConfiguration(samtLintConfigFile)
        } catch (e: Exception) {
            controller.reportGlobalError("Failed to parse lint configuration file '${samtLintConfigFile.toUri()}': ${e.message}")
            return null
        }

        return Pair(configuration, linterConfiguration)
    }
}
