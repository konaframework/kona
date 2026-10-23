package org.konaframework.core

/***********************************************************************************************************************
 * ServiceProvider is a contract for classes that register and boot services in the application.
 * ---------------------------------------------------------------------------------------------------------------------
 * Service providers are responsible for binding services into the container and performing any necessary bootstrapping.
 * They are registered during the application boot process and can be used to organize service registration and boot logic.
 * *********************************************************************************************************************
 */
interface ServiceProvider {
    fun register(app: Application)

    fun boot(app: Application) {
        // optional
    }
}
