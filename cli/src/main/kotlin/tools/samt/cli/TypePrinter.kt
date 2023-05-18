package tools.samt.cli

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import tools.samt.semantic.Package

internal object TypePrinter {
    fun dump(samtPackage: Package): String = buildString {
        appendLine(blue(samtPackage.name.ifEmpty { "<root>" }))
        for (enum in samtPackage.enums) {
            appendLine(" ${bold("enum")} ${yellow(enum.name)}")
        }
        for (record in samtPackage.records) {
            appendLine(" ${bold("record")} ${yellow(record.name)}")
        }
        for (alias in samtPackage.aliases) {
            appendLine(" ${bold("typealias")} ${yellow(alias.name)} = ${gray(alias.fullyResolvedType?.humanReadableName ?: "Unknown")}")
        }
        for (service in samtPackage.services) {
            appendLine(" ${bold("service")} ${yellow(service.name)}")
        }
        for (provider in samtPackage.providers) {
            appendLine(" ${bold("provider")} ${yellow(provider.name)}")
        }
        for (consumer in samtPackage.consumers) {
            appendLine(" ${bold("consumer")} for ${yellow(consumer.provider.humanReadableName)}")
        }

        val childDumps: List<String> = samtPackage.subPackages.map { dump(it) }

        childDumps.forEachIndexed { childIndex, child ->
            var firstLine = true
            child.lineSequence().forEach { line ->
                if (line.isNotEmpty()) {
                    if (childIndex != childDumps.lastIndex) {
                        if (firstLine) {
                            append("${white("├─")}$line")
                        } else {
                            append("${white("│ ")}$line")
                        }
                    } else {
                        if (firstLine) {
                            append("${white("└─")}$line")
                        } else {
                            append("  $line")
                        }
                    }

                    appendLine()
                }

                firstLine = false
            }
        }
    }
}
