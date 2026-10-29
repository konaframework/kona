package app.firefly.resources;

import app.models.Line;
import app.models.Order;
import org.konaframework.firefly.CreatePage;
import org.konaframework.firefly.EditPage;
import org.konaframework.firefly.ListPage;
import org.konaframework.firefly.RelationManager;
import org.konaframework.firefly.Resource;
import org.konaframework.firefly.ViewPage;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.infolists.TextEntry;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.tables.Table;
import org.konaframework.firefly.tables.TextColumn;
import org.konaframework.firefly.widgets.Page;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderResource extends Resource {
    public OrderResource() {
        super("orders");
        label("Order");
        model(Order.class);
    }

    @Override
    public Schema form(Schema schema) {
        return schema.components(TextInput.make("name").label("Name").required());
    }

    @Override
    public Schema infolist(Schema schema) {
        return schema.components(TextEntry.make("name").label("Name"));
    }

    @Override
    public Table<?, ?> table(Table<?, ?> table) {
        return table.columns(TextColumn.make("name").label("Name").searchable().sortable());
    }

    @Override
    public List<RelationManager> getRelations() {
        return List.of(
            new RelationManager("lines")
                .related(Line.class)
                .recordTitleAttribute("sku")
                .table(Table.forModel(Line.class).columns(TextColumn.make("sku").label("SKU").searchable()))
                .form(Schema.make().components(TextInput.make("sku").label("SKU").required()))
        );
    }

    @Override
    public Map<String, Page> getPages() {
        Map<String, Page> pages = new LinkedHashMap<>();
        pages.put("list", new ListPage("list"));
        pages.put("create", new CreatePage("create"));
        pages.put("edit", new EditPage("edit"));
        pages.put("view", new ViewPage("view"));
        return pages;
    }
}
