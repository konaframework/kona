package org.konaframework.brew

/**
 * Marker shipped by the deprecated `org.konaframework:kona-brew` artifact so 0.2.x
 * coordinates keep resolving after the rename to `kona-wire`.
 */
@Deprecated("Depend on org.konaframework:kona-wire")
object KonaBrewArtifact {
    /** Canonical artifact that replaces this module. */
    const val REPLACEMENT = "kona-wire"

    @JvmStatic
    fun replacement(): String = REPLACEMENT
}
