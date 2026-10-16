package io.konaframework.http

import io.konaframework.core.Log
import java.util.function.BiFunction
import java.util.function.Consumer

fun interface ExceptionHandler {
    fun handle(request: Request, throwable: Throwable): Response
}

/**
 * Report/render callbacks plus JSON or HTML envelopes (Laravel Handler deepen).
 */
open class DefaultExceptionHandler @JvmOverloads constructor(
    private val debug: Boolean = false,
) : ExceptionHandler {
    private var reportCallback: Consumer<Throwable>? = null
    private var renderCallback: BiFunction<Request, Throwable, Response?>? = null

    fun report(callback: Consumer<Throwable>): DefaultExceptionHandler {
        reportCallback = callback
        return this
    }

    fun render(callback: BiFunction<Request, Throwable, Response?>): DefaultExceptionHandler {
        renderCallback = callback
        return this
    }

    override fun handle(request: Request, throwable: Throwable): Response {
        reportThrowable(throwable)
        val custom = renderCallback?.apply(request, throwable)
        if (custom != null) {
            return custom
        }
        return renderDefault(request, throwable)
    }

    protected open fun reportThrowable(throwable: Throwable) {
        val callback = reportCallback
        if (callback != null) {
            callback.accept(throwable)
            return
        }
        if (throwable !is HttpException && throwable !is ValidationException) {
            Log.error("Unhandled exception", throwable)
        }
    }

    protected open fun renderDefault(request: Request, throwable: Throwable): Response {
        val wantsHtml = wantsHtml(request)
        return when (throwable) {
            is ValidationException -> {
                if (!request.expectsJson()) {
                    Response.back(request)
                        .withErrors(throwable.errors)
                        .withInput(request.all())
                } else {
                    Response.json(mapOf("message" to throwable.message, "errors" to throwable.errors), 422)
                }
            }
            is HttpException -> {
                val response = if (wantsHtml) {
                    htmlError(throwable.status, throwable.message!!)
                } else {
                    Response.json(envelope(throwable.message, throwable), throwable.status)
                }
                throwable.headers.forEach { (k, v) -> response.header(k, v) }
                response
            }
            else -> {
                val detail = throwable.message
                if (wantsHtml) {
                    htmlError(500, if (debug && detail != null) detail else "Server Error")
                } else {
                    Response.json(envelope(if (debug) detail else "Server Error", throwable), 500)
                }
            }
        }
    }

    private fun wantsHtml(request: Request): Boolean {
        val accept = request.header("Accept").orEmpty()
        return accept.contains("text/html", ignoreCase = true) &&
            !accept.contains("application/json", ignoreCase = true)
    }

    private fun htmlError(status: Int, message: String): Response {
        val body = "<!DOCTYPE html><html><head><title>$status</title></head>" +
            "<body><h1>$status</h1><p>${escape(message)}</p></body></html>"
        return Response(status, body, linkedMapOf("Content-Type" to "text/html; charset=utf-8"))
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun envelope(message: String?, throwable: Throwable): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>("message" to message)
        if (debug) {
            payload["exception"] = throwable::class.java.name
        }
        return payload
    }
}
