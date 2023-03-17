package cli

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
            println("Took ${gugus(parseTime)}ms")
        }

        println("Average took ${gugus(totalTime / cliArgs.benchmarkRuns)}ms")
    } else {
        parse(cliArgs.files)
    }
}

private fun gugus(timeInNs: Long): String {
    // 0.15ms
    val timeInMs = timeInNs.toDouble() / 1_000_000.0
    return String.format("%.2f", timeInMs)

}
