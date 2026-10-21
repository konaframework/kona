package org.konaframework.support.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourceBlocksTest {
    @Test
    fun joinTrimsEachBlockAndSkipsEmpty() {
        assertEquals("\n", SourceBlocks.join())
        assertEquals("\n", SourceBlocks.join("  ", "\n\n"))
        val joined =
            SourceBlocks.join(
                """
                import a from 'a';
                import b from 'b';
                """.trimIndent(),
                "",
                """
                    function hot() {
                      return true;
                    }
                """.trimIndent(),
                "export default {};",
            )
        assertEquals(
            """
            import a from 'a';
            import b from 'b';

            function hot() {
              return true;
            }

            export default {};
            """.trimIndent() + "\n",
            joined,
        )
        assertEquals("a\n", SourceBlocks.join("a"))
    }

    @Test
    fun replaceLineIndentsOrRemoves() {
        val source =
            """
            <body>
              __BREW__
              <footer></footer>
            </body>

            """.trimIndent()
        val withBlock =
            SourceBlocks.replaceLine(
                source,
                "__BREW__",
                """
                <section>
                  <p>ok</p>
                </section>
                """,
            )
        assertEquals(
            """
            <body>
              <section>
                <p>ok</p>
              </section>
              <footer></footer>
            </body>

            """.trimIndent(),
            withBlock,
        )
        assertEquals(
            """
            <body>
              <footer></footer>
            </body>

            """.trimIndent(),
            SourceBlocks.replaceLine(source, "__BREW__", null),
        )
        assertEquals(
            """
            <body>
              <footer></footer>
            </body>

            """.trimIndent(),
            SourceBlocks.replaceLine(source, "__BREW__", "   "),
        )
        val unchanged = "<html></html>\n"
        assertEquals(unchanged, SourceBlocks.replaceLine(unchanged, "__BREW__", "<section></section>"))
        val tabbed = "\t__TOKEN__\nnext\n"
        val replaced = SourceBlocks.replaceLine(tabbed, "__TOKEN__", "a\n\nb")
        assertEquals("\ta\n\n\tb\nnext\n", replaced)
        val crlf = "head\r\n  __X__\r\ntail\r\n"
        assertEquals("head\r\n  yes\ntail\r\n", SourceBlocks.replaceLine(crlf, "__X__", "yes"))
    }
}
