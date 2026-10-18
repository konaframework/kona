package sample

import bootstrap.App
import com.fasterxml.jackson.databind.ObjectMapper
import org.konaframework.brew.Brew
import org.konaframework.core.Kona
import org.konaframework.http.HttpKernel
import org.konaframework.http.Request
import org.konaframework.http.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class BrewSampleTest {
    private val json = ObjectMapper()

    @AfterEach
    fun reset() {
        Brew.reset()
        Kona.reset()
    }

    private fun kernel(): HttpKernel {
        val local = Path.of("src/main/kotlin/bootstrap")
        val base = if (Files.isDirectory(local)) Path.of("") else Path.of("samples/brew-kotlin")
        return App.create(arrayOf("--base=${base.toAbsolutePath().normalize()}")).resolve(HttpKernel::class.java)
    }

    private fun cookieHeader(response: Response) =
        response.cookies.joinToString("; ") { it.toSetCookieHeader().substringBefore(';') }

    private fun attr(html: String, name: String, from: Int = 0): String {
        val start = html.indexOf("$name=\"", from) + name.length + 2
        return html.substring(start, html.indexOf('"', start))
            .replace("&quot;", "\"").replace("&#039;", "'").replace("&amp;", "&")
    }

    @Test
    fun `page renders every component with injected assets`() {
        val page = kernel().handle(Request.get("/"))
        val html = page.body

        assertEquals(200, page.status)
        assertTrue(Regex("wire:snapshot=").findAll(html).count() >= 5, html)
        assertTrue(html.contains("<output>3</output>"), html)
        assertTrue(html.contains("Grind the beans"), html)
        assertTrue(html.contains("Kona Peaberry"), html)
        assertTrue(html.contains("Dashboard") && html.contains("wire:island=\"stats\""), html)
        assertTrue(html.contains("Loading panel") || html.contains("Lazy ready"), html)
        assertTrue(Regex("""<script src="/brew/brew(\.min)?\.js\?id=[0-9a-f]{8}" data-csrf="[^"]+" data-update-uri="/brew/update"""").containsMatchIn(html), html)
        assertTrue(html.contains("<!-- Brew Styles -->"), html)
    }

    @Test
    fun `clicking increment round-trips through the update endpoint`() {
        val kernel = kernel()
        val page = kernel.handle(Request.get("/"))
        val html = page.body
        val counter = html.indexOf("wire:name=\"counter\"").let { html.lastIndexOf("wire:snapshot=", it) }
        val payload = mapOf(
            "_token" to attr(html, "data-csrf"),
            "components" to listOf(mapOf(
                "snapshot" to attr(html, "wire:snapshot", counter),
                "updates" to emptyMap<String, Any>(),
                "calls" to listOf(mapOf("path" to "", "method" to "increment", "params" to emptyList<Any>(), "metadata" to emptyMap<String, Any>())),
            )),
        )

        val update = kernel.handle(Request(
            "POST", "/brew/update",
            headers = mapOf("Content-Type" to "application/json", "X-Brew" to "1", "Cookie" to cookieHeader(page)),
            body = json.writeValueAsString(payload),
        ))

        assertEquals(200, update.status, update.body)
        val component = json.readTree(update.body)["components"][0]
        assertEquals(4, json.readTree(component["snapshot"].asText())["data"]["count"].asInt())
        assertTrue(component["effects"]["html"].asText().contains("<output>4</output>"))
    }

    /**
     * Kit-equivalent smoke: Todos mount + `/brew/update` add
     * (complements CLI `GeneratedAppBuildTest` when mavenLocal is published).
     */
    @Test
    fun `adding a todo round-trips through the update endpoint`() {
        val kernel = kernel()
        val page = kernel.handle(Request.get("/"))
        val html = page.body
        assertTrue(html.contains("wire:name=\"todos\""), html)
        assertTrue(html.contains("1 left") || html.contains("(1 left)"), html)
        val todos = html.indexOf("wire:name=\"todos\"").let { html.lastIndexOf("wire:snapshot=", it) }
        val payload = mapOf(
            "_token" to attr(html, "data-csrf"),
            "components" to listOf(
                mapOf(
                    "snapshot" to attr(html, "wire:snapshot", todos),
                    "updates" to mapOf("draft" to "Pull espresso"),
                    "calls" to listOf(
                        mapOf(
                            "path" to "",
                            "method" to "add",
                            "params" to emptyList<Any>(),
                            "metadata" to emptyMap<String, Any>(),
                        ),
                    ),
                ),
            ),
        )

        val update = kernel.handle(
            Request(
                "POST",
                "/brew/update",
                headers = mapOf("Content-Type" to "application/json", "X-Brew" to "1", "Cookie" to cookieHeader(page)),
                body = json.writeValueAsString(payload),
            ),
        )

        assertEquals(200, update.status, update.body)
        val component = json.readTree(update.body)["components"][0]
        val effectsHtml = component["effects"]["html"].asText()
        assertTrue(effectsHtml.contains("Pull espresso"), update.body)
        assertTrue(effectsHtml.contains("2 left") || effectsHtml.contains("(2 left)"), update.body)
    }

    @Test
    fun `updates without a valid csrf token are rejected`() {
        val kernel = kernel()
        val html = kernel.handle(Request.get("/")).body
        val payload = mapOf("_token" to "wrong", "components" to listOf(mapOf("snapshot" to attr(html, "wire:snapshot"), "updates" to emptyMap<String, Any>(), "calls" to emptyList<Any>())))

        val update = kernel.handle(Request("POST", "/brew/update", headers = mapOf("Content-Type" to "application/json", "X-Brew" to "1"), body = json.writeValueAsString(payload)))

        assertEquals(419, update.status)
    }

    @Test
    fun `the browser runtime is served with long-lived caching`() {
        val script = kernel().handle(Request.get("/brew/brew.min.js"))

        assertEquals(200, script.status)
        assertTrue(script.headers["Cache-Control"].orEmpty().contains("immutable"), script.headers.toString())
        assertTrue(script.bodyBytes().size > 50_000)
    }
}
