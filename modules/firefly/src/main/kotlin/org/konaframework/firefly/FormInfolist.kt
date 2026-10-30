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
import org.konaframework.firefly.schemas.Fieldset
import org.konaframework.firefly.schemas.Flex
import org.konaframework.firefly.schemas.Grid
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.Section
import org.konaframework.firefly.schemas.Tab
import org.konaframework.firefly.schemas.Tabs
import org.konaframework.firefly.schemas.Wizard
import org.konaframework.firefly.schemas.WizardStep

/**
 * Builds a readonly infolist from a form schema (Filament-style fallback when a resource or
 * relation manager defines [Resource.form] / a relation form but no infolist).
 *
 * Layout is preserved: root [Schema.columns] (count or breakpoints), [Section], [Tabs], [Grid],
 * column spans, and nested structure stay in place; dehydrated fields map to matching entry types.
 * Hidden and chrome-only fields are skipped.
 */
object FormInfolist {
    /**
     * Top-level form components remapped for View — layout containers keep their structure;
     * leaf fields become entries.
     */
    @JvmStatic
    fun entriesFrom(form: Schema): List<Component> =
        form.components().mapNotNull { mapNode(it) }

    /**
     * Copy the form’s root column grid onto [target] when set, then append [entriesFrom]
     * when that list is non-empty.
     */
    @JvmStatic
    fun fill(target: Schema, form: Schema): Schema {
        val layout = form.columnLayout()
        if (layout != null) {
            target.columns(layout)
        }
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

    private fun mapNode(component: Component): Component? {
        when (component) {
            is Field<*> -> {
                if (!component.isDehydrated()) return null
                val entry = entryFor(component) ?: return null
                return component.copyPresentationOnto(entry)
            }
            is Section -> return remapContainer(component) { kids -> component.schema(*kids) }
            is Grid -> return remapContainer(component) { kids -> component.schema(*kids) }
            is Flex -> return remapContainer(component) { kids -> component.schema(*kids) }
            is Fieldset -> return remapContainer(component) { kids -> component.schema(*kids) }
            is Tab -> return remapContainer(component) { kids -> component.schema(*kids) }
            is WizardStep -> return remapContainer(component) { kids -> component.schema(*kids) }
            is Tabs -> {
                val tabs = component.children().mapNotNull { child ->
                    if (child !is Tab) return@mapNotNull null
                    mapNode(child) as? Tab
                }
                if (tabs.isEmpty()) return null
                component.tabs(*tabs.toTypedArray())
                return component
            }
            is Wizard -> {
                val steps = component.children().mapNotNull { child ->
                    if (child !is WizardStep) return@mapNotNull null
                    mapNode(child) as? WizardStep
                }
                if (steps.isEmpty()) return null
                component.steps(*steps.toTypedArray())
                return component
            }
            else -> {
                val kids = component.children()
                if (kids.isEmpty()) return component
                return remapContainer(component) { mapped -> component.children(*mapped) }
            }
        }
    }

    private fun remapContainer(component: Component, apply: (Array<Component>) -> Unit): Component? {
        val mapped = component.children().mapNotNull { mapNode(it) }
        if (mapped.isEmpty()) return null
        apply(mapped.toTypedArray())
        return component
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
