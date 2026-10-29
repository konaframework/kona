package app.firefly.resources

import app.models.Project
import org.konaframework.firefly.Resource
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.forms.Toggle
import org.konaframework.firefly.infolists.IconEntry
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.IconColumn
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn

class ProjectResource : Resource("projects") {
    init {
        label("Project")
        simple()
        model(Project::class.java)
        formColumns(2)
        infolistColumns(2)
    }

    override fun form(schema: Schema): Schema =
        schema.components(
            TextInput.make("name").label("Name").required(),
            Toggle.make("active").label("Active"),
        )

    override fun infolist(schema: Schema): Schema =
        schema.components(
            TextEntry.make("name").label("Name"),
            IconEntry.make("active").label("Active").boolean(),
        )

    override fun table(table: Table<*, *>): Table<*, *> =
        table.columns(
            TextColumn.make("name").label("Name").searchable().sortable(),
            IconColumn.make("active").boolean().label("Active"),
        )
}
