package tools.samt.ls

import tools.samt.common.DiagnosticContext
import tools.samt.common.DiagnosticException
import tools.samt.common.SourceFile
import tools.samt.lexer.Lexer
import tools.samt.lexer.Token
import tools.samt.parser.FileNode
import tools.samt.parser.Parser

class FileInfo(
    val diagnosticContext: DiagnosticContext,
    val sourceFile: SourceFile,
    @Suppress("unused") val tokens: List<Token>,
    val fileNode: FileNode? = null,
)

fun parseFile(sourceFile: SourceFile): FileInfo {
    val diagnosticContext = DiagnosticContext(sourceFile)

    val tokens = Lexer.scan(sourceFile.content.reader(), diagnosticContext).toList()

    if (diagnosticContext.hasErrors()) {
        return FileInfo(diagnosticContext, sourceFile, tokens)
    }

    val fileNode = try {
        Parser.parse(sourceFile, tokens.asSequence(), diagnosticContext)
    } catch (e: DiagnosticException) {
        // error message is added to the diagnostic console, so it can be ignored here
        return FileInfo(diagnosticContext, sourceFile, tokens)
    }

    return FileInfo(diagnosticContext, sourceFile, tokens, fileNode)
}
