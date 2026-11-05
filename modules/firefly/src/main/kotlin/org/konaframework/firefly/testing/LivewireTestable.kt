package org.konaframework.firefly.testing

import org.konaframework.cadence.Cadence
import org.konaframework.firefly.CreatePage
import org.konaframework.firefly.EditPage
import org.konaframework.firefly.ListPage
import org.konaframework.firefly.ManagePage
import org.konaframework.firefly.PanelPage
import org.konaframework.firefly.Resource
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordStores
import org.konaframework.firefly.tables.Table

/**
 * In-process Firefly resource page driver (Filament `livewire(EditPage::class)` shape).
 *
 * Mounts the resource's [page], fills the form, calls header actions such as `save`, and asserts
 * form errors, notifications, table records and row-action visibility — without opening a socket.
 *
 * ```kotlin
 * Firefly.livewire(UsersResource(), EditPage::class.java, user.getKey())
 *     .fillForm(mapOf("name" to "Ada"))
 *     .call("save")
 *     .assertHasNoFormErrors()
 * ```
 */
class LivewireTestable internal constructor(
    private val resource: Resource,
    private val page: PanelPage,
    private var record: Any?,
) {
    private val form = LinkedHashMap<String, Any?>()
    private var errors: Map<String, List<String>> = emptyMap()
    private var html: String = ""
    private var notified: String? = null
    private var actor: Any? = null

    init {
        refresh()
    }

    /** Authenticate later authorization checks as [user] (Filament `actingAs`). */
    fun actingAs(user: Any?): LivewireTestable = apply { actor = user }

    /** The acting user, or null. */
    fun actor(): Any? = actor

    /** Merge [data] into the posted form state. */
    fun fillForm(data: Map<String, Any?>): LivewireTestable = apply { form.putAll(data) }

    /** Replace the form state with [data]. */
    fun setForm(data: Map<String, Any?>): LivewireTestable = apply {
        form.clear()
        form.putAll(data)
    }

    /** Current form values. */
    fun formData(): Map<String, Any?> = LinkedHashMap(form)

    /** Field errors from the last [call] (`save`), empty when valid. */
    fun formErrors(): Map<String, List<String>> = errors

    /** Last notification title (`Saved`, `Created`, `Please fix the form`), or null. */
    fun notification(): String? = notified

    /** Last rendered page HTML. */
    fun html(): String = html

    /** The page under test. */
    fun page(): PanelPage = page

    /** The resource under test. */
    fun resource(): Resource = resource

    /** The current record (updated after a successful create / save). */
    fun record(): Any? = record

    /**
     * Run a page action. `save` validates and persists the form (create or edit). Unknown names
     * throw [IllegalArgumentException].
     */
    fun call(name: String): LivewireTestable = apply {
        when (name) {
            "save" -> save()
            else -> throw IllegalArgumentException("Unknown Firefly livewire action [$name]")
        }
    }

    /** Re-render the page (list / form) after mutations. */
    fun refresh(): LivewireTestable = apply {
        html = page.render(state())
    }

    // ——— Assertions ———

    fun assertSee(text: String): LivewireTestable = apply {
        require(html.contains(text)) { "Expected the page to contain [$text]: $html" }
    }

    fun assertDontSee(text: String): LivewireTestable = apply {
        require(!html.contains(text)) { "Expected the page not to contain [$text]" }
    }

    /** Assert the last save left no validation errors. */
    fun assertHasNoFormErrors(): LivewireTestable = apply {
        require(errors.isEmpty()) { "Expected no form errors but had $errors" }
    }

    /** Assert none of [keys] has a form error. */
    fun assertHasNoFormErrors(vararg keys: String): LivewireTestable = apply {
        val failing = keys.filter { it in errors }
        require(failing.isEmpty()) { "Expected $failing to be valid but had $errors" }
    }

    /** Assert at least one form error. */
    fun assertHasFormErrors(): LivewireTestable = apply {
        require(errors.isNotEmpty()) { "Expected form errors but there were none" }
    }

    /** Assert every field in [keys] has a form error. */
    fun assertHasFormErrors(vararg keys: String): LivewireTestable = apply {
        val missing = keys.filter { it !in errors }
        require(missing.isEmpty()) { "Expected form errors for $missing but had ${errors.keys}" }
    }

    /** Assert a notification was flashed. */
    fun assertNotified(): LivewireTestable = apply {
        require(notified != null) { "Expected a notification but none was flashed" }
    }

    /** Assert a notification was flashed with [title]. */
    fun assertNotified(title: String): LivewireTestable = apply {
        require(notified != null) { "Expected a notification but none was flashed" }
        require(notified == title) { "Expected notification [$title] but was [$notified]" }
    }

    /** Assert no notification was flashed. */
    fun assertNotNotified(): LivewireTestable = apply {
        require(notified == null) { "Expected no notification but had [$notified]" }
    }

    /** Assert the list / manage table HTML shows every [records] key (or string form). */
    fun assertCanSeeTableRecords(vararg records: Any): LivewireTestable = apply {
        refresh()
        for (item in records) {
            val needle = recordNeedle(item)
            require(html.contains(needle) || html.contains("""data-ff-record="$needle"""")) {
                "Expected the table to show record [$needle]: $html"
            }
        }
    }

    /** Assert the list / manage table does not show any of [records]. */
    fun assertCannotSeeTableRecords(vararg records: Any): LivewireTestable = apply {
        refresh()
        for (item in records) {
            val needle = recordNeedle(item)
            require(!html.contains("""data-ff-record="$needle"""")) {
                "Expected the table not to show record [$needle]"
            }
        }
    }

    /**
     * Assert row / record action [name] is hidden. When [record] is set, only that row is checked
     * (`data-ff-record`); otherwise the whole table must omit `data-ff-action="[name]"`.
     */
    fun assertTableActionHidden(name: String): LivewireTestable = assertTableActionHidden(name, null)

    fun assertTableActionHidden(name: String, record: Any?): LivewireTestable = apply {
        refresh()
        if (record == null) {
            require(!html.contains("""data-ff-action="$name"""")) {
                "Expected table action [$name] to be hidden"
            }
            return@apply
        }
        val row = rowHtml(recordNeedle(record))
        require(!row.contains("""data-ff-action="$name"""")) {
            "Expected table action [$name] to be hidden for record [${recordNeedle(record)}]: $row"
        }
    }

    /** Assert row / record action [name] is visible (optionally on [record]'s row). */
    fun assertTableActionVisible(name: String): LivewireTestable = assertTableActionVisible(name, null)

    fun assertTableActionVisible(name: String, record: Any?): LivewireTestable = apply {
        refresh()
        if (record == null) {
            require(html.contains("""data-ff-action="$name"""")) {
                "Expected table action [$name] to be visible"
            }
            return@apply
        }
        val row = rowHtml(recordNeedle(record))
        require(row.contains("""data-ff-action="$name"""")) {
            "Expected table action [$name] to be visible for record [${recordNeedle(record)}]: $row"
        }
    }

    /** Assert a form field path is present on the page. */
    fun assertFormField(name: String): LivewireTestable = apply {
        require(html.contains("""data-ff-path="$name""") || html.contains("""name="$name"""")) {
            "Expected form field [$name] on the page"
        }
    }

    /** The mounted table after [refresh], or null. */
    fun table(): Table<*, *>? = page.mountedTable()

    // ——— Internals ———

    private fun save() {
        val operation = when (page) {
            is EditPage -> "edit"
            is CreatePage -> "create"
            is ManagePage -> if (recordKey() != null) "edit" else "create"
            else -> throw IllegalStateException("${page.javaClass.simpleName} cannot call save")
        }
        val key = recordKey()
        val model = resolveRecord(operation, key)
        val submission = resource.submit(operation, model, key, LinkedHashMap(form))
        errors = submission.errors
        if (errors.isNotEmpty()) {
            notified = "Please fix the form"
            refresh()
            return
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(resource.boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            store.fill(model, submission.attributes)
            store.save(model)
            record = model
        } catch (_: FireflyException) {
            // Memory-only / no store — keep the form state; still count as a successful validate.
        }
        notified = if (operation == "edit") "Saved" else "Created"
        refresh()
    }

    private fun resolveRecord(operation: String, key: String?): Any? {
        val current = record
        if (!isKeyRef(current)) return current
        try {
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(resource.boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            if (operation == "edit" && key != null) {
                val found = store.find(key)
                if (found != null) return found
            }
            return store.newRecord()
        } catch (_: FireflyException) {
            return record
        }
    }

    private fun isKeyRef(value: Any?): Boolean {
        if (value == null) return true
        if (value is String) return true
        if (value is Number) return true
        return false
    }

    private fun recordKey(): String? {
        val current = record ?: return null
        return recordNeedle(current)
    }

    private fun state(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        out["csrfField"] = ""
        out["formAction"] = ""
        out["panelHome"] = "/"
        out["returnUrl"] = "/"
        recordKey()?.let { out["record"] = it }
        return out
    }

    private fun recordNeedle(item: Any): String {
        if (item is Cadence<*>) {
            val key = item.getKey()
            if (key != null) return key.toString()
            return item.toString()
        }
        if (item is String) return item
        if (item is Number) return item.toString()
        return try {
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(resource.boundModel()) as org.konaframework.firefly.tables.RecordStore<Any?, Any?>
            store.keyOf(item)
        } catch (_: RuntimeException) {
            item.toString()
        }
    }

    private fun rowHtml(key: String): String {
        val marker = """data-ff-record="$key""""
        val start = html.indexOf(marker)
        require(start >= 0) { "Table has no row for record [$key]: $html" }
        val trStart = html.lastIndexOf("<tr", start)
        val trEnd = html.indexOf("</tr>", start)
        if (trStart < 0 || trEnd <= trStart) return html
        return html.substring(trStart, trEnd + 5)
    }
}
