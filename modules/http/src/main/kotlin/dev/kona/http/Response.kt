package dev.kona.http

/**
 * HTTP response (Laravel Response shape).
 *
 * Multiple `Set-Cookie` values live in [cookies] so adapters can emit repeated headers
 * (a single [headers] map cannot hold duplicates).
 */
class Response(
    var status: Int = 200,
    var body: String = "",
    val headers: MutableMap<String, String> = linkedMapOf("Content-Type" to "text/plain; charset=utf-8"),
    val cookies: MutableList<Cookie> = mutableListOf(),
) {
    fun header(name: String, value: String): Response {
        headers[name] = value
        return this
    }

    fun cookie(cookie: Cookie): Response {
        cookies += cookie
        return this
    }

    @JvmOverloads
    fun cookie(
        name: String,
        value: String,
        maxAgeSeconds: Long? = null,
        path: String = "/",
        domain: String? = null,
        secure: Boolean = false,
        httpOnly: Boolean = true,
        sameSite: Cookie.SameSite? = Cookie.SameSite.Lax,
    ): Response = cookie(
        Cookie(
            name = name,
            value = value,
            maxAge = maxAgeSeconds?.let { java.time.Duration.ofSeconds(it) },
            path = path,
            domain = domain,
            secure = secure,
            httpOnly = httpOnly,
            sameSite = sameSite,
        ),
    )

    fun withStatus(code: Int): Response {
        status = code
        return this
    }

    fun withBody(content: String): Response {
        body = content
        return this
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun text(body: String, status: Int = 200): Response =
            Response(status, body, linkedMapOf("Content-Type" to "text/plain; charset=utf-8"))

        @JvmStatic
        @JvmOverloads
        fun html(body: String, status: Int = 200): Response =
            Response(status, body, linkedMapOf("Content-Type" to "text/html; charset=utf-8"))

        @JvmStatic
        @JvmOverloads
        fun json(payload: Any?, status: Int = 200): Response =
            Response(
                status,
                JsonSupport.stringify(payload),
                linkedMapOf("Content-Type" to "application/json; charset=utf-8"),
            )

        @JvmStatic
        fun noContent(): Response = Response(204, "")
    }
}
