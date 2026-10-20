package org.konaframework.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Java-callable smoke for kona-lsp public surface. */
class JavaLspApiTest {
    @Test
    void directivesAndAnalyzerFromJava() {
        assertTrue(CremaDirectives.isKnown("foreach"));
        assertFalse(CremaDirectives.isKnown("zzz"));
        assertTrue(CremaDirectives.COMPLETION_LABELS.contains("@json"));

        List<CremaAnalyzer.Diagnostic> diags =
                CremaAnalyzer.diagnostics("@csrf @nope");
        assertEquals(1, diags.size());
        assertTrue(diags.get(0).getMessage().contains("@nope"));

        List<CremaAnalyzer.CompletionItem> items =
                CremaAnalyzer.completions("@js", 0, 3);
        assertTrue(items.stream().anyMatch(i -> i.getLabel().equals("@json")));

        assertTrue(
                CremaEchoCatalog.completions("ur").stream()
                        .anyMatch(i -> i.getName().equals("url")));
        List<CremaAnalyzer.CompletionItem> echoItems =
                CremaAnalyzer.completions("{{ ur", 0, 5);
        assertEquals(1, echoItems.size());
        assertEquals("url", echoItems.get(0).getLabel());
        assertEquals("url('')", echoItems.get(0).getInsertText());

        String javaSrc =
                "public class Counter {\n"
                        + "  public int count = 0;\n"
                        + "  public String template() { return \"<div>{{ cou @csrf</div>\"; }\n"
                        + "}\n";
        List<InlineCremaRanges.Range> ranges = InlineCremaRanges.find(javaSrc);
        assertFalse(ranges.isEmpty());
        int offset = javaSrc.indexOf("{{ cou") + 6;
        int line = 0;
        int column = 0;
        for (int i = 0; i < offset; i++) {
            if (javaSrc.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        List<CremaAnalyzer.CompletionItem> hostItems =
                HostDocument.completions(javaSrc, "java", "file:///Counter.java", line, column);
        assertTrue(
                hostItems.stream().anyMatch(i -> i.getLabel().equals("count")),
                hostItems.toString());
        List<CremaAnalyzer.Diagnostic> hostDiags =
                HostDocument.diagnostics(javaSrc, "java", "file:///Counter.java");
        assertTrue(hostDiags.isEmpty() || hostDiags.get(0).getMessage() != null);
        assertEquals("java", HostDocument.languageId("", "file:///Counter.java"));
        assertFalse(HostMembers.members(javaSrc, javaSrc.indexOf("count")).isEmpty());
    }

    @Test
    void serverInitializeFromJava() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String init =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"capabilities\":{}}}";
        String shutdown = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"shutdown\"}";
        String exit = "{\"jsonrpc\":\"2.0\",\"method\":\"exit\"}";
        byte[] payload =
                concat(
                        frame(init),
                        frame(shutdown),
                        frame(exit));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int code = new LspServer().serve(new ByteArrayInputStream(payload), out);
        assertEquals(0, code);

        String raw = out.toString(StandardCharsets.UTF_8);
        assertTrue(raw.contains("kona-lsp"));
        assertTrue(raw.contains("completionProvider"));

        JsonNode ignored = mapper.readTree("{\"ok\":true}");
        assertTrue(ignored.path("ok").asBoolean());
    }

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header =
                ("Content-Length: " + body.length + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII);
        byte[] all = new byte[header.length + body.length];
        System.arraycopy(header, 0, all, 0, header.length);
        System.arraycopy(body, 0, all, header.length, body.length);
        return all;
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] all = new byte[len];
        int at = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, all, at, p.length);
            at += p.length;
        }
        return all;
    }
}
