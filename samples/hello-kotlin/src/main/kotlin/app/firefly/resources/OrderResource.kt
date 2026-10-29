package app.firefly.resources

import app.models.Line
import app.models.Order
import org.konaframework.firefly.CreatePage
import org.konaframework.firefly.EditPage
import org.konaframework.firefly.ListPage
import org.konaframework.firefly.RelationManager
import org.konaframework.firefly.Resource
import org.konaframework.firefly.ViewPage
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn
import org.konaframework.firefly.widgets.Page

class OrderResource : Resource("orders") {
    init {
        label("Order")
        model(Order::class.java)
    }

    override fun form(schema: Schema): Schema =
        schema.components(TextInput.make("name").label("Name").required())

    override fun infolist(schema: Schema): Schema =
        schema.components(TextEntry.make("name").label("Name"))

    override fun table(table: Table<*, *>): Table<*, *> =
        table.columns(TextColumn.make("name").label("Name").searchable().sortable())

    override fun getRelations(): List<RelationManager> = listOf(
        RelationManager("lines")
            .related(Line::class.java)
            .titleAttribute("sku")
            .table(
                Table.forModel(Line::class.java).columns(TextColumn.make("sku").label("SKU").searchable()),
            )
            .form(Schema.make().components(TextInput.make("sku").label("SKU").required())),
    )

    override fun getPages(): Map<String, Page> = mapOf(
        "list" to ListPage("list"),
        "create" to CreatePage("create"),
        "edit" to EditPage("edit"),
        "view" to ViewPage("view"),
    )
}
