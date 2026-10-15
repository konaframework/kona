package dev.kona.http

import dev.kona.core.Application
import dev.kona.core.Kona
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.function.Supplier

class TrustAndHttpMiddlewareTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun reset() = Kona.reset()

    @Test
    fun trustProxiesSetsClientIpWhenTrusted() {
        val mw = TrustProxies()
        val request = Request(
            "GET",
            "/",
            headers = mapOf(
                "X-Forwarded-For" to "203.0.113.10, 10.0.0.1",
                "X-Forwarded-Proto" to "https",
                "X-Forwarded-Host" to "app.example.com",
                "X-Forwarded-Port" to "443",
            ),
        )
        var seen: Request? = null
        mw.handle(request) { req ->
            seen = req
            Response.text("ok")
        }
        assertEquals("203.0.113.10", seen!!.getAttribute(TrustProxies.CLIENT_IP))
        assertEquals("https", seen.getAttribute(TrustProxies.SCHEME))
        assertEquals("app.example.com", seen.getAttribute(TrustProxies.HOST))
        assertEquals("443", seen.getAttribute(TrustProxies.PORT))
    }

    @Test
    fun trustProxiesSkipsWhenPeerNotTrusted() {
        val mw = TrustProxies(listOf("10.0.0.1"))
        val request = Request("GET", "/", headers = mapOf("X-Forwarded-For" to "203.0.113.10"))
        request.setAttribute(TrustProxies.REMOTE_ADDR, "192.168.1.1")
        mw.handle(request) { Response.text("ok") }
        assertNull(request.getAttribute(TrustProxies.CLIENT_IP))
    }

    @Test
    fun trustProxiesTrustedPeerUsesRealIpAndSkipsEmptyForwarded() {
        val mw = TrustProxies(listOf("10.0.0.1"))
        val request = Request(
            "GET",
            "/",
            headers = mapOf(
                "X-Forwarded-For" to " , ",
                "X-Real-IP" to "198.51.100.7",
            ),
        )
        request.setAttribute(TrustProxies.REMOTE_ADDR, "10.0.0.1")
        mw.handle(request) { Response.text("ok") }
        assertEquals("198.51.100.7", request.getAttribute(TrustProxies.CLIENT_IP))
    }

    @Test
    fun trustProxiesIgnoresMissingClientIpAndBlankForwardedHeaders() {
        val mw = TrustProxies(
            listOf("*"),
            setOf(
                TrustProxies.HEADER_FORWARDED_FOR,
                TrustProxies.HEADER_REAL_IP,
                TrustProxies.HEADER_FORWARDED_PROTO,
                TrustProxies.HEADER_FORWARDED_HOST,
                TrustProxies.HEADER_FORWARDED_PORT,
            ),
        )
        val request = Request(
            "GET",
            "/",
            headers = mapOf(
                "X-Forwarded-Proto" to "  ",
                "X-Forwarded-Host" to "",
                "X-Forwarded-Port" to " ",
                "X-Real-IP" to " ",
            ),
        )
        mw.handle(request) { Response.text("ok") }
        assertNull(request.getAttribute(TrustProxies.CLIENT_IP))
        assertNull(request.getAttribute(TrustProxies.SCHEME))
        assertNull(request.getAttribute(TrustProxies.HOST))
        assertNull(request.getAttribute(TrustProxies.PORT))
    }

    @Test
    fun trustProxiesSubsetHeadersSkipIpExtraction() {
        val mw = TrustProxies(listOf("*"), setOf(TrustProxies.HEADER_FORWARDED_PROTO))
        val request = Request(
            "GET",
            "/",
            headers = mapOf(
                "X-Forwarded-For" to "203.0.113.10",
                "X-Forwarded-Proto" to "https",
            ),
        )
        mw.handle(request) { Response.text("ok") }
        assertNull(request.getAttribute(TrustProxies.CLIENT_IP))
        assertEquals("https", request.getAttribute(TrustProxies.SCHEME))
    }

    @Test
    fun trustProxiesRequiresRemoteAddrWhenNotTrustAny() {
        val mw = TrustProxies(listOf("10.0.0.1"))
        val request = Request("GET", "/", headers = mapOf("X-Forwarded-For" to "203.0.113.10"))
        mw.handle(request) { Response.text("ok") }
        assertNull(request.getAttribute(TrustProxies.CLIENT_IP))
    }

    @Test
    fun trustProxiesMatchesPrefixPatterns() {
        assertTrue(TrustProxies.peerMatches("10.0.0.5", "10.0.*"))
        assertTrue(TrustProxies.peerMatches("10.0.0.5", "10.0.0.5"))
        assertTrue(TrustProxies.peerMatches("10.0.0.5", "*"))
        assertTrue(TrustProxies.peerMatches("10.0.0.5", "10.0.0.0/0"))
        assertTrue(TrustProxies.peerMatches("10.0", "10.0.*"))
        assertFalse(TrustProxies.peerMatches("11.0.0.5", "10.0.*"))
    }

    @Test
    fun trustHostsAllowsAndRejects() {
        val mw = TrustHosts(listOf("example.com", "*.app.test"))
        assertEquals(200, mw.handle(Request("GET", "/", headers = mapOf("Host" to "example.com:8080"))) { Response.text("ok") }.status)
        assertEquals(200, mw.handle(Request("GET", "/", headers = mapOf("Host" to "api.app.test"))) { Response.text("ok") }.status)
        assertEquals(400, mw.handle(Request("GET", "/", headers = mapOf("Host" to "evil.com"))) { Response.text("ok") }.status)
        assertEquals(400, mw.handle(Request.get("/")) { Response.text("ok") }.status)
        assertTrue(TrustHosts.hostMatches("x.com", "*"))
        assertFalse(TrustHosts.hostMatches("app.test", "*.app.test"))
    }

    @Test
    fun httpMiddlewareAppliesWebStackAndAliases() {
        val app = Kona.create(arrayOf("--base=$temp"))
            .providers(HttpServiceProvider())
            .afterBoot { application ->
                HttpMiddleware()
                    .alias(
                        mapOf(
                            "mark" to Supplier { Middleware { req, next -> next.handle(req).header("X-Mark", "1") } },
                        ),
                    )
                    .web(prepend = listOf("mark"))
                    .apply(application)
            }
            .boot()
        val kernel = app.resolve(HttpKernel::class.java)
        app.resolve(Router::class.java).get("/") { Response.text("hi") }
        val response = kernel.handle(Request.get("/"))
        assertEquals("1", response.headers["X-Mark"])
        assertTrue(app.resolve(HttpMiddleware::class.java).apiStack().isEmpty())
    }

    @Test
    fun httpMiddlewareUnknownAliasFails() {
        assertThrows(IllegalArgumentException::class.java) {
            HttpMiddleware().web(prepend = listOf("missing")).webStack()
        }
    }

    @Test
    fun httpMiddlewareTrustAndApiStack() {
        val mw = HttpMiddleware()
            .alias("a") { Middleware { r, n -> n.handle(r) } }
            .api()
            .api(prepend = listOf("a"))
            .trustProxies()
            .trustProxies(listOf("10.0.0.1"))
            .trustHosts(listOf("localhost"))
        assertEquals(1, mw.apiStack().size)
        val app = Application(temp)
        app.register(HttpServiceProvider())
        app.boot()
        mw.apply(app)
        assertEquals(2, app.resolve(HttpKernel::class.java).globalMiddleware.size)
    }

    @Test
    fun trustProxiesRealIpOnlyAndMissingOptionalHeaders() {
        val mw = TrustProxies(
            listOf("*"),
            setOf(TrustProxies.HEADER_REAL_IP, TrustProxies.HEADER_FORWARDED_HOST),
        )
        val request = Request("GET", "/", headers = mapOf("X-Real-IP" to "203.0.113.9"))
        mw.handle(request) { Response.text("ok") }
        assertEquals("203.0.113.9", request.getAttribute(TrustProxies.CLIENT_IP))
        assertNull(request.getAttribute(TrustProxies.HOST))
        assertNull(request.getAttribute(TrustProxies.SCHEME))
        assertNull(request.getAttribute(TrustProxies.PORT))
    }
}
