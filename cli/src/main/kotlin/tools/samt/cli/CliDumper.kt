package tools.samt.cli

import com.github.ajalt.mordant.terminal.Terminal
import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticException
import tools.samt.common.collectSamtFiles
import tools.samt.common.readSamtSource
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModel
import kotlin.io.path.isDirectory
import kotlin.io.path.notExists

internal fun dump(command: DumpCommand, terminal: Terminal, controller: DiagnosticController) {
    val (configuration ,_) = CliConfigParser.readConfig(command.file, controller) ?: return

    if (configuration.source.notExists() || !configuration.source.isDirectory()) {
        controller.reportGlobalError("Source path '${configuration.source.toUri()}' does not point to valid directory")
        return
    }

    val sourceFiles = collectSamtFiles(configuration.source.toUri()).readSamtSource(controller)

    if (controller.hasErrors()) {
        return
    }

    if (controller.hasErrors()) {
        return
    }

    val dumpAll = !command.dumpTokens && !command.dumpAst && !command.dumpTypes

    // attempt to parse each source file into an AST
    val fileNodes = buildList {
        for (source in sourceFiles) {
            val context = controller.getOrCreateContext(source)

            if (dumpAll || command.dumpTokens) {
                // create duplicate scan because sequence can only be iterated once
                val tokenStream = Lexer.scan(source.content.reader(), context)
                terminal.println("Tokens for ${source.path}:")
                terminal.println(TokenPrinter.dump(tokenStream))
                // clear the diagnostic messages so that messages aren't duplicated
                context.messages.clear()
            }

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

            if (dumpAll || command.dumpAst) {
                terminal.println(ASTPrinter.dump(fileNode))
            }

            if (context.hasErrors()) {
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
    val samtPackage = SemanticModel.build(fileNodes, controller).global

    if (dumpAll || command.dumpTypes) {
        terminal.println(TypePrinter.dump(samtPackage))
    }
}
