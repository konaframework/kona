package app.models

import org.konaframework.firefly.support.BrandDisplay
import org.konaframework.firefly.support.PanelBranding
import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory panel branding overrides for the Desk sample.
 * Production apps typically persist the same fields on a Cadence singleton.
 */
object AppSettings {
    data class Snapshot(
        val name: String? = null,
        val logo: String? = null,
        val logoDark: String? = null,
        val logoHeight: String? = null,
        val brandDisplay: String? = null,
        val copyright: String? = null,
        val primaryColor: String? = null,
        val accentColor: String? = null,
    )

    private val current = AtomicReference(Snapshot())

    fun get(): Snapshot = current.get()

    fun toMap(): Map<String, Any?> {
        val s = get()
        return linkedMapOf(
            "name" to (s.name ?: ""),
            "logo" to (s.logo ?: ""),
            "logo_dark" to (s.logoDark ?: ""),
            "logo_height" to (s.logoHeight ?: ""),
            "brand_display" to (s.brandDisplay ?: "both"),
            "copyright" to (s.copyright ?: ""),
            "primary_color" to (s.primaryColor ?: ""),
            "accent_color" to (s.accentColor ?: ""),
        )
    }

    fun save(input: Map<String, String>): Snapshot {
        val next = Snapshot(
            name = input["name"]?.trim()?.ifEmpty { null },
            logo = input["logo"]?.trim()?.ifEmpty { null },
            logoDark = input["logo_dark"]?.trim()?.ifEmpty { null },
            logoHeight = input["logo_height"]?.trim()?.ifEmpty { null },
            brandDisplay = input["brand_display"]?.trim()?.ifEmpty { null },
            copyright = input["copyright"]?.trim()?.ifEmpty { null },
            primaryColor = input["primary_color"]?.trim()?.ifEmpty { null },
            accentColor = input["accent_color"]?.trim()?.ifEmpty { null },
        )
        current.set(next)
        return next
    }

    fun toPanelBranding(): PanelBranding? {
        val s = get()
        if (
            s.name.isNullOrBlank() &&
            s.logo.isNullOrBlank() &&
            s.logoDark.isNullOrBlank() &&
            s.logoHeight.isNullOrBlank() &&
            s.brandDisplay.isNullOrBlank() &&
            s.copyright.isNullOrBlank() &&
            s.primaryColor.isNullOrBlank() &&
            s.accentColor.isNullOrBlank()
        ) {
            return null
        }
        val display = when (s.brandDisplay?.lowercase()) {
            "logo" -> BrandDisplay.LOGO
            "name" -> BrandDisplay.NAME
            "both" -> BrandDisplay.BOTH
            else -> null
        }
        return PanelBranding(
            name = s.name,
            logo = s.logo,
            logoDark = s.logoDark,
            logoHeight = s.logoHeight,
            brandDisplay = display,
            copyright = s.copyright,
            primaryColor = s.primaryColor,
            accentColor = s.accentColor,
        )
    }

    fun reset() {
        current.set(Snapshot())
    }
}
