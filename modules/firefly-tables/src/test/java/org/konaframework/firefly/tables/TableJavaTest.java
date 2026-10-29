package org.konaframework.firefly.tables;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.konaframework.cadence.Cadence;
import org.konaframework.cadence.ConnectionConfig;
import org.konaframework.cadence.Database;
import org.konaframework.cadence.ModelBuilder;
import org.konaframework.cadence.relations.HasMany;
import org.konaframework.firefly.schemas.Text;
import org.konaframework.firefly.tables.layout.Grid;
import org.konaframework.firefly.tables.layout.Split;
import org.konaframework.firefly.tables.layout.Stack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TableJavaTest {
    @AfterEach
    void reset() {
        Table.configureUsing(null);
        TextColumn.configureUsing(null);
        Filter.configureUsing(null);
        TableButton.configureUsing(null);
        Filter.configureUsing(SelectFilter.class, null);
        Column.configureUsing(TextColumn.class, null);
    }

    @Test
    void javaBuildsAMemoryTable() {
        TextColumn.configureUsing(column -> column.searchable(true));
        Map<String, Object> ada = new LinkedHashMap<>();
        ada.put("id", "1");
        ada.put("name", "Ada");
        ada.put("score", 2);
        Map<String, Object> bea = new LinkedHashMap<>();
        bea.put("id", "2");
        bea.put("name", "Bea");
        bea.put("score", 4);
        Table<Map<String, Object>, MemoryQuery> table = Table.memory(List.of(ada, bea));
        table.columns(
            TextColumn.make("name").label(cell -> "Name").sortable(true).searchable(true),
            TextColumn.make("score").money().summarize(Summary.SUM)
        );
        table.filters(SelectFilter.make("name").attribute("name").options(Map.of("Ada", "Ada")));
        table.headerButtons(TableButton.make("export").label("Export").modal().schema(Text.make("Go")));
        table.modifyQueryUsing(query -> query.where("score", ">", 0));
        table.search("ada").sort("name", "asc").page(1);
        assertEquals("Ada", table.load().getRecords().get(0).get("name"));
        assertTrue(table.render().contains("ffColumn"));
        assertTrue(table.render().contains("@entangle"));
        assertTrue(table.render().contains("$wire.$call"));
    }

    @Test
    void javaCallsStaticFactories() {
        Cell.of(path -> null);
        CheckboxColumn.make("on");
        ColorColumn.make("swatch");
        Column.configureUsing(TextColumn.class, null);
        Filter.make("name");
        Filter.configureUsing(Filter.class, null);
        IconColumn.make("on").bool();
        ImageColumn.make("photo");
        BadgeColumn.make("status");
        SelectColumn.make("status");
        SelectFilter.configureUsing((java.util.function.Consumer<SelectFilter>) null);
        Table.memory(Map.of("id", "1"));
        TableGroup.make("status");
        TernaryFilter.make("on");
        ToggleColumn.make("on");
        TrashedFilter.make();
        // JVM overloads: 2-arg render + TableButton.group static forwarder.
        TableButton view = TableButton.make("view").label("View").url("view");
        TableButton edit = TableButton.make("edit").label("Edit").url("edit");
        assertTrue(view.render(Cell.of(path -> null), false).contains("ff-table-button"));
        assertTrue(TableButton.group(view, edit).render(Cell.of(path -> null), false).contains("ff-row-action-menu"));
    }

    @Test
    void javaBuildsSplitStackContentGridAndStackedOnMobile() {
        Map<String, Object> ada = new LinkedHashMap<>();
        ada.put("id", "1");
        ada.put("name", "Ada");
        ada.put("email", "ada@x.test");
        ada.put("phone", "111");
        Table<Map<String, Object>, MemoryQuery> table = Table.memory(List.of(ada));
        table.columns(
            Split.make(
                TextColumn.make("name").label("Name").sortable().grow(false),
                Stack.make(
                    TextColumn.make("phone").label("Phone"),
                    TextColumn.make("email").label("Email")
                ).space(1).alignment(Align.END).visibleFrom("md")
            ).from("md")
        );
        table.stackedOnMobile(true);
        table.contentGrid(Map.of("md", 2, "xl", 3));
        String html = table.render();
        assertTrue(html.contains("ff-layout-split"));
        assertTrue(html.contains("ff-layout-stack"));
        assertTrue(html.contains("data-ff-content-grid"));
        assertTrue(html.contains("data-ff-stacked-mobile=\"true\""));
        assertTrue(Grid.make(2).schema(TextColumn.make("name")).flattenColumns().size() >= 1);
    }

    @Test
    void javaForModelOpensCadenceOrRejectsUnknown() {
        String url = "jdbc:h2:mem:ffjavamodel" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Database database = new Database().addConnection("default", new ConnectionConfig(url, "sa", ""));
        database.connection().statement(
            "create table ff_java_people (id bigint auto_increment primary key, name varchar(100))"
        );
        database.setAsGlobal();
        Table<?, ?> table = Table.forModel(JavaPerson.class);
        table.columns(TextColumn.make("name"));
        assertTrue(table.render().contains("ff-table"));
        org.junit.jupiter.api.Assertions.assertThrows(
            org.konaframework.firefly.support.FireflyException.class,
            () -> Table.forModel(Object.class)
        );
    }

    @Test
    void javaReceivesAModelBuilder() {
        String url = "jdbc:h2:mem:ffjava" + System.nanoTime() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        Database database = new Database().addConnection("default", new ConnectionConfig(url, "sa", ""));
        database.connection().statement(
            "create table java_customers (id bigint auto_increment primary key, name varchar(100), active boolean)"
        );
        database.connection().statement(
            "create table java_orders (id bigint auto_increment primary key, customer_id bigint, total int)"
        );
        database.setAsGlobal();
        CadenceRecordStore<JavaCustomer> customers = new CadenceRecordStore<>(JavaCustomer.class);
        JavaCustomer ada = customers.save(customers.fill(customers.newRecord(), Map.of("name", "Ada", "active", true)));
        new CadenceRecordStore<>(JavaOrder.class).save(
            new CadenceRecordStore<>(JavaOrder.class).newRecord().fill(Map.of("customer_id", ada.getKey(), "total", 10))
        );
        Table<JavaCustomer, ModelBuilder<JavaCustomer>> table = Table.cadence(JavaCustomer.class);
        table.modifyQueryUsing(query -> query
            .select("java_customers.*")
            .where("active", true)
            .with("orders")
            .scope(builder -> builder.where("name", "Ada"))
            .whereHas("orders", orders -> orders.where("total", ">", 0))
            .join("java_orders", "java_orders.customer_id", "=", "java_customers.id")
        );
        table.columns(TextColumn.make("name"));
        assertEquals("Ada", table.load().getRecords().get(0).getAttribute("name"));
        ModelBuilder<JavaOrder> options = table.relationship(JavaOrder.class, query -> query.where("total", ">", 0));
        assertEquals(1, options.get().size());
    }

    public static class JavaCustomer extends Cadence<JavaCustomer> {
        @Override
        public String getTable() {
            return "java_customers";
        }

        @Override
        public boolean getTimestamps() {
            return false;
        }

        @Override
        public Set<String> getFillable() {
            return Set.of("name", "active");
        }

        public HasMany<JavaOrder> orders() {
            return hasMany(JavaOrder.class, "customer_id", "id");
        }
    }

    public static class JavaOrder extends Cadence<JavaOrder> {
        @Override
        public String getTable() {
            return "java_orders";
        }

        @Override
        public boolean getTimestamps() {
            return false;
        }

        @Override
        public Set<String> getFillable() {
            return Set.of("customer_id", "total");
        }
    }

    public static class JavaPerson extends Cadence<JavaPerson> {
        @Override
        public String getTable() {
            return "ff_java_people";
        }

        @Override
        public boolean getTimestamps() {
            return false;
        }

        @Override
        public Set<String> getFillable() {
            return Set.of("name");
        }
    }
}
