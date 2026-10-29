package org.konaframework.firefly;

import org.junit.jupiter.api.Test;
import org.konaframework.cadence.ConnectionConfig;
import org.konaframework.cadence.DB;
import org.konaframework.cadence.Database;
import org.konaframework.cadence.ModelBuilder;
import org.konaframework.firefly.forms.TextInput;
import org.konaframework.firefly.infolists.TextEntry;
import org.konaframework.firefly.schemas.Schema;
import org.konaframework.firefly.tables.BulkActionGroup;
import org.konaframework.firefly.tables.TextColumn;
import org.konaframework.http.Request;
import org.konaframework.http.Response;
import org.konaframework.http.Router;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationManagerJavaTest {
    @Test
    void javaRelationManagersCreateAttachAndBulkDetach() {
        database();
        RmProject project = new RmProject();
        project.fill(Map.of("name", "Kona", "team", "a"));
        project.save();
        RmUser bob = new RmUser();
        bob.fill(Map.of("name", "Bob"));
        bob.save();
        String owner = project.getKey().toString();

        Router router = new Router();
        new Panel().path("admin").authenticate(false).resource(new Projects()).mount(router);
        String edit = get(router, "/admin/projects/edit", Map.of("id", owner));
        assertTrue(edit.contains("data-ff-relation-managers"));
        assertTrue(edit.contains("data-ff-relation-tab=\"tasks\""));
        assertTrue(edit.contains("data-ff-relation-tab=\"members\""));
        assertTrue(edit.contains(">New task<"));

        assertEquals(302, post(router, "create", "tasks", owner, "name=Write+docs").getStatus());
        assertNotNull(new RmTask().newQuery().where("name", "Write docs").first());
        assertEquals(302, post(router, "attach", "members", owner, "recordId=" + bob.getKey() + "&role=lead").getStatus());
        assertEquals("lead", DB.table("rm_project_user").where("user_id", bob.getKey()).first().get("role"));
        assertEquals(302, post(router, "bulk", "members", owner, "_bulk_action=detach&_bulk_keys=" + bob.getKey()).getStatus());
        assertEquals(0L, DB.table("rm_project_user").where("user_id", bob.getKey()).count());
    }

    @Test
    void javaFactoriesAndOverloads() {
        assertEquals("dissociate", DissociateBulkAction.make().getName());
        assertEquals("detach", DetachBulkAction.make().getName());
        assertEquals("restore", RestoreBulkAction.make().getName());
        assertEquals("forceDelete", ForceDeleteBulkAction.make().getName());
        assertEquals("delete", RelationBulkAction.delete().getName());
        assertEquals(RelationOperation.ATTACH, RelationOperation.fromToken("attach"));
        RelationAction attach = RelationAction.attach().multiple().preloadRecordSelect().allowDuplicates().slideOver();
        assertSame(RelationOperation.ATTACH, attach.getOperation());
        List<RelationAction> all = List.of(
            RelationAction.create(), RelationAction.view(), RelationAction.edit(), RelationAction.delete(),
            RelationAction.associate(), RelationAction.dissociate(), RelationAction.detach(),
            RelationAction.restore(), RelationAction.forceDelete()
        );
        assertEquals(9, all.size());
        assertEquals("New task", RelationAction.create().getLabel("task"));
    }

    /** Filament-style class-based manager written in Java. */
    public static class TasksRelationManager extends RelationManager {
        public TasksRelationManager() {
            super("tasks");
            recordTitleAttribute("name");
            form(schema -> schema.components(TextInput.make("name").required()));
            infolist(schema -> schema.components(TextEntry.make("name")));
            table(table -> table.columns(TextColumn.make("name").searchable()));
            headerActions(RelationAction.create(), RelationAction.associate());
            actions(RelationAction.view(), RelationAction.edit(), RelationAction.dissociate(), RelationAction.delete());
            bulkActions(BulkActionGroup.make(DissociateBulkAction.make(), RelationBulkAction.delete()));
            canDelete(context -> context.getOwner() instanceof RmProject);
            modifyQueryUsing((query, context) -> ((ModelBuilder<?>) query).orderBy("name"));
        }
    }

    public static class Projects extends Resource {
        public Projects() {
            super("projects");
            label("Project");
            model(RmProject.class);
        }

        @Override
        public Schema form(Schema schema) {
            return schema.components(TextInput.make("name"));
        }

        @Override
        public List<RelationManager> getRelations() {
            return List.of(
                new TasksRelationManager(),
                new RelationManager("members")
                    .recordTitleAttribute("name")
                    .pivotColumns("role")
                    .table(table -> table.columns(TextColumn.make("name"), TextColumn.make("role")))
                    .headerActions(RelationAction.attach().form(schema -> schema.components(TextInput.make("role"))))
            );
        }
    }

    private static String get(Router router, String path, Map<String, String> query) {
        Response response = router.find("GET", path).getRoute().getAction()
            .handle(new Request("GET", path, Map.of(), query, ""));
        assertEquals(200, response.getStatus(), response.getBody());
        return response.getBody();
    }

    private static Response post(Router router, String operation, String relation, String owner, String body) {
        String path = "/admin/projects/relations/" + operation;
        Map<String, String> query = Map.of("relation", relation, "owner", owner, "page", "edit");
        return router.find("POST", path).getRoute().getAction()
            .handle(new Request("POST", path, Map.of("Content-Type", "application/x-www-form-urlencoded"), query, body));
    }

    private static void database() {
        String url = "jdbc:h2:mem:ffreljava" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Database database = new Database().addConnection("default", new ConnectionConfig(url, "sa", "", null, 2));
        database.connection().statement("create table rm_projects (id bigint auto_increment primary key, name varchar(100), team varchar(20))");
        database.connection().statement(
            "create table rm_tasks (id bigint auto_increment primary key, project_id bigint, name varchar(100), deleted_at timestamp)"
        );
        database.connection().statement("create table rm_users (id bigint auto_increment primary key, name varchar(100))");
        database.connection().statement("create table rm_project_user (project_id bigint, user_id bigint, role varchar(20))");
        database.setAsGlobal();
    }
}
