package org.konaframework.firefly;

import org.junit.jupiter.api.Test;
import org.konaframework.cadence.ConnectionConfig;
import org.konaframework.cadence.Database;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.http.Router;
import org.konaframework.firefly.widgets.Page;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operation-aware forms from Java: `defaultOnCreate`, `dehydrated(predicate)`, `Get.operation()` (#463). */
class OperationJavaTest {
    public static class Accounts extends Resource {
        public Accounts() {
            super("accounts");
            label("Account");
            model(Shelf.class);
        }

        @Override
        public Schema form(Schema schema) {
            TextInput secret = TextInput.make("secret")
                .password()
                .dehydrateStateUsing(value -> "hashed:" + value)
                .required(get -> "create".equals(get.operation()));
            secret.dehydrated(state -> state != null && !state.toString().isBlank());
            TextInput name = TextInput.make("name");
            name.defaultOnCreate("Anonymous");
            return schema.components(name, secret);
        }

        @Override
        public Map<String, Page> getPages() {
            Map<String, Page> pages = new LinkedHashMap<>();
            pages.put("list", new ListPage("list"));
            pages.put("create", new CreatePage("create"));
            pages.put("edit", new EditPage("edit"));
            return pages;
        }
    }

    @Test
    void javaFormsDefaultOnCreateAndRequirePasswordsOnCreateOnly() {
        String url = "jdbc:h2:mem:ffop" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Database database = new Database().addConnection("default", new ConnectionConfig(url, "sa", "", null, 2));
        database.connection().statement("create table ff_shelves (id bigint auto_increment primary key, name varchar(100))");
        database.setAsGlobal();

        Router router = new Router();
        new Panel().path("admin").authenticate(false).resource(new Accounts()).mount(router);

        String create = router.find("GET", "/admin/accounts/create").getRoute().getAction().handle(Request.get("/admin/accounts/create")).getBody();
        assertTrue(create.contains("value=\"Anonymous\""));
        assertTrue(create.contains("ff-required-mark"));

        Response rejected = post(router, "name=Ada&secret=");
        assertEquals(302, rejected.getStatus());
        assertEquals("/admin/accounts/create", rejected.getHeaders().get("Location"));

        Response saved = post(router, "name=Ada&secret=s3cret");
        assertEquals("/admin/accounts/list", saved.getHeaders().get("Location"));
        assertFalse(create.contains("hashed:"));
    }

    private static Response post(Router router, String body) {
        Request request = new Request("POST", "/admin/accounts/create", Map.of("Content-Type", "application/x-www-form-urlencoded"), Map.of(), body);
        return router.find("POST", "/admin/accounts/create").getRoute().getAction().handle(request);
    }
}
