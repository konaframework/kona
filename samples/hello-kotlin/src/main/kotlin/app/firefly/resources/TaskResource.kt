package app.firefly.resources

import app.models.Task
import org.konaframework.firefly.Resource
import org.konaframework.firefly.forms.Select
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn

/**
 * Demo resource for Select.relationship + combobox: Create opens a Project picker
 * preloaded from the `project` belongsTo on [Task].
 */
class TaskResource : Resource("tasks") {
    init {
        label("Task")
        simple()
        model(Task::class.java)
        formColumns(2)
        infolistColumns(2)
    }

    override fun form(schema: Schema): Schema =
        schema.components(
            TextInput.make("name").label("Name").required(),
            Select.make("project_id")
                .label("Project")
                .relationship("project", "name")
                .preload()
                .searchable(),
            Select.make("status")
                .label("Status")
                .options(mapOf("open" to "Open", "done" to "Done"))
                .searchable(),
        )

    override fun infolist(schema: Schema): Schema =
        schema.components(
            TextEntry.make("name").label("Name"),
            TextEntry.make("project_id").label("Project"),
            TextEntry.make("status").label("Status"),
        )

    override fun table(table: Table<*, *>): Table<*, *> =
        table.columns(
            TextColumn.make("name").label("Name").searchable().sortable(),
            TextColumn.make("project_id").label("Project").relationship("project", "name"),
            TextColumn.make("status").label("Status").badge(),
        )
}
