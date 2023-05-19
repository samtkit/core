package tools.samt.ls

import java.net.URI
import kotlin.io.path.toPath

internal fun String.toPathUri(): URI = URI(this).toPath().toUri()

internal fun URI.startsWith(other: URI): Boolean = toPath().startsWith(other.toPath())
