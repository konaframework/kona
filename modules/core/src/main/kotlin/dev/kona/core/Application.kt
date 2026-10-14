package dev.kona.core

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Application kernel: base path, container, providers, config, boot lifecycle.
 */
class Application(
    val basePath: Path,
) {
    val container: Container = Container()
    val config: Config = Config()
    private val providers = CopyOnWriteArrayList<ServiceProvider>()
    private var booted = false

    init {
        container.instance(Application::class.java, this)
        container.instance(Container::class.java, container)
        container.instance(Config::class.java, config)
        container.instance("app", this)
        container.instance("config", config)
    }

    fun path(vararg segments: String): Path {
        var result = basePath
        for (segment in segments) {
            result = result.resolve(segment)
        }
        return result
    }

    fun register(provider: ServiceProvider): Application {
        provider.register(this)
        providers.add(provider)
        if (booted) {
            provider.boot(this)
        }
        return this
    }

    fun boot(): Application {
        if (booted) {
            return this
        }
        providers.forEach { it.boot(this) }
        booted = true
        log.info("Kona application booted at {}", basePath)
        return this
    }

    fun isBooted(): Boolean = booted

    fun <T : Any> make(type: Class<T>): T = container.make(type)

    fun <T : Any> resolve(type: Class<T>): T = container.resolve(type)

    companion object {
        private val log = LoggerFactory.getLogger(Application::class.java)
    }
}
