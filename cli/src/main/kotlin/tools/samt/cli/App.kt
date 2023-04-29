package tools.samt.cli

import com.beust.jcommander.JCommander

import com.github.ajalt.mordant.terminal.Terminal
import tools.samt.common.DiagnosticController
import tools.samt.common.SourceFile
import tools.samt.semantic.SemanticModelBuilder
import java.io.File

fun main(args: Array<String>) {
    val cliArgs = CliArgs()
    val jCommander = JCommander.newBuilder()
        .addObject(cliArgs)
        .programName("./samtw")
        .build()
    jCommander.parse(*args)
    if (cliArgs.help) {
        jCommander.usage()
        return
    }

    val t = Terminal()

    val workingDirectory = System.getProperty("user.dir")
    val filePaths = cliArgs.files

    val startTimestamp = System.currentTimeMillis()
    val diagnosticController = DiagnosticController(workingDirectory).also { controller ->

        // must specify at least one SAMT file
        if (filePaths.isEmpty()) {
            controller.reportGlobalError("No files specified")
            return@also
        }

        // attempt to load each source file
        val sourceFiles = buildList {
            for (path in filePaths) {
                val file = File(path)

                if (!file.exists()) {
                    controller.reportGlobalError("File '$path' does not exist")
                    continue
                }

                if (!file.canRead()) {
                    controller.reportGlobalError("File '$path' cannot be read, bad file permissions?")
                    continue
                }

                if (file.extension != "samt") {
                    controller.reportGlobalError("File '$path' must end in .samt")
                    continue
                }

                val source = file.readText()
                add(SourceFile(file.canonicalPath, source))
            }
        }

        // if any source files failed to load, exit
        if (controller.hasErrors()) {
            return@also
        }

        // attempt to parse each source file into an AST
        val fileNodes = buildList {
            for (source in sourceFiles) {
                val context = controller.createContext(source)
                val fileNode = parseSourceFile(source, context)
                if (fileNode != null) {
                    add(fileNode)
                }
            }
        }

        // if any source files failed to parse, exit
        if (controller.hasErrors()) {
            return@also
        }

        // if the user requested the AST to be dumped, do so
        if (cliArgs.dumpAst) {
            fileNodes.forEach {
                t.print(ASTPrinter.dump(it))
            }
        }

        // build up the semantic model from the AST
        SemanticModelBuilder.build(fileNodes, controller)
    }

    val currentTimestamp = System.currentTimeMillis()
    t.println(DiagnosticFormatter.format(diagnosticController, startTimestamp, currentTimestamp, terminalWidth = t.info.width))
}
