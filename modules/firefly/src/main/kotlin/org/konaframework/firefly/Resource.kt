package org.konaframework.firefly

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordStores
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TableButton
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
        if (page is CreatePage) {
            applyCreate(page, state)
            return
        }
        if (page is EditPage) {
            applyEdit(page, state)
            return
        }
        if (page is ViewPage) {
            applyView(page, state)
        }
    }

    private fun applyManage(page: ManagePage, state: Map<String, Any?>) {
        page.title(label())
        val schema = form(liveSchema(state))
        val info = infolist(liveSchema(state))
        val built = table(listTable())
        applyTableQuery(built, state)
        page.table(built)
        page.mountForm(schema)
        // Simple resources keep create/edit/view/delete as dialog header actions.
        page.headerActions(
            operation("create", "Create", schema.components()),
            operation("edit", "Edit", schema.components()),
            operation("view", "View", info.components()),
            deleteAction(state),
        )
    }

    private fun applyList(page: ListPage, state: Map<String, Any?>) {
        page.title(label())
        val built = table(listTable())
        applyTableQuery(built, state)
        val csrf = state["csrfField"] as? String ?: ""
        // Row actions — never sidebar links — open the record pages (Shamar list toolbar).
        built.rowButtons(
            TableButton.make("view").label("View").url(rowRecordUrl("view")),
            TableButton.make("edit").label("Edit").url(rowRecordUrl("edit")),
            TableButton.make("delete").label("Delete").confirmPost("delete", csrf),
        )
        page.table(built)
        // Full-resource create is a header link — dialogs belong on simple/manage and delete.
        page.headerActions(Action.make("create").label("New").url("create"))
    }

    private fun applyCreate(page: CreatePage, state: Map<String, Any?>) {
        page.title("New ${label()}")
        page.mountForm(form(liveSchema(state)))
        // Native form submit (Shamar) — Create posts #ff-form; Cancel is a list link.
        page.headerActions(
            Action.make("save").label("Create").submitForm("ff-form"),
            Action.make("cancel").label("Cancel").url("list").outlined(),
        )
    }

    private fun applyEdit(page: EditPage, state: Map<String, Any?>) {
        page.title("Edit ${label()}")
        page.mountForm(form(liveSchema(state)))
        page.infolistHtml(relationMarkup())
        val recordId = state["record"]?.toString().orEmpty()
        val viewUrl = if (recordId.isEmpty()) "view" else "view?id=${encodeQuery(recordId)}"
        page.headerActions(
            Action.make("save").label("Save").submitForm("ff-form"),
            Action.make("view").label("View").url(viewUrl).outlined(),
            Action.make("cancel").label("Cancel").url("list").outlined(),
            deleteAction(state),
        )
    }

    /** Prefer a Cadence-backed table when the resource has a model; otherwise an empty memory table. */
    private fun listTable(): Table<*, *> {
        return try {
            // Table.forModel succeeds without a Database; load() then throws CadenceException.
            // Require a global Database before preferring Cadence (tests / pre-boot stay on memory).
            org.konaframework.cadence.Database.global()
            Table.forModel(boundModel())
        } catch (_: FireflyException) {
            Table.memory()
        } catch (_: org.konaframework.cadence.CadenceException) {
            Table.memory()
        }
    }

    private fun applyTableQuery(built: Table<*, *>, state: Map<String, Any?>) {
        val search = state["search"] as? String
        if (!search.isNullOrBlank()) built.search(search)
        val sort = state["sort"] as? String
        if (!sort.isNullOrBlank()) {
            val rawDirection = state["direction"]
            val descending = rawDirection != null && rawDirection.toString().equals("desc", ignoreCase = true)
            try {
                built.sort(sort, if (descending) "desc" else "asc")
            } catch (_: FireflyException) {
                // Unknown / non-sortable column — ignore.
            }
        }
        val pageRaw = state["page"] as? String
        val pageNumber = pageRaw?.toIntOrNull()
        if (pageNumber != null) {
            try {
                built.page(pageNumber)
            } catch (_: FireflyException) {
                // Invalid page — keep default.
            }
        }
        val perPageRaw = state["perPage"] as? String
        val perPage = perPageRaw?.toIntOrNull()
        if (perPage != null) {
            try {
                built.perPage(perPage)
            } catch (_: FireflyException) {
                // Invalid size — keep default.
            }
        }
    }

    private fun applyView(page: ViewPage, state: Map<String, Any?>) {
        page.title(label())
        page.infolistHtml(viewMarkup(state))
        val recordId = state["record"]?.toString().orEmpty()
        val editUrl = if (recordId.isEmpty()) "edit" else "edit?id=${encodeQuery(recordId)}"
        page.headerActions(
            Action.make("edit").label("Edit").url(editUrl),
            Action.make("back").label("Back to list").url("list").outlined(),
            deleteAction(state),
        )
    }

    private fun operation(name: String, heading: String, components: List<Component>): Action {
        val action = Action.make(name).label(heading).modal().modalHeading(heading)
        if (components.isNotEmpty()) action.schema(*components.toTypedArray())
        return action
    }

    /**
     * Confirm dialog that POSTs to the resource `delete` route with CSRF + record id.
     * Opens via chrome `data-ff-dialog-*` (no Alpine required on DefaultLayout).
     */
    private fun deleteAction(state: Map<String, Any?>): Action {
        val id = state["record"]?.toString().orEmpty()
        val csrf = state["csrfField"] as? String ?: ""
        val extras = buildString {
            append(csrf)
            if (id.isNotEmpty()) {
                append("""<input type="hidden" name="id" value="${escapeHtml(id)}" />""")
            }
        }
        return Action.make("delete")
            .label("Delete")
            .color("danger")
            .requiresConfirmation()
            .modalHeading("Delete")
            .modalDescription("Delete this record permanently?")
            .modalSubmitActionLabel("Delete")
            .postForm("delete", extras)
    }

    /**
     * Load the Cadence/memory record for edit/view and fill form/infolist state.
     * [state] carries the route id under `record` (from `?id=` / `?record=`).
     */
    private fun liveSchema(state: Map<String, Any?>): Schema {
        val schema = Schema.make()
        val raw = state["record"] ?: return schema
        val key = raw.toString()
        if (key.isBlank()) return schema
        schema.record(raw)
        try {
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            val found = store.find(key) ?: return schema
            schema.record(found)
            schema.key(key)
            schema.fill(store.attributes(found))
        } catch (_: FireflyException) {
            // No model / store — keep the raw id for hooks that only need schema.record().
        }
        return schema
    }

    private fun viewMarkup(state: Map<String, Any?>): String = infolist(liveSchema(state)).render() + relationMarkup()

    private fun relationMarkup(): String {
        if (managers.isEmpty()) return ""
        val out = StringBuilder()
        for (manager in managers) out.append(manager.render())
        return out.toString()
    }

    private fun rowRecordUrl(page: String): java.util.function.Function<org.konaframework.firefly.tables.Cell, String> =
        java.util.function.Function { cell ->
            "$page?id=${encodeQuery(cell.get("key").toString())}"
        }

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
