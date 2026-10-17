package org.konaframework.events

/**
 * SAM-friendly event listener (Kotlin lambdas and Java method references).
 */
fun interface EventListener<T : Any> {
    fun handle(event: T)
}
