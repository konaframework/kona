package dev.kona.http

/**
 * HTTP response (Laravel Response shape).
 *
 * Multiple `Set-Cookie` values live in [cookies] so adapters can emit repeated headers
 * (a single [headers] map cannot hold duplicates).
 *
 * [sessionFlash], [sessionOldInput], and [sessionErrors] are applied by
 * `StartSession` before the session is saved (redirect `with` / `withInput` / `withErrors`).
 */
class Response(
    var status: Int = 200,
    var body: String = "",
    val headers: MutableMap<String, String> = linkedMapOf("Content-Type" to "text/plain; charset=utf-8"),
    val cookies: MutableList<Cookie> = mutableListOf(),
) {
    /** Pending flash key→value pairs applied on the next request. */
    val sessionFlash: MutableMap<String, Any?> = linkedMapOf()

    /** Pending old input flashed as `_old_input`. */
    var sessionOldInput: Map<String, String>? = null

    /** Pending validation errors flashed as `errors`. */
    var sessionErrors: Map<String, List<String>>? = null

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

    /** Flash [value] under [key] when `StartSession` saves (Laravel redirect `with`). */
    fun with(key: String, value: Any?): Response {
        sessionFlash[key] = value
        return this
    }

    /** Flash request input for redisplay after redirect. */
    fun withInput(input: Map<String, String>): Response {
        sessionOldInput = LinkedHashMap(input)
        return this
    }

    /** Flash validation errors for redisplay after redirect. */
    fun withErrors(errors: Map<String, List<String>>): Response {
        sessionErrors = LinkedHashMap(errors.mapValues { (_, v) -> v.toList() })
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

        /** Redirect to [to] with status 302 (or [status]). */
        @JvmStatic
        @JvmOverloads
        fun redirect(to: String, status: Int = 302): Response =
            Response(status, "", linkedMapOf("Location" to to))

        /**
         * Redirect to the previous URL (session `_previous.url`, else `Referer`, else [fallback]).
         */
        @JvmStatic
        @JvmOverloads
        fun back(request: Request, fallback: String = "/", status: Int = 302): Response {
            val session = request.session()
            val target = session?.previousUrl()
                ?: request.header("Referer")
                ?: fallback
            return redirect(target, status)
        }
    }
}
