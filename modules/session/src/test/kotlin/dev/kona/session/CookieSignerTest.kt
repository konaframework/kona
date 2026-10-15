package dev.kona.session

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class CookieSignerTest {
    @Test
    fun signAndUnsignRoundTrip() {
        val key = ByteArray(32) { it.toByte() }
        val signed = CookieSigner.sign("""{"id":"abc","data":{}}""", key)
        assertTrue(signed.contains('.'))
        assertEquals("""{"id":"abc","data":{}}""", CookieSigner.unsign(signed, key))
    }

    @Test
    fun unsignRejectsTamperedMacAndMalformed() {
        val key = ByteArray(16) { 1 }
        val signed = CookieSigner.sign("payload", key)
        val sep = signed.lastIndexOf('.')
        val body = signed.substring(0, sep)
        val mac = signed.substring(sep + 1)
        val flipped = mac.mapIndexed { i, c -> if (i == 0) if (c == 'A') 'B' else 'A' else c }.joinToString("")
        assertNull(CookieSigner.unsign("$body.$flipped", key))
        assertNull(CookieSigner.unsign("no-dot", key))
        assertNull(CookieSigner.unsign(".onlymac", key))
        assertNull(CookieSigner.unsign("body.", key))
        assertNull(CookieSigner.unsign("body.!!!not-b64!!!", key))
        assertNull(CookieSigner.unsign("$body.AA", key)) // mac length mismatch
        val otherKey = ByteArray(16) { 2 }
        assertNull(CookieSigner.unsign(signed, otherKey))
    }

    @Test
    fun keyFromAppKeyParsesBase64PrefixAndRaw() {
        val raw = ByteArray(32) { 7 }
        val encoded = "base64:" + Base64.getEncoder().encodeToString(raw)
        assertTrue(CookieSigner.keyFromAppKey(encoded)!!.contentEquals(raw))
        assertTrue(CookieSigner.keyFromAppKey(Base64.getEncoder().encodeToString(raw))!!.contentEquals(raw))
        assertNull(CookieSigner.keyFromAppKey(""))
        assertNull(CookieSigner.keyFromAppKey("   "))
        assertNotEquals(0, CookieSigner.keyFromAppKey("not-valid-base64!!")!!.size)
    }
}
