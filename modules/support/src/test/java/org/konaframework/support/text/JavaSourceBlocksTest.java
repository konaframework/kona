package org.konaframework.support.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class JavaSourceBlocksTest {
    @Test
    void joinAndReplaceLineFromJava() {
        String joined = SourceBlocks.join("import a;", "export default {};");
        assertTrue(joined.startsWith("import a;"));
        assertTrue(joined.contains("\n\nexport default {};\n"));
        String html = "  __SLOT__\n";
        assertEquals("  <p></p>\n", SourceBlocks.replaceLine(html, "__SLOT__", "<p></p>"));
        assertEquals("", SourceBlocks.replaceLine(html, "__SLOT__"));
    }
}
