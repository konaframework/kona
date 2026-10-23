package org.konaframework.core

import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

/**
 * ------------------------------------------------------------------------------
 * Kona Framework
 * ------------------------------------------------------------------------------
 * Copyright (c) 2024, Kona Framework contributors
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 * ------------------------------------------------------------------------------
 * This is the Application kernel: base path, container, providers, config, boot lifecycle.
 *
 * We are adapting a Laravel-ordering: every provider's `register()` runs before any `boot()`.
 * Providers registered while booting are booted in the same pass; providers
 * registered after boot are registered and booted immediately.
 *
 * We are also adapting Laravel's "paths" concept: the application has a base path, and a layout of subpaths (app, bootstrap, config, database, public, resources, routes, storage, views). The layout can be overridden by `paths.*` config values.
 *
 * To better understand the lifecycle, see [Application.Builder] for a fluent bootstrap API.
 * @param basePath the base path of the application (the root of the project)
 *
 * Usage:
 * ```kotlin
 * val app = Application.builder(basePath)
 *    .withProvider(MyServiceProvider())
 *    .configure { config -> config.set("app.name", "MyApp") }
 *    .build()
 * ```
 *
 * @author <a href="mailto:devmaosa@gmail.com">Sam Maosa</a>
 *
 */
class Application(val basePath: Path) {

    /**
     * The application container is a simple dependency injection container that can be used to register and resolve services.
     * It is used by the application to manage its dependencies and to provide a way for services to be registered and resolved.
     */
    val container: Container = Container()

    /**
     * The application config is a simple configuration object that can be used to store and retrieve configuration values.
     * It is used by the application to manage its configuration and to provide a way for services to access configuration values.
     */
    val config: Config = Config()

    /**
     * The application providers are a list of service providers that are registered with the application.
     * Service providers are used to register services and to perform bootstrapping tasks.
     */
    private val providers = CopyOnWriteArrayList<ServiceProvider>()

    /**
     * The application booted flag is used to indicate whether the application has been booted.
     * It is used to prevent the application from being booted multiple times.
     *
     * Booted is annotated with @Volatile to ensure that the value is visible to all threads and that changes to the value are immediately visible to other threads.
     * This is important because the booted flag is used to prevent the application from being booted multiple times, and we want to ensure that all threads see the correct value of the flag.
     */
    @Volatile
    private var booted = false

    /**
     * The application path layout is a simple object that can be used to store and retrieve the paths of the application.
     * It is used by the application to manage its paths and to provide a way for services to access the paths of the application.
     *
     * PathLayout is annotated with @Volatile to ensure that the value is visible to all threads and that changes to the value are immediately visible to other threads.
     * This is important because the path layout can be refreshed at runtime, and we want to ensure that all threads see the correct value of the path layout.
     */
    @Volatile
    private var layout: PathLayout = PathLayout.detect(basePath)

    /**
     * The application path layout is a simple object that can be used to store and retrieve the paths of the application.
     * It is used by the application to manage its paths and to provide a way for services to access the paths of the application.
     */
    val pathLayout: PathLayout get() = layout

    /*******************************************************************************************************************
     * The init block is used to register the application and its config with the container.
     * This allows services to access the application and its config from the container.
     * The init block is executed when the application is created,
     * and it ensures that the application and its config are available to services that are registered with the container.
     * *****************************************************************************************************************
     */
    init {
        container.instance(Application::class.java, this)
        container.instance(Config::class.java, config)
        container.instance("app", this)
        container.instance("config", config)
    }

    /***********************************************************************************************************
     * The path function is used to resolve a path relative to the base path of the application.
     * It takes a variable number of string segments and resolves them against the base path.
     * This allows services to easily access files and directories within the application.
     * *****************************************************************************************************************
     */
    fun path(vararg segments: String): Path {
        var result = basePath
        for (segment in segments) {
            result = result.resolve(segment)
        }
        return result
    }

    /*******************************************************************************************************************
     * The refreshPathLayout function is used to recompute the path layout of the application based on the current configuration.
     * It is called when the configuration is changed, and it ensures that the path layout is always up to date with the current configuration.
     * This allows services to access the correct paths based on the current configuration of the application.
     * *****************************************************************************************************************
     *  Recompute [pathLayout] from current [config] (`paths.*` overrides).
     */
    fun refreshPathLayout(): PathLayout {
        layout = PathLayout.detect(basePath, config)
        return layout
    }

    /*******************************************************************************************************************
     * The following functions are used to resolve specific paths within the application.
     * They take a variable number of string segments and resolve them against the corresponding path in the path layout.
     * This allows services to easily access specific files and directories within the application.
     * *****************************************************************************************************************
     */
    fun basePath(vararg segments: String): Path = path(*segments)

    /** Resolves a path relative to the application root (basePath). */
    fun appPath(vararg segments: String): Path = layout.app(*segments)

    /** Resolves a path relative to the bootstrap directory (basePath/bootstrap). */
    fun bootstrapPath(vararg segments: String): Path = layout.bootstrap(*segments)

    /** Resolves a path relative to the routes directory (basePath/routes). */
    fun routesPath(vararg segments: String): Path = layout.routes(*segments)

    /** Resolves a path relative to the database directory (basePath/database). */
    fun databasePath(vararg segments: String): Path = layout.database(*segments)

    /** Resolves a path relative to the config directory (basePath/config). */
    fun configPath(vararg segments: String): Path = layout.config(*segments)

    /** Resolves a path relative to the resources directory (basePath/resources). */
    fun resourcePath(vararg segments: String): Path = layout.resource(*segments)

    /** Resolves a path relative to the views directory (basePath/resources/views). */
    fun viewPath(vararg segments: String): Path = layout.view(*segments)

    /** Resolves a path relative to the public directory (basePath/public). */
    fun publicPath(vararg segments: String): Path = layout.publicPath(*segments)

    /** Resolves a path relative to the lang directory (basePath/resources/lang). */
    fun langPath(vararg segments: String): Path = layout.lang(*segments)

    /** Resolves a path relative to the storage directory (basePath/storage). */
    fun storagePath(vararg segments: String): Path = layout.storage(*segments)

    /*******************************************************************************************************************
     * The register function is used to register a service provider with the application.
     * It takes a service provider as an argument and registers it with the application.
     * If the application has already been booted, the service provider's boot method is called immediately after registration.
     * This allows services to be registered and booted at runtime, even after the application has been booted.
     *
     * The @Synchronized annotation is used to ensure that the register function is thread-safe.
     * *****************************************************************************************************************
     */
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

    /*******************************************************************************************************************
     * The registerAll function is used to register multiple service providers with the application.
     * It takes an iterable of service providers as an argument and registers each one with the application.
     * This allows services to be registered in bulk, which can be more efficient than registering them one at a time.
     * *****************************************************************************************************************
     */
    fun registerAll(providers: Iterable<ServiceProvider>): Application {
        providers.forEach { register(it) }
        return this
    }

    /*******************************************************************************************************************
     * The boot function is used to boot the application.
     * It initializes the logging system, registers all service providers, and calls their boot methods.
     * If the application has already been booted, it simply returns the application instance.
     * This allows the application to be booted exactly once, even if the boot function is called multiple times.
     *
     * The @Synchronized annotation is used to ensure that the boot function is thread-safe.
     * *****************************************************************************************************************
     */
    @Synchronized
    fun boot(): Application {
        /** Booting is idempotent: if already booted, return immediately. */
        if (booted) {
            return this
        }
        LogManager.seedDefaults(config)
        FileLogging.ensureConfigured(this)
        val logManager = LogManager(this)
        container.instance(LogManager::class.java, logManager)
        container.instance("log", logManager)
        Log.bind(logManager)
        // Package providers from META-INF/kona/providers (./grind install) — register before boot loop.
        val loader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader
        PackageDiscovery.registerAll(this, loader)
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

    /** Returns true if the application has been booted, false otherwise. */
    fun isBooted(): Boolean = booted

    /** Returns a copy of the list of registered service providers. */
    fun providers(): List<ServiceProvider> = providers.toList()

    /** Returns the first registered provider of the given type, or null if none is found. */
    fun <P : ServiceProvider> getProvider(type: Class<P>): P? =
        providers.firstOrNull { it.javaClass == type }?.let { type.cast(it) }

    /** Returns the first registered provider of the given type, or null if none is found. */
    fun <T : Any> make(type: Class<T>): T = container.make(type)

    /** Resolves a service from the container, throwing an exception if it is not found. */
    fun <T : Any> resolve(type: Class<T>): T = container.resolve(type)

    /***************************************************************************************************************************
     * Fluent bootstrap: configure, register all providers, then boot exactly once.
     *
     * The Builder class is used to create an instance of the Application class.
     * It provides a fluent API for configuring the application, including setting the base path, registering service providers, and configuring the application.
     * The build method creates the application instance and boots it, ensuring that all service providers are registered and booted in the correct order.
     * This allows for a clean and efficient way to set up the application, with all necessary configurations and services in place before the application is booted.
     *
     * Usage:
     * ```kotlin
     * Application.builder(base).withProviders(A(), B()).configure { it.set("app.name", "Demo") }.build()
     * ```
     * ********************************************************************************************************************
     */
    class Builder @JvmOverloads constructor(
        private var basePath: Path = Path.of("").toAbsolutePath().normalize(),
    ) {
        private val providers = mutableListOf<ServiceProvider>()
        private val configFiles = mutableListOf<ConfigFile>()
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

        /** Registers executable config modules (merged after `.env` / defaults, before [configure]). */
        fun withConfig(file: ConfigFile): Builder {
            configFiles.add(file)
            return this
        }

        fun withConfigs(vararg files: ConfigFile): Builder = withConfigs(files.asList())

        fun withConfigs(files: Iterable<ConfigFile>): Builder {
            configFiles.addAll(files)
            return this
        }

        /** Alias of [withConfigs] for the `Kona.create(...).configs(...)` shape. */
        fun configs(vararg files: ConfigFile): Builder = withConfigs(files.asList())

        fun configure(configurer: Consumer<Config>): Builder {
            configurers.add(configurer)
            return this
        }

        /** Runs after providers have booted (e.g. register HTTP middleware stacks). */
        fun afterBoot(action: Consumer<Application>): Builder {
            onBooted.add { action.accept(it) }
            return this
        }

        /**
         * Creates the application, runs bootstrappers (`.env` + defaults), merges [ConfigFile]s,
         * applies [configure] callbacks, registers every provider, then boots once.
         */
        fun build(): Application {
            val app = Application(basePath)
            bootstrappers.forEach { it(app) }
            configFiles.forEach { app.config.load(it) }
            configurers.forEach { it.accept(app.config) }
            app.refreshPathLayout()
            app.registerAll(providers)
            app.boot()
            onBooted.forEach { it(app) }
            return app
        }

        /** Alias of [build]. */
        fun boot(): Application = build()
    }

    /********************************************************************************************************************
     * The companion object provides static methods for creating an instance of the Builder class.
     * It allows for a clean and efficient way to set up the application, with all necessary configurations and services
     * in place before the application is booted.
     *
     * Usage:
     * ```kotlin
     * val app = Application.builder(basePath)
     *    .withProvider(MyServiceProvider())
     *    .configure { config -> config.set("app.name", "MyApp") }
     *    .build()
     * ```
     * *****************************************************************************************************************
     */
    companion object {
        private val log = LoggerFactory.getLogger(Application::class.java)

        @JvmStatic
        fun builder(): Builder = Builder()

        @JvmStatic
        fun builder(basePath: Path): Builder = Builder(basePath)
    }
}
