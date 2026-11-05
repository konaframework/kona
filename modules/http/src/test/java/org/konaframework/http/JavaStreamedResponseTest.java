package org.konaframework.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaStreamedResponseTest {
    @Test
    void streamDownloadAndEventStreamOverloads() {
        Response download =
                Response.streamDownload(
                        out -> {
                            try {
                                out.write("dl".getBytes(StandardCharsets.UTF_8));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        },
                        "a.csv");
        assertTrue(download.isStreamed());
        assertTrue(download.getHeaders().get("Content-Disposition").contains("a.csv"));
        assertEquals("dl", new String(download.bodyBytes(), StandardCharsets.UTF_8));

        Response sse =
                Response.eventStream(
                        emit -> {
                            emit.comment();
                            emit.comment("x");
                            emit.emit("hi");
                            emit.emit(new StreamedEvent("ping", "x"));
                            emit.emit("n", 1);
                            emit.emit("n", "d", "9");
                        });
        assertTrue(sse.isStreamed());
        String body = new String(sse.bodyBytes(), StandardCharsets.UTF_8);
        assertTrue(body.contains("data: hi"));
        assertTrue(body.contains("</stream>"));

        Response withHeaders =
                Response.eventStream(
                        emit -> emit.emit("a"),
                        Map.of("X-A", "1"),
                        null,
                        Duration.ZERO);
        assertEquals("1", withHeaders.getHeaders().get("X-A"));
        assertTrue(new String(withHeaders.bodyBytes()).contains("data: a"));

        Request request = new Request("GET", "/sse", Map.of("Last-Event-ID", "3"), Map.of(), "");
        Response fromRequest =
                Response.eventStream(
                        request,
                        emit -> assertEquals("3", emit.lastEventId()));
        fromRequest.bodyBytes();

        assertTrue(Response.stream(out -> {}, "text/plain", 200).isStreamed());
        assertTrue(
                Response.streamDownload(
                                out -> {},
                                "x.txt",
                                Map.of(),
                                "text/plain",
                                "attachment",
                                200,
                                null)
                        .isStreamed());
        assertEquals(null, StreamedBody.StreamContext.materialize().getLastEventId());
        assertEquals("z", StreamedBody.StreamContext.materialize("z").getLastEventId());
    }
}
