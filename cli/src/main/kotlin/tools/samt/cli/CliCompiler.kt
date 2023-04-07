package tools.samt.cli

import tools.samt.common.DiagnosticContext
import tools.samt.common.DiagnosticException
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.parser.FileNode
import tools.samt.parser.Parser

fun parseSourceFile(source: SourceFile, context: DiagnosticContext): FileNode? {
    val tokenStream = Lexer.scan(source.content.reader(), context)

    if (context.hasErrors()) {
        return null
    }

    return try {
        Parser.parse(source, tokenStream, context)
    } catch (e: DiagnosticException) {
        // error message is added to the diagnostic console, so it can be ignored here
        null
    }
}
