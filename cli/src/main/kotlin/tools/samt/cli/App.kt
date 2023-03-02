package tools.samt.cli

import com.beust.jcommander.JCommander

object App {
    val greeting: String
        get() = "Hello World!"
}

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
    println(App.greeting)
}
