package app.firefly.pages

import org.konaframework.firefly.CustomPage
import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.forms.DateTimePicker
import org.konaframework.firefly.forms.Select
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.Infolist
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn

class Desk : CustomPage("desk") {
    init {
        title("Desk")
        mountForm(
            Schema.make().components(
                TextInput.make("name"),
                Select.make("status").options(mapOf("open" to "Open", "done" to "Done")),
                DateTimePicker.make("due"),
            ),
        )
        mountInfolist(Infolist.make().components(TextEntry.make("name")))
        table(Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name")))
        headerActions(
            Action.make("edit").label("Edit").modal().modalHeading("Edit note").schema(TextInput.make("note")),
        )
    }
}
