package tools.samt.cli

import tools.samt.common.DiagnosticConsole
import tools.samt.common.DiagnosticContext
import tools.samt.lexer.Lexer
import tools.samt.parser.FileNode
import tools.samt.parser.Parser
import tools.samt.parser.ParserException
import java.io.File

fun parse(filePaths: List<String>): List<FileNode> = buildList {
    for (filePath in filePaths) {
        val file = File(filePath)
        if (!file.exists()) {
            println("File '$filePath' does not exist")
            continue
        }
        if (!file.canRead()) {
            println("File '$filePath' cannot be read, bad file permissions?")
            continue
        }
        if (file.extension != "samt") {
            println("File '$filePath' must end in .samt")
            continue
        }

        val source = file.readText()
        val diagnostics = DiagnosticConsole(DiagnosticContext(file.canonicalPath, source))

        val parsedFileNode = parseSource(file.canonicalPath, source, diagnostics)

        if (diagnostics.hasMessages()) {
            diagnostics.printToConsole()

            if (diagnostics.hasErrors()) {
                continue
            }
        }

        add(parsedFileNode!!)
    }
}

fun parseSource(filePath: String, fileSource: String, diagnostics: DiagnosticConsole): FileNode? {
    val tokenStream = Lexer.scan(fileSource.reader(), diagnostics)

    if (diagnostics.hasErrors()) {
        return null
    }

    return try {
        Parser.parse(filePath, tokenStream, diagnostics)
    } catch (e: ParserException) {
        // error message is added to the diagnostic console, so it can be ignored here
        null
    }
}

private fun DiagnosticConsole.printToConsole() {
    println(this)
}
