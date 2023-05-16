package tools.samt.codegen

import tools.samt.common.DiagnosticController
import tools.samt.semantic.*

data class CodegenFile(val filepath: String, val source: String)

/*
 * Proof of concept codegen for Kotlin code
 *
 * Todos:
 * - Emit providers
 * - Emit consumers
 * - Modular
 * - Extendable
 * - Configurable
 * */
object Codegen {
    private val generators: List<Generator> = listOf(
        KotlinTypesGenerator(),
        KotlinKtorGenerator(),
    )

    private val transports: List<TransportConfigurationParser> = listOf(
        HttpTransportConfigurationParser(),
    )

    internal class SamtGeneratorParams(rootPackage: Package, private val controller: DiagnosticController) : GeneratorParams {
        private val apiMapper = PublicApiMapper(transports, controller)
        override val packages: List<SamtPackage> = rootPackage.allSubPackages.map { apiMapper.toPublicApi(it) }

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

    fun generate(rootPackage: Package, controller: DiagnosticController): List<CodegenFile> {
        check(rootPackage.isRootPackage)
        check(rootPackage.parent == null)
        check(rootPackage.records.isEmpty())
        check(rootPackage.enums.isEmpty())
        check(rootPackage.aliases.isEmpty())
        check(rootPackage.services.isEmpty())
        check(rootPackage.providers.isEmpty())
        check(rootPackage.consumers.isEmpty())

        val generatorIdentifier = "kotlin-ktor" // TODO: read from config
        val matchingGenerators = generators.filter { it.identifier == generatorIdentifier }
        when (matchingGenerators.size) {
            0 -> controller.reportGlobalError("No matching generator found for '$generatorIdentifier'")
            1 -> return matchingGenerators.single().generate(SamtGeneratorParams(rootPackage, controller))
            else -> controller.reportGlobalError("Multiple matching generators found for '$generatorIdentifier'")
        }
        return emptyList()
    }
}
