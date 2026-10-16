package io.konaframework.http

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

/**
 * HTTP response (Laravel Response shape).
 *
 * Multiple `Set-Cookie` values live in [cookies] so adapters can emit repeated headers
 * (a single [headers] map cannot hold duplicates).
 *
 * [sessionFlash], [sessionOldInput], and [sessionErrors] are applied by
 * `StartSession` before the session is saved (redirect `with` / `withInput` / `withErrors`).
 *
 * Binary payloads use [bodyBytes]; text factories keep UTF-8 [body] and derive bytes on write.
 * [stream] buffers the callback into memory so every server adapter can serve it today.
 */
class Response(
    var status: Int = 200,
    var body: String = "",
    val headers: MutableMap<String, String> = linkedMapOf("Content-Type" to "text/plain; charset=utf-8"),
    val cookies: MutableList<Cookie> = mutableListOf(),
    bodyBytes: ByteArray? = null,
) {
    private var binaryBody: ByteArray? = bodyBytes

    /** Pending flash key→value pairs applied on the next request. */
    val sessionFlash: MutableMap<String, Any?> = linkedMapOf()

    /** Pending old input flashed as `_old_input`. */
    var sessionOldInput: Map<String, String>? = null

    /** Pending validation errors flashed as `errors`. */
    var sessionErrors: Map<String, List<String>>? = null

    /** Wire body bytes (binary when set; otherwise UTF-8 of [body]). */
    fun bodyBytes(): ByteArray = binaryBody ?: body.toByteArray(Charsets.UTF_8)

    fun header(name: String, value: String): Response {
        headers[name] = value
        return this
    }

    /** Set multiple headers (Laravel `withHeaders`). */
    fun withHeaders(values: Map<String, String>): Response {
        headers.putAll(values)
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
        binaryBody = null
        return this
    }

    fun withBodyBytes(bytes: ByteArray): Response {
        binaryBody = bytes
        body = ""
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

        /** Raw binary body with [contentType]. */
        @JvmStatic
        @JvmOverloads
        fun binary(
            bytes: ByteArray,
            contentType: String = "application/octet-stream",
            status: Int = 200,
        ): Response =
            Response(
                status = status,
                body = "",
                headers = linkedMapOf("Content-Type" to contentType),
                bodyBytes = bytes,
            )

        /**
         * Stream-shaped response: [writer] receives an [OutputStream] and may write incrementally.
         * The result is buffered in memory so sync adapters can emit a complete body
         * (Laravel `stream` shape for app code; wire chunking can land later).
         */
        @JvmStatic
        @JvmOverloads
        fun stream(
            writer: Consumer<OutputStream>,
            contentType: String = "application/octet-stream",
            status: Int = 200,
        ): Response {
            val buffer = ByteArrayOutputStream()
            writer.accept(buffer)
            return binary(buffer.toByteArray(), contentType, status)
        }

        /**
         * Inline file response (Laravel `Response::file` shape).
         * Sets `Content-Disposition: inline` when [filename] is provided.
         */
        @JvmStatic
        @JvmOverloads
        fun file(
            bytes: ByteArray,
            contentType: String? = null,
            filename: String? = null,
            status: Int = 200,
        ): Response {
            val type = contentType ?: filename?.let { MimeTypes.guess(it) } ?: "application/octet-stream"
            val response = binary(bytes, type, status)
            if (filename != null) {
                response.header("Content-Disposition", contentDisposition("inline", filename))
            }
            return response
        }

        /** Inline file from a filesystem [path]. */
        @JvmStatic
        @JvmOverloads
        fun file(
            path: Path,
            contentType: String? = null,
            filename: String? = null,
            status: Int = 200,
        ): Response {
            val name = filename ?: path.fileName.toString()
            return file(Files.readAllBytes(path), contentType, name, status)
        }

        /**
         * Attachment download (Laravel `Response::download` shape).
         */
        @JvmStatic
        @JvmOverloads
        fun download(
            bytes: ByteArray,
            filename: String,
            contentType: String? = null,
            status: Int = 200,
        ): Response {
            val type = contentType ?: MimeTypes.guess(filename)
            return binary(bytes, type, status)
                .header("Content-Disposition", contentDisposition("attachment", filename))
        }

        /** Attachment download from a filesystem [path]. */
        @JvmStatic
        @JvmOverloads
        fun download(
            path: Path,
            filename: String? = null,
            contentType: String? = null,
            status: Int = 200,
        ): Response {
            val name = filename ?: path.fileName.toString()
            return download(Files.readAllBytes(path), name, contentType, status)
        }

        private fun contentDisposition(type: String, filename: String): String {
            val safe = filename.replace("\"", "")
            return "$type; filename=\"$safe\""
        }
    }
}
