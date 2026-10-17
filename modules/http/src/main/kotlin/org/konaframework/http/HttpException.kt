package org.konaframework.http

open class HttpException @JvmOverloads constructor(
    val status: Int,
    message: String,
    val headers: Map<String, String> = emptyMap(),
) : RuntimeException(message)

class NotFoundException @JvmOverloads constructor(
    message: String = "Not Found",
) : HttpException(404, message)

class MethodNotAllowedException @JvmOverloads constructor(
    val allowed: List<String> = emptyList(),
    message: String = "Method Not Allowed",
) : HttpException(405, message, allowHeader(allowed)) {
    private companion object {
        fun allowHeader(allowed: List<String>): Map<String, String> =
            if (allowed.isEmpty()) emptyMap() else mapOf("Allow" to allowed.joinToString(", "))
    }
}
