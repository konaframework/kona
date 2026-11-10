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
import org.konaframework.firefly.tables.BulkActionGroup
import org.konaframework.firefly.tables.DeleteBulkAction
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TableButton
import org.konaframework.firefly.tables.Cell
import org.konaframework.firefly.widgets.Breadcrumb
import org.konaframework.firefly.widgets.Page
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * A panel resource discovered from `resources/`. It binds one model to its pages, form,
 * infolist, table, and actions. Override [form], [table], [infolist], and [getPages].
 * A simple resource uses one Manage page. A full resource uses list, create, edit, and
 * view pages plus relation managers. The base class calls the schema hooks when a page
 * renders, so they see the current tenant and record.
 */
open class Resource @JvmOverloads constructor(private val slugValue: String = "") {
    private val managers = ArrayList<RelationManager>()
    private val groups = ArrayList<RelationGroup>()
    private var labelLiteral: String? = null
    private var labelUsing: Function<Get, String>? = null
    private var pluralLabelLiteral: String? = null
    private var pluralLabelUsing: Function<Get, String>? = null
    private var iconLiteral: String? = null
    private val nav = NavigationSettings()
    private var modelClass: Class<*>? = null
    private var modelUsing: Function<Get, Class<*>?>? = null
    private var simpleFlag = false
    private var tenantColumn: String? = null
    private var scopedToTenantFlag = true
    private var ownershipName: String? = null
    private var resolvedPages: List<PanelPage>? = null
    private var maxWidthLiteral: String? = null
    private var formColumnCount: Int? = null
    private var infolistColumnCount: Int? = null
    private var combinedRelationTabs = false
    private var contentTabLabelText: String? = null

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

    /** Navigation icon (kona-icons name or short alias). Unset → `heroicon-o-squares-2x2`. */
    fun icon(name: String): Resource = apply { iconLiteral = name }

    fun navigationIcon(): String? = iconLiteral

    /** Navigation link text. Unset → [pluralLabel]. */
    fun navigationLabel(value: String): Resource = apply { nav.label(value) }

    open fun navigationLabel(): String = nav.label ?: pluralLabel()

    /** Sidebar heading this resource sits under (declare its order with `Panel.navigationGroups`). */
    fun navigationGroup(name: String): Resource = apply { nav.group(name) }

    open fun navigationGroup(): String? = nav.group

    /** Sort among its siblings, lowest first. Unset sorts last, by label. */
    fun navigationSort(value: Int): Resource = apply { nav.sort = value }

    open fun navigationSort(): Int? = nav.sort

    /** Badge text at the end of the link, recomputed on every render. Null or blank hides it. */
    fun navigationBadge(provider: Supplier<Any?>): Resource = apply { nav.badgeProvider = provider }

    fun navigationBadge(color: String, provider: Supplier<Any?>): Resource = apply {
        nav.badgeColor(color)
        nav.badgeProvider = provider
    }

    fun navigationBadgeColor(color: String): Resource = apply { nav.badgeColor(color) }

    /** Badge now (null for none). Override it, or set a provider with `navigationBadge { count }`. */
    open fun navigationBadge(): NavigationBadge? = nav.badge()

    /** Hides the resource from the navigation (Filament `shouldRegisterNavigation`). Its routes stay. */
    fun shouldRegisterNavigation(on: Boolean): Resource = apply { nav.register = on }

    open fun shouldRegisterNavigation(): Boolean = nav.register

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

    /**
     * Validates and dehydrates a posted create / edit form (#463). The schema carries the
     * [operation] and [record], so closures such as `required { it.operation == "create" }` and
     * `dehydrated { … }` see them. Only posted paths are rewritten: a field that is not dehydrated
     * (or is hidden) is left out of the saved attributes, and `dehydrateStateUsing` transforms apply.
     */
    internal fun submit(operation: String, record: Any?, key: String?, input: Map<String, Any?>): FormSubmission {
        val schema = Schema.make().operation(operation).record(record)
        if (key != null) schema.key(key)
        if (operation == "edit") {
            try {
                @Suppress("UNCHECKED_CAST")
                val store = RecordStores.open(boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
                schema.fill(store.attributes(record))
            } catch (_: FireflyException) {
                // No model store — only the posted values are state.
            }
        }
        prepareForm(schema)
        schema.fill(input)
        // A path that was not posted only counts when it is a required field still empty; stored
        // values of untouched fields are not re-validated.
        val missing = missingRequired(schema, input)
        val errors = org.konaframework.firefly.forms.FieldValidation.validate(schema, schema.components())
            .filterKeys { it in input || it in missing }
        if (errors.isNotEmpty()) return FormSubmission(errors, input, passwordPaths(schema))
        val saved = schema.dehydrate()
        val attributes = LinkedHashMap(input)
        for (component in schema.walk()) {
            if (component !is org.konaframework.firefly.forms.Field<*>) continue
            val path = component.absolutePath() ?: continue
            if (!input.containsKey(path)) continue
            if (saved.containsKey(path)) attributes[path] = saved[path] else attributes.remove(path)
        }
        return FormSubmission(emptyMap(), attributes, emptySet())
    }

    private fun missingRequired(schema: Schema, input: Map<String, Any?>): Set<String> =
        schema.walk().filterIsInstance<org.konaframework.firefly.forms.Field<*>>()
            .filter { it.isRequired(schema) }
            .mapNotNull { it.absolutePath() }
            .filter { it !in input && schema.get(it)?.toString().isNullOrBlank() }
            .toSet()

    private fun passwordPaths(schema: Schema): Set<String> =
        schema.walk().filterIsInstance<org.konaframework.firefly.forms.TextInput>()
            .filter { it.isPassword() }
            .mapNotNull { it.absolutePath() }
            .toSet()

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
        stripFilterQueryPrefix(field)?.let { filterField ->
            return table(listTable()).filterOptions(filterField, query).map { (value, label) ->
                FieldOption(value, label, true, null)
            }
        }
        val schema = prepareForm(liveSchema(emptyMap(), "create"))
        val select = schema.component(field) as? Select ?: return emptyList()
        return select.search(schema, query)
    }

    /**
     * Applies [infolistColumns] then [infolist], then loads dotted Cadence relations into state (#361).
     * When [infolist] adds no components, derive readonly entries from [form] (#417).
     */
    private fun prepareInfolist(schema: Schema): Schema {
        val count = infolistColumnCount
        if (count != null) schema.columns(count)
        infolist(schema)
        if (schema.components().isEmpty()) {
            val formOnly = Schema.make().operation(schema.operation()).record(schema.record())
            schema.key()?.let { formOnly.key(it) }
            // Match prepareForm so formColumns / schema.columns() on the form carry into View (#418).
            val formCount = formColumnCount
            if (formCount != null) formOnly.columns(formCount)
            form(formOnly)
            FormInfolist.fill(schema, formOnly)
        }
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
        if (on && (managers.isNotEmpty() || groups.isNotEmpty() || getRelations().isNotEmpty() || getRelationGroups().isNotEmpty())) {
            throw FireflyException("Simple resource [${slug()}] has no relation managers")
        }
        simpleFlag = on
    }

    fun isSimple(): Boolean = simpleFlag

    // ——— Authorization (Kona Gate / Policy) ———

    private var allowWithoutPolicyFlag: Boolean? = null
    private var inheritedWithoutPolicy: Boolean? = null

    /**
     * What this resource answers when its model has no policy: true (default) allows everything,
     * false denies (strict mode). Wins over [Panel.authorizeWithoutPolicy] and [Firefly.authorizeWithoutPolicy].
     */
    fun authorizeWithoutPolicy(on: Boolean): Resource = apply { allowWithoutPolicyFlag = on }

    internal fun inheritAuthorizeWithoutPolicy(on: Boolean?) {
        inheritedWithoutPolicy = on
    }

    private fun allowsWithoutPolicy(): Boolean =
        allowWithoutPolicyFlag ?: inheritedWithoutPolicy ?: Firefly.authorizesWithoutPolicy()

    /**
     * Asks the Kona Gate for [ability] on the current user (see [FireflyAuth]). With a [record] the
     * policy receives it, without one the check is class level (`viewAny`, `create`, …). A model
     * without a policy follows [authorizeWithoutPolicy]. Use it for custom abilities in your own hooks.
     */
    @JvmOverloads
    fun can(ability: String, record: Any? = null): Boolean {
        val model = try {
            boundModel()
        } catch (_: FireflyException) {
            return allowsWithoutPolicy()
        }
        return FireflyPolicies.allows(model, ability, record, allowsWithoutPolicy())
    }

    /** Policy `viewAny`. Denied resources leave the navigation and answer 403 on their list. */
    open fun canViewAny(): Boolean = can("viewAny")

    /** Policy `view` for [record]. */
    open fun canView(record: Any): Boolean = can("view", record)

    /** Policy `create`. */
    open fun canCreate(): Boolean = can("create")

    /** Policy `update` for [record]. */
    open fun canEdit(record: Any): Boolean = can("update", record)

    /** Policy `delete` for [record]. */
    open fun canDelete(record: Any): Boolean = can("delete", record)

    /** Policy `deleteAny`: the bulk delete action. */
    open fun canDeleteAny(): Boolean = can("deleteAny")

    /** Policy `restore` for [record]. */
    open fun canRestore(record: Any): Boolean = can("restore", record)

    /** Policy `restoreAny`. */
    open fun canRestoreAny(): Boolean = can("restoreAny")

    /** Policy `forceDelete` for [record]. */
    open fun canForceDelete(record: Any): Boolean = can("forceDelete", record)

    /** Policy `forceDeleteAny`. */
    open fun canForceDeleteAny(): Boolean = can("forceDeleteAny")

    /** Policy `replicate` for [record]. */
    open fun canReplicate(record: Any): Boolean = can("replicate", record)

    /** Policy `reorder`. */
    open fun canReorder(): Boolean = can("reorder")

    /** Hooks a subclass overrides. A row check loads its record only for those or when a policy exists. */
    private val overriddenHooks: Set<String> by lazy {
        javaClass.methods
            .filter { it.name.startsWith("can") && it.declaringClass != Resource::class.java }
            .map { it.name }
            .toSet()
    }

    /** Registers the model's policy found by convention (`app.policies.{Model}Policy`) with the Gate at mount. */
    internal fun discoverPolicy() {
        try {
            Firefly.policyFor(boundModel())
        } catch (_: FireflyException) {
            // No model yet: nothing to discover.
        }
    }

    private fun needsRecord(hook: String): Boolean {
        if (hook in overriddenHooks) return true
        val model = try {
            boundModel()
        } catch (_: FireflyException) {
            return false
        }
        return FireflyPolicies.hasPolicy(model)
    }

    /** A row check for table buttons: hides the button when [check] denies the row's record. */
    private fun rowCheck(hook: String, check: (Any) -> Boolean): Predicate<Cell> = Predicate { cell ->
        if (!needsRecord(hook)) return@Predicate allowsWithoutPolicy()
        val record = recordForAuth(cell.get("key").toString())
        record != null && check(record)
    }

    private fun rowView(): Predicate<Cell> = rowCheck("canView") { canView(it) }

    private fun rowEdit(): Predicate<Cell> = rowCheck("canEdit") { canEdit(it) }

    private fun rowDelete(): Predicate<Cell> = rowCheck("canDelete") { canDelete(it) }

    /** The record for [key] inside the current tenant, loaded once per request. Null when missing. */
    internal fun recordForAuth(key: String): Any? =
        FireflyAuth.remember("${slug()}:$key") { loadForAuth(key) }

    private fun loadForAuth(key: String): Any? {
        val found = try {
            @Suppress("UNCHECKED_CAST")
            (RecordStores.open(boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>).find(key)
        } catch (_: RuntimeException) {
            return null
        }
        val column = tenantColumn
        val tenant = FireflyTenant.id()
        if (found is org.konaframework.cadence.Cadence<*> && column != null && tenant != null &&
            found.getAttribute(column)?.toString() != tenant
        ) {
            return null
        }
        return found
    }

    /**
     * Whether the current user may open [page] (the record is [key] on edit and view pages).
     * Edit and view pages need `update` / `view` on the record, create needs `create`, every
     * other page needs `viewAny`. A page without a loadable record falls back to `viewAny`.
     */
    internal fun allowsPage(page: PanelPage, key: String?): Boolean {
        if (!page.canAccess()) return false
        if (page is CreatePage) return canCreate()
        if (page is EditPage || page is ViewPage) {
            val record = if (key.isNullOrBlank()) null else recordForAuth(key)
            if (record == null) return canViewAny()
            return if (page is EditPage) canEdit(record) else canView(record)
        }
        return canViewAny()
    }

    /** Whether the current user may run bulk action [name] posted to the list page. */
    internal fun allowsBulk(name: String): Boolean = canViewAny() && (name != DELETE_BULK || canDeleteAny())

    /** Hides the built-in header actions the current user may not run (create, view, edit, delete). */
    private fun authorizeHeader(page: PanelPage, state: Map<String, Any?>) {
        val actions = page.headerActionList()
        if (page is ListPage || page is ManagePage) {
            if (!canCreate()) denyActions(actions, "create")
            return
        }
        if (page !is EditPage && page !is ViewPage) return
        val key = state["record"]?.toString()
        val record = if (key.isNullOrBlank()) null else recordForAuth(key)
        if (record == null) return
        if (!canView(record)) denyActions(actions, "view")
        if (!canEdit(record)) denyActions(actions, "edit")
        if (!canDelete(record)) denyActions(actions, "delete")
    }

    private fun denyActions(actions: List<Action>, name: String) {
        for (action in actions) {
            if (action.name == name) action.authorize(false)
        }
    }

    fun tenant(column: String): Resource = apply {
        if (column.isBlank()) throw FireflyException("Tenant column is blank")
        tenantColumn = column
    }

    /**
     * Whether this resource's queries and new records follow the panel's model-backed tenant
     * ([Panel.tenant] with a model class). On by default; turn it off for a model every tenant shares.
     * The panel limits the model's rows to the current tenant through its ownership relationship and
     * stamps new rows with it.
     */
    fun scopedToTenant(on: Boolean): Resource = apply { scopedToTenantFlag = on }

    /** Whether this resource follows the current tenant. Override it, or call [scopedToTenant]. */
    open fun isScopedToTenant(): Boolean = scopedToTenantFlag

    /**
     * Names the relationship of the model that points at the tenant, when it differs from the one the
     * panel was given (Filament `$tenantOwnershipRelationshipName`).
     */
    fun tenantOwnershipRelationship(name: String): Resource = apply {
        if (name.isBlank()) throw FireflyException("Tenant ownership relationship is blank")
        ownershipName = name
    }

    /** The ownership relationship this resource names for its model, or null for the panel's. */
    open fun tenantOwnershipRelationshipName(): String? = ownershipName

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

    /** Add a [RelationGroup] (several managers under one top-level tab). */
    fun relationGroup(group: RelationGroup): Resource = apply {
        if (simpleFlag) throw FireflyException("Simple resource [${slug()}] has no relation managers")
        groups.add(group)
    }

    /**
     * When true, the edit form / view infolist shares the relation tab strip as the first tab
     * (Filament `hasCombinedRelationManagerTabsWithContent`).
     */
    @JvmOverloads
    fun combinedRelationManagerTabsWithContent(on: Boolean = true): Resource = apply {
        combinedRelationTabs = on
    }

    fun hasCombinedRelationManagerTabsWithContent(): Boolean = combinedRelationTabs

    /** Label of the form / infolist tab when [hasCombinedRelationManagerTabsWithContent] is on. */
    fun contentTabLabel(value: String): Resource = apply {
        if (value.isBlank()) throw FireflyException("Content tab label is blank")
        contentTabLabelText = value
    }

    fun getContentTabLabel(): String = contentTabLabelText ?: "Content"

    /**
     * Filament-style class-based relation managers. Override on the resource instead of
     * (or in addition to) calling [relation] from `init`.
     */
    open fun getRelations(): List<RelationManager> = emptyList()

    /**
     * Filament-style relation groups. Override instead of (or in addition to) [relationGroup].
     */
    open fun getRelationGroups(): List<RelationGroup> = emptyList()

    /** Flat list of every manager (including those inside groups) for POST / options routes. */
    fun relationManagers(): List<RelationManager> {
        val merged = LinkedHashMap<String, RelationManager>()
        for (entry in relationEntries()) {
            for (manager in entry.managers()) merged[manager.name()] = manager
        }
        return merged.values.toList()
    }

    /** Top-level tab entries: singles and groups, fluent then overrides. */
    internal fun relationEntries(): List<RelationEntry> {
        val groupedNames = HashSet<String>()
        val out = ArrayList<RelationEntry>()
        for (group in groups + getRelationGroups()) {
            out.add(RelationEntry.Group(group))
            for (manager in group.managers()) groupedNames.add(manager.name())
        }
        val singles = LinkedHashMap<String, RelationManager>()
        for (manager in managers) {
            if (manager.name() !in groupedNames) singles[manager.name()] = manager
        }
        for (manager in getRelations()) {
            if (manager.name() !in groupedNames) singles[manager.name()] = manager
        }
        for (manager in singles.values) out.add(RelationEntry.Single(manager))
        return out
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
            page.onShow { state ->
                apply(page, state)
                authorizeHeader(page, state)
            }
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
        val createForm = prepareForm(liveSchema(emptyMap(), "create"))
        val built = table(listTable())
        applyTableQuery(built, state)
        val csrf = state["csrfField"] as? String ?: ""
        // Row dialogs carry the record; the manage list itself stays table-only (no standing form).
        // Buttons already set on the table (for example an ungrouped link) stay (#514).
        val custom = built.configuredRowButtons()
        built.rowButtons(
            *custom.toTypedArray(),
            manageViewButton(),
            manageEditButton(csrf),
            TableButton.make("delete").label("Delete").visible(rowDelete()).confirmPost("delete", csrf),
        )
        defaultBulkActions(built)
        bindRecordActions(built, state, csrf)
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
        TableButton.make("view").label("View").visible(rowView()).modal().body { cell ->
            val markup = prepareInfolist(liveSchema(mapOf("record" to cell.get("key").toString()), "view")).render()
            """$markup<div class="ff-action-footer"><button type="button" class="ff-action-cancel" data-ff-dialog-close>Close</button><button type="button" class="ff-action-submit" data-ff-dialog-switch="edit">Edit</button></div>"""
        }

    /** Row Edit: dialog with the filled form for that record (native POST). */
    private fun manageEditButton(csrf: String): TableButton =
        TableButton.make("edit").label("Edit").visible(rowEdit()).modal().body { cell ->
            val key = cell.get("key").toString()
            val markup = prepareForm(liveSchema(mapOf("record" to key), "edit")).render()
            """<form method="post" action="edit" class="ff-action-form">$csrf<input type="hidden" name="id" value="${escapeHtml(key)}" /><div class="ff-action-schema">$markup</div><div class="ff-action-footer"><button type="button" class="ff-action-cancel" data-ff-dialog-close>Cancel</button><button type="submit" class="ff-action-submit">Save</button></div></form>"""
        }

    /** Bulk delete in a "Bulk actions" dropdown unless [table] already configured bulk actions. */
    private fun defaultBulkActions(built: Table<*, *>) {
        if (built.isBulkActionsConfigured()) return
        if (!canDeleteAny()) {
            built.bulkActions()
            return
        }
        built.bulkActions(BulkActionGroup.make(DeleteBulkAction.make().authorizeIndividualRecords(rowDelete())))
    }

    private fun applyList(page: ListPage, state: Map<String, Any?>) {
        page.title(pluralLabel())
        page.breadcrumbs(resourceCrumbs(state, pluralLabel()))
        val built = table(listTable())
        applyTableQuery(built, state)
        val csrf = state["csrfField"] as? String ?: ""
        // Row actions — never sidebar links — open the record pages (Shamar list toolbar).
        val custom = built.configuredRowButtons()
        built.rowButtons(
            *custom.toTypedArray(),
            TableButton.make("view").label("View").visible(rowView()).url(rowRecordUrl("view")),
            TableButton.make("edit").label("Edit").visible(rowEdit()).url(rowRecordUrl("edit")),
            TableButton.make("delete").label("Delete").visible(rowDelete()).confirmPost("delete", csrf),
        )
        defaultBulkActions(built)
        bindRecordActions(built, state, csrf)
        page.table(built)
        // Full-resource create is a header link — dialogs belong on simple/manage and delete.
        page.headerActions(Action.make("create").label("New").url("create"))
    }

    private fun applyCreate(page: CreatePage, state: Map<String, Any?>) {
        page.title("New ${label()}")
        page.breadcrumbs(resourceCrumbs(state, "New ${label()}", listHref = true))
        val form = prepareForm(liveSchema(state, "create"))
        applySubmitted(form, state)
        page.mountForm(form)
        // Native form submit (Shamar) — Create posts #ff-form; Cancel is a list link.
        page.headerActions(
            Action.make("save").label("Create").submitForm("ff-form"),
            Action.make("cancel").label("Cancel").url("list").outlined(),
        )
    }

    private fun applyEdit(page: EditPage, state: Map<String, Any?>) {
        page.title("Edit ${label()}")
        page.breadcrumbs(resourceCrumbs(state, "Edit ${label()}", listHref = true))
        val schema = prepareForm(liveSchema(state, "edit"))
        applySubmitted(schema, state)
        if (combinedRelationTabs) {
            page.relationsHtml(
                relationMarkup(schema.record(), page, state, contentHtml = wrapResourceForm(schema.render(), state)),
            )
        } else {
            page.mountForm(schema)
            page.relationsHtml(relationMarkup(schema.record(), page, state))
        }
        val recordId = state["record"]?.toString().orEmpty()
        val viewUrl = if (recordId.isEmpty()) "view" else "view?id=${encodeQuery(recordId)}"
        bindHeaderActions(
            page,
            schema,
            state,
            listOf(
                Action.make("save").label("Save").submitForm("ff-form"),
                Action.make("view").label("View").url(viewUrl).outlined(),
                Action.make("cancel").label("Cancel").url("list").outlined(),
                deleteAction(state),
            ),
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
        TableState.apply(built, state, "firefly.tables.${slug()}.filters")
    }

    private fun applyView(page: ViewPage, state: Map<String, Any?>) {
        page.title(label())
        page.breadcrumbs(resourceCrumbs(state, label(), listHref = true))
        val schema = prepareInfolist(liveSchema(state, "view"))
        if (combinedRelationTabs) {
            page.relationsHtml(relationMarkup(schema.record(), page, state, contentHtml = schema.render()))
        } else {
            page.infolistHtml(schema.render())
            page.relationsHtml(relationMarkup(schema.record(), page, state))
        }
        val recordId = state["record"]?.toString().orEmpty()
        val editUrl = if (recordId.isEmpty()) "edit" else "edit?id=${encodeQuery(recordId)}"
        bindHeaderActions(
            page,
            schema,
            state,
            listOf(
                Action.make("edit").label("Edit").url(editUrl),
                Action.make("back").label("Back to list").url("list").outlined(),
                deleteAction(state),
            ),
        )
    }

    /**
     * Header actions of an edit / view page: the built-in [defaults] with the page's own
     * ([RecordPage.getHeaderActions] / fluent) merged in, rendered against the page record and
     * posting handler actions to `actions/{name}`.
     */
    private fun bindHeaderActions(page: RecordPage, schema: Schema, state: Map<String, Any?>, defaults: List<Action>) {
        page.mergeHeader(defaults)
        page.actionHost(actionHost(schema))
        val recordId = state["record"]?.toString().orEmpty()
        val csrf = state["csrfField"] as? String ?: ""
        val id = if (recordId.isEmpty()) "" else """<input type="hidden" name="id" value="${escapeHtml(recordId)}" />"""
        val prefix = actionPrefix(state)
        page.actionPost(Function { action -> "$prefix/${action.name}" }, csrf + id + returnField(state))
    }

    /** Posts the table's record actions to `actions/{name}` with the CSRF field and return location. */
    private fun bindRecordActions(built: Table<*, *>, state: Map<String, Any?>, csrf: String) {
        val prefix = actionPrefix(state)
        built.recordActionsPostTo(Function { action -> "$prefix/${action.name}" }, csrf + returnField(state))
    }

    private fun actionPrefix(state: Map<String, Any?>): String {
        val resourceBase = state["resourceBase"]
        return if (resourceBase == null) ACTIONS_PATH else "$resourceBase/$ACTIONS_PATH"
    }

    private fun returnField(state: Map<String, Any?>): String = returnFieldHtml(state["returnUrl"]?.toString())

    /** Schema that header actions render against: the page record with its attributes as state. */
    private fun actionHost(schema: Schema): Schema {
        val host = Schema.make().operation(schema.operation()).record(schema.record())
        schema.key()?.let { host.key(it) }
        // Edit and view schemas always carry a record (a blank one when no id is given).
        val record = schema.record()!!
        try {
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            host.fill(store.attributes(record))
        } catch (_: FireflyException) {
            // No model store — the action still sees the record itself.
        }
        return host
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
    private fun liveSchema(state: Map<String, Any?>, operation: String): Schema {
        val schema = Schema.make().operation(operation)
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

    /**
     * Relation managers for a saved Cadence [owner] on the edit / view [page]. Several managers
     * (and optional [RelationGroup]s) render as tabs; the `relation` query value picks the active
     * manager (its table gets [state]). When [contentHtml] is set, the form / infolist is the
     * first tab (`relation` empty or `content`). Without a saved owner nothing renders.
     */
    private fun relationMarkup(
        owner: Any?,
        page: PanelPage,
        state: Map<String, Any?>,
        contentHtml: String? = null,
    ): String {
        if (owner !is org.konaframework.cadence.Cadence<*> || owner.getKey() == null) return ""
        val csrf = state["csrfField"] as? String ?: ""
        val resourceBase = state["resourceBase"]
        val base = if (resourceBase == null) RELATIONS_PATH else "$resourceBase/$RELATIONS_PATH"
        val entries = ArrayList<BoundEntry>()
        for (entry in relationEntries()) {
            when (entry) {
                is RelationEntry.Single -> {
                    val runtime = entry.manager.bind(owner, page, base, csrf)
                    if (entry.manager.visibleFor(runtime)) entries.add(BoundEntry.Single(runtime))
                }
                is RelationEntry.Group -> {
                    val runtimes = entry.group.managers()
                        .map { it.bind(owner, page, base, csrf) }
                        .filter { it.manager.visibleFor(it) }
                    if (runtimes.isNotEmpty()) entries.add(BoundEntry.Group(entry.group.label(), runtimes))
                }
            }
        }
        val hasContent = !contentHtml.isNullOrEmpty()
        if (entries.isEmpty() && !hasContent) return ""
        val requested = state["relation"]?.toString()
        val contentActive = hasContent && (requested.isNullOrBlank() || requested == CONTENT_TAB)
        val activeRuntime = when {
            contentActive -> null
            else -> findActiveRuntime(entries, requested)
        }
        val ownerKey = owner.getKey().toString()
        // One manager, no content tab, no groups → bare section (no tab strip).
        if (!hasContent && entries.size == 1 && entries[0] is BoundEntry.Single) {
            val runtime = (entries[0] as BoundEntry.Single).runtime
            return runtime.manager.renderFor(runtime, state, true)
        }
        val tabs = StringBuilder()
        val panels = StringBuilder()
        if (hasContent) {
            val href = "?id=${encodeQuery(ownerKey)}&relation=$CONTENT_TAB"
            tabs.append(
                """<a class="ff-relation-tab" role="tab" href="${escapeHtml(href)}" data-ff-relation-tab="$CONTENT_TAB" aria-selected="$contentActive">""" +
                    """${escapeHtml(getContentTabLabel())}</a>""",
            )
            val hidden = if (contentActive) "" else " hidden"
            panels.append(
                """<div class="ff-relation-panel" role="tabpanel" data-ff-relation-panel="$CONTENT_TAB"$hidden>""" +
                    """<div class="ff-relation-content">$contentHtml</div></div>""",
            )
        }
        for (entry in entries) {
            when (entry) {
                is BoundEntry.Single -> {
                    val runtime = entry.runtime
                    val manager = runtime.manager
                    val current = !contentActive && runtime === activeRuntime
                    val name = escapeHtml(manager.name())
                    val href = "?id=${encodeQuery(ownerKey)}&relation=${encodeQuery(manager.name())}"
                    val badge = manager.badgeFor(runtime)
                    val badgeHtml = if (badge == null) "" else """<span class="ff-relation-badge">${escapeHtml(badge)}</span>"""
                    tabs.append(
                        """<a class="ff-relation-tab" role="tab" href="${escapeHtml(href)}" data-ff-relation-tab="$name" aria-selected="$current">""" +
                            """${escapeHtml(manager.title())}$badgeHtml</a>""",
                    )
                    val hidden = if (current) "" else " hidden"
                    panels.append(
                        """<div class="ff-relation-panel" role="tabpanel" data-ff-relation-panel="$name"$hidden>""" +
                            manager.renderFor(runtime, state, current) + "</div>",
                    )
                }
                is BoundEntry.Group -> {
                    val groupActive = !contentActive && entry.runtimes.any { it === activeRuntime }
                    val first = entry.runtimes.first()
                    val activeInGroup = if (groupActive) {
                        entry.runtimes.first { it === activeRuntime }
                    } else {
                        first
                    }
                    val href = "?id=${encodeQuery(ownerKey)}&relation=${encodeQuery(activeInGroup.manager.name())}"
                    val groupKey = escapeHtml("group-" + entry.label.lowercase().replace(' ', '-'))
                    tabs.append(
                        """<a class="ff-relation-tab" role="tab" href="${escapeHtml(href)}" data-ff-relation-tab="$groupKey" aria-selected="$groupActive">""" +
                            """${escapeHtml(entry.label)}</a>""",
                    )
                    val hidden = if (groupActive) "" else " hidden"
                    panels.append(
                        """<div class="ff-relation-panel" role="tabpanel" data-ff-relation-panel="$groupKey"$hidden>""" +
                            renderGroup(entry, activeInGroup, state, ownerKey) + "</div>",
                    )
                }
            }
        }
        return """<section class="ff-relation-managers" data-ff-relation-managers>""" +
            """<nav class="ff-relation-tabs" role="tablist">$tabs</nav>$panels</section>"""
    }

    private fun renderGroup(
        entry: BoundEntry.Group,
        active: RelationRuntime,
        state: Map<String, Any?>,
        ownerKey: String,
    ): String {
        if (entry.runtimes.size == 1) {
            return active.manager.renderFor(active, state, true)
        }
        val tabs = StringBuilder()
        val panels = StringBuilder()
        for (runtime in entry.runtimes) {
            val manager = runtime.manager
            val current = runtime === active
            val name = escapeHtml(manager.name())
            val href = "?id=${encodeQuery(ownerKey)}&relation=${encodeQuery(manager.name())}"
            val badge = manager.badgeFor(runtime)
            val badgeHtml = if (badge == null) "" else """<span class="ff-relation-badge">${escapeHtml(badge)}</span>"""
            tabs.append(
                """<a class="ff-relation-tab ff-relation-nested-tab" role="tab" href="${escapeHtml(href)}" data-ff-relation-tab="$name" aria-selected="$current">""" +
                    """${escapeHtml(manager.title())}$badgeHtml</a>""",
            )
            val hidden = if (current) "" else " hidden"
            panels.append(
                """<div class="ff-relation-panel ff-relation-nested-panel" role="tabpanel" data-ff-relation-panel="$name"$hidden>""" +
                    manager.renderFor(runtime, state, current) + "</div>",
            )
        }
        return """<div class="ff-relation-group" data-ff-relation-group="${escapeHtml(entry.label)}">""" +
            """<nav class="ff-relation-tabs ff-relation-nested-tabs" role="tablist">$tabs</nav>$panels</div>"""
    }

    private fun findActiveRuntime(entries: List<BoundEntry>, requested: String?): RelationRuntime? {
        if (!requested.isNullOrBlank() && requested != CONTENT_TAB) {
            for (entry in entries) {
                when (entry) {
                    is BoundEntry.Single -> if (entry.runtime.manager.name() == requested) return entry.runtime
                    is BoundEntry.Group -> {
                        val match = entry.runtimes.firstOrNull { it.manager.name() == requested }
                        if (match != null) return match
                    }
                }
            }
        }
        val first = entries.firstOrNull() ?: return null
        return when (first) {
            is BoundEntry.Single -> first.runtime
            is BoundEntry.Group -> first.runtimes.first()
        }
    }

    private sealed class BoundEntry {
        class Single(val runtime: RelationRuntime) : BoundEntry()
        class Group(val label: String, val runtimes: List<RelationRuntime>) : BoundEntry()
    }

    /**
     * The record action called [name] and how to load its record, or null when none matches:
     * edit / view header actions first (found through [RecordPage.extraHeaderActions]), then the
     * actions of this resource's list table. Only actions with a server handler qualify.
     */
    internal fun recordActionSource(name: String): RecordActionSource? {
        val header = pages().filterIsInstance<RecordPage>()
            .flatMap { it.extraHeaderActions() }
            .firstOrNull { it.name == name && it.hasHandler() }
        if (header != null) {
            return RecordActionSource(header, { key -> findRecord(key) }, { record -> storeAttributes(record) })
        }
        @Suppress("UNCHECKED_CAST")
        val table = table(listTable()) as Table<Any?, Any?>
        val row = table.findRecordAction(name) ?: return null
        return RecordActionSource(
            row,
            { key -> tenantScoped(table.findRecord(key)) },
            { record -> table.recordAttributes(record) },
        )
    }

    private fun storeAttributes(record: Any): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val store = RecordStores.open(boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
        return store.attributes(record)
    }

    /** A record of this resource by key, or null when missing or outside the current tenant. */
    internal fun findRecord(key: String): Any? {
        @Suppress("UNCHECKED_CAST")
        val store = RecordStores.open(boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
        return tenantScoped(store.find(key))
    }

    private fun tenantScoped(found: Any?): Any? {
        val column = tenantColumn
        val tenant = FireflyTenant.id()
        if (found is org.konaframework.cadence.Cadence<*> && column != null && tenant != null &&
            found.getAttribute(column)?.toString() != tenant
        ) {
            return null
        }
        return found
    }

    /** The record a relation POST belongs to, or null when missing or outside the current tenant. */
    internal fun relationOwner(key: String): org.konaframework.cadence.Cadence<*>? {
        @Suppress("UNCHECKED_CAST")
        val store = RecordStores.open(boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
        val found = store.find(key) as? org.konaframework.cadence.Cadence<*> ?: return null
        val column = tenantColumn
        val tenant = FireflyTenant.id()
        if (column != null && tenant != null && found.getAttribute(column)?.toString() != tenant) return null
        return found
    }

    internal companion object {
        /** Relation POST / options routes under the resource (`{panel}/{resource}/relations/…`). */
        const val RELATIONS_PATH = "relations"

        /** Record action POST route under the resource (`{panel}/{resource}/actions/{name}`). */
        const val ACTIONS_PATH = "actions"

        /** Query value for the form / infolist tab when relation tabs are combined with content. */
        const val CONTENT_TAB: String = "content"

        /** Name of the default bulk delete action, gated by [canDeleteAny]. */
        const val DELETE_BULK: String = "delete"
    }

    private fun rowRecordUrl(page: String): java.util.function.Function<org.konaframework.firefly.tables.Cell, String> =
        java.util.function.Function { cell ->
            "$page?id=${encodeQuery(cell.get("key").toString())}"
        }

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8)

    /** Same `#ff-form` chrome [org.konaframework.firefly.widgets.Page] uses when mounting a form. */
    private fun wrapResourceForm(body: String, state: Map<String, Any?>): String =
        wrapCombinedResourceForm(body, state)

    /** `filters.status` or `ordersFilters.status` → `status` (for combobox option search). */
    private fun stripFilterQueryPrefix(field: String): String? {
        if (field.startsWith("filters.")) return field.removePrefix("filters.")
        val marker = "Filters."
        val at = field.indexOf(marker)
        if (at > 0) return field.substring(at + marker.length)
        return null
    }
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

/** Form chrome for combined content tabs — package-visible for coverage of missing state keys. */
internal fun wrapCombinedResourceForm(body: String, state: Map<String, Any?>): String {
    val csrf = when (val value = state["csrfField"]) {
        null -> ""
        else -> value.toString()
    }
    val action = when (val value = state["formAction"]) {
        null -> ""
        else -> escapeHtml(value.toString())
    }
    val recordId = when (val value = state["record"]) {
        null -> ""
        else -> escapeHtml(value.toString())
    }
    return """<form id="ff-form" class="ff-resource-form" method="post" action="$action">$csrf""" +
        """<input type="hidden" name="id" value="$recordId" />$body</form>"""
}

/**
 * A record action found for a POST, with how to load its record and read the record's attributes.
 * Resources hand out the edit / view header actions and list-table actions; relation managers hand out
 * their table's actions for the related record.
 */
internal class RecordActionSource(
    val action: Action,
    val find: (String) -> Any?,
    val attributes: (Any) -> Map<String, Any?>,
)
