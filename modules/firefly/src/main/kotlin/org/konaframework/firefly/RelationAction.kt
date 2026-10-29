package org.konaframework.firefly

import org.konaframework.cadence.Cadence
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.FireflyException
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/** What a relation manager does to its related records. [token] is the route segment. */
enum class RelationOperation(val token: String, val mutates: Boolean) {
    VIEW("view", false),
    CREATE("create", true),
    EDIT("edit", true),
    DELETE("delete", true),
    ASSOCIATE("associate", true),
    DISSOCIATE("dissociate", true),
    ATTACH("attach", true),
    DETACH("detach", true),
    RESTORE("restore", true),
    FORCE_DELETE("force-delete", true),
    BULK("bulk", true),
    ;

    companion object {
        @JvmStatic
        fun fromToken(token: String): RelationOperation? = entries.firstOrNull { it.token == token }
    }
}

/** The Cadence relationship shapes a relation manager can drive. */
enum class RelationKind {
    HAS_MANY,
    MORPH_MANY,
    BELONGS_TO_MANY,
    MORPH_TO_MANY,
    HAS_MANY_THROUGH,
    ;

    /** Has-many shapes: related rows hold the owner's key, so they associate / dissociate. */
    fun isOneToMany(): Boolean = this == HAS_MANY || this == MORPH_MANY

    /** Pivot shapes: related rows link through a pivot table, so they attach / detach. */
    fun isManyToMany(): Boolean = this == BELONGS_TO_MANY || this == MORPH_TO_MANY
}

/**
 * Handed to relation manager predicates and hooks. [owner] is the record of the resource page;
 * [record] is the related record (a Cadence model on POST, a [RowRecord] while a table renders,
 * null for header actions). [page] is the owner page slug (`edit`, `view`).
 */
class RelationContext internal constructor(
    val manager: RelationManager,
    val owner: Any,
    val record: Any?,
    val operation: RelationOperation,
    val page: String,
    val readOnly: Boolean,
) {
    /** An attribute of [record], whether it is a model or a row map. */
    fun get(attribute: String): Any? = when (val current = record) {
        is Cadence<*> -> current.getAttribute(attribute)
        is RowRecord -> current.get(attribute)
        is Map<*, *> -> current[attribute]
        else -> null
    }
}

/**
 * A header, row, or modal action on a relation manager table (Filament `CreateAction`,
 * `AttachAction`, `DetachAction`, …). Build one with the factory for its [operation].
 */
class RelationAction private constructor(val operation: RelationOperation) {
    private var labelText: String? = null
    private var headingText: String? = null
    private var descriptionText: String? = null
    private var submitText: String? = null
    private var notificationTitle: String? = null
    private var hiddenWhen: Predicate<RelationContext>? = null
    private var visibleWhen: Predicate<RelationContext>? = null
    private var authorizeWhen: Predicate<RelationContext>? = null
    private var formUsing: Function<Schema, Schema>? = null
    private var multipleFlag = false
    private var preloadFlag = true
    private var optionsLimit = 50
    private var optionsQuery: BiConsumer<Any, RelationContext>? = null
    private val searchColumnList = ArrayList<String>()
    private var mutateUsing: Function<Map<String, Any?>, Map<String, Any?>>? = null
    private var beforeHook: Consumer<RelationContext>? = null
    private var afterHook: Consumer<RelationContext>? = null
    private var duplicates = false
    private var slideOverFlag = false

    fun label(value: String): RelationAction = apply { labelText = requireText(value, "label") }

    fun modalHeading(value: String): RelationAction = apply { headingText = requireText(value, "modal heading") }

    fun modalDescription(value: String): RelationAction = apply { descriptionText = value }

    fun modalSubmitActionLabel(value: String): RelationAction = apply { submitText = requireText(value, "submit label") }

    fun successNotificationTitle(value: String): RelationAction = apply { notificationTitle = requireText(value, "notification title") }

    /** Hide the action when [predicate] passes (row actions see the row as [RelationContext.record]). */
    fun hidden(predicate: Predicate<RelationContext>): RelationAction = apply { hiddenWhen = predicate }

    fun visible(predicate: Predicate<RelationContext>): RelationAction = apply { visibleWhen = predicate }

    /** Extra authorization on top of the manager's `can…` predicates; checked again on POST. */
    fun authorize(predicate: Predicate<RelationContext>): RelationAction = apply { authorizeWhen = predicate }

    /**
     * Create / edit: replaces the manager form for this action. Attach: pivot fields shown under
     * the record picker. Associate: extra fields saved onto the associated record.
     */
    fun form(build: Function<Schema, Schema>): RelationAction = apply { formUsing = build }

    /** Attach / associate: pick several records at once (Filament `multiple()`). */
    @JvmOverloads
    fun multiple(on: Boolean = true): RelationAction = apply { multipleFlag = on }

    /** Attach / associate: embed the first options in the picker instead of waiting for a search. */
    @JvmOverloads
    fun preloadRecordSelect(on: Boolean = true): RelationAction = apply { preloadFlag = on }

    fun recordSelectOptionsLimit(limit: Int): RelationAction = apply {
        if (limit < 1) throw FireflyException("Record select options limit must be at least 1")
        optionsLimit = limit
    }

    /** Attach / associate: narrow the record picker query (Filament `recordSelectOptionsQuery`). */
    fun recordSelectOptionsQuery(modify: BiConsumer<Any, RelationContext>): RelationAction = apply { optionsQuery = modify }

    /** Attach / associate: columns searched besides the record title attribute. */
    fun recordSelectSearchColumns(vararg columns: String): RelationAction = apply {
        searchColumnList.clear()
        searchColumnList.addAll(columns)
    }

    /** Attach: allow the same record to be attached more than once (Filament `allowDuplicates`). */
    @JvmOverloads
    fun allowDuplicates(on: Boolean = true): RelationAction = apply { duplicates = on }

    /** Rewrite submitted data before it is saved (create / edit fields, attach pivot fields). */
    fun mutateFormDataUsing(mutate: Function<Map<String, Any?>, Map<String, Any?>>): RelationAction = apply { mutateUsing = mutate }

    fun before(hook: Consumer<RelationContext>): RelationAction = apply { beforeHook = hook }

    fun after(hook: Consumer<RelationContext>): RelationAction = apply { afterHook = hook }

    /** Open the dialog as a slide-over instead of a centered modal. */
    @JvmOverloads
    fun slideOver(on: Boolean = true): RelationAction = apply { slideOverFlag = on }

    fun getLabel(modelLabel: String): String {
        val custom = labelText
        if (custom != null) return custom
        if (operation == RelationOperation.CREATE) return "New $modelLabel"
        return VERBS.getValue(operation)
    }

    internal fun heading(modelLabel: String): String = headingText ?: "${VERBS.getValue(operation)} $modelLabel"

    internal fun description(): String = descriptionText ?: DESCRIPTIONS[operation].orEmpty()

    internal fun submitLabel(): String = submitText ?: SUBMITS[operation] ?: VERBS.getValue(operation)

    internal fun successTitle(): String = notificationTitle ?: TITLES.getValue(operation)

    internal fun shows(context: RelationContext): Boolean {
        val hidden = hiddenWhen
        if (hidden != null && hidden.test(context)) return false
        val visible = visibleWhen
        if (visible != null && !visible.test(context)) return false
        return permits(context)
    }

    internal fun permits(context: RelationContext): Boolean {
        val gate = authorizeWhen ?: return true
        return gate.test(context)
    }

    internal fun formBuilder(): Function<Schema, Schema>? = formUsing

    internal fun isMultiple(): Boolean = multipleFlag

    internal fun preloads(): Boolean = preloadFlag

    internal fun limit(): Int = optionsLimit

    internal fun optionsQuery(): BiConsumer<Any, RelationContext>? = optionsQuery

    internal fun searchColumns(): List<String> = searchColumnList.toList()

    internal fun allowsDuplicates(): Boolean = duplicates

    internal fun isSlideOver(): Boolean = slideOverFlag

    internal fun mutate(data: Map<String, Any?>): Map<String, Any?> {
        val using = mutateUsing ?: return data
        return using.apply(data)
    }

    internal fun runBefore(context: RelationContext) {
        beforeHook?.accept(context)
    }

    internal fun runAfter(context: RelationContext) {
        afterHook?.accept(context)
    }

    companion object {
        @JvmStatic
        fun create(): RelationAction = RelationAction(RelationOperation.CREATE)

        @JvmStatic
        fun view(): RelationAction = RelationAction(RelationOperation.VIEW)

        @JvmStatic
        fun edit(): RelationAction = RelationAction(RelationOperation.EDIT)

        @JvmStatic
        fun delete(): RelationAction = RelationAction(RelationOperation.DELETE)

        @JvmStatic
        fun associate(): RelationAction = RelationAction(RelationOperation.ASSOCIATE)

        @JvmStatic
        fun dissociate(): RelationAction = RelationAction(RelationOperation.DISSOCIATE)

        @JvmStatic
        fun attach(): RelationAction = RelationAction(RelationOperation.ATTACH)

        @JvmStatic
        fun detach(): RelationAction = RelationAction(RelationOperation.DETACH)

        @JvmStatic
        fun restore(): RelationAction = RelationAction(RelationOperation.RESTORE)

        @JvmStatic
        fun forceDelete(): RelationAction = RelationAction(RelationOperation.FORCE_DELETE)

        private fun requireText(value: String, what: String): String {
            if (value.isBlank()) throw FireflyException("Relation action $what is blank")
            return value
        }

        private val VERBS = mapOf(
            RelationOperation.VIEW to "View",
            RelationOperation.CREATE to "Create",
            RelationOperation.EDIT to "Edit",
            RelationOperation.DELETE to "Delete",
            RelationOperation.ASSOCIATE to "Associate",
            RelationOperation.DISSOCIATE to "Dissociate",
            RelationOperation.ATTACH to "Attach",
            RelationOperation.DETACH to "Detach",
            RelationOperation.RESTORE to "Restore",
            RelationOperation.FORCE_DELETE to "Force delete",
            RelationOperation.BULK to "Run",
        )

        private val TITLES = mapOf(
            RelationOperation.VIEW to "Viewed",
            RelationOperation.CREATE to "Created",
            RelationOperation.EDIT to "Saved",
            RelationOperation.DELETE to "Deleted",
            RelationOperation.ASSOCIATE to "Associated",
            RelationOperation.DISSOCIATE to "Dissociated",
            RelationOperation.ATTACH to "Attached",
            RelationOperation.DETACH to "Detached",
            RelationOperation.RESTORE to "Restored",
            RelationOperation.FORCE_DELETE to "Deleted",
            RelationOperation.BULK to "Done",
        )

        private val SUBMITS = mapOf(
            RelationOperation.EDIT to "Save changes",
            RelationOperation.FORCE_DELETE to "Delete",
        )

        private val DESCRIPTIONS = mapOf(
            RelationOperation.DELETE to "Are you sure you would like to do this?",
            RelationOperation.FORCE_DELETE to "This permanently deletes the record. Are you sure?",
            RelationOperation.DETACH to "The record is unlinked from this owner but kept.",
            RelationOperation.DISSOCIATE to "The record is unlinked from this owner but kept.",
            RelationOperation.RESTORE to "Restore this record?",
        )
    }
}
