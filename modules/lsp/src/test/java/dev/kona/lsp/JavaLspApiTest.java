package dev.kona.lsp;

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
