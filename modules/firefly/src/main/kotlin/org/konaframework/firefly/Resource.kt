package org.konaframework.firefly

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.infolists.Infolist
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordStores
import org.konaframework.firefly.tables.Table
import java.util.function.Function

/**
 * A panel resource discovered from `resources/`. It binds one model to its pages, form,
 * infolist, table, and actions. A simple resource uses one Manage page. A full resource
 * uses list, create, edit, and view pages plus relation managers.
 */
open class Resource @JvmOverloads constructor(private val slugValue: String = "") {
    private val pageList = ArrayList<PanelPage>()
    private val managers = ArrayList<RelationManager>()
    private var labelLiteral: String? = null
    private var labelUsing: Function<Get, String>? = null
    private var modelClass: Class<*>? = null
    private var modelUsing: Function<Get, Class<*>?>? = null
    private var formSchema: Schema? = null
    private var infolistRef: Infolist? = null
    private var tableRef: Table<*, *>? = null
    private var simpleFlag = false
    private var configured = false

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
        configured = true
    }

    fun model(type: Function<Get, Class<*>?>): Resource = apply {
        modelUsing = type
        modelClass = null
        configured = true
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
    fun query(): Any = RecordStores.open(boundModel()).query() as Any

    fun form(schema: Schema): Resource = apply {
        formSchema = schema
        configured = true
    }

    fun form(): Schema? = formSchema

    fun infolist(infolist: Infolist): Resource = apply {
        infolistRef = infolist
        configured = true
    }

    fun infolist(): Infolist? = infolistRef

    fun table(table: Table<*, *>): Resource = apply {
        tableRef = table
        configured = true
    }

    fun table(): Table<*, *>? = tableRef

    @JvmOverloads
    fun simple(on: Boolean = true): Resource = apply {
        if (on && managers.isNotEmpty()) {
            throw FireflyException("Simple resource [${slug()}] has no relation managers")
        }
        simpleFlag = on
        configured = true
    }

    fun isSimple(): Boolean = simpleFlag

    fun relation(manager: RelationManager): Resource = apply {
        if (simpleFlag) throw FireflyException("Simple resource [${slug()}] has no relation managers")
        managers.add(manager)
        configured = true
    }

    fun relationManagers(): List<RelationManager> = managers.toList()

    fun page(page: PanelPage): Resource = apply { pageList.add(page) }

    fun pages(): List<PanelPage> {
        if (pageList.isEmpty()) {
            if (configured) materialize()
        }
        for (page in pageList) wire(page)
        return pageList.toList()
    }

    private fun materialize() {
        if (simpleFlag) {
            page(ManagePage("manage"))
            return
        }
        page(ListPage("list"))
        page(CreatePage("create"))
        page(EditPage("edit"))
        page(ViewPage("view"))
    }

    private fun wire(page: PanelPage) {
        if (page is ManagePage) {
            wireManage(page)
            return
        }
        if (page is ListPage) {
            wireList(page)
            return
        }
        if (page is CreatePage || page is EditPage) {
            val form = formSchema
            if (form != null) page.mountForm(form)
        }
        if (page is ViewPage) {
            page.infolistHtml(viewMarkup())
            return
        }
        if (page is EditPage) page.infolistHtml(relationMarkup())
    }

    private fun wireManage(page: ManagePage) {
        val table = tableRef
        if (table != null) page.table(table)
        val form = formSchema
        if (form != null) page.mountForm(form)
        page.headerActions(*manageActions().toTypedArray())
    }

    private fun wireList(page: ListPage) {
        val table = tableRef
        if (table != null) page.table(table)
        page.headerActions(*listActions().toTypedArray())
    }

    private fun manageActions(): List<Action> = listOf(
        operation("create", "Create", formComponents()),
        operation("edit", "Edit", formComponents()),
        operation("view", "View", infoComponents()),
        Action.make("delete").label("Delete").modal().modalHeading("Delete"),
    )

    private fun listActions(): List<Action> = listOf(
        operation("create", "Create", formComponents()),
        Action.make("delete").label("Delete").modal().modalHeading("Delete"),
    )

    private fun operation(name: String, heading: String, components: List<Component>): Action {
        val action = Action.make(name).label(heading).modal().modalHeading(heading)
        if (components.isNotEmpty()) action.schema(*components.toTypedArray())
        return action
    }

    private fun formComponents(): List<Component> {
        val form = formSchema
        if (form == null) return emptyList()
        return form.components()
    }

    private fun infoComponents(): List<Component> {
        val info = infolistRef
        if (info == null) return emptyList()
        return info.schema().components()
    }

    private fun viewMarkup(): String {
        val info = infolistRef
        val rendered = if (info == null) "" else info.render()
        return rendered + relationMarkup()
    }

    private fun relationMarkup(): String {
        if (managers.isEmpty()) return ""
        val out = StringBuilder()
        for (manager in managers) out.append(manager.render())
        return out.toString()
    }
}
