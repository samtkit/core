package tools.samt.ls

import java.net.URI
import kotlin.io.path.toPath

fun String.toPathUri(): URI = URI(this).toPath().toUri()
