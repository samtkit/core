package tools.samt.api.plugin

import tools.samt.api.types.SamtPackage

/**
 * A code generator.
 * This interface is intended to be implemented by a code generator, for example Kotlin-Ktor.
 */
interface Generator {
    /**
     * The name of the generator, used to identify it in the configuration
     */
    val name: String

    /**
     * Generate code for the given packages
     * @param generatorParams The parameters for the generator
     * @return A list of generated files, which will be written to disk
     */
    fun generate(generatorParams: GeneratorParams): List<CodegenFile>
}

/**
 * This class represents a file generated by a [Generator].
 */
data class CodegenFile(val filepath: String, val source: String)

/**
 * The parameters for a [Generator].
 */
interface GeneratorParams {
    /**
     * The packages to generate code for, includes all SAMT subpackages
     */
    val packages: List<SamtPackage>

    /**
     * The configuration for the generator as specified in the SAMT configuration
     */
    val options: Map<String, String>

    /**
     * Report an error
     * @param message The error message
     */
    fun reportError(message: String)

    /**
     * Report a warning
     * @param message The warning message
     */
    fun reportWarning(message: String)

    /**
     * Report an info message
     * @param message The info message
     */
    fun reportInfo(message: String)
}
