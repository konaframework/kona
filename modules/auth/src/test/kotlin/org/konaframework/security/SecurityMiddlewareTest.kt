package org.konaframework.security

import org.konaframework.http.Request
import org.konaframework.http.Response
import org.konaframework.http.TrustProxies
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

class SecurityMiddlewareTest {
    @Test
    fun encrypterRoundTripAndFailures() {
        val key = Encrypter.generateKey()
        val encrypter = Encrypter.fromBase64Key(key)
        val cipher = encrypter.encryptString("hello")
        val mapCipher = encrypter.encrypt(mapOf("a" to 1))
        assertEquals(1, (encrypter.decrypt(mapCipher, Map::class.java)["a"] as Number).toInt())
        assertEquals("hello", encrypter.decryptString(cipher))

        assertThrows(EncryptionException::class.java) { Encrypter(ByteArray(16)) }
        assertThrows(EncryptionException::class.java) { Encrypter.fromBase64Key("!!!") }
        assertThrows(EncryptionException::class.java) {
            Encrypter.fromBase64Key(Base64.getEncoder().encodeToString(ByteArray(8)))
        }
        assertThrows(EncryptionException::class.java) { encrypter.decryptString("not-base64!!!") }
        assertThrows(EncryptionException::class.java) {
            encrypter.decryptString(Base64.getEncoder().encodeToString("[]".toByteArray()))
        }
        assertThrows(EncryptionException::class.java) {
            encrypter.decryptString(Base64.getEncoder().encodeToString("""{"iv":""}""".toByteArray()))
        }
        val badPayload = Base64.getEncoder().encodeToString(
            """{"iv":"YQ==","value":"YQ==","tag":"YQ=="}""".toByteArray(),
        )
        assertThrows(EncryptionException::class.java) { encrypter.decryptString(badPayload) }

        val config = org.konaframework.core.Config()
        assertThrows(EncryptionException::class.java) { Encrypter.fromConfig(config) }
        config.set("app.key", key)
        assertEquals("secret", Encrypter.fromConfig(config).decryptString(Encrypter.fromConfig(config).encryptString("secret")))

        config.set("app.cipher", "AES-256-CBC")
        assertThrows(EncryptionException::class.java) { Encrypter.fromConfig(config) }
        config.set("app.cipher", "")
        assertEquals("ok", Encrypter.fromConfig(config).decryptString(Encrypter.fromConfig(config).encryptString("ok")))
        config.set("app.cipher", Encrypter.CIPHER)

        val previous = Encrypter.generateKey()
        val old = Encrypter.fromBase64Key(previous)
        val payload = old.encryptString("rotated")
        val current = Encrypter.generateKey()
        config.set("app.key", current)
        config.set("app.previous_keys", listOf(previous))
        assertEquals("rotated", Encrypter.fromConfig(config).decryptString(payload))
        config.set("app.previous_keys", previous)
        assertEquals("rotated", Encrypter.fromConfig(config).decryptString(payload))
        config.set("app.previous_keys", arrayOf(previous, "  "))
        assertEquals("rotated", Encrypter.fromConfig(config).decryptString(payload))
        config.set("app.previous_keys", 12)
        assertEquals(listOf("12"), Encrypter.previousKeyStrings(config))
        config.set("app.previous_keys", listOf("  ", null, previous))
        assertEquals(listOf(previous), Encrypter.previousKeyStrings(config))
        config.set("app.previous_keys", listOf("", "  "))
        assertEquals(emptyList<String>(), Encrypter.previousKeyStrings(config))
        config.set("app.previous_keys", arrayOf(null, "", "  "))
        assertEquals(emptyList<String>(), Encrypter.previousKeyStrings(config))
        config.set("app.previous_keys", ", ,")
        assertEquals(emptyList<String>(), Encrypter.previousKeyStrings(config))
        config.set("app.previous_keys", object : Any() {
            override fun toString(): String = "  "
        })
        assertEquals(emptyList<String>(), Encrypter.previousKeyStrings(config))
        val missing = org.konaframework.core.Config()
        assertEquals(emptyList<String>(), Encrypter.previousKeyStrings(missing))
        val mapperCtor = Encrypter(
            java.util.Base64.getDecoder().decode(current.removePrefix("base64:")),
            com.fasterxml.jackson.databind.ObjectMapper(),
        )
        assertEquals("two-arg", mapperCtor.decryptString(mapperCtor.encryptString("two-arg")))
        val fresh = Encrypter.generateKey()
        assertThrows(EncryptionException::class.java) {
            Encrypter.fromBase64Key(fresh).decryptString(payload)
        }
    }

    @Test
    fun securityHeadersMiddleware() {
        val mw = SecurityHeadersMiddleware(
            SecurityHeadersConfig(
                permissionsPolicy = "geolocation=()",
                strictTransportSecurity = "max-age=1",
                contentSecurityPolicy = "default-src 'self'",
                custom = mapOf("X-Custom" to "1"),
            ),
        )
        val response = mw.handle(Request.get("/")) { Response.text("ok") }
        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("SAMEORIGIN", response.headers["X-Frame-Options"])
        assertEquals("no-referrer-when-downgrade", response.headers["Referrer-Policy"])
        assertEquals("0", response.headers["X-XSS-Protection"])
        assertEquals("geolocation=()", response.headers["Permissions-Policy"])
        assertEquals("max-age=1", response.headers["Strict-Transport-Security"])
        assertEquals("default-src 'self'", response.headers["Content-Security-Policy"])
        assertEquals("1", response.headers["X-Custom"])

        val minimal = SecurityHeadersMiddleware(SecurityHeadersConfig(xContentTypeOptions = null, xFrameOptions = null, referrerPolicy = null, xXssProtection = null))
        val bare = minimal.handle(Request.get("/")) { Response.text("x") }
        assertNull(bare.headers["X-Content-Type-Options"])
    }

    @Test
    fun corsPreflightAndCredentials() {
        val cors = CorsMiddleware(
            CorsConfig(
                allowedOrigins = listOf("https://app.test"),
                allowedHeaders = listOf("Content-Type", "X-Custom"),
                exposedHeaders = listOf("X-Trace"),
                allowCredentials = true,
                maxAgeSeconds = 120,
            ),
        )
        val preflight = Request(
            "OPTIONS",
            "/api",
            headers = mapOf(
                "Origin" to "https://app.test",
                "Access-Control-Request-Method" to "POST",
                "Access-Control-Request-Headers" to "Content-Type",
            ),
        )
        val pre = cors.handle(preflight) { Response.text("should-not-run") }
        assertEquals(204, pre.status)
        assertEquals("https://app.test", pre.headers["Access-Control-Allow-Origin"])
        assertEquals("true", pre.headers["Access-Control-Allow-Credentials"])
        assertEquals("X-Trace", pre.headers["Access-Control-Expose-Headers"])
        assertEquals("120", pre.headers["Access-Control-Max-Age"])

        val denied = cors.handle(
            Request("GET", "/", headers = mapOf("Origin" to "https://evil.test")),
        ) { Response.text("ok") }
        assertNull(denied.headers["Access-Control-Allow-Origin"])

        val star = CorsMiddleware(CorsConfig(allowCredentials = false))
        val open = star.handle(Request("GET", "/", headers = mapOf("Origin" to "https://a"))) { Response.text("ok") }
        assertEquals("*", open.headers["Access-Control-Allow-Origin"])

        val starCred = CorsMiddleware(CorsConfig(allowCredentials = true))
        val echoed = starCred.handle(Request("GET", "/", headers = mapOf("Origin" to "https://a"))) { Response.text("ok") }
        assertEquals("https://a", echoed.headers["Access-Control-Allow-Origin"])
        assertTrue(echoed.headers["Vary"]!!.contains("Origin"))

        val noOrigin = star.handle(Request.get("/")) { Response.text("ok") }
        assertEquals("*", noOrigin.headers["Access-Control-Allow-Origin"])

        val starCredNoOrigin = starCred.handle(Request.get("/")) { Response.text("ok") }
        assertNull(starCredNoOrigin.headers["Access-Control-Allow-Origin"])

        val headerStar = CorsMiddleware(CorsConfig(allowedHeaders = listOf("*")))
        val withReqHeaders = headerStar.handle(
            Request(
                "OPTIONS",
                "/",
                headers = mapOf(
                    "Origin" to "https://a",
                    "Access-Control-Request-Method" to "PUT",
                    "Access-Control-Request-Headers" to "X-A, X-B",
                ),
            ),
        ) { Response.text("x") }
        assertEquals("X-A, X-B", withReqHeaders.headers["Access-Control-Allow-Headers"])

        val varyMerge = CorsMiddleware(CorsConfig(allowedOrigins = listOf("https://app.test"), allowCredentials = true))
        val existingVary = Response.text("ok").header("Vary", "Accept-Encoding")
        varyMerge.applyHeaders(Request("GET", "/", headers = mapOf("Origin" to "https://app.test")), existingVary)
        assertTrue(existingVary.headers["Vary"]!!.contains("Accept-Encoding"))
        assertTrue(existingVary.headers["Vary"]!!.contains("Origin"))
        varyMerge.applyHeaders(Request("GET", "/", headers = mapOf("Origin" to "https://app.test")), existingVary)
    }

    @Test
    fun rateLimiterAndThrottleMiddleware() {
        val clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
        val limiter = RateLimiter(clock)
        limiter.forName("api") { RateLimit(2, 60) }
        assertEquals(2, limiter.resolve("api", Request.get("/")).maxAttempts)
        assertThrows(IllegalArgumentException::class.java) { limiter.resolve("missing", Request.get("/")) }

        assertTrue(limiter.attempt("k", 2, 60))
        assertTrue(limiter.attempt("k", 2, 60))
        assertFalse(limiter.attempt("k", 2, 60))
        assertTrue(limiter.tooManyAttempts("k", 2))
        assertEquals(0, limiter.retriesLeft("k", 2))
        assertTrue(limiter.availableIn("k") >= 0)
        limiter.clear("k")
        assertFalse(limiter.tooManyAttempts("k", 2))

        val expiredClock = object : Clock() {
            private var instant = Instant.parse("2020-01-01T00:00:00Z")
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = instant
            fun advance(seconds: Long) {
                instant = instant.plusSeconds(seconds)
            }
        }
        val rotating = RateLimiter(expiredClock)
        rotating.hit("x", 10)
        expiredClock.advance(11)
        assertFalse(rotating.tooManyAttempts("x", 1))
        rotating.hit("y", 10)
        expiredClock.advance(11)
        assertEquals(5, rotating.retriesLeft("y", 5))
        rotating.clearAll()

        val throttle = ThrottleRequests(limiter.also { it.forName("login") { RateLimit(1, 30) } }, "login")
        val first = throttle.handle(Request.get("/").setAttribute("ip", "1.1.1.1")) { Response.text("ok") }
        assertEquals("1", first.headers["X-RateLimit-Limit"])
        assertEquals("0", first.headers["X-RateLimit-Remaining"])
        val ex = assertThrows(RateLimitExceededException::class.java) {
            throttle.handle(Request.get("/").setAttribute("ip", "1.1.1.1")) { Response.text("ok") }
        }
        assertEquals(429, ex.status)
        assertTrue(ex.headers.containsKey("Retry-After"))

        val byHeader = ThrottleRequests(limiter.also { it.clearAll(); it.forName("h") { RateLimit(1) } }, "h")
        byHeader.handle(Request("GET", "/", headers = mapOf("X-Forwarded-For" to "9.9.9.9"))) { Response.text("a") }
        assertThrows(RateLimitExceededException::class.java) {
            byHeader.handle(Request("GET", "/", headers = mapOf("X-Forwarded-For" to "9.9.9.9"))) { Response.text("a") }
        }
        limiter.clearAll()
        val byRealIp = ThrottleRequests(limiter.also { it.forName("r") { RateLimit(1) } }, "r")
        byRealIp.handle(Request("GET", "/", headers = mapOf("X-Real-IP" to "8.8.8.8"))) { Response.text("a") }
        limiter.clearAll()
        val byClientIp = ThrottleRequests(limiter.also { it.forName("c") { RateLimit(1) } }, "c")
        val trusted = Request.get("/").also { it.setAttribute(TrustProxies.CLIENT_IP, "203.0.113.1") }
        byClientIp.handle(trusted) { Response.text("a") }
        assertThrows(RateLimitExceededException::class.java) {
            byClientIp.handle(
                Request.get("/").also { it.setAttribute(TrustProxies.CLIENT_IP, "203.0.113.1") },
            ) { Response.text("a") }
        }
        ThrottleRequests(limiter.also { it.clearAll(); it.forName("g") { RateLimit(1) } }, "g")
            .handle(Request.get("/")) { Response.text("g") }
    }
}
