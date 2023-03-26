package tools.samt.cli

import com.beust.jcommander.JCommander
import kotlin.system.measureNanoTime

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

    if (cliArgs.benchmark) {
        var totalTime = 0L
        repeat(cliArgs.benchmarkRuns) {
            val parseTime = measureNanoTime { parse(cliArgs.files) }
            totalTime += parseTime
            println("Took ${format(parseTime)}ms")
        }

        println("Average took ${format(totalTime / cliArgs.benchmarkRuns)}ms")
    } else {
        parse(cliArgs.files, cliArgs.dumpAst)
    }
}

private fun format(timeInNs: Long): String {
    val timeInMs = timeInNs.toDouble() / 1_000_000.0
    return String.format("%.2f", timeInMs)

}
