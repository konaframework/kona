package org.konaframework.firefly.widgets

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.MaxWidth
import org.konaframework.firefly.tables.Table
import java.util.function.Function

/**
 * A screen that mounts a schema, a table, header and footer actions, and widgets.
 * Panel routes arrive with the panel module. This type is the composition host those routes serve.
 */
open class Page {
    private val titleSetting = TextSetting()
    private val header = ArrayList<Action>()
    private val footer = ArrayList<Action>()
    private val widgetList = ArrayList<Widget>()
    private val crumbList = ArrayList<Breadcrumb>()
    private var contentSchema: Schema? = null
    private var formSchema: Schema? = null
    private var filtersFormSchema: Schema? = null
    private var infolistMarkup: String? = null
    private var relationsMarkup: String = ""
    private var contentTable: Table<*, *>? = null
    private var maxWidthValue: String? = null
    private var maxWidthExplicit: Boolean = false
    /** Dashboard grid columns (1–4). Only emitted when [emitDashboardColumns] is true. */
    private var dashboardColumns: Int = 3

    private companion object {
        /** Matches one or more relation manager `<section class="ff-relation" …>…</section>` blocks. */
        private val RELATION_SECTION =
            Regex("""<section\b[^>]*\bff-relation\b[^>]*>[\s\S]*?</section>""")
    }

    fun title(value: String): Page = apply { titleSetting.setLiteral(value) }

    fun title(provider: Function<Get, String>): Page = apply { titleSetting.setUsing(provider) }

    fun breadcrumbs(vararg crumbs: Breadcrumb): Page = apply {
        crumbList.clear()
        crumbList.addAll(crumbs)
    }

    fun breadcrumbs(crumbs: List<Breadcrumb>): Page = apply {
        crumbList.clear()
        crumbList.addAll(crumbs)
    }

    fun headerActions(vararg actions: Action): Page = apply {
        header.clear()
        header.addAll(actions)
    }

    fun footerActions(vararg actions: Action): Page = apply {
        footer.clear()
        footer.addAll(actions)
    }

    fun widgets(vararg widgets: Widget): Page = apply {
        widgetList.clear()
        widgetList.addAll(widgets)
    }

    fun discoverWidgets(panelDirectory: java.nio.file.Path): Page = apply {
        widgetList.addAll(WidgetDiscovery.discover(panelDirectory))
    }

    /** Widgets mounted with [widgets] / discovery (sorted copy not applied). */
    fun mountedWidgets(): List<Widget> = widgetList.toList()

    fun findWidget(id: String): Widget? = widgetList.firstOrNull { it.name == id }

    fun schema(schema: Schema): Page = apply { contentSchema = schema }

    fun form(schema: Schema): Page = apply { formSchema = schema }

    /**
     * Optional dashboard filters form (Shamar `filtersForm` / Filament `HasFiltersForm`).
     * Rendered as GET above the widget grid; field values live in the query string / page state.
     */
    open fun filtersForm(schema: Schema): Page = apply { filtersFormSchema = schema }

    fun mountedFiltersForm(): Schema? = filtersFormSchema

    /**
     * Responsive grid columns for dashboard widgets (Shamar `DashboardPage.columns`, 1–4).
     * Default is 3 on [Dashboard].
     */
    open fun columns(count: Int): Page = apply {
        if (count < 1 || count > 4) throw FireflyException("Dashboard columns must be 1–4, got [$count]")
        dashboardColumns = count
    }

    fun columns(): Int = dashboardColumns

    fun infolistHtml(html: String): Page = apply { infolistMarkup = html }

    /** Relation manager markup, rendered after the form/infolist without an infolist card around it. */
    fun relationsHtml(html: String): Page = apply { relationsMarkup = html }

    fun table(table: Table<*, *>): Page = apply { contentTable = table }

    /** The table mounted with [table], or null. The panel runs bulk actions against it. */
    fun mountedTable(): Table<*, *>? = contentTable

    /** The form schema mounted with [form], or null. The panel answers its Select searches and submits. */
    fun mountedForm(): Schema? = formSchema

    /**
     * Cap this page's content column (`xs`…`7xl`, `full`, `screen`, `prose`).
     * Overrides the panel default. Emits `data-ff-max-width` on the page section.
     */
    open fun maxWidth(width: String): Page = apply {
        maxWidthValue = MaxWidth.requireKnown(width)
        maxWidthExplicit = true
    }

    /**
     * Resource (or other host) default when the page did not call [maxWidth].
     * No-op if the page already set an explicit width.
     */
    open fun defaultMaxWidth(width: String): Page = apply {
        if (maxWidthExplicit) return@apply
        maxWidthValue = MaxWidth.requireKnown(width)
    }

    fun render(): String = render(emptyMap())

    open fun render(state: Map<String, Any?>): String {
        WidgetAssets.reset()
        val get = WidgetBridge.open(state)
        val title = titleSetting.resolve(get) ?: fallbackTitle()
        val visible = widgetList
            .filter { it.visibleFor(get) }
            .sortedBy { it.sort() }
        val widgetSchema = Schema().fill(state).components(*visible.toTypedArray())
        val schemaHtml = if (contentSchema == null) "" else contentSchema!!.render()
        val formHtml = wrapForm(if (formSchema == null) "" else formSchema!!.render(), state)
        val infoHtml = if (infolistMarkup == null) "" else wrapInfolist(infolistMarkup!!)
        val tableHtml = if (contentTable == null) "" else contentTable!!.render()
        val filtersHtml = renderFiltersForm(state)
        val mode = modeLabel()
        val modeHtml = if (mode == null) {
            ""
        } else {
            """<span class="ff-page-mode" data-ff-mode="${escapeHtml(mode)}">${escapeHtml(mode)}</span>"""
        }
        val crumbs = renderBreadcrumbs()
        val widthAttr =
            if (maxWidthValue == null) {
                ""
            } else {
                """ data-ff-max-width="${escapeHtml(maxWidthValue!!)}""""
            }
        val columnsAttr = if (emitDashboardColumns()) {
            """ data-ff-dashboard-columns="$dashboardColumns""""
        } else {
            ""
        }
        val widgetsHtml = if (visible.isEmpty() && emitDashboardColumns()) {
            """<div class="ff-dashboard-empty">No dashboard widgets configured.</div>"""
        } else {
            widgetSchema.render()
        }
        // Crumbs on their own top row; heading + mode chip share the next row with actions (#359).
        return """<section class="${sectionClass()}"$widthAttr$columnsAttr><header class="ff-page-header">$crumbs<div class="ff-page-toolbar"><div class="ff-page-heading"><h1>${escapeHtml(title)}</h1>$modeHtml</div><div class="ff-page-header-actions">${renderActions(header)}</div></div></header><div class="ff-page-content">$schemaHtml$formHtml$infoHtml$relationsMarkup$tableHtml</div>$filtersHtml<div class="ff-page-widgets">$widgetsHtml</div><footer class="ff-page-footer">${renderActions(footer)}</footer></section>"""
    }

    private fun renderFiltersForm(state: Map<String, Any?>): String {
        val schema = filtersFormSchema ?: return ""
        val body = schema.fill(state).render()
        val home = state["panelHome"]
        val action = if (home != null) {
            home.toString()
        } else {
            val formAction = state["formAction"]
            if (formAction == null) "" else formAction.toString()
        }
        val actionAttr = if (action.isBlank()) "" else """ action="${escapeHtml(action)}""""
        return """<form class="ff-dashboard-filters" method="get"$actionAttr><div class="ff-dashboard-filters-fields">$body</div><button type="submit" class="ff-btn">Apply</button></form>"""
    }

    /** True for [Dashboard] / dashboard pages that use the fixed widget grid. */
    protected open fun emitDashboardColumns(): Boolean = false

    private fun wrapInfolist(html: String): String {
        if (html.isEmpty()) return ""
        if (html.contains("ff-infolist")) return html
        // Edit mounts relations via infolistHtml; .ff-relation already owns a flat surface
        // (.ff-table-wrap card). Wrapping that markup in .ff-infolist nests a second card (#271).
        if (isRelationOnlyMarkup(html)) return html
        return """<div class="ff-infolist">$html</div>"""
    }

    /** True when [html] is only relation manager section(s) (no schema/infolist body). */
    private fun isRelationOnlyMarkup(html: String): Boolean {
        if (!html.contains("ff-relation")) return false
        val withoutRelations =
            RELATION_SECTION.replace(html, "")
        return withoutRelations.isBlank()
    }

    private fun renderBreadcrumbs(): String {
        if (crumbList.isEmpty()) return ""
        val out = StringBuilder("""<nav aria-label="Breadcrumb"><ol class="ff-breadcrumbs">""")
        crumbList.forEachIndexed { index, crumb ->
            val last = index == crumbList.lastIndex
            if (index > 0) out.append("""<li class="ff-breadcrumb-sep" aria-hidden="true"></li>""")
            val href = crumb.href
            if (last || href.isNullOrBlank()) {
                out.append("""<li class="ff-breadcrumb-current"><span>${escapeHtml(crumb.label)}</span></li>""")
            } else {
                out.append("""<li><a href="${escapeHtml(href)}">${escapeHtml(crumb.label)}</a></li>""")
            }
        }
        out.append("</ol></nav>")
        return out.toString()
    }

    /**
     * Wrap mounted form schema in a native POST form (Shamar create/edit). Header save actions
     * use `submitForm("ff-form")` so they post this form from outside the content region.
     */
    private fun wrapForm(body: String, state: Map<String, Any?>): String {
        if (body.isEmpty()) return ""
        val csrf = state["csrfField"] as? String ?: ""
        val action = state["formAction"] as? String
        val actionAttr = if (action.isNullOrBlank()) "" else """ action="${escapeHtml(action)}""""
        val recordId = state["record"]?.toString()
        val hiddenId =
            if (recordId.isNullOrBlank()) {
                ""
            } else {
                """<input type="hidden" name="id" value="${escapeHtml(recordId)}" />"""
            }
        return """<form id="ff-form" class="ff-resource-form" method="post"$actionAttr>$csrf$hiddenId$body</form>"""
    }

    protected open fun sectionClass(): String = "ff-page"

    protected open fun fallbackTitle(): String = "Page"

    /** Optional mode chip rendered beside the title (create / edit / detail). */
    protected open fun modeLabel(): String? = null

    private fun renderActions(actions: List<Action>): String {
        if (actions.isEmpty()) return ""
        return Schema().components(*actions.toTypedArray()).render()
    }
}

open class Dashboard : Page() {
    init {
        maxWidth("full")
    }

    override fun sectionClass(): String = "ff-page ff-dashboard"

    override fun fallbackTitle(): String = "Dashboard"

    override fun emitDashboardColumns(): Boolean = true

    override fun columns(count: Int): Dashboard {
        super.columns(count)
        return this
    }

    override fun filtersForm(schema: Schema): Dashboard {
        super.filtersForm(schema)
        return this
    }
}
