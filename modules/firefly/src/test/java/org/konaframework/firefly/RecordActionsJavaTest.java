package org.konaframework.firefly;

import org.junit.jupiter.api.Test;
import org.konaframework.cadence.ConnectionConfig;
import org.konaframework.cadence.Database;
import org.konaframework.firefly.actions.Action;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.infolists.TextEntry;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.tables.Table;
import org.konaframework.firefly.tables.TextColumn;
import org.konaframework.firefly.widgets.Page;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.http.Router;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Record actions written the Java way: lambdas for handlers, a subclassed edit page for header actions. */
class RecordActionsJavaTest {
    private static final List<String> LOG = new ArrayList<>();

    @Test
    void javaHandlersRunFromHeaderAndTableActions() {
        String url = "jdbc:h2:mem:ffrajava" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Database database = new Database().addConnection("default", new ConnectionConfig(url, "sa", "", null, 2));
        database.connection().statement(
            "create table ra_entries (id bigint auto_increment primary key, name varchar(100), state varchar(20), tenant_id varchar(20))");
        database.setAsGlobal();
        LOG.clear();
        RaEntry entry = new RaEntry();
        entry.fill(Map.of("name", "First", "state", "draft"));
        entry.save();
        String id = String.valueOf(entry.getKey());

        Router router = new Router();
        new Panel().path("admin").authenticate(false).resource(new Entries()).mount(router);

        Request edit = new Request("GET", "/admin/entries/edit", Map.of(), Map.of("id", id), "");
        String html = router.find("GET", "/admin/entries/edit").getRoute().getAction().handle(edit).getBody();
        assertTrue(html.contains("action=\"/admin/entries/actions/rename\""), html);
        assertTrue(html.contains("Rename"), html);

        Response renamed = post(router, "/admin/entries/actions/rename", "id=" + id + "&new_name=Second");
        assertEquals(302, renamed.getStatus());
        assertEquals(List.of("rename:Second"), LOG);

        Response row = post(router, "/admin/entries/actions/touch", "id=" + id);
        assertEquals(302, row.getStatus());
        assertEquals(List.of("rename:Second", "touch:" + id), LOG);
    }

    private static Response post(Router router, String path, String body) {
        Request request = new Request("POST", path, Map.of("Content-Type", "application/x-www-form-urlencoded"), Map.of(), body);
        return router.find("POST", path).getRoute().getAction().handle(request);
    }

    public static class Entries extends Resource {
        public Entries() {
            super("entries");
            label("Entry");
            model(RaEntry.class);
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
            return table.columns(TextColumn.make("name")).recordActions(
                Action.make("touch").label("Touch").action(record -> LOG.add("touch:" + ((RaEntry) record).getKey())));
        }

        @Override
        public Map<String, Page> getPages() {
            Map<String, Page> pages = new LinkedHashMap<>();
            pages.put("list", new ListPage("list"));
            pages.put("edit", new EntryEdit());
            pages.put("view", new ViewPage("view"));
            return pages;
        }
    }

    public static class EntryEdit extends EditPage {
        public EntryEdit() {
            super("edit");
        }

        @Override
        public List<Action> getHeaderActions() {
            return List.of(
                Action.make("rename")
                    .label("Rename")
                    .schema(TextInput.make("new_name").required())
                    .actionWithData((record, data) -> LOG.add("rename:" + data.get("new_name"))));
        }
    }
}
