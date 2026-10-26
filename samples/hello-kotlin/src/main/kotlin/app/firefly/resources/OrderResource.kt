package app.firefly.resources

import app.models.Line
import app.models.Order
import org.konaframework.firefly.RelationManager
import org.konaframework.firefly.Resource
import org.konaframework.firefly.forms.TextInput
import org.konaframework.firefly.infolists.Infolist
import org.konaframework.firefly.infolists.TextEntry
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.tables.Table
import org.konaframework.firefly.tables.TextColumn

class OrderResource : Resource("orders") {
    init {
        label("Orders")
        model(Order::class.java)
        form(Schema.make().components(TextInput.make("name")))
        infolist(Infolist.make().components(TextEntry.make("name")))
        table(Table.memory(mapOf("id" to "1", "name" to "Ada")).columns(TextColumn.make("name")))
        relation(
            RelationManager("lines")
                .related(Line::class.java)
                .titleAttribute("sku")
                .table(Table.memory(mapOf("id" to "1", "sku" to "A")).columns(TextColumn.make("sku"))),
        )
    }
}
