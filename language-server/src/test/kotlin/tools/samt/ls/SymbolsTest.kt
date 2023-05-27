package tools.samt.ls

import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import tools.samt.common.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals

class SymbolsTest {
    @Test
    fun `getSymbols returns correct symbols`() {
        val source = """
            package foo.bar
            enum Foo {
                A
            }
            record Bar {
                a: Int
            }
            service Baz {
                a()
            }
            provide BazProvider {
                implements Baz
                
                transport http
            }
            consume BazProvider {
                uses Baz
            }
        """.trimIndent()
        val sourceFile = SourceFile("file:///tmp/test/src/model.samt".toPathUri(), source)
        val fileInfo = parseFile(sourceFile)
        val symbols = fileInfo.fileNode?.getSymbols()
        assertEquals(
            listOf(
                DocumentSymbol(
                    "foo.bar",
                    SymbolKind.Package,
                    Range(Position(0, 0), Position(0, 15)),
                    Range(Position(0, 8), Position(0, 15))
                ),
                DocumentSymbol(
                    "Foo",
                    SymbolKind.Enum,
                    Range(Position(1, 0), Position(3, 1)),
                    Range(Position(1, 5), Position(1, 8))
                ).apply {
                    children = listOf(
                        DocumentSymbol(
                            "A",
                            SymbolKind.EnumMember,
                            Range(Position(2, 4), Position(2, 5)),
                            Range(Position(2, 4), Position(2, 5))
                        )
                    )
                },
                DocumentSymbol(
                    "Bar",
                    SymbolKind.Struct,
                    Range(Position(4, 0), Position(6, 1)),
                    Range(Position(4, 7), Position(4, 10))
                ).apply {
                    children = listOf(
                        DocumentSymbol(
                            "a",
                            SymbolKind.Property,
                            Range(Position(5, 4), Position(5, 10)),
                            Range(Position(5, 4), Position(5, 5))
                        )
                    )
                },
                DocumentSymbol(
                    "Baz",
                    SymbolKind.Interface,
                    Range(Position(7, 0), Position(9, 1)),
                    Range(Position(7, 8), Position(7, 11))
                ).apply {
                    children = listOf(
                        DocumentSymbol(
                            "a",
                            SymbolKind.Method,
                            Range(Position(8, 4), Position(8, 7)),
                            Range(Position(8, 4), Position(8, 5))
                        )
                    )
                },
                DocumentSymbol(
                    "BazProvider",
                    SymbolKind.Class,
                    Range(Position(10, 0), Position(14, 1)),
                    Range(Position(10, 8), Position(10, 19))
                ).apply {  children = emptyList() },
                DocumentSymbol(
                    "Consumer for BazProvider",
                    SymbolKind.Class,
                    Range(Position(15, 0), Position(17, 1)),
                    Range(Position(15, 8), Position(15, 19))
                )
            ),
            symbols
        )
    }
}
