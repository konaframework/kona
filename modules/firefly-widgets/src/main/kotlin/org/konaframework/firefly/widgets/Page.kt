package org.konaframework.firefly.widgets

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
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
    private var contentSchema: Schema? = null
    private var formSchema: Schema? = null
    private var infolistMarkup: String? = null
    private var contentTable: Table<*, *>? = null

    fun title(value: String): Page = apply { titleSetting.setLiteral(value) }

    fun title(provider: Function<Get, String>): Page = apply { titleSetting.setUsing(provider) }

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

    fun schema(schema: Schema): Page = apply { contentSchema = schema }

    fun form(schema: Schema): Page = apply { formSchema = schema }

    fun infolistHtml(html: String): Page = apply { infolistMarkup = html }

    fun table(table: Table<*, *>): Page = apply { contentTable = table }

    fun render(): String = render(emptyMap())

    fun render(state: Map<String, Any?>): String {
        WidgetAssets.reset()
        val get = WidgetBridge.open(state)
        val title = titleSetting.resolve(get) ?: fallbackTitle()
        val widgetSchema = Schema().fill(state).components(*widgetList.toTypedArray())
        val schemaHtml = if (contentSchema == null) "" else contentSchema!!.render()
        val formHtml = if (formSchema == null) "" else formSchema!!.render()
        val infoHtml = if (infolistMarkup == null) "" else infolistMarkup!!
        val tableHtml = if (contentTable == null) "" else contentTable!!.render()
        return """<section class="${sectionClass()}"><header class="ff-page-header"><h1>${escapeHtml(title)}</h1><div class="ff-page-header-actions">${renderActions(header)}</div></header><div class="ff-page-content">$schemaHtml$formHtml$infoHtml$tableHtml</div><div class="ff-page-widgets">${widgetSchema.render()}</div><footer class="ff-page-footer">${renderActions(footer)}</footer></section>"""
    }

    protected open fun sectionClass(): String = "ff-page"

    protected open fun fallbackTitle(): String = "Page"

    private fun renderActions(actions: List<Action>): String {
        if (actions.isEmpty()) return ""
        return Schema().components(*actions.toTypedArray()).render()
    }
}

open class Dashboard : Page() {
    override fun sectionClass(): String = "ff-page ff-dashboard"

    override fun fallbackTitle(): String = "Dashboard"
}
