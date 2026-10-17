package org.konaframework.core

/**
 * Laravel-style service provider.
 */
interface ServiceProvider {
    fun register(app: Application)

    fun boot(app: Application) {
        // optional
    }
}
