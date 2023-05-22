package tools.samt.codegen

import tools.samt.codegen.http.HttpTransportConfigurationParser
import tools.samt.codegen.kotlin.KotlinTypesGenerator
import tools.samt.codegen.kotlin.ktor.KotlinKtorConsumerGenerator
import tools.samt.codegen.kotlin.ktor.KotlinKtorProviderGenerator
import tools.samt.common.DiagnosticController
import tools.samt.common.SamtGeneratorConfiguration
import tools.samt.semantic.*

data class CodegenFile(val filepath: String, val source: String)

object Codegen {
    private val generators: List<Generator> = listOf(
        KotlinTypesGenerator,
        KotlinKtorProviderGenerator,
        KotlinKtorConsumerGenerator,
    )

    private val transports: List<TransportConfigurationParser> = listOf(
        HttpTransportConfigurationParser,
    )

    internal class SamtGeneratorParams(
        semanticModel: SemanticModel,
        private val controller: DiagnosticController,
        override val options: Map<String, String>,
    ) : GeneratorParams {
        private val apiMapper = PublicApiMapper(transports, controller)
        override val packages: List<SamtPackage> = semanticModel.global.allSubPackages.map { apiMapper.toPublicApi(it) }

        override fun reportError(message: String) {
            controller.reportGlobalError(message)
        }

        override fun reportWarning(message: String) {
            controller.reportGlobalWarning(message)
        }

        override fun reportInfo(message: String) {
            controller.reportGlobalInfo(message)
        }
    }

    fun generate(
        semanticModel: SemanticModel,
        configuration: SamtGeneratorConfiguration,
        controller: DiagnosticController,
    ): List<CodegenFile> {
        val matchingGenerators = generators.filter { it.name == configuration.name }
        when (matchingGenerators.size) {
            0 -> controller.reportGlobalError("No matching generator found for '${configuration.name}'")
            1 -> return matchingGenerators.single().generate(SamtGeneratorParams(semanticModel, controller, configuration.options))
            else -> controller.reportGlobalError("Multiple matching generators found for '${configuration.name}'")
        }
        return emptyList()
    }
}
