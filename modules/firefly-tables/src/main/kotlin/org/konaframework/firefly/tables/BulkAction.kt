package org.konaframework.firefly.tables

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.Dialog
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.support.Icon
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/** Where the row selection checkbox sits: before the data cells (default) or after them. */
enum class RecordCheckboxPosition {
    BEFORE_CELLS,
    AFTER_CELLS,
}

/** Anything [Table.bulkActions] accepts: a [BulkAction] or a [BulkActionGroup]. */
interface BulkActionEntry {
    /** The actions this entry contributes, in order. */
    fun bulkActions(): List<BulkAction>
}

/** Runs a bulk action against the selected records. */
fun interface BulkActionCallback {
    fun handle(context: BulkActionContext)
}

/** Counts handed to notification-title and failure-message closures. */
class BulkCounts(
    val successCount: Int,
    val failureCount: Int,
    val totalCount: Int,
)

/** Selected key with its row attributes, or null attributes when the record no longer exists. */
internal class BulkRecord(val key: String, val attributes: Map<String, Any?>?)

/**
 * What a [BulkActionCallback] receives: the selected keys, the selected rows (as attribute maps),
 * the submitted modal data, and helpers to delete records or report per-record failures.
 */
class BulkActionContext internal constructor(
    val action: BulkAction,
    private val selected: List<BulkRecord>,
    private val data: Map<String, Any?>,
    private val deleter: (String) -> Boolean,
) {
    private val failureCounts = LinkedHashMap<String, Int>()
    private val failureMessages = LinkedHashMap<String, Function<BulkCounts, String>>()

    /** Selected record keys that passed [BulkAction.authorizeIndividualRecords]. */
    fun keys(): List<String> = selected.map { it.key }

    /**
     * Selected rows as attribute maps. Empty when [BulkAction.fetchSelectedRecords] is false —
     * use [keys] then.
     */
    fun records(): List<Map<String, Any?>> {
        if (!action.fetchesSelectedRecords()) return emptyList()
        return selected.mapNotNull { it.attributes }
    }

    /** Walks [records] in slices of [BulkAction.chunkSelectedRecords] (all at once when unset). */
    fun eachChunk(consumer: Consumer<List<Map<String, Any?>>>) {
        val rows = records()
        if (rows.isEmpty()) return
        rows.chunked(action.chunkSize() ?: rows.size).forEach { consumer.accept(it) }
    }

    /** A submitted modal field (from [BulkAction.schema]). */
    fun get(field: String): Any? = data[field]

    /** Every submitted modal field. */
    fun data(): Map<String, Any?> = data

    /** Delete one selected record through the table's record store. False when it no longer exists. */
    fun delete(key: String): Boolean = deleter(key)

    /** Delete every selected record; records that no longer exist are reported as failures. */
    fun deleteAll(): Int {
        var deleted = 0
        for (key in keys()) {
            if (delete(key)) {
                deleted++
            } else {
                reportBulkProcessingFailure("missing") { counts ->
                    if (counts.failureCount == 1) "One selected record no longer exists." else "${counts.failureCount} selected records no longer exist."
                }
            }
        }
        return deleted
    }

    /** Record one failed record under [key]; failures sharing a key produce a single message. */
    fun reportBulkProcessingFailure(key: String, message: String) {
        reportBulkProcessingFailure(key, Function { message })
    }

    fun reportBulkProcessingFailure(key: String, message: Function<BulkCounts, String>) {
        failureCounts[key] = (failureCounts[key] ?: 0) + 1
        failureMessages[key] = message
    }

    internal fun failures(): Map<String, Int> = failureCounts

    internal fun message(key: String): Function<BulkCounts, String> = failureMessages.getValue(key)
}

/** Outcome of [Table.runBulkAction]: counts, the notification title, and grouped failure messages. */
class BulkActionResult internal constructor(
    val action: String,
    val successCount: Int,
    val failureCount: Int,
    val totalCount: Int,
    private val title: String?,
    val messages: List<String>,
    val deselectRecordsAfterCompletion: Boolean,
) {
    /** Success title when nothing failed, failure title otherwise; null when the action set neither. */
    fun notificationTitle(): String? = title

    fun isSuccessful(): Boolean = failureCount == 0
}

/**
 * A table bulk action: runs against the selected rows. Opens a confirm/schema dialog when
 * [requiresConfirmation] or [schema] is set; otherwise submits as soon as it is clicked.
 */
open class BulkAction(val name: String) : BulkActionEntry {
    private val labelValue = TextSetting()
    private val hiddenFlag = FlagSetting(false)
    private val visibleFlag = FlagSetting(true)
    private val disabledFlag = FlagSetting(false)
    private var iconName: String? = null
    private var colorName: String? = null
    private var tooltipText: String? = null
    private var style = "grouped"
    private var confirm = false
    private var heading: String? = null
    private var description: String? = null
    private var submitLabel: String? = null
    private var cancelLabel: String? = null
    private val schemaParts = ArrayList<Component>()
    private var callback: BulkActionCallback? = null
    private var authorizer: Predicate<Cell>? = null
    private var denyMessage: Function<BulkCounts, String> = Function { counts ->
        if (counts.failureCount == 1) "One selected record was not authorized." else "${counts.failureCount} selected records were not authorized."
    }
    private var successTitle: String? = null
    private var failureTitle: Function<BulkCounts, String>? = null
    private var deselect = false
    private var fetchRecords = true
    private var chunk: Int? = null
    private var postUrl: String? = null

    init {
        if (name.isBlank()) throw FireflyException("Bulk action name is blank")
    }

    override fun bulkActions(): List<BulkAction> = listOf(this)

    fun label(value: String): BulkAction = apply { labelValue.set(value) }

    fun label(value: Function<Cell, String>): BulkAction = apply { labelValue.set(value) }

    fun icon(value: String): BulkAction = apply {
        Icon.fromName(value)
        iconName = value
    }

    /** `danger`, `primary`, `success`, `warning`, `gray` — styles the trigger. */
    fun color(value: String): BulkAction = apply { colorName = value }

    fun tooltip(value: String): BulkAction = apply { tooltipText = value }

    /** Render outside a [BulkActionGroup] as a toolbar button (default when ungrouped). */
    fun button(): BulkAction = apply { style = "button" }

    /** Render outside a group as a quiet text link. */
    fun link(): BulkAction = apply { style = "link" }

    fun hidden(): BulkAction = hidden(true)

    fun hidden(on: Boolean): BulkAction = apply { hiddenFlag.set(on) }

    fun hidden(predicate: Predicate<Cell>): BulkAction = apply { hiddenFlag.set(predicate) }

    fun visible(on: Boolean): BulkAction = apply { visibleFlag.set(on) }

    fun visible(predicate: Predicate<Cell>): BulkAction = apply { visibleFlag.set(predicate) }

    /** Always disabled (even with a selection). Selection-gating is automatic. */
    fun disabled(): BulkAction = disabled(true)

    fun disabled(on: Boolean): BulkAction = apply { disabledFlag.set(on) }

    fun disabled(predicate: Predicate<Cell>): BulkAction = apply { disabledFlag.set(predicate) }

    @JvmOverloads
    fun requiresConfirmation(on: Boolean = true): BulkAction = apply { confirm = on }

    fun modalHeading(value: String): BulkAction = apply { heading = value }

    fun modalDescription(value: String): BulkAction = apply { description = value }

    fun modalSubmitActionLabel(value: String): BulkAction = apply { submitLabel = value }

    fun modalCancelActionLabel(value: String): BulkAction = apply { cancelLabel = value }

    /** Fields collected in the dialog; read them with [BulkActionContext.get]. */
    fun schema(vararg components: Component): BulkAction = apply {
        schemaParts.clear()
        schemaParts.addAll(components)
    }

    fun action(callback: BulkActionCallback): BulkAction = apply { this.callback = callback }

    /** Only records passing [predicate] reach [action]; the rest count as failures. */
    fun authorizeIndividualRecords(predicate: Predicate<Cell>): BulkAction = apply { authorizer = predicate }

    fun authorizeIndividualRecords(predicate: Predicate<Cell>, message: Function<BulkCounts, String>): BulkAction = apply {
        authorizer = predicate
        denyMessage = message
    }

    fun successNotificationTitle(value: String): BulkAction = apply { successTitle = value }

    fun failureNotificationTitle(value: String): BulkAction = apply { failureTitle = Function { value } }

    fun failureNotificationTitle(value: Function<BulkCounts, String>): BulkAction = apply { failureTitle = value }

    @JvmOverloads
    fun deselectRecordsAfterCompletion(on: Boolean = true): BulkAction = apply { deselect = on }

    /** When false, [BulkActionContext.records] is empty and the callback works from keys only. */
    fun fetchSelectedRecords(on: Boolean): BulkAction = apply { fetchRecords = on }

    /** Slice size for [BulkActionContext.eachChunk]. */
    fun chunkSelectedRecords(size: Int): BulkAction = apply {
        if (size < 1) throw FireflyException("Bulk chunk size must be at least 1")
        chunk = size
    }

    /** POST target for this action's form. Default: the page URL (the panel runs it there). */
    fun url(value: String): BulkAction = apply {
        if (value.isBlank()) throw FireflyException("Bulk action url is blank")
        postUrl = value
    }

    fun isHidden(cell: Cell): Boolean = hiddenFlag.get(cell) || !visibleFlag.get(cell)

    fun isDisabled(cell: Cell): Boolean = disabledFlag.get(cell)

    fun getLabel(cell: Cell): String = labelValue.get(cell, defaultLabel())

    fun fetchesSelectedRecords(): Boolean = fetchRecords

    fun chunkSize(): Int? = chunk

    fun deselectsRecordsAfterCompletion(): Boolean = deselect

    private fun opensDialog(): Boolean = confirm || schemaParts.isNotEmpty()

    private fun defaultLabel(): String {
        // name is never blank (checked in init), so the first character always exists.
        val words = name.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ').lowercase()
        return words.substring(0, 1).uppercase() + words.substring(1)
    }

    /** Trigger inside the bulk dropdown ([menuItem]) or beside it. */
    internal fun renderTrigger(cell: Cell, menuItem: Boolean): String {
        val label = getLabel(cell)
        val classes = when {
            menuItem -> "ff-menu-link ff-bulk-action"
            style == "link" -> "ff-bulk-action ff-bulk-link"
            else -> "ff-table-button ff-bulk-action"
        }
        val icon = iconName?.let { Icon.fromName(it).svg() }.orEmpty()
        val color = colorName?.let { """ data-ff-color="${escapeHtml(it)}"""" }.orEmpty()
        val tip = tooltipText?.let { """ title="${escapeHtml(it)}"""" }.orEmpty()
        // Locked actions stay disabled even after the chrome enables selection-gated triggers.
        val locked = if (isDisabled(cell)) " data-ff-bulk-locked" else ""
        return """<button type="button" class="$classes" data-ff-bulk-run="${escapeHtml(name)}"$color$tip$locked disabled>$icon<span>${escapeHtml(label)}</span></button>"""
    }

    /** Hidden form (or dialog with form) the chrome fills with the selected keys and submits. */
    internal fun renderMount(cell: Cell): String {
        val label = getLabel(cell)
        val action = postUrl?.let { """ action="${escapeHtml(it)}"""" }.orEmpty()
        val hidden =
            """<input type="hidden" name="_bulk_action" value="${escapeHtml(name)}" />""" +
                """<input type="hidden" name="_bulk_keys" value="" data-ff-bulk-keys />""" +
                """<input type="hidden" name="_bulk_all" value="" data-ff-bulk-all />"""
        val mountOpen = """<div class="ff-bulk-mount" data-ff-dialog-mount data-ff-bulk-mount="${escapeHtml(name)}">"""
        val formOpen = """<form method="post"$action class="ff-bulk-form" data-ff-bulk-form data-ff-deselect="$deselect">"""
        if (!opensDialog()) {
            // No dialog: the chrome submits this hidden form as soon as the trigger is clicked.
            return """$mountOpen<form method="post"$action class="ff-bulk-form" data-ff-bulk-form data-ff-deselect="$deselect" hidden>$hidden</form></div>"""
        }
        val text = description ?: if (confirm) "Are you sure you would like to do this?" else ""
        val descriptionHtml = if (text.isEmpty()) "" else """<p class="ff-action-description">${escapeHtml(text)}</p>"""
        val fields = if (schemaParts.isEmpty()) {
            ""
        } else {
            """<div class="ff-action-schema">${Schema.make().components(*schemaParts.toTypedArray()).render()}</div>"""
        }
        val submitColor = colorName?.let { """ data-ff-color="${escapeHtml(it)}"""" }.orEmpty()
        val body =
            """$descriptionHtml$formOpen$hidden$fields<div class="ff-action-footer">""" +
                """<button type="button" class="ff-action-cancel" data-ff-dialog-close>${escapeHtml(cancelLabel ?: "Cancel")}</button>""" +
                """<button type="submit" class="ff-action-submit"$submitColor>${escapeHtml(submitLabel ?: if (confirm) "Confirm" else "Submit")}</button>""" +
                """</div></form>"""
        val dialog = Dialog.make(heading ?: label).modal().body(body)
        return """$mountOpen<div class="ff-table-button-dialog" data-ff-dialog-panel style="display:none" hidden aria-hidden="true">${dialog.render(open = true)}</div></div>"""
    }

    /** Authorize, run [action], and fold failures into a [BulkActionResult]. */
    internal fun execute(
        records: List<BulkRecord>,
        data: Map<String, Any?>,
        deleter: (String) -> Boolean,
        cellFor: (BulkRecord) -> Cell,
    ): BulkActionResult {
        val allowed = ArrayList<BulkRecord>()
        var denied = 0
        val gate = authorizer
        for (record in records) {
            if (gate == null || gate.test(cellFor(record))) allowed.add(record) else denied++
        }
        val context = BulkActionContext(this, allowed, data, deleter)
        callback?.handle(context)
        val total = records.size
        val failed = denied + context.failures().values.sum()
        val messages = ArrayList<String>()
        if (denied > 0) messages.add(denyMessage.apply(BulkCounts(denied, denied, total)))
        for ((key, count) in context.failures()) {
            messages.add(context.message(key).apply(BulkCounts(total - failed, count, total)))
        }
        val counts = BulkCounts(total - failed, failed, total)
        val title = if (failed == 0) successTitle else failureTitle?.apply(counts)
        return BulkActionResult(name, counts.successCount, failed, total, title, messages, deselect)
    }

    companion object {
        private var configurer: Consumer<BulkAction>? = null

        @JvmStatic
        fun make(name: String): BulkAction = BulkAction(name).also { configurer?.accept(it) }

        @JvmStatic
        fun configureUsing(block: Consumer<BulkAction>?) {
            configurer = block
        }
    }
}

/**
 * Filament-style prebuilt delete: confirm dialog, danger color, deletes every selected record
 * through the table's record store, then clears the selection.
 */
class DeleteBulkAction private constructor(name: String) : BulkAction(name) {
    init {
        label("Delete selected")
        icon("trash")
        color("danger")
        requiresConfirmation()
        modalHeading("Delete selected")
        modalDescription("Are you sure you would like to do this?")
        modalSubmitActionLabel("Delete")
        successNotificationTitle("Deleted")
        failureNotificationTitle { counts ->
            if (counts.successCount > 0) "${counts.successCount} of ${counts.totalCount} deleted" else "Nothing was deleted"
        }
        deselectRecordsAfterCompletion()
        action { it.deleteAll() }
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun make(name: String = "delete"): DeleteBulkAction = DeleteBulkAction(name)
    }
}

/** Groups bulk actions under one dropdown trigger (default label "Bulk actions"). */
class BulkActionGroup private constructor(private val actions: List<BulkAction>) : BulkActionEntry {
    private var labelText = "Bulk actions"
    private var iconName: String? = null
    private var colorName: String? = null
    private var tooltipText: String? = null

    override fun bulkActions(): List<BulkAction> = actions

    fun label(value: String): BulkActionGroup = apply { labelText = value }

    fun icon(value: String): BulkActionGroup = apply {
        Icon.fromName(value)
        iconName = value
    }

    fun color(value: String): BulkActionGroup = apply { colorName = value }

    fun tooltip(value: String): BulkActionGroup = apply { tooltipText = value }

    internal fun render(cell: Cell): String {
        val items = actions.filter { !it.isHidden(cell) }
        if (items.isEmpty()) return ""
        val icon = iconName?.let { Icon.fromName(it).svg() }.orEmpty()
        val color = colorName?.let { """ data-ff-color="${escapeHtml(it)}"""" }.orEmpty()
        val tip = tooltipText?.let { """ title="${escapeHtml(it)}"""" }.orEmpty()
        val panel = items.joinToString("") { it.renderTrigger(cell, menuItem = true) }
        return """<div class="ff-menu ff-bulk-menu" data-ff-menu>""" +
            """<button type="button" class="ff-table-button ff-bulk-trigger" data-ff-menu-trigger data-ff-bulk-trigger aria-haspopup="true"$color$tip disabled>""" +
            """$icon<span>${escapeHtml(labelText)}</span><span class="ff-bulk-count" data-ff-bulk-count hidden>0</span>$CHEVRON</button>""" +
            """<div class="ff-menu-panel" data-ff-menu-panel role="menu">$panel</div></div>"""
    }

    companion object {
        private val CHEVRON: String =
            org.konaframework.icons.Icons.svg("heroicon-o-chevron-down").className("ff-bulk-chevron").toHtml()

        @JvmStatic
        fun make(vararg actions: BulkAction): BulkActionGroup = BulkActionGroup(actions.toList())

        @JvmStatic
        fun make(actions: List<BulkAction>): BulkActionGroup = BulkActionGroup(actions.toList())
    }
}
