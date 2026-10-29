package app.firefly.resources

import app.models.Note
import org.konaframework.firefly.Resource
import org.konaframework.firefly.forms.DateTimePicker
import org.konaframework.firefly.forms.Select
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn

class NoteResource : Resource("notes") {
    init {
        label("Note")
        simple()
        model(Note::class.java)
        formColumns(2)
        infolistColumns(2)
    }

    override fun form(schema: Schema): Schema =
        schema.components(
            TextInput.make("name").label("Name").required(),
            Select.make("status").label("Status").options(mapOf("open" to "Open", "done" to "Done")),
            DateTimePicker.make("due").label("Due"),
        )

    override fun infolist(schema: Schema): Schema =
        schema.components(
            TextEntry.make("name").label("Name"),
            TextEntry.make("status").label("Status"),
            TextEntry.make("due").label("Due"),
        )

    override fun table(table: Table<*, *>): Table<*, *> =
        table.columns(
            TextColumn.make("name").label("Name").searchable().sortable(),
            TextColumn.make("status").label("Status").badge(),
            TextColumn.make("due").label("Due"),
        )
}
