package org.konaframework.firefly

import org.konaframework.core.Config

/**
 * Settings under `config/firefly`.
 *
 * [discovery] lists extra directories of panel classes. `app/panels` is always scanned.
 */
internal object FireflyConfig {
    fun discovery(config: Config): List<String> {
        val section = config.all()["firefly"]
        val raw = if (section is Map<*, *>) section["discovery"] else null
        return paths(raw)
    }

    /** `firefly.policies`: packages searched for `{Model}Policy` (default `app.policies`). */
    fun policies(config: Config): List<String> = paths(section(config)?.get("policies"))

    /** `firefly.authorize_without_policy`: false denies models that have no policy. Null when unset. */
    fun authorizeWithoutPolicy(config: Config): Boolean? = Config.toBoolean(section(config)?.get("authorize_without_policy"))

    private fun section(config: Config): Map<*, *>? = config.all()["firefly"] as? Map<*, *>

    private fun paths(raw: Any?): List<String> = when (raw) {
        null -> emptyList()
        is Collection<*> -> raw.mapNotNull { item ->
            if (item == null) return@mapNotNull null
            val text = item.toString().trim()
            if (text.isEmpty()) null else text
        }
        is String -> raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        else -> emptyList()
    }
}
