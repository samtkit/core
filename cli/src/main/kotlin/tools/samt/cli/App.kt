package tools.samt.cli

import com.beust.jcommander.JCommander
import tools.samt.parser.FileNode
import kotlin.system.measureNanoTime

import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal

fun main(args: Array<String>) {
    val cliArgs = CliArgs()
    val jCommander = JCommander.newBuilder()
        .addObject(cliArgs)
        .programName("java -jar samt-cli.jar")
        .build()
    jCommander.parse(*args)
    if (cliArgs.help) {
        jCommander.usage()
        return
    }

    val t = Terminal()

    var fileNodes: List<FileNode>? = null
    if (cliArgs.benchmark) {
        var totalTime = 0L
        repeat(cliArgs.benchmarkRuns) {
            val parseTime = measureNanoTime {
                fileNodes = parse(cliArgs.files)
            }
            totalTime += parseTime
        }

        t.println("Average parse step took ${underline(format(totalTime / cliArgs.benchmarkRuns))}ms")
    } else {
        fileNodes = parse(cliArgs.files)
    }

    require(fileNodes != null)

    if (cliArgs.dumpAst) {
        fileNodes!!.forEach { fileNode ->
            t.print(ASTPrinter.dump(fileNode))
        }
    }
}

private fun format(timeInNs: Long): String {
    val timeInMs = timeInNs.toDouble() / 1_000_000.0
    return String.format("%.2f", timeInMs)

}
