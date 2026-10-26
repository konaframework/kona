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
import java.util.Map;

public class OrderResource extends Resource {
    public OrderResource() {
        super("orders");
        label("Orders");
        model(Order.class);
        relation(new RelationManager("lines")
            .related(Line.class)
            .titleAttribute("sku")
            .table(Table.memory(lineRow()).columns(TextColumn.make("sku"))));
    }

    @Override
    public Schema form(Schema schema) {
        return schema.components(TextInput.make("name"));
    }

    @Override
    public Schema infolist(Schema schema) {
        return schema.components(TextEntry.make("name"));
    }

    @Override
    public Table<?, ?> table(Table<?, ?> table) {
        return Table.memory(orderRow()).columns(TextColumn.make("name"));
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

    private static Map<String, Object> orderRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("name", "Ada");
        return row;
    }

    private static Map<String, Object> lineRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("sku", "A");
        return row;
    }
}
