package dev.kona.inertia.assets

/**
 * Backward-compatible facade — implementation lives in `dev.kona.crema.assets`
 * so Crema `@vite` and Inertia share one Vite hot/manifest resolver.
 */
typealias AssetManifest = dev.kona.crema.assets.AssetManifest
typealias ViteException = dev.kona.crema.assets.ViteException

object Vite {
    @JvmStatic
    fun setManifest(value: AssetManifest?) {
        dev.kona.crema.assets.Vite.setManifest(value)
    }

    @JvmStatic
    fun manifest(): AssetManifest = dev.kona.crema.assets.Vite.manifest()

    @JvmStatic
    fun reset() {
        dev.kona.crema.assets.Vite.reset()
    }

    @JvmStatic
    fun tags(vararg entries: String): String = dev.kona.crema.assets.Vite.tags(*entries)

    @JvmStatic
    fun asset(entry: String): String = dev.kona.crema.assets.Vite.asset(entry)
}
