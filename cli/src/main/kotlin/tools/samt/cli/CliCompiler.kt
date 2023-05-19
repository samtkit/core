package tools.samt.cli

import com.github.ajalt.mordant.rendering.TextColors.*
import tools.samt.codegen.Codegen
import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticException
import tools.samt.common.collectSamtFiles
import tools.samt.common.readSamtSource
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModel
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists

internal fun compile(command: CompileCommand, controller: DiagnosticController) {
    val (configuration ,_) = CliConfigParser.readConfig(command.file, controller) ?: return

    if (configuration.source.notExists() || !configuration.source.isDirectory()) {
        controller.reportGlobalError("Source path '${configuration.source.toUri()}' does not point to valid directory")
        return
    }

    val sourceFiles = collectSamtFiles(configuration.source.toUri()).readSamtSource(controller)

    if (controller.hasErrors()) {
        return
    }

    // attempt to parse each source file into an AST
    val fileNodes = buildList {
        for (source in sourceFiles) {
            val context = controller.getOrCreateContext(source)
            val tokenStream = Lexer.scan(source.content.reader(), context)

            if (context.hasErrors()) {
                continue
            }

            val fileNode = try {
                Parser.parse(source, tokenStream, context)
            } catch (e: DiagnosticException) {
                // error message is added to the diagnostic console, so it can be ignored here
                continue
            }

            add(fileNode)
        }
    }

    // if any source files failed to parse, exit
    if (controller.hasErrors()) {
        return
    }

    // build up the semantic model from the AST
    val model = SemanticModel.build(fileNodes, controller)

    // if the semantic model failed to build, exit
    if (controller.hasErrors()) {
        return
    }

    val files = Codegen.generate(model, controller)

    // if the semantic model failed to build, exit
    if (controller.hasErrors()) {
        return
    }

    // emit files for debug purposes
    // TODO: emit into an "out" folder and build package folder structure
    for (file in files) {
        println("${yellow(file.filepath)}:\n${file.source}\n")
    }
}
