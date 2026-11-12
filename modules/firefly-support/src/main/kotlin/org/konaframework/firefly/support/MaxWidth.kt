package org.konaframework.firefly.support

/**
 * Named content-width tokens for panel / page / resource [maxWidth] settings.
 * Tokens in [TOKENS] keep `data-ff-max-width`. [PROSE], the screen names,
 * [CONTAINER], and a single bracketed CSS length add a class instead
 * (`max-w-prose`, `max-w-screen-sm`, `container`, `max-w-[900px]`).
 */
object MaxWidth {
    /** Rem scale plus fill / viewport / prose. These stay on `data-ff-max-width`. */
    @JvmField
    val TOKENS: Set<String> =
        linkedSetOf(
            "xs",
            "sm",
            "md",
            "lg",
            "xl",
            "2xl",
            "3xl",
            "4xl",
            "5xl",
            "6xl",
            "7xl",
            "full",
            "screen",
            "prose",
        )

    const val PROSE: String = "Prose"
    const val SCREEN_SMALL: String = "ScreenSmall"
    const val SCREEN_MEDIUM: String = "ScreenMedium"
    const val SCREEN_LARGE: String = "ScreenLarge"
    const val SCREEN_EXTRA_LARGE: String = "ScreenExtraLarge"
    const val SCREEN_TWO_EXTRA_LARGE: String = "ScreenTwoExtraLarge"
    const val CONTAINER: String = "container"

    /** Default panel content width (historical `.ff-page` cap of 80rem). */
    const val DEFAULT: String = "7xl"

    private val UTILITY_CLASSES: Map<String, String> =
        linkedMapOf(
            PROSE to "max-w-prose",
            SCREEN_SMALL to "max-w-screen-sm",
            SCREEN_MEDIUM to "max-w-screen-md",
            SCREEN_LARGE to "max-w-screen-lg",
            SCREEN_EXTRA_LARGE to "max-w-screen-xl",
            SCREEN_TWO_EXTRA_LARGE to "max-w-screen-2xl",
            CONTAINER to "container",
            "Container" to "container",
        )

    /**
     * One bracketed CSS length and nothing else: `[900px]`, `[48rem]`, `[80ch]`, `[0]`.
     */
    private val ARBITRARY =
        Regex(
            """\[(0|(?:\d+\.\d+|\d*\.\d+|\d+)(?:px|rem|em|ch|ex|vw|vh|vmin|vmax|dvw|dvh|svw|svh|lvw|lvh|cqw|cqh|%|cm|mm|in|pt|pc))\]""",
            RegexOption.IGNORE_CASE,
        )

    @JvmStatic
    fun requireKnown(width: String): String {
        if (width in TOKENS || UTILITY_CLASSES.containsKey(width) || arbitraryLength(width) != null) {
            return width
        }
        throw FireflyException("Unknown max width [$width]")
    }

    /**
     * Class for [width], or null when [width] still uses `data-ff-max-width`.
     * A bracketed length becomes `max-w-[900px]` (brackets included).
     */
    @JvmStatic
    fun utilityClass(width: String): String? {
        val named = UTILITY_CLASSES[width]
        if (named != null) return named
        if (arbitraryLength(width) != null) return "max-w-$width"
        return null
    }

    /**
     * Inline custom property for a bracketed length, or null.
     * The panel stylesheet applies it from elements whose class is `max-w-[…]`.
     */
    @JvmStatic
    fun arbitraryStyle(width: String): String? {
        val length = arbitraryLength(width) ?: return null
        return "--ff-arbitrary-max: $length"
    }

    private fun arbitraryLength(width: String): String? {
        val match = ARBITRARY.matchEntire(width) ?: return null
        return match.groupValues[1]
    }
}
