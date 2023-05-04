package tools.samt.ls

private const val FILE_PROTOCOL = "file://"

fun String.uriToPath(): String = removePrefix(FILE_PROTOCOL)

fun String.pathToUri(): String  = if (startsWith(FILE_PROTOCOL)) this else "$FILE_PROTOCOL$this"