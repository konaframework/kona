package org.konaframework.http

/**
 * Global URL / route helper holder (same idea as [org.konaframework.support.lang.Lang]).
 */
object Url {
    @Volatile
    private var generator: UrlGenerator? = null

    @JvmStatic
    fun setGenerator(generator: UrlGenerator) {
        this.generator = generator
    }

    /** Clears the global generator (tests). */
    @JvmStatic
    fun clearGenerator() {
        generator = null
    }

    @JvmStatic
    fun generator(): UrlGenerator =
        generator ?: throw IllegalStateException("URL generator not configured")

    @JvmStatic
    @JvmOverloads
    fun route(name: String, params: Map<String, String> = emptyMap()): String =
        generator().route(name, params)

    @JvmStatic
    fun url(path: String): String = generator().to(path)

    @JvmStatic
    fun to(path: String): String = generator().to(path)
}

/** Top-level Kotlin helper: named route URL. */
@JvmOverloads
fun route(name: String, params: Map<String, String> = emptyMap()): String = Url.route(name, params)

/** Top-level Kotlin helper: absolute or rooted path URL. */
fun url(path: String): String = Url.url(path)
