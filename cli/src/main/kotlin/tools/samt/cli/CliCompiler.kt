package tools.samt.cli

import com.github.ajalt.mordant.rendering.TextColors.*
import tools.samt.codegen.Codegen
import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticException
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModel

internal fun compile(command: CompileCommand, controller: DiagnosticController) {
    val sourceFiles = command.files.readSamtSourceFiles(controller)

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

    // Code Generators will be called here
    val files = Codegen.generate(model, controller)
    for (file in files) {
        println("${yellow(file.filepath)}:\n${file.source}\n")
    }
}
