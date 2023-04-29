package tools.samt.ls

import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

fun redirectLogs(client: LanguageClient) {
    fun Level.toMessageType(): MessageType = when (this) {
        Level.SEVERE -> MessageType.Error
        Level.WARNING -> MessageType.Warning
        Level.INFO -> MessageType.Info
        Level.CONFIG -> MessageType.Log
        Level.FINE -> MessageType.Log
        Level.FINER -> MessageType.Log
        Level.FINEST -> MessageType.Log
        else -> MessageType.Log
    }
    fun LogRecord.toMessageParams(): MessageParams = MessageParams(
        level.toMessageType(),
        message
    )


    val rootLogger = Logger.getLogger("")
    rootLogger.addHandler(object : Handler() {
        override fun publish(record: LogRecord) {
            client.logMessage(record.toMessageParams())
        }

        override fun flush() {
        }

        override fun close() {
        }
    })
}
