package dev.kona.http

import dev.kona.core.Log

fun interface ExceptionHandler {
    fun handle(request: Request, throwable: Throwable): Response
}

/**
 * JSON error envelope (Laravel Handler shape): `{"message": ...}` with the exception status; validation adds
 * `errors`; debug adds `exception` (class name); [HttpException.headers] (e.g. `Allow` on 405) are copied.
 */
class DefaultExceptionHandler @JvmOverloads constructor(
    private val debug: Boolean = false,
) : ExceptionHandler {
    override fun handle(request: Request, throwable: Throwable): Response {
        return when (throwable) {
            is ValidationException ->
                Response.json(mapOf("message" to throwable.message, "errors" to throwable.errors), 422)
            is HttpException -> {
                val response = Response.json(envelope(throwable.message, throwable), throwable.status)
                throwable.headers.forEach { (k, v) -> response.header(k, v) }
                response
            }
            else -> {
                Log.error("Unhandled exception", throwable)
                Response.json(envelope(if (debug) throwable.message else "Server Error", throwable), 500)
            }
        }
    }

    private fun envelope(message: String?, throwable: Throwable): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>("message" to message)
        if (debug) {
            payload["exception"] = throwable::class.java.name
        }
        return payload
    }
}
