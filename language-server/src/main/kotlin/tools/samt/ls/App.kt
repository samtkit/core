package tools.samt.ls

import com.beust.jcommander.JCommander
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

private fun startServer(inStream: InputStream, outStream: OutputStream) {
    SamtLanguageServer().use {
        val launcher = LSPLauncher.createServerLauncher(it, inStream, outStream)
        val client = launcher.remoteProxy
        redirectLogs(client)
        it.connect(client)
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
            println("Connecting to client at ${it.remoteSocketAddress}:${it.port}")
            startServer(it.inputStream, it.outputStream)
        }
        return
    }

    cliArgs.serverPort?.also { port ->
        ServerSocket(port).use {
            println("Starting server on port ${it.localPort}")
            val socket = it.accept()
            startServer(socket.inputStream, socket.outputStream)
        }
        return
    }

    if (cliArgs.isStdio) {
        startServer(System.`in`, System.out)
        return
    }

    jCommander.usage()
}