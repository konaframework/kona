package org.konaframework.firefly

import org.konaframework.firefly.support.FireflyException

/**
 * A panel resource discovered from `resources/`. Model binding, relation managers, and the
 * generator arrive with the resource milestone. This type is the page group the panel mounts.
 */
open class Resource @JvmOverloads constructor(private val slugValue: String = "") {
    private val pageList = ArrayList<PanelPage>()
    private var labelLiteral: String? = null

    fun slug(): String {
        if (slugValue.isNotEmpty()) return slugValue
        return javaClass.simpleName.lowercase()
    }

    fun label(value: String): Resource = apply {
        if (value.isBlank()) throw FireflyException("Resource label is blank")
        labelLiteral = value
    }

    fun label(): String {
        val value = labelLiteral
        if (value != null) return value
        return slug()
    }

    fun page(page: PanelPage): Resource = apply { pageList.add(page) }

    fun pages(): List<PanelPage> = pageList.toList()
}
