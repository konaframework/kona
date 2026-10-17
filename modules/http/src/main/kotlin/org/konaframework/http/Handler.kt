package org.konaframework.http

/**
 * Request handler (route action / pipeline destination). SAM for Java lambdas and Kotlin trailing lambdas.
 */
fun interface Handler {
    fun handle(request: Request): Response
}

/** Kotlin sugar: `next(request)` inside middleware. */
operator fun Handler.invoke(request: Request): Response = handle(request)
