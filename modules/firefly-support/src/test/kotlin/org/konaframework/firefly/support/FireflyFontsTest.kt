package org.konaframework.firefly.support

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FireflyFontsTest {
    @AfterEach
    fun clear() {
        FireflyFonts.resetCache()
        FireflyFonts.resourceDirectory = "/org/konaframework/firefly/support/fonts/"
    }

    @Test
    fun loadsVendoredWoff2AndBuildsCachedUrls() {
        assertTrue(FireflyFonts.allowed(FireflyFonts.INTER))
        assertFalse(FireflyFonts.allowed("missing.woff2"))
        assertNull(FireflyFonts.asset("missing.woff2"))

        val inter = FireflyFonts.asset(FireflyFonts.INTER)
        assertNotNull(inter)
        assertTrue(inter!!.bytes.isNotEmpty())
        assertEquals(8, inter.hash.length)
        // Second call hits the cache.
        assertEquals(inter.hash, FireflyFonts.asset(FireflyFonts.INTER)!!.hash)

        val dm = FireflyFonts.asset(FireflyFonts.DM_SANS)
        assertNotNull(dm)
        assertTrue(dm!!.bytes.isNotEmpty())

        val url = FireflyFonts.url(FireflyFonts.INTER)
        assertTrue(url.startsWith("${FireflyFonts.PREFIX}/${FireflyFonts.INTER}?id="))
        assertTrue(url.contains(inter.hash))

        val faces = FireflyFonts.css()
        assertTrue(faces.contains("@font-face"))
        assertTrue(faces.contains("font-family: \"Inter\""))
        assertTrue(faces.contains("font-family: \"DM Sans\""))
        assertTrue(faces.contains(FireflyFonts.INTER))
        assertTrue(faces.contains(FireflyFonts.DM_SANS))
        assertFalse(faces.contains("fonts.googleapis.com"))

        assertThrows(FireflyException::class.java) { FireflyFonts.url("nope.woff2") }
        FireflyFonts.resetCache()
        assertNotNull(FireflyFonts.asset(FireflyFonts.INTER))

        // Allowed name but missing classpath entry → null (and url throws).
        FireflyFonts.resetCache()
        FireflyFonts.resourceDirectory = "/org/konaframework/firefly/support/fonts-missing/"
        assertNull(FireflyFonts.asset(FireflyFonts.INTER))
        assertThrows(FireflyException::class.java) { FireflyFonts.url(FireflyFonts.INTER) }
        FireflyFonts.resourceDirectory = "/org/konaframework/firefly/support/fonts/"
    }
}
