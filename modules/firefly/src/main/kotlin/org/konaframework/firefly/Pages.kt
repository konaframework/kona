package org.konaframework.firefly

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.infolists.Infolist
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.MaxWidth
import org.konaframework.firefly.widgets.Page
import java.util.function.Function
import java.util.function.Supplier

/**
 * A page the panel can route to. List, create, edit, view, manage, and custom pages are pages.
 */
open class PanelPage @JvmOverloads constructor(private val slugValue: String? = null) : Page() {
    private var parentName: String? = null
    private var boundSlug: String? = null
    private var onShow: ((Map<String, Any?>) -> Unit)? = null
    private var submitHandler: Function<Map<String, String>, String>? = null
    private var iconLiteral: String? = null
    private val nav = NavigationSettings()

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
        prepare(state)
        return super.render(state)
    }

    /** Run the resource hook that builds this page's table/form for [state] without rendering. */
    internal fun prepare(state: Map<String, Any?>) {
        val hook = onShow
        if (hook != null) hook.invoke(state)
    }

    /**
     * Whether the signed-in user may open this page (Filament `canAccess`). A denied page leaves
     * the navigation and answers 403 on its URL. Ask [FireflyAuth.user] or a Kona `Gate` here.
     */
    open fun canAccess(): Boolean = true

    /** The navigation link text: [navigationLabel] with a value, else the slug (`Dashboard` for home). */
    open fun navigationLabel(): String {
        val set = nav.label
        if (set != null) return set
        val slug = slug()
        if (slug.isEmpty()) return "Dashboard"
        return slug
    }

    fun navigationLabel(value: String): PanelPage = apply { nav.label(value) }

    /** Sidebar heading this page sits under (declare its order with `Panel.navigationGroups`). */
    open fun navigationGroup(): String? = nav.group

    fun navigationGroup(name: String): PanelPage = apply { nav.group(name) }

    /** Sort among its siblings, lowest first. Unset sorts last, by label. */
    open fun navigationSort(): Int? = nav.sort

    fun navigationSort(value: Int): PanelPage = apply { nav.sort = value }

    /** Badge now (null for none). Override, or set a provider with `navigationBadge { count }`. */
    open fun navigationBadge(): NavigationBadge? = nav.badge()

    /** Badge text (null or blank hides it), recomputed on every render. */
    fun navigationBadge(provider: Supplier<Any?>): PanelPage = apply { nav.badgeProvider = provider }

    fun navigationBadge(color: String, provider: Supplier<Any?>): PanelPage = apply {
        nav.badgeColor(color)
        nav.badgeProvider = provider
    }

    fun navigationBadgeColor(color: String): PanelPage = apply { nav.badgeColor(color) }

    /** Whether the page gets a navigation link (Filament `shouldRegisterNavigation`). The page still routes. */
    open fun shouldRegisterNavigation(): Boolean = nav.register

    fun shouldRegisterNavigation(on: Boolean): PanelPage = apply { nav.register = on }

    /** Navigation icon (kona-icons name or short alias). Unset → dashboard home / squares-2x2 default. */
    fun icon(name: String): PanelPage = apply { iconLiteral = name }

    fun navigationIcon(): String? = iconLiteral

    fun parent(name: String): PanelPage = apply { parentName = name }

    fun parentName(): String? = parentName

    fun mountForm(schema: Schema): PanelPage = apply { form(schema) }

    /**
     * Handle the mounted form when it posts back to this page. [handler] receives the posted fields
     * and returns the notification title; without a handler the title is "Form submitted".
     */
    fun onFormSubmit(handler: Function<Map<String, String>, String>): PanelPage = apply { submitHandler = handler }

    internal fun submitForm(input: Map<String, String>): String {
        val handler = submitHandler ?: return "Form submitted"
        return handler.apply(input)
    }

    fun mountInfolist(infolist: Infolist): PanelPage = apply { infolistHtml(infolist.render()) }

    override fun maxWidth(width: String): PanelPage = apply { super.maxWidth(width) }

    override fun maxWidth(width: MaxWidth): PanelPage = apply { super.maxWidth(width) }

    override fun maxContentWidth(width: String): PanelPage = apply { super.maxContentWidth(width) }

    override fun maxContentWidth(width: MaxWidth): PanelPage = apply { super.maxContentWidth(width) }

    override fun defaultMaxWidth(width: String): PanelPage = apply { super.defaultMaxWidth(width) }

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

/**
 * An edit or view page: the resource record is on the page. Add actions next to the built-in
 * header actions with [getHeaderActions] or the fluent [headerActions]. Actions that carry a server
 * handler (`Action.action { record -> … }`) run against the page record through
 * `POST {panel}/{resource}/actions/{name}`; their `visibleForRecord`, `authorize("ability")` and
 * dialog [schema][Action.schema] resolve against that record.
 */
open class RecordPage @JvmOverloads constructor(slug: String? = null) : PanelPage(slug) {
    private val fluentHeader = ArrayList<Action>()

    /**
     * Extra header actions (Filament `getHeaderActions`). Override and return fresh actions; the
     * panel calls this for every render and every action POST. An action named like a built-in
     * (`save`, `edit`, `view`, `cancel`, `back`, `delete`) replaces it.
     */
    open fun getHeaderActions(): List<Action> = emptyList()

    /**
     * Extra header actions next to the built-in ones (not a replacement for them). Replaces earlier
     * calls; [getHeaderActions] adds after these.
     */
    override fun headerActions(vararg actions: Action): RecordPage = apply {
        fluentHeader.clear()
        fluentHeader.addAll(actions)
    }

    /** Fluent and overridden extra header actions, in declaration order. */
    fun extraHeaderActions(): List<Action> = fluentHeader + getHeaderActions()

    /** Sets the rendered header: [defaults] with the extra actions merged in. */
    internal fun mergeHeader(defaults: List<Action>) {
        super.headerActions(*mergeActions(defaults, extraHeaderActions()).toTypedArray())
    }
}

/**
 * Merges [extras] into [defaults]: an extra named like a default replaces it in place, the rest follow
 * the first (primary) default. Extra names must be unique.
 */
internal fun mergeActions(defaults: List<Action>, extras: List<Action>): List<Action> {
    val byName = LinkedHashMap<String, Action>()
    for (extra in extras) {
        if (byName.put(extra.name, extra) != null) {
            throw org.konaframework.firefly.support.FireflyException("Duplicate header action [${extra.name}]")
        }
    }
    val defaultNames = defaults.map { it.name }.toSet()
    val added = extras.filter { it.name !in defaultNames }
    val merged = ArrayList<Action>()
    defaults.forEachIndexed { index, action ->
        merged.add(byName[action.name] ?: action)
        if (index == 0) merged.addAll(added)
    }
    return merged
}

open class EditPage @JvmOverloads constructor(slug: String? = null) : RecordPage(slug) {
    override fun sectionClass(): String = "ff-page ff-edit"

    override fun fallbackTitle(): String = "Edit"

    override fun modeLabel(): String? = "edit"

    override fun headerActions(vararg actions: Action): EditPage {
        super.headerActions(*actions)
        return this
    }
}

open class ViewPage @JvmOverloads constructor(slug: String? = null) : RecordPage(slug) {
    override fun sectionClass(): String = "ff-page ff-view"

    override fun fallbackTitle(): String = "View"

    override fun modeLabel(): String? = "detail"

    override fun headerActions(vararg actions: Action): ViewPage {
        super.headerActions(*actions)
        return this
    }
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
 * The page does not set a max width, so it uses the panel content width.
 * Call [maxWidth] or [maxContentWidth] when this page should differ.
 */
open class DashboardPage : PanelPage("") {
    override fun sectionClass(): String = "ff-page ff-dashboard"

    override fun fallbackTitle(): String = "Dashboard"

    override fun emitDashboardColumns(): Boolean = true

    override fun columns(count: Int): DashboardPage {
        super.columns(count)
        return this
    }

    override fun filtersForm(schema: Schema): DashboardPage {
        super.filtersForm(schema)
        return this
    }
}
