package org.konaframework.firefly.actions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.schemas.Text;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActionJavaTest {
    @AfterEach
    void clear() {
        Action.configureUsing(null);
        ActionGroup.configureUsing(null);
        Action.gate(null);
    }

    @Test
    void javaChainsActions() {
        Action.configureUsing(action -> action.tooltip("Save"));
        Action save = Action.make("save")
            .label(get -> String.valueOf(get.get("/kind")))
            .icon("home")
            .color("primary")
            .slideOver()
            .modalHeading("Edit")
            .schema(Text.make("Hello"));
        Schema schema = Schema.make().fill(Map.of("kind", "org")).components(save);
        String html = schema.render();
        assertTrue(html.contains("org"));
        assertTrue(html.contains("ff-dialog-sidebar"));
        assertTrue(html.contains("ff-action-dialog\" data-ff-dialog-panel x-show=\"open\" x-cloak style=\"display:none\" hidden"));
        assertTrue(html.contains("data-ff-dialog-open"));
        assertTrue(html.contains("Hello"));
        assertTrue(html.contains("title=\"Save\""));
        Action delete = Action.make("delete").label("Delete").postForm("delete", "<input type=\"hidden\" name=\"id\" value=\"1\" />");
        assertTrue(Schema.make().components(delete).render().contains("ff-action-post"));
        final boolean[] ran = {false};
        save.modal(false);
        save.action((current, host) -> ran[0] = true);
        ActionResult result = save.call(schema);
        assertTrue(ran[0]);
        assertEquals(ActionResult.SUCCESS, result.getStatus());
        ActionGroup.make(save);
        ActionGroup group = ActionGroup.make(List.of(save)).label("More").button();
        assertTrue(Schema.make().components(group).render().contains("ff-action-group-inline"));
        assertTrue(Schema.make().components(new Rename()).render().contains("Rename"));
        Action link = Action.make("docs").url("https://example.test").openUrlInNewTab(true);
        assertTrue(Schema.make().components(link).render().contains("target=\"_blank\""));
        Action.gate((ability, component, host) -> "edit".equals(ability));
        Action gated = Action.make("save").authorize("edit");
        assertTrue(gated.isAuthorized(Schema.make().components(gated)));
    }

    public static class Rename extends Action {
        public Rename() {
            super("rename");
            label("Rename");
        }
    }
}
