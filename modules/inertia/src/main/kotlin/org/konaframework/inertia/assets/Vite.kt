package org.konaframework.inertia.assets

/**
 * Backward-compatible facade — implementation lives in `org.konaframework.crema.assets`
 * so Crema `@vite` and Inertia share one Vite hot/manifest resolver.
 */
typealias AssetManifest = org.konaframework.crema.assets.AssetManifest
typealias ViteException = org.konaframework.crema.assets.ViteException

object Vite {
    @JvmStatic
    fun setManifest(value: AssetManifest?) {
        org.konaframework.crema.assets.Vite.setManifest(value)
    }

    @JvmStatic
    fun manifest(): AssetManifest = org.konaframework.crema.assets.Vite.manifest()

    @JvmStatic
    fun reset() {
        org.konaframework.crema.assets.Vite.reset()
    }

    @JvmStatic
    fun tags(vararg entries: String): String = org.konaframework.crema.assets.Vite.tags(*entries)

    @JvmStatic
    fun asset(entry: String): String = org.konaframework.crema.assets.Vite.asset(entry)
}
