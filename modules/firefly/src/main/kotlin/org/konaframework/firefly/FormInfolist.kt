package org.konaframework.firefly

import org.konaframework.firefly.forms.Checkbox
import org.konaframework.firefly.forms.CodeEditor
import org.konaframework.firefly.forms.ColorPicker
import org.konaframework.firefly.forms.DateTimePicker
import org.konaframework.firefly.forms.Field
import org.konaframework.firefly.forms.Hidden
import org.konaframework.firefly.forms.KeyValue
import org.konaframework.firefly.forms.MarkdownEditor
import org.konaframework.firefly.forms.Placeholder
import org.konaframework.firefly.forms.RichEditor
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.forms.Toggle
import org.konaframework.firefly.forms.ViewField
import org.konaframework.firefly.infolists.CodeEntry
import org.konaframework.firefly.infolists.ColorEntry
import org.konaframework.firefly.infolists.IconEntry
import org.konaframework.firefly.infolists.KeyValueEntry
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema

/**
 * Builds readonly infolist entries from a form schema (Filament-style fallback when a
 * resource or relation manager defines [Resource.form] / a relation form but no infolist).
 */
object FormInfolist {
    /** Dehydrated form fields mapped to infolist entries (skips hidden / chrome-only fields). */
    @JvmStatic
    fun entriesFrom(form: Schema): List<Component> =
        form.walk()
            .filterIsInstance<Field<*>>()
            .filter { it.isDehydrated() }
            .mapNotNull { entryFor(it) }

    /** Append [entriesFrom] onto [target] when that list is non-empty. */
    @JvmStatic
    fun fill(target: Schema, form: Schema): Schema {
        val entries = entriesFrom(form)
        if (entries.isNotEmpty()) {
            target.components(*entries.toTypedArray())
        }
        return target
    }

    internal fun entryFor(field: Field<*>): Component? {
        if (field.name.isBlank()) return null
        return when (field) {
            is Hidden, is Placeholder, is ViewField -> null
            is ColorPicker -> ColorEntry.make(field.name)
            is Checkbox, is Toggle -> IconEntry.make(field.name).bool(true)
            is KeyValue -> KeyValueEntry.make(field.name)
            is CodeEditor -> CodeEntry.make(field.name)
            is MarkdownEditor -> TextEntry.make(field.name).markdown(true)
            is RichEditor -> TextEntry.make(field.name).html(true)
            is DateTimePicker -> dateEntry(field)
            is TextInput -> textInputEntry(field)
            else -> TextEntry.make(field.name)
        }
    }

    private fun dateEntry(field: DateTimePicker): TextEntry {
        val entry = TextEntry.make(field.name)
        return when {
            field.hasDate() && field.hasTime() -> entry.dateTime()
            field.hasTime() -> entry.time()
            else -> entry.date()
        }
    }

    private fun textInputEntry(field: TextInput): TextEntry {
        val entry = TextEntry.make(field.name)
        val currency = field.currencyOptions() ?: return entry
        return entry.money(currency.code, currency.locale, currency.precision)
    }
}
