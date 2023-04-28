package tools.samt.ls

import com.beust.jcommander.JCommander

fun main(args: Array<String>) {
    val cliArgs = CliArgs()
    val jCommander = JCommander.newBuilder()
        .addObject(cliArgs)
        .programName("java -jar samt-ls.jar")
        .build()
    jCommander.parse(*args)
    if (cliArgs.help) {
        jCommander.usage()
        return
    }
}
