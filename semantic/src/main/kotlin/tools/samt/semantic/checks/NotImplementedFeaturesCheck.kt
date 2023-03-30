package tools.samt.semantic.checks

import tools.samt.common.DiagnosticContext
import tools.samt.parser.*
import tools.samt.semantic.SemanticCheck

internal class NotImplementedFeaturesCheck(private val diagnostics: DiagnosticContext) : SemanticCheck {
    override fun check(fileNode: FileNode) {
        fileNode.statements.forEach {
            when (it) {
                is ProviderDeclarationNode -> checkProvider(it)
                is RecordDeclarationNode -> checkRecord(it)
                is ServiceDeclarationNode -> checkService(it)
                is TypeAliasNode -> reportNotImplemented("Type aliases", it)
                is ConsumerDeclarationNode,
                is TypeImportNode,
                is WildcardImportNode,
                is EnumDeclarationNode,
                is PackageDeclarationNode,
                -> Unit
            }
        }
    }

    private fun checkRecord(record: RecordDeclarationNode) {
        if (record.extends.isNotEmpty()) {
            reportNotImplemented("Record extends", record.extends.first())
        }
    }

    private fun checkService(service: ServiceDeclarationNode) {
        service.operations.forEach {
            when(it) {
                is OnewayOperationNode -> reportNotImplemented("Oneway operations", it)
                is RequestResponseOperationNode -> {
                    if (it.isAsync) {
                        reportNotImplemented("Async operations", it)
                    }
                    if (it.raises.isNotEmpty()) {
                        reportNotImplemented("Operations that raise exceptions", it)
                    }
                }
            }
        }
    }

    private fun checkProvider(provider: ProviderDeclarationNode) {
        if (provider.transport.configuration != null) {
            // A validation for each supported transport and corresponding configuration will replace this in the future
            reportNotImplemented("Transport configurations", provider.transport.configuration!!)
        }
    }

    private fun reportNotImplemented(what: String, node: Node) {
        diagnostics.error {
            message("$what have not yet been implemented")
            highlight("not yet implemented", node.location)
        }
    }
}
