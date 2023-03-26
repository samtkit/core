package tools.samt.cli

import tools.samt.common.DiagnosticConsole
import tools.samt.common.DiagnosticContext
import tools.samt.lexer.Lexer
import tools.samt.parser.FileNode
import tools.samt.parser.Parser
import tools.samt.parser.ParserException
import java.io.File

fun parse(files: List<String>, dumpAST: Boolean = false) {
    for (it in files) {
        val file = File(it)
        if (!file.exists()) {
            println("File '$it' does not exist")
            continue
        }
        if (!file.canRead()) {
            println("File '$it' cannot be read, bad file permissions?")
            continue
        }
        if (file.extension != "samt") {
            println("File '$it' must end in .samt")
            continue
        }

        val source = file.readText()
        val diagnostics = DiagnosticConsole(DiagnosticContext(file.canonicalPath, source))

        val tokenStream = Lexer.scan(source.reader(), diagnostics)

        if (diagnostics.hasErrors()) {
            diagnostics.printToConsole()
            continue
        }

        val fileNode = try {
            Parser.parse(tokenStream, diagnostics)
        } catch (e: ParserException) {
            diagnostics.printToConsole()
            continue
        }

        diagnostics.printToConsole()

        if (dumpAST) {
            println(fileNode)
        }
    }
}

private fun DiagnosticConsole.printToConsole() {
    println(this)
}
