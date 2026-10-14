package dev.kona.http

/**
 * HTTP response (Laravel Response shape).
 */
class Response(
    var status: Int = 200,
    var body: String = "",
    val headers: MutableMap<String, String> = linkedMapOf("Content-Type" to "text/plain; charset=utf-8"),
) {
    fun header(name: String, value: String): Response {
        headers[name] = value
        return this
    }

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
