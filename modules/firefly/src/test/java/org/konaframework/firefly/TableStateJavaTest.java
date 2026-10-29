package org.konaframework.firefly;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.konaframework.firefly.tables.SelectFilter;
import org.konaframework.firefly.tables.Table;
import org.konaframework.firefly.tables.TextColumn;

class TableStateJavaTest {
    @Test
    void javaPagesApplyFilterStateToCustomTables() {
        Table<Map<String, Object>, ?> table = Table.memory(List.of(
                Map.<String, Object>of("id", "1", "name", "Ada", "status", "open"),
                Map.<String, Object>of("id", "2", "name", "Bea", "status", "draft")))
            .columns(TextColumn.make("name"))
            .filters(SelectFilter.make("status").options(Map.of("open", "Open", "draft", "Draft")));
        TableState.apply(table, Map.of("filterInput", Map.of("filters", "1", "filters.status", "draft")), "java.filters");
        assertEquals(1, table.load().getRecords().size());
        assertEquals(Map.of("filters", "1", "filters.status", "draft"), table.filterQuery());
    }
}
