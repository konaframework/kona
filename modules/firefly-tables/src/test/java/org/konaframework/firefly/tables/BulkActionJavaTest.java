package org.konaframework.firefly.tables;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.konaframework.firefly.schemas.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BulkActionJavaTest {
    @AfterEach
    void reset() {
        BulkAction.configureUsing(null);
    }

    private static Map<String, Object> row(String id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    @Test
    void javaConfiguresAndRunsBulkActions() {
        BulkAction.configureUsing(action -> action.tooltip("Runs on the selection"));
        List<List<String>> seen = new ArrayList<>();

        Table<Map<String, Object>, MemoryQuery> table = Table.memory(row("1", "Ada"), row("2", "Grace"), row("3", "Linus"))
            .columns(TextColumn.make("name"))
            .bulkActions(
                BulkActionGroup.make(
                    DeleteBulkAction.make(),
                    BulkAction.make("archive")
                        .icon("check")
                        .requiresConfirmation()
                        .schema(Text.make("Archived rows stay searchable."))
                        .action(context -> seen.add(context.keys()))
                ).label("Manage"),
                BulkActionGroup.make(List.of(BulkAction.make("export").button())),
                DeleteBulkAction.make("purge")
            )
            .maxSelectableRecords(10);

        String html = table.render();
        assertTrue(html.contains("data-ff-selectable"));
        assertTrue(html.contains("data-ff-bulk-run=\"archive\""));
        assertTrue(html.contains("Runs on the selection"));
        assertTrue(table.isSelectable());

        BulkActionResult archived = table.runBulkAction("archive", List.of("1", "2"));
        assertEquals(List.of("1", "2"), seen.get(0));
        assertTrue(archived.isSuccessful());

        BulkActionResult deleted = table.runBulkAction("delete", List.of("3"), Map.of());
        assertEquals("Deleted", deleted.notificationTitle());
        assertFalse(table.render().contains("data-ff-record=\"3\""));

        BulkActionResult purged = table.runBulkAction("purge", List.of(), Map.of(), true);
        assertEquals(2, purged.getSuccessCount());
    }
}
