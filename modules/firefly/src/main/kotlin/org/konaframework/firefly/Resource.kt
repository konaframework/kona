package org.konaframework.firefly

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.forms.FieldOption
import org.konaframework.firefly.forms.Select
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.MaxWidth
import org.konaframework.firefly.tables.RecordStores
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TableButton
import org.konaframework.firefly.widgets.Breadcrumb
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
    private var pluralLabelLiteral: String? = null
    private var pluralLabelUsing: Function<Get, String>? = null
    private var modelClass: Class<*>? = null
    private var modelUsing: Function<Get, Class<*>?>? = null
    private var simpleFlag = false
    private var tenantColumn: String? = null
    private var resolvedPages: List<PanelPage>? = null
    private var maxWidthLiteral: String? = null
    private var formColumnCount: Int? = null
    private var infolistColumnCount: Int? = null

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

    /**
     * Plural resource title for list/manage page headers, navigation, and list breadcrumbs
     * (Shamar `label` / Filament plural model label). Defaults to an English plural of [label].
     */
    fun pluralLabel(value: String): Resource = apply {
        if (value.isBlank()) throw FireflyException("Resource plural label is blank")
        pluralLabelLiteral = value
        pluralLabelUsing = null
    }

    fun pluralLabel(value: Function<Get, String>): Resource = apply {
        pluralLabelUsing = value
        pluralLabelLiteral = null
    }

    fun pluralLabel(): String {
        val using = pluralLabelUsing
        if (using != null) {
            val text = using.apply(SchemaBridge.open(emptyMap()))
            if (text.isBlank()) throw FireflyException("Resource plural label is blank")
            return text
        }
        val value = pluralLabelLiteral
        if (value != null) return value
        return englishPluralLabel(label())
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
     * Grid columns for [form] schemas (Shamar / Filament `columns`). Applied before
     * [form] when non-null. Integer count applies from `lg` up (mobile stays 1).
     * Generated resources default to 2 via `schema.columns()`.
     */
    fun formColumns(count: Int): Resource = apply {
        if (count < 1) throw FireflyException("Form columns must be at least 1")
        formColumnCount = count
    }

    fun formColumns(): Int? = formColumnCount

    /**
     * Grid columns for [infolist] schemas. Applied before [infolist] when non-null.
     */
    fun infolistColumns(count: Int): Resource = apply {
        if (count < 1) throw FireflyException("Infolist columns must be at least 1")
        infolistColumnCount = count
    }

    fun infolistColumns(): Int? = infolistColumnCount

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

    /** Applies [formColumns] then [form], then loads dotted Cadence relations into state (#361). */
    private fun prepareForm(schema: Schema): Schema {
        val count = formColumnCount
        if (count != null) schema.columns(count)
        form(schema)
        hydrateDottedRelations(schema)
        return schema
    }

    /**
     * Combobox search for a named [Select] on the create/edit form. Relationship Selects query
     * past [org.konaframework.firefly.forms.Select.preloadLimit]; static option maps filter in memory.
     */
    internal fun searchFormOptions(field: String, query: String): List<FieldOption> {
        if (field.isBlank()) return emptyList()
        val schema = prepareForm(liveSchema(emptyMap()))
        val select = schema.component(field) as? Select ?: return emptyList()
        return select.search(schema, query)
    }

    /** Applies [infolistColumns] then [infolist], then loads dotted Cadence relations into state (#361). */
    private fun prepareInfolist(schema: Schema): Schema {
        val count = infolistColumnCount
        if (count != null) schema.columns(count)
        infolist(schema)
        hydrateDottedRelations(schema)
        return schema
    }

    /**
     * `TextEntry.make("project.name")` (and form counterparts) need the related model on the
     * schema record and nested maps in the state store. [liveSchema] finds without `with()`,
     * so load missing relation paths from dotted component names and refill attributes.
     */
    private fun hydrateDottedRelations(schema: Schema) {
        val record = schema.record()
        if (record !is org.konaframework.cadence.Cadence<*>) return
        val relations = LinkedHashSet<String>()
        for (component in schema.walk()) {
            val path = component.name
            if (!path.contains('.')) continue
            val parts = path.split('.')
            // Skip invalid / over-deep dotted names (entries reject these at make; forms may not).
            if (parts.size > org.konaframework.firefly.tables.Column.MAX_RELATION_DEPTH) continue
            if (parts.any { it.isBlank() }) continue
            relations.add(parts.dropLast(1).joinToString("."))
        }
        if (relations.isEmpty()) return
        try {
            record.loadMissing(*relations.toTypedArray())
        } catch (_: RuntimeException) {
            return
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            @Suppress("UNCHECKED_CAST")
            schema.fill(store.attributes(record as Any))
        } catch (_: FireflyException) {
            // No model store — relations stay on the record for dig fallbacks.
        }
    }

    /**
     * Table for list and manage. [table] is empty. The base returns it unchanged.
     * Called when a page renders.
     */
    open fun table(table: Table<*, *>): Table<*, *> = table

    @JvmOverloads
    fun simple(on: Boolean = true): Resource = apply {
        if (on && (managers.isNotEmpty() || getRelations().isNotEmpty())) {
            throw FireflyException("Simple resource [${slug()}] has no relation managers")
        }
        simpleFlag = on
    }

    fun isSimple(): Boolean = simpleFlag

    fun tenant(column: String): Resource = apply {
        if (column.isBlank()) throw FireflyException("Tenant column is blank")
        tenantColumn = column
    }

    /**
     * Default content max-width for this resource's pages (`xs`…`7xl`, `full`, `screen`, `prose`).
     * Applied when each page renders unless the page already called [Page.maxWidth].
     * Overrides the panel default for those pages.
     */
    fun maxWidth(width: String): Resource = apply {
        maxWidthLiteral = MaxWidth.requireKnown(width)
    }

    fun relation(manager: RelationManager): Resource = apply {
        if (simpleFlag) throw FireflyException("Simple resource [${slug()}] has no relation managers")
        managers.add(manager)
    }

    /**
     * Filament-style class-based relation managers. Override on the resource instead of
     * (or in addition to) calling [relation] from `init`.
     */
    open fun getRelations(): List<RelationManager> = emptyList()

    fun relationManagers(): List<RelationManager> {
        val declared = getRelations()
        if (declared.isEmpty()) return managers.toList()
        // simple() already rejects getRelations(); merge fluent relation() with overrides.
        val merged = LinkedHashMap<String, RelationManager>()
        for (manager in managers) merged[manager.name()] = manager
        for (manager in declared) merged[manager.name()] = manager
        return merged.values.toList()
    }

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
        val width = maxWidthLiteral
        if (width != null) page.defaultMaxWidth(width)
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
        page.title(pluralLabel())
        page.breadcrumbs(resourceCrumbs(state, pluralLabel()))
        // Prototype record so Select.relationship can load options in the create dialog (#337).
        val createForm = prepareForm(liveSchema(emptyMap()))
        val built = table(listTable())
        applyTableQuery(built, state)
        val csrf = state["csrfField"] as? String ?: ""
        // Row dialogs carry the record; the manage list itself stays table-only (no standing form).
        built.rowButtons(
            manageViewButton(),
            manageEditButton(csrf),
            TableButton.make("delete").label("Delete").confirmPost("delete", csrf),
        )
        page.table(built)
        // Create alone lives in the header; Edit/View/Delete open from each row.
        page.headerActions(
            operation("create", "Create", createForm)
                .postForm("create", csrf)
                .modalSubmitActionLabel("Create"),
        )
    }

    /**
     * Row View: dialog with the filled infolist for that record (simple/manage only).
     * Footer Edit switches to this row’s Edit modal via [data-ff-dialog-switch].
     * Full resources open View/Edit as pages — see [applyView] header Edit → edit page (#376).
     */
    private fun manageViewButton(): TableButton =
        TableButton.make("view").label("View").modal().body { cell ->
            val markup = prepareInfolist(liveSchema(mapOf("record" to cell.get("key").toString()))).render()
            """$markup<div class="ff-action-footer"><button type="button" class="ff-action-cancel" data-ff-dialog-close>Close</button><button type="button" class="ff-action-submit" data-ff-dialog-switch="edit">Edit</button></div>"""
        }

    /** Row Edit: dialog with the filled form for that record (native POST). */
    private fun manageEditButton(csrf: String): TableButton =
        TableButton.make("edit").label("Edit").modal().body { cell ->
            val key = cell.get("key").toString()
            val markup = prepareForm(liveSchema(mapOf("record" to key))).render()
            """<form method="post" action="edit" class="ff-action-form">$csrf<input type="hidden" name="id" value="${escapeHtml(key)}" /><div class="ff-action-schema">$markup</div><div class="ff-action-footer"><button type="button" class="ff-action-cancel" data-ff-dialog-close>Cancel</button><button type="submit" class="ff-action-submit">Save</button></div></form>"""
        }

    private fun applyList(page: ListPage, state: Map<String, Any?>) {
        page.title(pluralLabel())
        page.breadcrumbs(resourceCrumbs(state, pluralLabel()))
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
        page.breadcrumbs(resourceCrumbs(state, "New ${label()}", listHref = true))
        page.mountForm(prepareForm(liveSchema(state)))
        // Native form submit (Shamar) — Create posts #ff-form; Cancel is a list link.
        page.headerActions(
            Action.make("save").label("Create").submitForm("ff-form"),
            Action.make("cancel").label("Cancel").url("list").outlined(),
        )
    }

    private fun applyEdit(page: EditPage, state: Map<String, Any?>) {
        page.title("Edit ${label()}")
        page.breadcrumbs(resourceCrumbs(state, "Edit ${label()}", listHref = true))
        page.mountForm(prepareForm(liveSchema(state)))
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
        // Soft-nav column picker: `?columns=name,created_at` (#363).
        val columnsRaw = state["columns"] as? String
        if (columnsRaw != null) {
            built.applyToggleableVisibility(columnsRaw.split(','))
        }
    }

    private fun applyView(page: ViewPage, state: Map<String, Any?>) {
        page.title(label())
        page.breadcrumbs(resourceCrumbs(state, label(), listHref = true))
        page.infolistHtml(viewMarkup(state))
        val recordId = state["record"]?.toString().orEmpty()
        val editUrl = if (recordId.isEmpty()) "edit" else "edit?id=${encodeQuery(recordId)}"
        page.headerActions(
            Action.make("edit").label("Edit").url(editUrl),
            Action.make("back").label("Back to list").url("list").outlined(),
            deleteAction(state),
        )
    }

    private fun resourceCrumbs(
        state: Map<String, Any?>,
        current: String,
        listHref: Boolean = false,
    ): List<Breadcrumb> {
        val crumbs = ArrayList<Breadcrumb>()
        val home = state["panelHome"] as? String
        if (!home.isNullOrBlank()) crumbs.add(Breadcrumb.make("Dashboard", home))
        val list = state["resourceList"] as? String
        if (listHref && !list.isNullOrBlank()) {
            crumbs.add(Breadcrumb.make(pluralLabel(), list))
            crumbs.add(Breadcrumb.make(current))
        } else {
            crumbs.add(Breadcrumb.make(current))
        }
        return crumbs
    }

    private fun operation(name: String, heading: String, form: Schema): Action {
        val action = Action.make(name).label(heading).modal().modalHeading(heading)
        if (form.components().isNotEmpty()) action.schema(form)
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
     * Create / blank id binds a prototype model so [Select.relationship] can resolve options (#337).
     */
    private fun liveSchema(state: Map<String, Any?>): Schema {
        val schema = Schema.make()
        val raw = state["record"]
        val key = raw?.toString().orEmpty()
        // Preserve raw route ids for hooks when there is no model/store (e.g. memory-only resources).
        if (key.isNotBlank()) schema.record(raw)
        try {
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            if (key.isNotBlank()) {
                try {
                    val found = store.find(key)
                    if (found != null) {
                        schema.record(found)
                        schema.key(key)
                        schema.fill(store.attributes(found))
                        return schema
                    }
                } catch (_: RuntimeException) {
                    // Fall through to a prototype model below.
                }
                // Missing row still needs a model prototype so Select.relationship can resolve (#337).
                schema.record(store.newRecord())
                schema.key(key)
                return schema
            }
            // Create: prototype model so Select.relationship can resolve options (#337).
            schema.record(store.newRecord())
        } catch (_: FireflyException) {
            // No model / store — leave schema with the raw record (if any).
        }
        return schema
    }

    private fun viewMarkup(state: Map<String, Any?>): String =
        prepareInfolist(liveSchema(state)).render() + relationMarkup()

    private fun relationMarkup(): String {
        val all = relationManagers()
        if (all.isEmpty()) return ""
        val out = StringBuilder()
        for (manager in all) out.append(manager.render())
        return out.toString()
    }

    private fun rowRecordUrl(page: String): java.util.function.Function<org.konaframework.firefly.tables.Cell, String> =
        java.util.function.Function { cell ->
            "$page?id=${encodeQuery(cell.get("key").toString())}"
        }

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)
}

/**
 * English plural for resource titles. Leaves labels that already end in a simple `s`
 * alone (`Orders`, `Notes`) so apps that set a plural [Resource.label] stay stable.
 */
internal fun englishPluralLabel(label: String): String {
    if (label.isBlank()) return label
    val trimmed = label.trimEnd()
    val lower = trimmed.lowercase()
    if (lower.length > 1 &&
        lower.endsWith("s") &&
        !lower.endsWith("ss") &&
        !lower.endsWith("us") &&
        !lower.endsWith("is")
    ) {
        return trimmed
    }
    if (lower.length > 1 && lower.endsWith("y") && lower[lower.length - 2] !in "aeiou") {
        return trimmed.dropLast(1) + "ies"
    }
    if (Regex("""(?i)(s|x|z|ch|sh)$""").containsMatchIn(trimmed)) {
        return trimmed + "es"
    }
    return trimmed + "s"
}
