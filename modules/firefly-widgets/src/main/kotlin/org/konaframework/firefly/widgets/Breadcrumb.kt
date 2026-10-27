package org.konaframework.firefly.widgets

/**
 * One crumb in a page breadcrumb trail (Shamar page-heading parity).
 * [href] null marks the current page (or a non-link crumb).
 */
data class Breadcrumb(
    val label: String,
    val href: String? = null,
) {
    companion object {
        @JvmStatic
        fun make(label: String): Breadcrumb = Breadcrumb(label, null)

        @JvmStatic
        fun make(label: String, href: String?): Breadcrumb = Breadcrumb(label, href)
    }
}
