package org.konaframework.firefly.support

/**
 * Named content-width tokens for panel / page / resource [maxWidth] settings
 * (Filament/Shamar `maxContentWidth` shape). CSS maps each token to `--ff-width-*`
 * (or full / screen / prose).
 */
object MaxWidth {
    /** Tailwind-compatible rem scale plus fill / viewport / prose. */
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

    /** Default panel content width (historical `.ff-page` cap of 80rem). */
    const val DEFAULT: String = "7xl"

    @JvmStatic
    fun requireKnown(width: String): String {
        if (width !in TOKENS) {
            throw FireflyException("Unknown max width [$width]")
        }
        return width
    }
}
