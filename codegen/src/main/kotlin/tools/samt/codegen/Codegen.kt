package tools.samt.codegen

import tools.samt.api.plugin.CodegenFile
import tools.samt.api.plugin.Generator
import tools.samt.api.plugin.GeneratorParams
import tools.samt.api.plugin.TransportConfigurationParser
import tools.samt.api.types.SamtPackage
import tools.samt.common.DiagnosticController
import tools.samt.common.SamtGeneratorConfiguration
import tools.samt.semantic.SemanticModel

object Codegen {
    private val generators: MutableList<Generator> = mutableListOf()

    /**
     * Register the [generator] to be used when generating code.
     * The generator is called when a user configures it within their `samt.yaml` configuration, where the generator is referenced by its [Generator.name].
     * Only generators registered here will be considered when calling [generate].
     */
    @JvmStatic
    fun registerGenerator(generator: Generator) {
        generators += generator
    }

    private val transports: MutableList<TransportConfigurationParser> = mutableListOf()

    /**
     * Register the [parser] as a transport configuration, which will be used to parse the `transport` section of a SAMT provider.
     * Only transport configurations registered here will be considered when calling [generate].
     */
    @JvmStatic
    fun registerTransportParser(parser: TransportConfigurationParser) {
        transports += parser
    }

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

    /**
     * Run the appropriate generator for the given [configuration], using the given [semanticModel].
     * To ensure binary compatibility, the types within the [semanticModel] will be mapped to their [tools.samt.api] equivalents.
     * @return a list of generated files
     */
    @JvmStatic
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
