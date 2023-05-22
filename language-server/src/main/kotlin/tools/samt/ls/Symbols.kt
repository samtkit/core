package tools.samt.ls

import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.SymbolKind
import tools.samt.parser.*

fun FileNode.getSymbols(): List<DocumentSymbol> = buildList {
    add(packageDeclaration.toSymbol())
    statements.filterIsInstance<NamedDeclarationNode>().mapTo(this) { it.toSymbol() }
}

private fun NamedDeclarationNode.toSymbol(): DocumentSymbol {
    val kind = when (this) {
        is EnumDeclarationNode -> SymbolKind.Enum
        is ProviderDeclarationNode -> SymbolKind.Class
        is RecordDeclarationNode -> SymbolKind.Struct
        is ServiceDeclarationNode -> SymbolKind.Interface
        is TypeAliasNode -> SymbolKind.Class
    }
    val children = when (this) {
        is EnumDeclarationNode -> values.map { DocumentSymbol(it.name, SymbolKind.EnumMember, it.location.toRange(), it.location.toRange()) }
        is RecordDeclarationNode -> fields.map { DocumentSymbol(it.name.name, SymbolKind.Property, it.location.toRange(), it.name.location.toRange()) }
        is ServiceDeclarationNode -> operations.map { DocumentSymbol(it.name.name, SymbolKind.Method, it.location.toRange(), it.name.location.toRange()) }
        is ProviderDeclarationNode, is TypeAliasNode -> emptyList()
    }
    return DocumentSymbol(name.name, kind, location.toRange(), name.location.toRange()).apply {
        this.children = children
    }
}

private fun PackageDeclarationNode.toSymbol() = DocumentSymbol(name.name, SymbolKind.Package, location.toRange(), name.location.toRange())
