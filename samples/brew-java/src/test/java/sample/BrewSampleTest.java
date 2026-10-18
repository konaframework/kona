package sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bootstrap.App;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.konaframework.brew.Brew;
import org.konaframework.core.Kona;
import org.konaframework.http.HttpKernel;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BrewSampleTest {
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void reset() {
        Brew.reset();
        Kona.reset();
    }

    private HttpKernel kernel() {
        Path base = Files.isDirectory(Path.of("src/main/java/bootstrap")) ? Path.of("") : Path.of("samples/brew-java");
        return App.create(new String[] {"--base=" + base.toAbsolutePath().normalize()}).resolve(HttpKernel.class);
    }

    private static String cookieHeader(Response response) {
        return response.getCookies().stream()
            .map(cookie -> cookie.toSetCookieHeader().split(";", 2)[0])
            .collect(Collectors.joining("; "));
    }

    private static String attr(String html, String name, int from) {
        int start = html.indexOf(name + "=\"", from) + name.length() + 2;
        return html.substring(start, html.indexOf('"', start))
            .replace("&quot;", "\"").replace("&#039;", "'").replace("&amp;", "&");
    }

    @Test
    void pageRendersEveryComponentWithInjectedAssets() {
        Response page = kernel().handle(Request.get("/"));
        String html = page.getBody();

        assertEquals(200, page.getStatus());
        assertTrue(html.split("wire:snapshot=", -1).length - 1 >= 5, html);
        assertTrue(html.contains("<output>3</output>"), html);
        assertTrue(html.contains("Steam the milk"), html);
        assertTrue(html.contains("Kona Peaberry"), html);
        assertTrue(html.contains("Dashboard") && html.contains("wire:island=\"stats\""), html);
        assertTrue(html.contains("Loading panel") || html.contains("Lazy ready"), html);
        assertTrue(Pattern.compile("<script src=\"/brew/brew(\\.min)?\\.js\\?id=[0-9a-f]{8}\" data-csrf=\"[^\"]+\"").matcher(html).find(), html);
    }

    @Test
    void addingATodoRoundTripsThroughTheUpdateEndpoint() throws Exception {
        HttpKernel kernel = kernel();
        Response page = kernel.handle(Request.get("/"));
        String html = page.getBody();
        int todos = html.lastIndexOf("wire:snapshot=", html.indexOf("wire:name=\"todos\""));
        Map<String, Object> payload = Map.of(
            "_token", attr(html, "data-csrf", 0),
            "components", List.of(Map.of(
                "snapshot", attr(html, "wire:snapshot", todos),
                "updates", Map.of("draft", "Pour over"),
                "calls", List.of(Map.of("path", "", "method", "add", "params", List.of(), "metadata", Map.of())))));

        Response update = kernel.handle(new Request(
            "POST", "/brew/update",
            Map.of("Content-Type", "application/json", "X-Brew", "1", "Cookie", cookieHeader(page)),
            Map.of(),
            json.writeValueAsString(payload)));

        assertEquals(200, update.getStatus(), update.getBody());
        JsonNode component = json.readTree(update.getBody()).get("components").get(0);
        JsonNode data = json.readTree(component.get("snapshot").asText()).get("data");
        assertEquals("", data.get("draft").asText());
        assertTrue(component.get("effects").get("html").asText().contains("Pour over"));
        assertTrue(component.get("effects").get("html").asText().contains("(2 left)"));
    }
}
