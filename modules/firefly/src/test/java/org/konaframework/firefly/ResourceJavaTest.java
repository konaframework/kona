package org.konaframework.firefly;

import org.junit.jupiter.api.Test;
import org.konaframework.cadence.ConnectionConfig;
import org.konaframework.cadence.Database;
import org.konaframework.cadence.ModelBuilder;
import org.konaframework.firefly.forms.DateTimePicker;
import org.konaframework.firefly.forms.Select;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.infolists.TextEntry;
import org.konaframework.firefly.schemas.Get;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.support.FireflyException;
import org.konaframework.firefly.tables.Table;
import org.konaframework.firefly.tables.TextColumn;
import org.konaframework.firefly.widgets.Page;
import org.konaframework.http.Request;
import org.konaframework.http.Router;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceJavaTest {
    @Test
    void javaResourceOpensItsManagePage() {
        Notes notes = new Notes();
        Router router = new Router();
        new Panel().path("admin").authenticate(false).resource(notes).mount(router);
        var match = router.find("GET", "/admin/notes/manage");
        String html = match.getRoute().getAction().handle(Request.get("/admin/notes/manage")).getBody();
        assertTrue(html.contains("ff-manage"));
        assertTrue(html.contains("data-ff-max-width=\"7xl\""));
        assertTrue(html.contains("ff-dialog"));
        assertTrue(html.contains("ff-combobox"));
        assertTrue(html.contains("Ada"));
        assertEquals(Shelf.class, new Resource("shelf").model((Get get) -> Shelf.class).boundModel());
        assertThrows(FireflyException.class, () -> new Resource("open").model((Get get) -> null).query());
        assertThrows(FireflyException.class, () -> new RelationManager("lines").related((Get get) -> null).query());
        database();
        assertTrue(new Resource("shelf").model(Shelf.class).query() instanceof ModelBuilder);
        assertEquals("manage", notes.getPages().keySet().iterator().next());
        assertEquals("Users", new Resource("user").label("User").pluralLabel());
        assertEquals("People", new Resource("user").label("User").pluralLabel("People").pluralLabel());
        assertEquals("Staff", new Resource("user").label("User").pluralLabel(get -> "Staff").pluralLabel());
    }

    private static Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "1");
        row.put("name", "Ada");
        return row;
    }

    private static void database() {
        String url = "jdbc:h2:mem:ffjava" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Database database = new Database().addConnection("default", new ConnectionConfig(url, "sa", "", null, 2));
        database.connection().statement("create table ff_shelves (id bigint auto_increment primary key, name varchar(100))");
        database.setAsGlobal();
    }

    public static class Notes extends Resource {
        public Notes() {
            super("notes");
            label("Notes");
            simple();
            maxWidth("7xl");
            model(Shelf.class);
        }

        @Override
        public Schema form(Schema schema) {
            return schema.components(
                TextInput.make("name"),
                Select.make("status").options(Map.of("open", "Open")),
                DateTimePicker.make("due")
            );
        }

        @Override
        public Schema infolist(Schema schema) {
            return schema.components(TextEntry.make("name"));
        }

        @Override
        public Table<?, ?> table(Table<?, ?> table) {
            return Table.memory(row()).columns(TextColumn.make("name"));
        }

        @Override
        public Map<String, Page> getPages() {
            Map<String, Page> pages = new LinkedHashMap<>();
            pages.put("manage", new ManagePage("manage"));
            return pages;
        }
    }
}
