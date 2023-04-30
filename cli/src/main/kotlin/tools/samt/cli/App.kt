package tools.samt.cli

import com.beust.jcommander.JCommander
import com.github.ajalt.mordant.terminal.Terminal
import tools.samt.common.DiagnosticController

fun main(args: Array<String>) {
    val cliArgs = CliArgs()
    val compileCommand = CompileCommand()
    val dumpCommand = DumpCommand()
    val wrapperCommand = WrapperCommand()
    val jCommander = JCommander.newBuilder()
        .addObject(cliArgs)
        .addCommand("compile", compileCommand)
        .addCommand("dump", dumpCommand)
        .addCommand("wrapper", wrapperCommand)
        .programName("./samtw")
        .build()
    jCommander.parse(*args)
    if (cliArgs.help) {
        jCommander.usage()
        return
    }

    val terminal = Terminal()

    val workingDirectory = System.getProperty("user.dir")

    val controller = DiagnosticController(workingDirectory)

    val startTimestamp = System.currentTimeMillis()
    when (jCommander.parsedCommand) {
        "compile" -> compile(compileCommand, controller)
        "dump" -> dump(dumpCommand, terminal, controller)
        "wrapper" -> wrapper(wrapperCommand, terminal, controller)
        else -> {
            jCommander.usage()
            return
        }
    }
    val currentTimestamp = System.currentTimeMillis()

    terminal.println(
        DiagnosticFormatter.format(
            controller,
            startTimestamp,
            currentTimestamp,
            terminalWidth = terminal.info.width
        )
    )
}
