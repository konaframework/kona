package org.konaframework.firefly.support

/**
 * Tailwind browser runtime shipped with Firefly (`@tailwindcss/browser` 4.3.5, MIT).
 * [FireflyTheme.useTailwind] adds [SCRIPT] to the panel and the sign-in page.
 * Stock themes leave that off, so this file is not requested.
 */
object FireflyTailwind {
    const val SCRIPT: String = "/_firefly/tailwind.js"

    private const val DEFAULT_RESOURCE = "/org/konaframework/firefly/support/tailwind.js"

    /** Classpath path of the runtime; tests point this at a missing file. */
    @JvmStatic
    @Volatile
    var resourcePath: String = DEFAULT_RESOURCE

    @JvmStatic
    fun bytes(): ByteArray? {
        val stream = FireflyTailwind::class.java.getResourceAsStream(resourcePath) ?: return null
        return stream.use { it.readAllBytes() }
    }
}
