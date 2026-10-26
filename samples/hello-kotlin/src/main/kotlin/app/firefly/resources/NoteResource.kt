package app.firefly.resources

import app.models.Note
import org.konaframework.firefly.Resource
import org.konaframework.firefly.forms.DateTimePicker
import org.konaframework.firefly.forms.Select
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.Infolist
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn

class NoteResource : Resource("notes") {
    init {
        label("Notes")
        simple()
        model(Note::class.java)
        form(
            Schema.make().components(
                TextInput.make("name"),
                Select.make("status").options(mapOf("open" to "Open", "done" to "Done")),
                DateTimePicker.make("due"),
            ),
        )
        infolist(Infolist.make().components(TextEntry.make("name")))
        table(Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name")))
    }
}
