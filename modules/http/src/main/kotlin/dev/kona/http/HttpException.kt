package dev.kona.http

open class HttpException(
    val status: Int,
    message: String,
) : RuntimeException(message)

class NotFoundException(message: String = "Not Found") : HttpException(404, message)

class MethodNotAllowedException(message: String = "Method Not Allowed") : HttpException(405, message)
