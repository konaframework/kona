package app.firefly.resources;

import app.models.Line;
import app.models.Order;
import org.konaframework.firefly.RelationManager;
import org.konaframework.firefly.Resource;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.infolists.Infolist;
import org.konaframework.firefly.infolists.TextEntry;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.tables.Table;
import org.konaframework.firefly.tables.TextColumn;

import java.util.LinkedHashMap;
import java.util.Map;

public class OrderResource extends Resource {
    public OrderResource() {
        super("orders");
        label("Orders");
        model(Order.class);
        form(Schema.make().components(TextInput.make("name")));
        infolist(Infolist.make().components(TextEntry.make("name")));
        table(Table.memory(orderRow()).columns(TextColumn.make("name")));
        relation(new RelationManager("lines")
            .related(Line.class)
            .titleAttribute("sku")
            .table(Table.memory(lineRow()).columns(TextColumn.make("sku"))));
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
