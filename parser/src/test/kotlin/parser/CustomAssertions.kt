package parser

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

fun assertIdentifier(expected: String?, actual: IdentifierNode?) {
    assertEquals(expected, actual?.name)
}

fun assertBundleIdentifier(expected: String, actual: BundleIdentifierNode) {
    val expectedParts = expected.split(".")
    assertEquals(expectedParts.size, actual.components.size)
    for (i in expectedParts.indices) {
        assertEquals(expectedParts[i], actual.components[i].name)
    }
}

fun assertPackage(expectedPackageIdentifier: String, packageDeclaration: PackageDeclarationNode) {
    assertBundleIdentifier(expectedPackageIdentifier, packageDeclaration.name)
}

fun assertEmpty(nodes: List<Node>) {
    assertContentEquals(emptyList(), nodes, "Expected no nodes")
}
