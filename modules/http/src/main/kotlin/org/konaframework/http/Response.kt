package org.konaframework.http

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
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
 * [stream], [streamDownload], and [eventStream] defer writing until the server adapter flushes
 * chunks (`Transfer-Encoding: chunked` on Netty). [bodyBytes] still materializes the writer for
 * in-process tests.
 */
class Response(
    var status: Int = 200,
    var body: String = "",
    val headers: MutableMap<String, String> = linkedMapOf("Content-Type" to "text/plain; charset=utf-8"),
    val cookies: MutableList<Cookie> = mutableListOf(),
    bodyBytes: ByteArray? = null,
) {
    private var binaryBody: ByteArray? = bodyBytes

    /** Deferred chunked body; null for ordinary buffered responses. */
    internal var streamedBody: StreamedBody? = null
        private set

    /** Pending flash key→value pairs applied on the next request. */
    val sessionFlash: MutableMap<String, Any?> = linkedMapOf()

    /** Pending old input flashed as `_old_input`. */
    var sessionOldInput: Map<String, String>? = null

    /** Pending validation errors flashed as `errors`. */
    var sessionErrors: Map<String, List<String>>? = null

    /** True when this response writes through a deferred stream callback. */
    fun isStreamed(): Boolean = streamedBody != null

    /** Wire body bytes (binary when set; otherwise UTF-8 of [body]; streams are materialized). */
    fun bodyBytes(): ByteArray {
        binaryBody?.let { return it }
        val streamed = streamedBody
        if (streamed != null) {
            val buffer = ByteArrayOutputStream()
            runCatching {
                streamed.writeTo(buffer, StreamedBody.StreamContext.materialize(streamedLastEventId()))
            }.onFailure { error ->
                if (error !is StreamAbortedException) {
                    throw error
                }
            }
            return buffer.toByteArray()
        }
        return body.toByteArray(Charsets.UTF_8)
    }

    /**
     * Write the deferred stream to [out]. Used by the Netty adapter for true chunked transfer.
     * No-op when [isStreamed] is false.
     */
    fun writeStreamedBody(out: OutputStream, context: StreamedBody.StreamContext = StreamedBody.StreamContext.materialize()) {
        streamedBody?.writeTo(out, context)
    }

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
        streamedBody = null
        return this
    }

    fun withBodyBytes(bytes: ByteArray): Response {
        binaryBody = bytes
        body = ""
        streamedBody = null
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

    private fun streamedLastEventId(): String? =
        (streamedBody as? StreamedBody.EventStream)?.lastEventId

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
         * True streamed response: [writer] receives an [OutputStream] and Netty flushes each write
         * as an HTTP chunk on a virtual thread (Laravel `response()->stream`).
         *
         * [timeout] bounds how long the writer may run; null means no deadline. Client disconnect
         * surfaces as [StreamAbortedException].
         */
        @JvmStatic
        @JvmOverloads
        fun stream(
            writer: Consumer<OutputStream>,
            contentType: String = "application/octet-stream",
            status: Int = 200,
            timeout: Duration? = null,
        ): Response {
            val response =
                Response(
                    status = status,
                    body = "",
                    headers = linkedMapOf("Content-Type" to contentType),
                )
            response.streamedBody = StreamedBody.Callback(writer, timeout)
            return response
        }

        /**
         * Streamed file download (Laravel `streamDownload`): same chunked transfer as [stream]
         * with `Content-Disposition` set for [filename].
         */
        @JvmStatic
        @JvmOverloads
        fun streamDownload(
            writer: Consumer<OutputStream>,
            filename: String,
            headers: Map<String, String> = emptyMap(),
            contentType: String? = null,
            disposition: String = "attachment",
            status: Int = 200,
            timeout: Duration? = null,
        ): Response {
            val type = contentType ?: MimeTypes.guess(filename)
            val response = stream(writer, type, status, timeout)
            response.header("Content-Disposition", contentDisposition(disposition, filename))
            if (headers.isNotEmpty()) {
                response.withHeaders(headers)
            }
            return response
        }

        /**
         * Server-Sent Events stream (`text/event-stream`). The [callback] receives an [SseEmitter]
         * to emit named events; a comment heartbeat keeps proxies alive; [lastEventId] is exposed
         * for reconnect replay (pass [Request] via the overload).
         *
         * When the callback returns, an `update` event with [endStreamWith] is written unless that
         * value is null/blank (Laravel default `</stream>`).
         */
        @JvmStatic
        @JvmOverloads
        fun eventStream(
            callback: Consumer<SseEmitter>,
            headers: Map<String, String> = emptyMap(),
            endStreamWith: String? = StreamedBody.EventStream.DEFAULT_END,
            heartbeat: Duration = StreamedBody.EventStream.DEFAULT_HEARTBEAT,
            timeout: Duration? = null,
            lastEventId: String? = null,
        ): Response {
            val response =
                Response(
                    status = 200,
                    body = "",
                    headers =
                        linkedMapOf(
                            "Content-Type" to "text/event-stream; charset=utf-8",
                            "Cache-Control" to "no-cache",
                            "Connection" to "keep-alive",
                            "X-Accel-Buffering" to "no",
                        ),
                )
            if (headers.isNotEmpty()) {
                response.withHeaders(headers)
            }
            response.streamedBody =
                StreamedBody.EventStream(
                    callback = callback,
                    endStreamWith = endStreamWith,
                    heartbeat = heartbeat,
                    timeout = timeout,
                    lastEventId = lastEventId,
                )
            return response
        }

        /**
         * [eventStream] that reads `Last-Event-ID` from [request] for reconnect support.
         */
        @JvmStatic
        @JvmOverloads
        fun eventStream(
            request: Request,
            callback: Consumer<SseEmitter>,
            headers: Map<String, String> = emptyMap(),
            endStreamWith: String? = StreamedBody.EventStream.DEFAULT_END,
            heartbeat: Duration = StreamedBody.EventStream.DEFAULT_HEARTBEAT,
            timeout: Duration? = null,
        ): Response =
            eventStream(
                callback = callback,
                headers = headers,
                endStreamWith = endStreamWith,
                heartbeat = heartbeat,
                timeout = timeout,
                lastEventId = request.header("Last-Event-ID"),
            )

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
