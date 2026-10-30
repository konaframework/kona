package org.konaframework.firefly

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.SoftDeletes
import org.konaframework.firefly.forms.Field
import org.konaframework.firefly.forms.FieldValidation
import org.konaframework.firefly.forms.Select
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.BulkAction
import org.konaframework.firefly.tables.BulkActionEntry
import org.konaframework.firefly.tables.BulkActionGroup
import org.konaframework.firefly.tables.Cell
import org.konaframework.firefly.tables.RecordStores
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TableButton
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.tables.TrashedFilter
import java.net.URLEncoder
import java.util.Collections
import java.util.EnumMap
import java.util.WeakHashMap
import java.util.function.BiConsumer
import java.util.function.Function
import java.util.function.Predicate

/**
 * Manages the related records of a full resource's record on its edit and view pages
 * (Filament relation manager). [name] is the Cadence relationship method on the owner model
 * (`tasks` → `Project.tasks()`). The table lists only the owner's related rows; create, edit,
 * delete, associate, dissociate, attach, detach, restore, and force delete run through panel
 * POST routes without leaving the page. Simple resources do not use one.
 */
open class RelationManager(private val name: String) {
    private var titleLiteral = "id"
    private var titleUsing: Function<Get, String>? = null
    private var relatedClass: Class<*>? = null
    private var relatedUsing: Function<Get, Class<*>?>? = null
    private var formUsing: Function<Schema, Schema>? = null
    private var formTemplate: Schema? = null
    private var infolistUsing: Function<Schema, Schema>? = null
    private var tableRef: Table<*, *>? = null
    private var tableUsing: Function<Table<*, *>, Table<*, *>>? = null
    private var attributesLiteral: Map<String, String>? = null
    private var attributesUsing: Function<Get, Map<String, String>>? = null
    private var headingText: String? = null
    private var modelLabelText: String? = null
    private var pluralModelLabelText: String? = null
    private var inverseName: String? = null
    private var recordTitleUsing: Function<Any, String>? = null
    private var readOnlyFlag: Boolean? = null
    private var badgeUsing: Function<RelationContext, String?>? = null
    private var headerActionList: List<RelationAction>? = null
    private var rowActionList: List<RelationAction>? = null
    private var bulkEntryList: List<BulkActionEntry>? = null
    private val permissions = EnumMap<RelationOperation, Predicate<RelationContext>>(RelationOperation::class.java)
    private var ownerGate: Predicate<RelationContext>? = null
    private var queryModifier: BiConsumer<Any, RelationContext>? = null
    private val pivotColumnList = ArrayList<String>()
    private val scopedTables: MutableSet<Table<*, *>> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    init {
        if (name.isBlank()) throw FireflyException("Relation name is blank")
    }

    fun name(): String = name

    /** The owner model's relationship method this manager drives (Filament `$relationship`). */
    fun relationship(): String = name

    fun titleAttribute(value: String): RelationManager = apply {
        if (value.isBlank()) throw FireflyException("Relation title attribute is blank")
        titleLiteral = value
        titleUsing = null
    }

    fun titleAttribute(value: Function<Get, String>): RelationManager = apply { titleUsing = value }

    /** Filament `$recordTitleAttribute`: the related column that names a record in pickers. */
    fun recordTitleAttribute(value: String): RelationManager = titleAttribute(value)

    /** Label a related record from the model itself (Filament `getRecordTitle`). */
    fun recordTitle(using: Function<Any, String>): RelationManager = apply { recordTitleUsing = using }

    /** Heading of the manager and its tab. Defaults to the relationship name in words (`Tasks`). */
    fun title(value: String): RelationManager = apply {
        if (value.isBlank()) throw FireflyException("Relation title is blank")
        headingText = value
    }

    fun title(): String = headingText ?: capitalized(words(name))

    fun modelLabel(value: String): RelationManager = apply {
        if (value.isBlank()) throw FireflyException("Relation model label is blank")
        modelLabelText = value
    }

    /** Singular label in action labels and headings (`New task`). Defaults to the singular of [name]. */
    fun modelLabel(): String = modelLabelText ?: singular(words(name))

    fun pluralModelLabel(value: String): RelationManager = apply {
        if (value.isBlank()) throw FireflyException("Relation plural model label is blank")
        pluralModelLabelText = value
    }

    fun pluralModelLabel(): String = pluralModelLabelText ?: words(name)

    /** The relationship back to the owner on the related model (Filament `$inverseRelationship`). */
    fun inverseRelationship(value: String): RelationManager = apply {
        if (value.isBlank()) throw FireflyException("Inverse relationship is blank")
        inverseName = value
    }

    fun inverseRelationship(): String? = inverseName

    fun related(type: Class<*>): RelationManager = apply {
        relatedClass = type
        relatedUsing = null
    }

    fun related(type: Function<Get, Class<*>?>): RelationManager = apply {
        relatedUsing = type
        relatedClass = null
    }

    /** Create / edit form fields. The components are re-rooted into a fresh schema per dialog. */
    fun form(schema: Schema): RelationManager = apply {
        formTemplate = schema
        formUsing = null
    }

    /** Build the create / edit form per dialog (the schema arrives with its record set). */
    fun form(build: Function<Schema, Schema>): RelationManager = apply {
        formUsing = build
        formTemplate = null
    }

    /** View dialog entries. Without one the view dialog lists the form fields read-only. */
    fun infolist(schema: Schema): RelationManager = apply { infolistUsing = templateOf(schema) }

    fun infolist(build: Function<Schema, Schema>): RelationManager = apply { infolistUsing = build }

    /** A ready table over the related model (`Table.forModel(Task::class.java).columns(…)`). */
    fun table(table: Table<*, *>): RelationManager = apply {
        tableRef = table
        tableUsing = null
    }

    /** Configure a fresh related-model table per request (columns, filters, layouts, bulk actions). */
    fun table(configure: Function<Table<*, *>, Table<*, *>>): RelationManager = apply {
        tableUsing = configure
        tableRef = null
    }

    fun extraAttributes(values: Map<String, String>): RelationManager = apply {
        attributesLiteral = values
        attributesUsing = null
    }

    fun extraAttributes(values: Function<Get, Map<String, String>>): RelationManager = apply {
        attributesUsing = values
        attributesLiteral = null
    }

    /**
     * Force read-only (`true`) or editable (`false`). Unset: read-only on the resource view page and
     * editable on the edit page (Filament `isReadOnly`). Read-only managers only offer View.
     */
    fun readOnly(on: Boolean): RelationManager = apply { readOnlyFlag = on }

    fun isReadOnly(page: PanelPage?): Boolean = readOnlyFlag ?: (page is ViewPage)

    fun badge(value: String): RelationManager = apply { badgeUsing = Function { value } }

    /** Tab / heading badge for the owner (for example a count of related rows). Null hides it. */
    fun badge(value: Function<RelationContext, String?>): RelationManager = apply { badgeUsing = value }

    /** Header actions (default: create plus associate or attach for the relationship kind). */
    fun headerActions(vararg actions: RelationAction): RelationManager = apply { headerActionList = actions.toList() }

    /** Row actions (default: view, edit, dissociate or detach, delete, and restore / force delete when soft-deleting). */
    fun actions(vararg actions: RelationAction): RelationManager = apply { rowActionList = actions.toList() }

    /** Bulk actions; reuse [org.konaframework.firefly.tables.BulkActionGroup] and the relation bulk actions. */
    fun bulkActions(vararg entries: BulkActionEntry): RelationManager = apply { bulkEntryList = entries.toList() }

    /** Pivot columns read into each row and saved from same-named form fields (many-to-many). */
    fun pivotColumns(vararg columns: String): RelationManager = apply {
        pivotColumnList.clear()
        for (column in columns) pivotColumnList.add(RelationBinding.ident(column))
    }

    /** Extra constraints on the owner-scoped related query (Filament `modifyQueryUsing`). */
    fun modifyQueryUsing(modify: BiConsumer<Any, RelationContext>): RelationManager = apply { queryModifier = modify }

    /** Authorize one operation; checked when rendering and again on POST. */
    fun can(operation: RelationOperation, predicate: Predicate<RelationContext>): RelationManager = apply {
        permissions[operation] = predicate
    }

    fun canView(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.VIEW, predicate)

    fun canCreate(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.CREATE, predicate)

    fun canEdit(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.EDIT, predicate)

    fun canDelete(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.DELETE, predicate)

    fun canAssociate(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.ASSOCIATE, predicate)

    fun canDissociate(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.DISSOCIATE, predicate)

    fun canAttach(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.ATTACH, predicate)

    fun canDetach(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.DETACH, predicate)

    fun canRestore(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.RESTORE, predicate)

    fun canForceDelete(predicate: Predicate<RelationContext>): RelationManager = can(RelationOperation.FORCE_DELETE, predicate)

    /** Whether the manager shows at all for an owner record (Filament `canViewForRecord`). */
    fun canViewForRecord(predicate: Predicate<RelationContext>): RelationManager = apply { ownerGate = predicate }

    /** Unscoped query over the related model (no owner). Owner-scoped tables come from the resource pages. */
    fun query(): Any = RecordStores.open(boundModel()).query() as Any

    /**
     * Standalone markup without an owner record (playground / docs previews).
     * Renders the configured table under a relation heading — create/edit [form] schemas
     * stay on dialogs when owner-bound and are not dumped under the table (#403).
     */
    fun render(): String {
        val table = tableRef
        val tableHtml = if (table == null) "" else table.render()
        return """<section class="ff-relation" data-ff-relation="${escapeHtml(name)}" data-ff-title="${escapeHtml(titleText())}"${attributeText()}>""" +
            """<header class="ff-relation-header"><h2 class="ff-relation-heading">${escapeHtml(title())}</h2></header>$tableHtml</section>"""
    }

    // ——— Owner-bound runtime ———

    internal fun bind(owner: Any, page: PanelPage?, actionBase: String, csrf: String): RelationRuntime {
        if (owner !is Cadence<*>) throw FireflyException("Relation manager [$name] needs a Cadence owner record")
        val key = owner.getKey() ?: throw FireflyException("Relation manager [$name] owner has no key")
        val slug = if (page == null) "edit" else page.slug()
        return RelationRuntime(this, RelationBinding(owner, name), key.toString(), slug, isReadOnly(page), actionBase, csrf)
    }

    internal fun visibleFor(runtime: RelationRuntime): Boolean {
        val gate = ownerGate ?: return true
        return gate.test(runtime.context(RelationOperation.VIEW, null))
    }

    internal fun badgeFor(runtime: RelationRuntime): String? {
        val using = badgeUsing ?: return null
        return using.apply(runtime.context(RelationOperation.VIEW, null))
    }

    internal fun allows(operation: RelationOperation, context: RelationContext): Boolean {
        if (operation.mutates && context.readOnly) return false
        val predicate = permissions[operation] ?: return true
        return predicate.test(context)
    }

    /** The section for one owner. [state] carries table query state when [active]. */
    internal fun renderFor(runtime: RelationRuntime, state: Map<String, Any?>, active: Boolean): String {
        val table = table(runtime, if (active) state else emptyMap())
        val tableHtml = RelationScope.with(runtime) { table.render() }
        val headerHtml = headerButtons(runtime).joinToString("") { it.render(Cell { null }, true) }
        val badge = badgeFor(runtime)
        val badgeHtml = if (badge == null) "" else """<span class="ff-relation-badge">${escapeHtml(badge)}</span>"""
        val readOnlyAttr = if (runtime.readOnly) """ data-ff-read-only="true"""" else ""
        return """<section class="ff-relation" data-ff-relation="${escapeHtml(name)}" data-ff-title="${escapeHtml(titleText())}" data-ff-kind="${runtime.binding.kind.name.lowercase()}"$readOnlyAttr${attributeText()}>""" +
            """<header class="ff-relation-header"><h2 class="ff-relation-heading">${escapeHtml(title())}$badgeHtml</h2>""" +
            """<div class="ff-relation-actions">$headerHtml</div></header>$tableHtml</section>"""
    }

    /** The owner-scoped table with actions, bulk actions, and (when given) query state applied. */
    internal fun table(runtime: RelationRuntime, state: Map<String, Any?>): Table<*, *> {
        val built = freshTable(runtime.binding)
        scopeOnce(built)
        if (runtime.binding.softDeletes() && built.filterSet().filters().none { it is TrashedFilter }) {
            built.filters(*(built.filterSet().filters() + TrashedFilter.make()).toTypedArray())
        }
        built.persistQueryParameters(mapOf("id" to runtime.ownerKey, "relation" to name))
        built.rowButtons(*rowButtons(runtime).toTypedArray())
        if (bulkEntryList == null && built.isBulkActionsConfigured()) bulkEntryList = built.bulkActionEntries()
        if (runtime.readOnly) {
            built.bulkActions()
        } else {
            val entries = bulkEntryList ?: defaultBulkActions(runtime)
            for (action in entries.flatMap { it.bulkActions() }) action.url(runtime.url(RelationOperation.BULK))
            built.bulkActions(*entries.toTypedArray())
        }
        TableState.apply(built, state, "firefly.relations.$name.filters")
        return built
    }

    /** Run a mutating operation posted from a relation dialog. */
    internal fun perform(runtime: RelationRuntime, operation: RelationOperation, input: Map<String, String>): RelationOutcome {
        if (!operation.mutates || operation == RelationOperation.BULK) {
            throw FireflyException("Relation operation [${operation.token}] is not a single-record POST")
        }
        if (runtime.readOnly) return RelationOutcome.denied("Relation [$name] is read-only here")
        val action = actionFor(operation, runtime) ?: return RelationOutcome.denied("${operation.token} is not available on relation [$name]")
        val binding = runtime.binding
        return when (operation) {
            RelationOperation.CREATE -> {
                val context = runtime.context(operation, null)
                if (!permitted(operation, action, context)) return RelationOutcome.denied("Not authorized")
                val submission = submitted(formSchema(action, binding.store().newRecord()), input)
                val data = submission.data ?: return submission.invalid()
                val (attributes, pivot) = split(action.mutate(data), runtime)
                action.runBefore(context)
                val created = binding.create(attributes, pivot)
                action.runAfter(runtime.context(operation, created))
                RelationOutcome.done(action.successTitle())
            }
            RelationOperation.EDIT -> {
                val record = scopedRecord(runtime, input, withTrashed = true) ?: return RelationOutcome.missing()
                val context = runtime.context(operation, record)
                if (!permitted(operation, action, context)) return RelationOutcome.denied("Not authorized")
                val submission = submitted(formSchema(action, record), input)
                val data = submission.data ?: return submission.invalid()
                val (attributes, pivot) = split(action.mutate(data), runtime)
                action.runBefore(context)
                record.fill(attributes)
                record.save()
                binding.updatePivot(record.getKey().toString(), pivot)
                action.runAfter(context)
                RelationOutcome.done(action.successTitle())
            }
            RelationOperation.ASSOCIATE, RelationOperation.ATTACH -> link(runtime, operation, action, input)
            else -> {
                val trashedOnly = operation == RelationOperation.RESTORE || operation == RelationOperation.FORCE_DELETE
                val record = scopedRecord(runtime, input, withTrashed = trashedOnly) ?: return RelationOutcome.missing()
                val context = runtime.context(operation, record)
                if (!permitted(operation, action, context)) return RelationOutcome.denied("Not authorized")
                if (trashedOnly && !(record as SoftDeletes).trashed()) return RelationOutcome.failed("The record is not deleted")
                action.runBefore(context)
                apply(binding, operation, record)
                action.runAfter(context)
                RelationOutcome.done(action.successTitle())
            }
        }
    }

    /** JSON-ready picker options for associate / attach (`value`, `label`). */
    internal fun options(runtime: RelationRuntime, operation: RelationOperation, search: String): List<Pair<String, String>> {
        if (runtime.readOnly) return emptyList()
        val action = actionFor(operation, runtime) ?: return emptyList()
        if (!permitted(operation, action, runtime.context(operation, null))) return emptyList()
        return candidates(runtime, action, search).map { keyOf(it) to recordTitle(it) }
    }

    /** Bulk dissociate / detach / delete / restore / force delete through [RelationBulkAction]. */
    internal fun runBulk(runtime: RelationRuntime, operation: RelationOperation, keys: List<String>, report: (String) -> Unit) {
        val binding = runtime.binding
        val trashedOnly = operation == RelationOperation.RESTORE || operation == RelationOperation.FORCE_DELETE
        for (key in keys) {
            val record = binding.find(key, withTrashed = true) { modifyQuery(it, runtime) }
            if (record == null) {
                report("missing")
                continue
            }
            if (!allows(operation, runtime.context(operation, record))) {
                report("denied")
                continue
            }
            if (trashedOnly && !(record as SoftDeletes).trashed()) {
                report("active")
                continue
            }
            apply(binding, operation, record)
        }
    }

    internal fun supports(operation: RelationOperation, runtime: RelationRuntime): Boolean {
        val kind = runtime.binding.kind
        return when (operation) {
            RelationOperation.CREATE -> kind != RelationKind.HAS_MANY_THROUGH
            RelationOperation.ASSOCIATE, RelationOperation.DISSOCIATE -> kind.isOneToMany()
            RelationOperation.ATTACH, RelationOperation.DETACH -> kind.isManyToMany()
            RelationOperation.RESTORE, RelationOperation.FORCE_DELETE -> runtime.binding.softDeletes()
            else -> true
        }
    }

    // ——— Actions ———

    private fun headerActions(runtime: RelationRuntime): List<RelationAction> = headerActionList ?: buildList {
        add(RelationAction.create())
        if (runtime.binding.kind.isOneToMany()) add(RelationAction.associate())
        if (runtime.binding.kind.isManyToMany()) add(RelationAction.attach())
    }

    private fun rowActions(runtime: RelationRuntime): List<RelationAction> = rowActionList ?: buildList {
        add(RelationAction.view())
        add(RelationAction.edit())
        if (runtime.binding.kind.isOneToMany()) add(RelationAction.dissociate())
        if (runtime.binding.kind.isManyToMany()) add(RelationAction.detach())
        add(RelationAction.delete())
        if (runtime.binding.softDeletes()) {
            add(RelationAction.restore())
            add(RelationAction.forceDelete())
        }
    }

    private fun defaultBulkActions(runtime: RelationRuntime): List<BulkActionEntry> {
        val actions = ArrayList<BulkAction>()
        if (runtime.binding.kind.isOneToMany()) actions.add(DissociateBulkAction.make())
        if (runtime.binding.kind.isManyToMany()) actions.add(DetachBulkAction.make())
        actions.add(RelationBulkAction.delete())
        if (runtime.binding.softDeletes()) {
            actions.add(RestoreBulkAction.make())
            actions.add(ForceDeleteBulkAction.make())
        }
        return listOf(BulkActionGroup.make(actions))
    }

    /** The configured (or default) action for [operation]; null when this manager does not offer it. */
    private fun actionFor(operation: RelationOperation, runtime: RelationRuntime): RelationAction? {
        if (!supports(operation, runtime)) return null
        return (headerActions(runtime) + rowActions(runtime)).firstOrNull { it.operation == operation }
    }

    private fun permitted(operation: RelationOperation, action: RelationAction, context: RelationContext): Boolean =
        allows(operation, context) && action.permits(context)

    private fun headerButtons(runtime: RelationRuntime): List<TableButton> {
        val context = runtime.context(RelationOperation.CREATE, null)
        return headerActions(runtime).filter { action ->
            action.operation.mutates && supports(action.operation, runtime) &&
                allows(action.operation, context) && action.shows(context)
        }.map { action ->
            val button = TableButton.make(action.operation.token)
                .label(action.getLabel(modelLabel()))
                .modalHeading(action.heading(modelLabel()))
                .body { _ -> headerBody(runtime, action) }
            if (action.isSlideOver()) button.slideOver() else button.modal()
        }
    }

    private fun rowButtons(runtime: RelationRuntime): List<TableButton> {
        val label = modelLabel()
        return rowActions(runtime).filter { supports(it.operation, runtime) }.map { action ->
            val operation = action.operation
            val button = TableButton.make(operation.token).label(action.getLabel(label))
            button.visible(Predicate { cell -> rowShows(runtime, action, cell) })
            when (operation) {
                RelationOperation.VIEW -> button.modalHeading(action.heading(label)).body { cell -> viewBody(runtime, cell) }
                RelationOperation.EDIT -> button.modalHeading(action.heading(label)).body { cell -> editBody(runtime, action, cell) }
                else -> button.confirmPost(runtime.url(operation), runtime.csrf, action.heading(label), action.description())
            }
            if (action.isSlideOver()) button.slideOver()
            button
        }
    }

    private fun rowShows(runtime: RelationRuntime, action: RelationAction, cell: Cell): Boolean {
        val operation = action.operation
        val trashed = runtime.binding.softDeletes() && cell.get(deletedAtColumn(runtime)) != null
        val trashedOnly = operation == RelationOperation.RESTORE || operation == RelationOperation.FORCE_DELETE
        if (trashedOnly != trashed && operation != RelationOperation.VIEW && operation != RelationOperation.EDIT) return false
        val context = runtime.rowContext(operation, cell)
        return allows(operation, context) && action.shows(context)
    }

    private fun deletedAtColumn(runtime: RelationRuntime): String = (runtime.binding.related as SoftDeletes).deletedAtColumn

    // ——— Dialog bodies ———

    private fun headerBody(runtime: RelationRuntime, action: RelationAction): String {
        val operation = action.operation
        val fields = if (operation == RelationOperation.CREATE) {
            formSchema(action, runtime.binding.store().newRecord()).render()
        } else {
            pickerSchema(runtime, action).render()
        }
        val description = action.description()
        val intro = if (description.isEmpty()) "" else """<p class="ff-action-description">${escapeHtml(description)}</p>"""
        return intro + postForm(runtime.url(operation), runtime.csrf, "", fields, action.submitLabel())
    }

    private fun viewBody(runtime: RelationRuntime, cell: Cell): String {
        // Missing only when a shared table renders outside its relation (no owner scope on the rows).
        val record = runtime.binding.find(cell.get("key").toString(), withTrashed = true) { modifyQuery(it, runtime) } ?: return ""
        val schema = Schema.make().record(record).fill(attributesOf(record, cell))
        val using = infolistUsing
        if (using != null) {
            using.apply(schema)
        } else {
            FormInfolist.fill(schema, formSchema(null, record))
        }
        val canEdit = rowActions(runtime).any { it.operation == RelationOperation.EDIT } && !runtime.readOnly
        val editSwitch = if (canEdit) """<button type="button" class="ff-action-submit" data-ff-dialog-switch="edit">Edit</button>""" else ""
        return """${schema.render()}<div class="ff-action-footer"><button type="button" class="ff-action-cancel" data-ff-dialog-close>Close</button>$editSwitch</div>"""
    }

    private fun editBody(runtime: RelationRuntime, action: RelationAction, cell: Cell): String {
        val key = cell.get("key").toString()
        val record = runtime.binding.find(key, withTrashed = true) { modifyQuery(it, runtime) } ?: return ""
        val schema = formSchema(action, record).fill(attributesOf(record, cell))
        val hidden = """<input type="hidden" name="id" value="${escapeHtml(key)}" />"""
        return postForm(runtime.url(RelationOperation.EDIT), runtime.csrf, hidden, schema.render(), action.submitLabel())
    }

    private fun postForm(url: String, csrf: String, hidden: String, fields: String, submit: String): String =
        """<form method="post" action="${escapeHtml(url)}" class="ff-action-form">$csrf$hidden<div class="ff-action-schema">$fields</div>""" +
            """<div class="ff-action-footer"><button type="button" class="ff-action-cancel" data-ff-dialog-close>Cancel</button>""" +
            """<button type="submit" class="ff-action-submit">${escapeHtml(submit)}</button></div></form>"""

    /** Record picker (`recordId`) for associate / attach, plus the action's extra fields. */
    private fun pickerSchema(runtime: RelationRuntime, action: RelationAction): Schema {
        val select = Select.make(RECORD_ID).label(capitalized(modelLabel())).required()
            .multiple(action.isMultiple())
            .searchUrl(runtime.optionsUrl(action.operation))
            .preload(action.preloads())
        if (action.preloads()) {
            val options = LinkedHashMap<String, String>()
            for (record in candidates(runtime, action, "")) options[keyOf(record)] = recordTitle(record)
            select.options(options)
        }
        val schema = Schema.make().record(runtime.binding.store().newRecord()).components(select)
        val extra = action.formBuilder() ?: return schema
        val fields = extra.apply(Schema.make()).components()
        return schema.components(select, *fields.toTypedArray())
    }

    private fun candidates(runtime: RelationRuntime, action: RelationAction, search: String): List<Cadence<*>> {
        val query = action.optionsQuery()
        val context = runtime.context(action.operation, null)
        return runtime.binding.candidates(
            action.operation,
            titleText(),
            action.searchColumns(),
            search,
            action.limit(),
            action.allowsDuplicates(),
        ) { builder -> query?.accept(builder, context) }
    }

    /** Associate / attach the posted `recordId` value(s). */
    private fun link(runtime: RelationRuntime, operation: RelationOperation, action: RelationAction, input: Map<String, String>): RelationOutcome {
        val keys = input[RECORD_ID].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (keys.isEmpty()) return RelationOutcome.failed("Select at least one ${modelLabel()}")
        val extras = action.formBuilder()?.apply(Schema.make())
        val submission = if (extras == null) Submission(emptyMap(), emptyList()) else submitted(extras, input)
        val data = submission.data ?: return submission.invalid()
        val allowed = candidates(runtime, action, "").associateBy { keyOf(it) }
        val chosen = ArrayList<Cadence<*>>()
        for (key in keys) {
            val record = allowed[key] ?: lookupCandidate(runtime, action, key) ?: return RelationOutcome.failed("${modelLabel()} [$key] cannot be ${if (operation == RelationOperation.ATTACH) "attached" else "associated"}")
            val context = runtime.context(operation, record)
            if (!permitted(operation, action, context)) return RelationOutcome.denied("Not authorized")
            chosen.add(record)
        }
        val payload = action.mutate(data)
        action.runBefore(runtime.context(operation, null))
        if (operation == RelationOperation.ATTACH) {
            runtime.binding.attach(chosen.map { keyOf(it) }, payload)
        } else {
            for (record in chosen) runtime.binding.associate(record, payload)
        }
        action.runAfter(runtime.context(operation, chosen.first()))
        return RelationOutcome.done(action.successTitle())
    }

    /** A posted key past the preload limit must still be an eligible candidate. */
    private fun lookupCandidate(runtime: RelationRuntime, action: RelationAction, key: String): Cadence<*>? {
        val query = action.optionsQuery()
        val context = runtime.context(action.operation, null)
        val pk = RelationBinding.ident(runtime.binding.related.primaryKey)
        return runtime.binding.candidates(action.operation, titleText(), emptyList(), "", 1, action.allowsDuplicates()) { builder ->
            query?.accept(builder, context)
            builder.where("${RelationBinding.ident(runtime.binding.related.table)}.$pk", RelationBinding.coerce(key))
        }.firstOrNull()
    }

    private fun apply(binding: RelationBinding, operation: RelationOperation, record: Cadence<*>) {
        RECORD_OPERATIONS.getValue(operation)(binding, record)
    }

    // ——— Forms and data ———

    /** The create / edit form for [record] (action form wins over the manager form). */
    private fun formSchema(action: RelationAction?, record: Any?): Schema {
        val schema = Schema.make().record(record)
        val custom = action?.formBuilder()
        if (custom != null) return custom.apply(schema)
        val using = formUsing ?: formTemplate?.let { templateOf(it) } ?: return schema
        return using.apply(schema)
    }

    private fun formFields(schema: Schema): List<Field<*>> =
        schema.walk().filterIsInstance<Field<*>>().filter { it.isDehydrated() }

    /** Posted values for the schema's fields; [Submission.data] is null when validation fails. */
    private fun submitted(schema: Schema, input: Map<String, String>): Submission {
        val data = LinkedHashMap<String, Any?>()
        for (field in formFields(schema)) {
            val path = field.absolutePath() ?: field.name
            if (input.containsKey(path)) data[path] = input[path]
        }
        schema.fill(data)
        val errors = FieldValidation.validate(schema, schema.components())
        if (errors.isNotEmpty()) return Submission(null, errors.values.flatten())
        return Submission(data, emptyList())
    }

    private class Submission(val data: Map<String, Any?>?, val errors: List<String>) {
        fun invalid(): RelationOutcome = RelationOutcome.failed("Please fix the form", errors)
    }

    /** Model attributes and pivot values; only many-to-many managers have pivot values. */
    private fun split(data: Map<String, Any?>, runtime: RelationRuntime): Pair<Map<String, Any?>, Map<String, Any?>> {
        if (!runtime.binding.kind.isManyToMany()) return data to emptyMap()
        val pivot = data.filterKeys { it in pivotColumnList }
        return data.filterKeys { it !in pivotColumnList } to pivot
    }

    private fun attributesOf(record: Cadence<*>, cell: Cell): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>(record.getAttributes())
        for (column in pivotColumnList) out[column] = cell.get(column)
        return out
    }

    private fun scopedRecord(runtime: RelationRuntime, input: Map<String, String>, withTrashed: Boolean): Cadence<*>? {
        val key = input["id"]
        if (key.isNullOrBlank()) return null
        return runtime.binding.find(key, withTrashed) { modifyQuery(it, runtime) }
    }

    private fun modifyQuery(query: ModelBuilder<*>, runtime: RelationRuntime) {
        queryModifier?.accept(query, runtime.context(RelationOperation.VIEW, null))
    }

    // ——— Tables ———

    private fun freshTable(binding: RelationBinding): Table<*, *> {
        val using = tableUsing
        if (using != null) return using.apply(Table.forModel(binding.relatedClass))
        val ref = tableRef
        if (ref != null) return ref
        return Table.forModel(binding.relatedClass).columns(TextColumn.make(titleText()).searchable().sortable())
    }

    /** One hook per table instance; it scopes queries for the runtime active on this thread. */
    private fun scopeOnce(table: Table<*, *>) {
        if (!scopedTables.add(table)) return
        @Suppress("UNCHECKED_CAST")
        (table as Table<Any?, Any?>).modifyQueryUsing { query ->
            val runtime = RelationScope.current() ?: return@modifyQueryUsing
            val builder = query as? ModelBuilder<*>
                ?: throw FireflyException("Relation manager [$name] table must use the related Cadence model (Table.forModel)")
            runtime.binding.scope(builder, pivotColumnList)
            modifyQuery(builder, runtime)
        }
    }

    // ——— Labels ———

    private fun recordTitle(record: Cadence<*>): String {
        val using = recordTitleUsing
        if (using != null) return using.apply(record)
        val title = record.getAttribute(titleText())
        return if (title == null) keyOf(record) else title.toString()
    }

    private fun keyOf(record: Cadence<*>): String = record.getKey().toString()

    private fun boundModel(): Class<*> {
        val using = relatedUsing
        if (using != null) {
            val type = using.apply(SchemaBridge.open(emptyMap()))
            if (type == null) throw FireflyException("Relation [$name] has no model")
            return type
        }
        val type = relatedClass
        if (type == null) throw FireflyException("Relation [$name] has no model")
        return type
    }

    private fun titleText(): String {
        val using = titleUsing
        if (using == null) return titleLiteral
        val text = using.apply(SchemaBridge.open(emptyMap()))
        if (text.isBlank()) throw FireflyException("Relation title attribute is blank")
        return text
    }

    private fun attributeText(): String {
        val using = attributesUsing
        val values = if (using != null) using.apply(SchemaBridge.open(emptyMap())) else attributesLiteral
        return htmlAttributes(values)
    }

    companion object {
        /** Field name of the associate / attach record picker (Filament `recordId`). */
        const val RECORD_ID: String = "recordId"

        private fun templateOf(template: Schema): Function<Schema, Schema> = Function { schema ->
            schema.components(*template.components().toTypedArray())
            val layout = template.columnLayout()
            if (layout != null) schema.columns(layout)
            schema
        }

        private val RECORD_OPERATIONS: Map<RelationOperation, (RelationBinding, Cadence<*>) -> Unit> = mapOf(
            RelationOperation.DELETE to { binding, record -> binding.delete(record) },
            RelationOperation.DISSOCIATE to { binding, record -> binding.dissociate(record) },
            RelationOperation.DETACH to { binding, record -> binding.detach(listOf(record.getKey().toString())) },
            RelationOperation.RESTORE to { _, record -> (record as SoftDeletes).restore() },
            RelationOperation.FORCE_DELETE to { binding, record -> binding.forceDelete(record) },
        )

        private fun capitalized(text: String): String = text.substring(0, 1).uppercase() + text.substring(1)

        /** `teamMembers` / `team_members` → `team members`. */
        internal fun words(name: String): String =
            name.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ').lowercase()

        internal fun singular(plural: String): String = when {
            plural.endsWith("ies") -> plural.dropLast(3) + "y"
            Regex("(ss|x|ch|sh)es$").containsMatchIn(plural) -> plural.dropLast(2)
            plural.endsWith("s") && !plural.endsWith("ss") -> plural.dropLast(1)
            else -> plural
        }
    }
}

/** One manager bound to one owner record on one page. */
internal class RelationRuntime(
    val manager: RelationManager,
    val binding: RelationBinding,
    val ownerKey: String,
    val pageSlug: String,
    val readOnly: Boolean,
    private val actionBase: String,
    val csrf: String,
) {
    fun url(operation: RelationOperation): String = "$actionBase/${operation.token}?${query()}"

    fun optionsUrl(operation: RelationOperation): String = "$actionBase/options?${query()}&op=${operation.token}"

    fun context(operation: RelationOperation, record: Any?): RelationContext =
        RelationContext(manager, binding.owner, record, operation, pageSlug, readOnly)

    fun rowContext(operation: RelationOperation, cell: Cell): RelationContext =
        RelationContext(manager, binding.owner, RowRecord(cell), operation, pageSlug, readOnly)

    private fun query(): String =
        "relation=${encode(manager.name())}&owner=${encode(ownerKey)}&page=${encode(pageSlug)}"

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)
}

/** A table row as [RelationContext.record] while the relation table renders. */
class RowRecord internal constructor(private val cell: Cell) {
    /** A column or attribute of the row. */
    fun get(attribute: String): Any? = cell.get(attribute)

    /** The row's record key. */
    fun key(): String = cell.get("key").toString()
}

/** Outcome of a relation POST: a toast title and status for the panel route. */
internal class RelationOutcome private constructor(
    val status: Int,
    val title: String,
    val messages: List<String>,
) {
    fun isSuccessful(): Boolean = status == 200

    companion object {
        fun done(title: String) = RelationOutcome(200, title, emptyList())

        fun failed(title: String, messages: List<String> = emptyList()) = RelationOutcome(422, title, messages)

        fun denied(title: String) = RelationOutcome(403, title, emptyList())

        fun missing() = RelationOutcome(404, "Record not found", emptyList())
    }
}

/** The relation runtime whose table is rendering or running bulk actions on this thread. */
internal object RelationScope {
    private val active = ThreadLocal<RelationRuntime?>()

    fun current(): RelationRuntime? = active.get()

    fun <R> with(runtime: RelationRuntime, block: () -> R): R {
        val previous = active.get()
        active.set(runtime)
        try {
            return block()
        } finally {
            active.set(previous)
        }
    }
}
