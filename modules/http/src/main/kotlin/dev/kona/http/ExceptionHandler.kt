package dev.kona.http

import dev.kona.core.Log

fun interface ExceptionHandler {
    fun handle(request: Request, throwable: Throwable): Response
}

class DefaultExceptionHandler(
    private val debug: Boolean = true,
) : ExceptionHandler {
    override fun handle(request: Request, throwable: Throwable): Response {
        return when (throwable) {
            is ValidationException ->
                Response.json(mapOf("message" to "Validation failed", "errors" to throwable.errors), 422)
            is HttpException ->
                Response.json(mapOf("message" to throwable.message), throwable.status)
            else -> {
                Log.error("Unhandled exception", throwable)
                val payload = linkedMapOf<String, Any?>(
                    "message" to (if (debug) throwable.message else "Server Error"),
                )
                if (debug) {
                    payload["exception"] = throwable::class.java.name
                }
                Response.json(payload, 500)
            }
        }
    }
}
