package dev.kona.core

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/**
 * Application kernel: base path, container, providers, config, boot lifecycle.
 *
 * Laravel ordering: every provider's `register()` runs before any `boot()`.
 * Providers registered while booting are booted in the same pass; providers
 * registered after boot are registered and booted immediately.
 */
class Application(
    val basePath: Path,
) {
    val container: Container = Container()
    val config: Config = Config()
    private val providers = CopyOnWriteArrayList<ServiceProvider>()

    @Volatile
    private var booted = false

    init {
        container.instance(Application::class.java, this)
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

    /** Registers [provider]; a second provider of an already-registered class is ignored (Laravel shape). */
    @Synchronized
    fun register(provider: ServiceProvider): Application {
        if (getProvider(provider.javaClass) != null) {
            return this
        }
        provider.register(this)
        providers.add(provider)
        if (booted) {
            provider.boot(this)
        }
        return this
    }

    fun registerAll(providers: Iterable<ServiceProvider>): Application {
        providers.forEach { register(it) }
        return this
    }

    @Synchronized
    fun boot(): Application {
        if (booted) {
            return this
        }
        // Index loop: providers registered from another provider's boot() must also boot.
        var index = 0
        while (index < providers.size) {
            providers[index].boot(this)
            index++
        }
        booted = true
        log.info("Kona application booted at {}", basePath)
        return this
    }

    fun isBooted(): Boolean = booted

    fun providers(): List<ServiceProvider> = providers.toList()

    fun <P : ServiceProvider> getProvider(type: Class<P>): P? =
        providers.firstOrNull { it.javaClass == type }?.let { type.cast(it) }

    fun <T : Any> make(type: Class<T>): T = container.make(type)

    fun <T : Any> resolve(type: Class<T>): T = container.resolve(type)

    /**
     * Fluent bootstrap: configure, register all providers, then boot exactly once.
     *
     * ```kotlin
     * Application.builder(base).withProviders(A(), B()).configure { it.set("app.name", "Demo") }.build()
     * ```
     */
    class Builder @JvmOverloads constructor(
        private var basePath: Path = Path.of("").toAbsolutePath().normalize(),
    ) {
        private val providers = mutableListOf<ServiceProvider>()
        private val configurers = mutableListOf<Consumer<Config>>()
        internal val bootstrappers = mutableListOf<(Application) -> Unit>()
        internal val onBooted = mutableListOf<(Application) -> Unit>()

        fun basePath(path: Path): Builder {
            basePath = path
            return this
        }

        fun withProvider(provider: ServiceProvider): Builder {
            providers.add(provider)
            return this
        }

        fun withProviders(vararg providers: ServiceProvider): Builder = withProviders(providers.asList())

        fun withProviders(providers: Iterable<ServiceProvider>): Builder {
            this.providers.addAll(providers)
            return this
        }

        /** Alias of [withProviders] for the `Kona.create(...).providers(...)` shape. */
        fun providers(vararg providers: ServiceProvider): Builder = withProviders(providers.asList())

        fun configure(configurer: Consumer<Config>): Builder {
            configurers.add(configurer)
            return this
        }

        /** Creates the application, applies config, registers every provider, then boots once. */
        fun build(): Application {
            val app = Application(basePath)
            bootstrappers.forEach { it(app) }
            configurers.forEach { it.accept(app.config) }
            app.registerAll(providers)
            app.boot()
            onBooted.forEach { it(app) }
            return app
        }

        /** Alias of [build]. */
        fun boot(): Application = build()
    }

    companion object {
        private val log = LoggerFactory.getLogger(Application::class.java)

        @JvmStatic
        fun builder(): Builder = Builder()

        @JvmStatic
        fun builder(basePath: Path): Builder = Builder(basePath)
    }
}
