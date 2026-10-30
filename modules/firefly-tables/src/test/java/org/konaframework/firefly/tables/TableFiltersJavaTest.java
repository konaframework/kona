package org.konaframework.firefly.tables;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TableFiltersJavaTest {
    private static Map<String, Object> row(String id, String name, String status, int score, Boolean active) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("status", status);
        row.put("score", score);
        row.put("active", active);
        return row;
    }

    private static List<Map<String, Object>> rows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("1", "Ada", "open", 10, true));
        rows.add(row("2", "Bea", "draft", 250, false));
        rows.add(row("3", "Cy", "open", 40, null));
        return rows;
    }

    private static Table<Map<String, Object>, MemoryQuery> table() {
        return Table.memory(rows());
    }

    @Test
    void javaConfiguresFiltersLayoutAndActions() {
        Cell none = Cell.of(path -> null);
        SelectFilter status = SelectFilter.make("status")
            .label("State")
            .label(cell -> "State")
            .indicator("State chosen")
            .indicator(cell -> "State chosen")
            .options(Map.of("open", "Open", "draft", "Draft"))
            .options(cell -> Map.of("open", "Open", "draft", "Draft"))
            .multiple()
            .searchable()
            .preload()
            .placeholder("Any state")
            .defaultState("open")
            .defaultUsing(cell -> "open")
            .attribute("status")
            .operator("=")
            .extraInputAttributes(Map.of("data-a", "1"))
            .extraInputAttributes(cell -> Map.of("data-a", "1"))
            .baseQuery((query, data) -> { })
            .indicateUsing(data -> "State: " + String.join(", ", data.values()))
            .modifyFormFieldUsing(field -> field.helperText("Pick some"))
            .columnSpan(2)
            .columnSpanFull()
            .visible(true)
            .hidden(false)
            .hidden(cell -> false)
            .excludeWhenResolvingRecord(true);
        SelectFilter queried = SelectFilter.make("q").query((query, value) -> { }).queryUsing((query, data) -> { }).defaultUsing(cell -> null).defaultState();
        assertTrue(queried.isActive(FilterData.single("1")));

        TernaryFilter active = TernaryFilter.make("active")
            .label("Active")
            .label(cell -> "Active")
            .indicator("On")
            .indicator(cell -> "On")
            .defaultState(null)
            .defaultUsing(cell -> null)
            .attribute("active")
            .operator("=")
            .options(Map.of())
            .options(cell -> Map.of())
            .extraInputAttributes(Map.of("data-t", "1"))
            .extraInputAttributes(cell -> Map.of("data-t", "1"))
            .baseQuery((query, data) -> { })
            .indicateUsing(data -> null)
            .modifyFormFieldUsing(field -> field.label("Active?"))
            .columnSpan(1)
            .columnSpanFull()
            .visible(true)
            .hidden(false)
            .hidden(cell -> false)
            .excludeWhenResolvingRecord(false)
            .placeholder("Everyone")
            .trueLabel("Active")
            .falseLabel("Inactive")
            .nullable()
            .queries((query, data) -> ((MemoryQuery) query).where("active", true), (query, data) -> ((MemoryQuery) query).where("active", false));
        TernaryFilter defaulted = TernaryFilter.make("d").defaultState().query((query, value) -> { }).queryUsing((query, data) -> { });
        assertTrue(defaulted.isActive(FilterData.single("true")));

        TextConstraint named = TextConstraint.make("name").insensitive().operators(
            new ConstraintOperator("contains", "contains", "does not contain"),
            new ConstraintOperator("soundex", "sounds like", "does not sound like", true,
                (query, store, rule, column) -> store.constrain(query, column, "like", "%" + rule.value() + "%"))
        );
        QueryBuilderFilter rules = QueryBuilderFilter.make("rules")
            .label("Rules")
            .indicator("Rules on")
            .modifyFormFieldUsing(field -> field.hiddenLabel())
            .columnSpan(2)
            .columnSpanFull()
            .visible(true)
            .hidden(false)
            .constraintPickerWidth("16rem")
            .constraints(
                named,
                NumberConstraint.make("score"),
                BooleanConstraint.make("active"),
                SelectConstraint.make("status").label("Status").attribute("status").options(Map.of("open", "Open")).multiple(),
                RelationshipConstraint.make("team").label("Team").titleAttribute("name").multiple().optionsLimit(10)
            );
        assertEquals(5, rules.constraints().size());
        assertEquals(2, named.operators().size());
        assertEquals("is", new ConstraintOperator("is", "is", "is not").getLabel());

        SelectFilter custom = SelectFilter.make("who").attribute("name").searchable()
            .getSearchResultsUsing((search, cell) -> Map.of("Ada", "Ada Lovelace"))
            .getOptionLabelUsing((key, cell) -> "Ada Lovelace");
        assertEquals(Map.of("Ada", "Ada Lovelace"), custom.options(none, null, "a", null));

        Table<Map<String, Object>, MemoryQuery> table = table()
            .filters(FiltersLayout.ABOVE_CONTENT_COLLAPSIBLE, status, active, rules, custom)
            .filtersFormColumns(2)
            .filtersFormWidth("2xl")
            .filtersFormMaxHeight("30rem")
            .filtersFormHeading("Refine")
            .filtersFormSchema(FilterSection.make("Basics").filters("status", "active").columns(2))
            .deferFilters()
            .persistFiltersInSession()
            .filtersSessionKey("java.filters")
            .deselectAllRecordsWhenFiltered()
            .hiddenFilterIndicators(false)
            .filtersResetActionPosition(FiltersResetActionPosition.FOOTER)
            .filtersTriggerAction(action -> action.label("Refine").button().hidden(false))
            .filtersApplyAction(action -> action.label("Go"))
            .filtersResetAction(action -> action.link().hidden())
            .filtersRemoveAllAction(action -> action.iconButton().visible(true));
        assertEquals(List.of("Ada", "Cy"), names(table));
        table.applyFilterQuery(Map.of("filters", "1", "filters.active", "false", "filters.rules", "score:isMin:20"));
        assertEquals(List.of("Bea"), names(table));
        assertEquals(List.of("Rules on"), table.filterIndicators().stream().map(FilterIndicator::getLabel).toList());
        assertEquals("1", table.filterQuery().get("filters"));
        String html = table.render();
        assertTrue(html.contains("data-ff-filters-layout=\"collapsible\""), html);
        assertTrue(html.contains("Refine"));
        assertTrue(table.filterSet().persistsInSession());
        table.queryStringIdentifier("orders");
        assertEquals("orders", table.queryStringIdentifier());
        assertEquals("ordersFilters", table.filterSet().queryPrefix());
        table.applyFilterQuery(Map.of("ordersFilters", "1", "ordersFilters.active", "false"));
        assertEquals("1", table.filterQuery().get("ordersFilters"));
        assertTrue(table.render().contains("data-ff-filters-prefix=\"ordersFilters\""));
        table.resetFilters();
        assertTrue(table.filterQuery().isEmpty());
        table.filtersLayout(FiltersLayout.DROPDOWN).hiddenFilterIndicators().deselectAllRecordsWhenFiltered(false).deferFilters(false);
        assertFalse(table.filterSet().isDeferred());
        assertFalse(table.filterSet().deselectsOnFilter());
        assertEquals("published_at", DateConstraint.make("published_at").getName());
        assertEquals("a", FilterData.of(Map.of("value", "a")).string("value"));
        assertEquals("Chip", FilterIndicator.make("Chip").removeField("from").getLabel());
        assertEquals(null, FilterSection.make().heading());
        assertEquals("name:contains:Ada", QueryBuilderFilter.encode(List.of(List.of(new QueryRule("name", "contains", false, List.of("Ada"))))));
        assertEquals(Map.of("open", "Open"), SelectFilter.make("s").options(Map.of("open", "Open", "draft", "Draft")).options(none, null, "op"));
        assertEquals("open", SelectFilter.make("s").defaultState("open").defaultValue(none));
        assertEquals(true, Filter.make("f").defaultState().defaultValue(none));
        assertEquals(true, Filter.make("f").defaultState(true).defaultValue(none));
    }

    private static List<Object> names(Table<Map<String, Object>, MemoryQuery> table) {
        return table.load().getRecords().stream().map(row -> row.get("name")).toList();
    }
}
