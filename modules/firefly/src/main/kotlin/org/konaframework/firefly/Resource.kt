package org.konaframework.firefly

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordStores
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.widgets.Page
import java.util.function.Function

/**
 * A panel resource discovered from `resources/`. It binds one model to its pages, form,
 * infolist, table, and actions. Override [form], [table], [infolist], and [getPages].
 * A simple resource uses one Manage page. A full resource uses list, create, edit, and
 * view pages plus relation managers. The base class calls the schema hooks when a page
 * renders, so they see the current tenant and record.
 */
open class Resource @JvmOverloads constructor(private val slugValue: String = "") {
    private val managers = ArrayList<RelationManager>()
    private var labelLiteral: String? = null
    private var labelUsing: Function<Get, String>? = null
    private var modelClass: Class<*>? = null
    private var modelUsing: Function<Get, Class<*>?>? = null
    private var simpleFlag = false
    private var tenantColumn: String? = null
    private var resolvedPages: List<PanelPage>? = null

    fun slug(): String {
        if (slugValue.isNotEmpty()) return slugValue
        return javaClass.simpleName.lowercase()
    }

    fun label(value: String): Resource = apply {
        if (value.isBlank()) throw FireflyException("Resource label is blank")
        labelLiteral = value
        labelUsing = null
    }

    fun label(value: Function<Get, String>): Resource = apply {
        labelUsing = value
        labelLiteral = null
    }

    fun label(): String {
        val using = labelUsing
        if (using != null) {
            val text = using.apply(SchemaBridge.open(emptyMap()))
            if (text.isBlank()) throw FireflyException("Resource label is blank")
            return text
        }
        val value = labelLiteral
        if (value != null) return value
        return slug()
    }

    fun model(type: Class<*>): Resource = apply {
        modelClass = type
        modelUsing = null
    }

    fun model(type: Function<Get, Class<*>?>): Resource = apply {
        modelUsing = type
        modelClass = null
    }

    fun boundModel(): Class<*> {
        val using = modelUsing
        if (using != null) {
            val type = using.apply(SchemaBridge.open(emptyMap()))
            if (type == null) throw FireflyException("Resource [${slug()}] has no model")
            return type
        }
        val type = modelClass
        if (type == null) throw FireflyException("Resource [${slug()}] has no model")
        return type
    }

    /** Cadence models use [org.konaframework.cadence.ModelBuilder]. A document model names the missing addon. */
    fun query(): Any {
        val opened = RecordStores.open(boundModel()).query()
        val column = tenantColumn
        val tenant = FireflyTenant.id()
        if (column == null || tenant == null) return opened as Any
        val builder = opened as org.konaframework.cadence.ModelBuilder<*>
        builder.where(column, tenant)
        return builder
    }

    /**
     * Form for create, edit, and the manage dialogs. The base returns [schema] unchanged.
     * Called when a page renders.
     */
    open fun form(schema: Schema): Schema = schema

    /**
     * Infolist for the view page and the manage view dialog. The base returns [schema] unchanged.
     * Called when a page renders.
     */
    open fun infolist(schema: Schema): Schema = schema

    /**
     * Table for list and manage. [table] is empty. The base returns it unchanged.
     * Called when a page renders.
     */
    open fun table(table: Table<*, *>): Table<*, *> = table

    @JvmOverloads
    fun simple(on: Boolean = true): Resource = apply {
        if (on && managers.isNotEmpty()) {
            throw FireflyException("Simple resource [${slug()}] has no relation managers")
        }
        simpleFlag = on
    }

    fun isSimple(): Boolean = simpleFlag

    fun tenant(column: String): Resource = apply {
        if (column.isBlank()) throw FireflyException("Tenant column is blank")
        tenantColumn = column
    }

    fun relation(manager: RelationManager): Resource = apply {
        if (simpleFlag) throw FireflyException("Simple resource [${slug()}] has no relation managers")
        managers.add(manager)
    }

    fun relationManagers(): List<RelationManager> = managers.toList()

    /**
     * Pages keyed by route slug. A simple resource returns one Manage page.
     * Otherwise list, create, edit, and view. Override to serve a different set.
     */
    open fun getPages(): Map<String, Page> {
        if (simpleFlag) return linkedMapOf("manage" to ManagePage("manage"))
        return linkedMapOf(
            "list" to ListPage("list"),
            "create" to CreatePage("create"),
            "edit" to EditPage("edit"),
            "view" to ViewPage("view"),
        )
    }

    /** Resolved pages. Schemas are built when each page renders. */
    fun pages(): List<PanelPage> {
        val cached = resolvedPages
        if (cached != null) return cached
        val built = ArrayList<PanelPage>()
        for ((key, page) in getPages()) {
            if (page !is PanelPage) throw FireflyException("Resource [${slug()}] page [$key] is not a panel page")
            if (key.isNotEmpty()) page.bindSlug(key)
            page.onShow { state -> apply(page, state) }
            built.add(page)
        }
        val frozen = built.toList()
        resolvedPages = frozen
        return frozen
    }

    private fun apply(page: PanelPage, state: Map<String, Any?>) {
        if (page is ManagePage) {
            applyManage(page, state)
            return
        }
        if (page is ListPage) {
            applyList(page, state)
            return
        }
        if (page is CreatePage || page is EditPage) {
            page.mountForm(form(liveSchema(state)))
        }
        if (page is ViewPage) {
            page.infolistHtml(viewMarkup(state))
            return
        }
        if (page is EditPage) page.infolistHtml(relationMarkup())
    }

    private fun applyManage(page: ManagePage, state: Map<String, Any?>) {
        val schema = form(liveSchema(state))
        val info = infolist(liveSchema(state))
        page.table(table(Table.memory()))
        page.mountForm(schema)
        page.headerActions(
            operation("create", "Create", schema.components()),
            operation("edit", "Edit", schema.components()),
            operation("view", "View", info.components()),
            Action.make("delete").label("Delete").modal().modalHeading("Delete"),
        )
    }

    private fun applyList(page: ListPage, state: Map<String, Any?>) {
        val schema = form(liveSchema(state))
        page.table(table(Table.memory()))
        page.headerActions(
            operation("create", "Create", schema.components()),
            Action.make("delete").label("Delete").modal().modalHeading("Delete"),
        )
    }

    private fun operation(name: String, heading: String, components: List<Component>): Action {
        val action = Action.make(name).label(heading).modal().modalHeading(heading)
        if (components.isNotEmpty()) action.schema(*components.toTypedArray())
        return action
    }

    private fun liveSchema(state: Map<String, Any?>): Schema {
        val schema = Schema.make()
        if (state.containsKey("record")) schema.record(state["record"])
        return schema
    }

    private fun viewMarkup(state: Map<String, Any?>): String = infolist(liveSchema(state)).render() + relationMarkup()

    private fun relationMarkup(): String {
        if (managers.isEmpty()) return ""
        val out = StringBuilder()
        for (manager in managers) out.append(manager.render())
        return out.toString()
    }
}
