package org.konaframework.firefly.support

import java.security.MessageDigest
import java.util.HexFormat

/**
 * Self-hosted Inter and DM Sans (Google font faces, OFL) for panel/auth chrome.
 *
 * Files live under `org/konaframework/firefly/support/fonts/` and are served from
 * `/_firefly/fonts/…` with long-lived cache headers (see Firefly.mount). Theme CSS
 * uses [css] so cold load never waits on fonts.googleapis.com.
 */
object FireflyFonts {
    const val PREFIX: String = "/_firefly/fonts"

    const val INTER: String = "inter-latin-wght-normal.woff2"
    const val DM_SANS: String = "dm-sans-latin-wght-normal.woff2"

    private val ALLOWED = setOf(INTER, DM_SANS)

    private val cache = LinkedHashMap<String, Asset>()

    /** Classpath directory for font files; overridable in tests. */
    @JvmStatic
    @Volatile
    var resourceDirectory: String = "/org/konaframework/firefly/support/fonts/"

    class Asset(val bytes: ByteArray, val hash: String)

    @JvmStatic
    fun allowed(file: String): Boolean = file in ALLOWED

    @JvmStatic
    fun asset(file: String): Asset? {
        if (file !in ALLOWED) return null
        synchronized(cache) {
            cache[file]?.let { return it }
            val stream = FireflyFonts::class.java.getResourceAsStream(resourceDirectory + file)
                ?: return null
            val bytes = stream.use { it.readAllBytes() }
            val hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).take(8)
            val loaded = Asset(bytes, hash)
            cache[file] = loaded
            return loaded
        }
    }

    /** Public URL with content hash for immutable caching. */
    @JvmStatic
    fun url(file: String): String {
        val loaded = asset(file) ?: throw FireflyException("Missing Firefly font [$file]")
        return "$PREFIX/$file?id=${loaded.hash}"
    }

    /**
     * `@font-face` rules for Inter + DM Sans variable latin files.
     * Prefixed so unscoped-body CSS checks in [Stylesheet] stay happy.
     */
    @JvmStatic
    fun css(): String {
        val inter = url(INTER)
        val dm = url(DM_SANS)
        return """
            @font-face {
              font-family: "Inter";
              font-style: normal;
              font-weight: 100 900;
              font-display: swap;
              src: url("$inter") format("woff2");
            }
            @font-face {
              font-family: "DM Sans";
              font-style: normal;
              font-weight: 100 900;
              font-display: swap;
              src: url("$dm") format("woff2");
            }
        """.trimIndent()
    }

    /** Clears the in-memory asset cache (tests / soft reload). */
    @JvmStatic
    fun resetCache() {
        synchronized(cache) { cache.clear() }
    }
}
