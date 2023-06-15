package tools.samt.codegen

import tools.samt.api.plugin.CodegenFile
import tools.samt.common.DiagnosticController
import tools.samt.common.collectSamtFiles
import tools.samt.common.readSamtSource
import tools.samt.config.SamtConfigurationParser
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModel
import java.net.URI
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import tools.samt.codegen.kotlin.KotlinTypesGenerator
import tools.samt.codegen.kotlin.ktor.KotlinKtorConsumerGenerator
import tools.samt.codegen.kotlin.ktor.KotlinKtorProviderGenerator

class CodegenTest {
    private val testDirectory = Path("src/test/resources/generator-test-model")

    @Test
    fun `correctly compiles test model`() {
        val controller = DiagnosticController(URI("file:///tmp"))

        val configuration = SamtConfigurationParser.parseConfiguration(testDirectory.resolve("samt.yaml"))
        val sourceFiles = collectSamtFiles(configuration.source.toUri()).readSamtSource(controller)

        assertFalse(controller.hasErrors())

        // attempt to parse each source file into an AST
        val fileNodes = buildList {
            for (source in sourceFiles) {
                val context = controller.getOrCreateContext(source)
                val tokenStream = Lexer.scan(source.content.reader(), context)

                add(Parser.parse(source, tokenStream, context))
            }
        }

        assertFalse(controller.hasErrors())

        // build up the semantic model from the AST
        val model = SemanticModel.build(fileNodes, controller)

        assertFalse(controller.hasErrors())

        Codegen.registerGenerator(KotlinTypesGenerator)
        Codegen.registerGenerator(KotlinKtorProviderGenerator)
        Codegen.registerGenerator(KotlinKtorConsumerGenerator)

        val actualFiles = mutableListOf<CodegenFile>()
        for (generator in configuration.generators) {
            actualFiles += Codegen.generate(model, generator, controller).map { it.copy(filepath = generator.output.resolve(it.filepath).toString()) }
        }

        val expectedFiles = testDirectory.toFile().walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

        val expected = expectedFiles.associate { it.toPath().normalize() to it.readText().replace("\r\n", "\n") }.toSortedMap()
        val actual = actualFiles.associate { Path(it.filepath).normalize() to it.source.replace("\r\n", "\n") }.toSortedMap()

        assertEquals(expected, actual)
    }
}
