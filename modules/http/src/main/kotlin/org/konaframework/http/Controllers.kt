package org.konaframework.http

/**
 * Controller helpers: invokable adapters for [Handler] registration.
 */
object Controllers {
    @JvmStatic
    fun asHandler(controller: InvokableController): Handler = Handler { controller.invoke(it) }
}

/** Adapts an [InvokableController] to a [Handler]. */
fun InvokableController.asHandler(): Handler = Controllers.asHandler(this)
