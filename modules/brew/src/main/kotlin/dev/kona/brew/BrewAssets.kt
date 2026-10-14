package dev.kona.brew

/**
 * Classpath client script for Brew (`brew.js`).
 */
object BrewAssets {
    private val cached: String by lazy {
        readScript(BrewAssets::class.java.classLoader)
    }

    @JvmStatic
    fun javascript(): String = cached

    /** Tag that loads the script from the Brew HTTP endpoint. */
    @JvmStatic
    @JvmOverloads
    fun scriptTag(src: String = "/brew/brew.js"): String =
        """<script src="$src" defer></script>"""

    /** Inline the script (useful in tests or single-page embeds). */
    @JvmStatic
    fun inlineScriptTag(): String =
        "<script>\n${javascript()}\n</script>"

    internal fun readScript(loader: ClassLoader): String {
        val stream = loader.getResourceAsStream("brew.js")
            ?: throw BrewException("Missing classpath resource brew.js")
        return try {
            String(stream.readAllBytes(), Charsets.UTF_8)
        } finally {
            stream.close()
        }
    }
}
