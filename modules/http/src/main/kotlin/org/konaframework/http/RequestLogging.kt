package org.konaframework.http

import org.konaframework.core.Application
import org.konaframework.core.Config
import org.konaframework.core.Env

/**
 * Whether HTTP request/access lines should be written during local development.
 *
 * Resolution order:
 * 1. Config `logging.requests` (seeded from env `LOG_REQUESTS` by [org.konaframework.core.Kona] when set)
 * 2. Env `LOG_REQUESTS` when config is unset
 * 3. Otherwise `app.debug` (from `APP_DEBUG`)
 */
object RequestLogging {
    const val CONFIG_KEY: String = "logging.requests"
    const val ENV_KEY: String = "LOG_REQUESTS"

    @JvmStatic
    fun enabled(app: Application): Boolean {
        app.config.getBoolean(CONFIG_KEY)?.let { return it }
        Env.get(ENV_KEY)?.let { raw ->
            Config.toBoolean(raw)?.let { return it }
        }
        return app.config.getBoolean("app.debug") == true
    }
}
