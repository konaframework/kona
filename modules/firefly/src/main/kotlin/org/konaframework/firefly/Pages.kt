package org.konaframework.firefly

import org.konaframework.firefly.infolists.Infolist
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.widgets.Page

/**
 * A page the panel can route to. List, create, edit, view, manage, and custom pages are pages.
 */
open class PanelPage @JvmOverloads constructor(private val slugValue: String? = null) : Page() {
    private var parentName: String? = null

    fun slug(): String {
        if (slugValue != null) return slugValue
        val raw = javaClass.simpleName
        val trimmed = if (raw.endsWith("Page")) raw.dropLast(4) else raw
        return trimmed.lowercase()
    }

    open fun navigationLabel(): String {
        val slug = slug()
        if (slug.isEmpty()) return "Dashboard"
        return slug
    }

    fun parent(name: String): PanelPage = apply { parentName = name }

    fun parentName(): String? = parentName

    fun mountForm(schema: Schema): PanelPage = apply { form(schema) }

    fun mountInfolist(infolist: Infolist): PanelPage = apply { infolistHtml(infolist.render()) }
}

open class ListPage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-list"

    override fun fallbackTitle(): String = "List"
}

open class CreatePage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-create"

    override fun fallbackTitle(): String = "Create"
}

open class EditPage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-edit"

    override fun fallbackTitle(): String = "Edit"
}

open class ViewPage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-view"

    override fun fallbackTitle(): String = "View"
}

open class ManagePage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-manage"

    override fun fallbackTitle(): String = "Manage"
}

open class CustomPage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-custom"

    override fun fallbackTitle(): String = "Custom"
}
