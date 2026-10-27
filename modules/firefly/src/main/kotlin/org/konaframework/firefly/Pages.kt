package org.konaframework.firefly

import org.konaframework.firefly.infolists.Infolist
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.widgets.Page

/**
 * A page the panel can route to. List, create, edit, view, manage, and custom pages are pages.
 */
open class PanelPage @JvmOverloads constructor(private val slugValue: String? = null) : Page() {
    private var parentName: String? = null
    private var boundSlug: String? = null
    private var onShow: ((Map<String, Any?>) -> Unit)? = null

    fun slug(): String {
        val bound = boundSlug
        if (bound != null) return bound
        if (slugValue != null) return slugValue
        val raw = javaClass.simpleName
        val trimmed = if (raw.endsWith("Page")) raw.dropLast(4) else raw
        return trimmed.lowercase()
    }

    internal fun bindSlug(key: String): PanelPage = apply { boundSlug = key }

    internal fun onShow(hook: (Map<String, Any?>) -> Unit): PanelPage = apply { onShow = hook }

    override fun render(state: Map<String, Any?>): String {
        val hook = onShow
        if (hook != null) hook.invoke(state)
        return super.render(state)
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

    /** Optional mode chip beside the page title (create / edit / detail), Shamar-style. */
    override fun modeLabel(): String? = null
}

open class ListPage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-list"

    override fun fallbackTitle(): String = "List"

    override fun modeLabel(): String? = null
}

open class CreatePage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-create"

    override fun fallbackTitle(): String = "Create"

    override fun modeLabel(): String? = "create"
}

open class EditPage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-edit"

    override fun fallbackTitle(): String = "Edit"

    override fun modeLabel(): String? = "edit"
}

open class ViewPage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-view"

    override fun fallbackTitle(): String = "View"

    override fun modeLabel(): String? = "detail"
}

open class ManagePage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-manage"

    override fun fallbackTitle(): String = "Manage"

    override fun modeLabel(): String? = null
}

open class CustomPage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    override fun sectionClass(): String = "ff-page ff-custom"

    override fun fallbackTitle(): String = "Custom"
}

/**
 * Panel home at the panel prefix (`GET /admin` when the id is `admin`).
 * Slug is empty so [Panel] mounts it at the panel root.
 */
open class DashboardPage : PanelPage("") {
    override fun sectionClass(): String = "ff-page ff-dashboard"

    override fun fallbackTitle(): String = "Dashboard"
}
