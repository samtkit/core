package tools.samt.ls

import com.beust.jcommander.JCommander
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.Socket


private fun startServer(inStream: InputStream, outStream: OutputStream, trace: PrintWriter? = null) {
    SamtLanguageServer().use { server ->
        val launcher = LSPLauncher.createServerLauncher(server, inStream, outStream, false, trace)
        val client = launcher.remoteProxy
        redirectLogs(client)
        server.connect(client)
        launcher.startListening().get()
    }
}

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

    cliArgs.clientPort?.also { port ->
        Socket(cliArgs.clientHost, port).use {
            println("Connecting to client at ${it.remoteSocketAddress}")
            startServer(it.inputStream, it.outputStream, PrintWriter(System.out))
        }
        return
    }

    if (cliArgs.isStdio) {
        startServer(System.`in`, System.out)
        return
    }

    jCommander.usage()
}
