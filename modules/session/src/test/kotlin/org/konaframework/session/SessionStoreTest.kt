package org.konaframework.session

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

class SessionStoreTest {
    @Test
    fun arrayStoreRoundTripAndClear() {
        val store = ArraySessionStore()
        val id = SessionId.generate()
        assertTrue(store.read(id).isEmpty())
        store.write(id, mapOf("a" to 1), 10)
        assertEquals(1, store.read(id)["a"])
        assertEquals(1, store.size())
        store.destroy(id)
        assertTrue(store.read(id).isEmpty())
        store.write(id, mapOf("b" to 2), 10)
        store.clear()
        assertEquals(0, store.size())
    }

    @Test
    fun fileStoreRoundTripExpiryAndInvalid(@TempDir dir: Path) {
        val store = FileSessionStore(dir)
        val id = SessionId.generate()
        store.write(id, mapOf("k" to "v"), 30)
        assertEquals("v", store.read(id)["k"])

        store.write("not-valid-id!!!!!!!!!!!", mapOf("x" to 1), 10)
        assertTrue(store.read("not-valid-id!!!!!!!!!!!").isEmpty())
        store.destroy("@@@@")

        assertTrue(store.read(SessionId.generate()).isEmpty())

        val expiredId = SessionId.generate()
        val mapper = jacksonObjectMapper()
        Files.writeString(
            dir.resolve(expiredId),
            mapper.writeValueAsString(
                mapOf(
                    "expires_at" to System.currentTimeMillis() - 1_000,
                    "data" to mapOf("gone" to true),
                ),
            ),
        )
        assertTrue(store.read(expiredId).isEmpty())
        assertTrue(!Files.exists(dir.resolve(expiredId)))

        val corruptId = SessionId.generate()
        Files.writeString(dir.resolve(corruptId), "{not-json")
        assertTrue(store.read(corruptId).isEmpty())

        val noDataId = SessionId.generate()
        Files.writeString(
            dir.resolve(noDataId),
            mapper.writeValueAsString(mapOf("expires_at" to System.currentTimeMillis() + 60_000)),
        )
        assertTrue(store.read(noDataId).isEmpty())

        store.destroy(id)
        assertTrue(store.read(id).isEmpty())
        assertTrue(FileSessionStore.isValidId(id))
        assertTrue(!FileSessionStore.isValidId("short"))
    }

    @Test
    fun cookieStoreEncodesPayloadAndHandlesBadInput() {
        val store = CookieSessionStore()
        assertTrue(store.read("any").isEmpty())
        store.inboundPayload = ""
        assertTrue(store.read("any").isEmpty())
        store.inboundPayload = "%%%not-base64%%%"
        assertTrue(store.read("any").isEmpty())

        val id = SessionId.generate()
        store.write(id, mapOf("user" to "ada"), 120)
        assertTrue(!store.outboundPayload.isNullOrBlank())
        assertEquals(id, store.readId(store.outboundPayload))

        store.inboundPayload = store.outboundPayload
        assertEquals("ada", store.read(id)["user"])

        val noData = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"id":"$id"}""".toByteArray())
        store.inboundPayload = noData
        assertTrue(store.read(id).isEmpty())

        assertEquals(null, store.readId(null))
        assertEquals(null, store.readId(""))
        assertEquals(null, store.readId("!!!"))
        assertEquals(null, store.readId(
            Base64.getUrlEncoder().withoutPadding().encodeToString("""{"id":1}""".toByteArray()),
        ))

        store.destroy(id)
        assertEquals("", store.outboundPayload)

        CookieSessionStore(jacksonObjectMapper()).write(id, emptyMap(), 1)
    }

    @Test
    fun cookieStoreEncryptRoundTripAndFallbackWithoutEncrypter() {
        val id = SessionId.generate()
        val plain = CookieSessionStore()
        plain.write(id, mapOf("x" to 1), 10)
        val rawBase64 = plain.outboundPayload
        assertTrue(!rawBase64.startsWith("ENC:"))

        val encrypted = CookieSessionStore(
            encrypt = { json -> "ENC:$json" },
            decrypt = { payload -> payload.removePrefix("ENC:") },
        )
        encrypted.write(id, mapOf("user" to "ada"), 120)
        assertTrue(encrypted.outboundPayload.startsWith("ENC:"))
        assertEquals(id, encrypted.readId(encrypted.outboundPayload))
        encrypted.inboundPayload = encrypted.outboundPayload
        assertEquals("ada", encrypted.read(id)["user"])

        encrypted.inboundPayload = "ENC:not-json"
        assertTrue(encrypted.read(id).isEmpty())
        assertEquals(null, encrypted.readId("ENC:!!!"))
        assertEquals(null, encrypted.readId("bad-cipher-text"))

        val javaFns = CookieSessionStore(
            jacksonObjectMapper(),
            java.util.function.Function { json: String -> "E:$json" },
            java.util.function.Function { payload: String -> payload.removePrefix("E:") },
        )
        javaFns.write(id, mapOf("n" to 2), 1)
        javaFns.inboundPayload = javaFns.outboundPayload
        assertEquals(2, javaFns.read(id)["n"])

        CookieSessionStore(
            jacksonObjectMapper(),
            null as java.util.function.Function<String, String>?,
            null as java.util.function.Function<String, String>?,
        ).write(id, emptyMap(), 1)
    }
}
