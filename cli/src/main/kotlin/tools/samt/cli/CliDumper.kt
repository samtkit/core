package tools.samt.cli

import com.github.ajalt.mordant.terminal.Terminal
import tools.samt.common.DiagnosticController
import tools.samt.common.DiagnosticException
import tools.samt.lexer.Lexer
import tools.samt.parser.Parser
import tools.samt.semantic.SemanticModelBuilder

internal fun dump(command: DumpCommand, terminal: Terminal, controller: DiagnosticController) {
    val sourceFiles = command.files.readSamtSourceFiles(controller)

    if (controller.hasErrors()) {
        return
    }

    // attempt to parse each source file into an AST
    val fileNodes = buildList {
        for (source in sourceFiles) {
            val context = controller.createContext(source)

            if (command.dumpTokens) {
                // create duplicate scan because sequence can only be iterated once
                val tokenStream = Lexer.scan(source.content.reader(), context)
                terminal.println("Tokens for ${source.absolutePath}:")
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

            if (command.dumpAst) {
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
    SemanticModelBuilder.build(fileNodes, controller)

    if (command.dumpTypes) {
        terminal.println("Types:")
        terminal.println("Not yet implemented")
        // Type dumper will be added here
    }
}
