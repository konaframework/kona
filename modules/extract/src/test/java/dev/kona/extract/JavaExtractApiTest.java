package dev.kona.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.extract.relations.HasMany;
import dev.kona.extract.relations.Relation;
import dev.kona.extract.schema.SchemaGrammar;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JavaExtractApiTest {
    public static class Member extends Extract<Member> {
        public static final ExtractCompanion<Member> QUERY = Extract.of(Member.class);

        @Override
        public String getTable() {
            return "users";
        }

        @Override
        public Set<String> getFillable() {
            return Set.of("name", "email", "age", "settings");
        }

        @Override
        public Map<String, Object> getCasts() {
            return Map.of("age", CastType.INT, "settings", CastType.JSON);
        }

        public HasMany<Article> posts() {
            return hasMany(Article.class, "user_id", "id");
        }
    }

    public static class Article extends Extract<Article> implements SoftDeletes {
        @Override
        public String getTable() {
            return "posts";
        }

        @Override
        public Set<String> getGuarded() {
            return Set.of();
        }
    }

    private Database database;

    @BeforeEach
    void setUp() {
        database = TestDb.INSTANCE.create();
    }

    @AfterEach
    void tearDown() {
        database.close();
        Database.clearGlobal();
    }

    @Test
    void connectionManagerFromJava() {
        ConnectionConfig config = ConnectionConfig.fromMap(Map.of("url", TestDb.INSTANCE.url(), "pool_size", 2));
        assertEquals(2, config.getPoolSize());
        ConnectionConfig env = ConnectionConfig.fromEnv(Map.of("DB_URL", "jdbc:h2:mem:java-env"));
        assertEquals("jdbc:h2:mem:java-env", env.getUrl());
        ConnectionConfig lookup = ConnectionConfig.fromEnv(key -> key.equals("DB_URL") ? "jdbc:h2:mem:java-fn" : null);
        assertEquals("jdbc:h2:mem:java-fn", lookup.getUrl());

        try (Database local = new Database().addConnection("reports", config)) {
            local.setDefaultConnection("reports");
            Boolean valid = local.withConnection(connection -> connection.isValid(1));
            assertTrue(valid);
            Boolean open = local.withConnection("reports", connection -> !connection.isClosed());
            assertTrue(open);
        }

        ExtractException wrapped = assertThrows(ExtractException.class, () -> database.withConnection(connection -> {
            throw new SQLException("driver said no");
        }));
        assertTrue(wrapped.getMessage().contains("driver said no"));
        assertTrue(wrapped.getCause() instanceof SQLException);
    }

    @Test
    void queryBuilderFromJava() {
        assertTrue(DB.table("users").insert(List.of(Map.of("name", "Ada", "age", 36), Map.of("name", "Grace", "age", 45))));
        Object id = DB.table("users").insertGetId(Map.of("name", "Linus"));
        assertEquals(3L, id);

        List<Map<String, Object>> rows = DB.table("users")
                .where("age", ">", 40)
                .orWhere(q -> q.whereNull("age").where("name", "Linus"))
                .orderBy("id")
                .limit(10)
                .get();
        assertEquals(2, rows.size());
        assertEquals("Grace", rows.get(0).get("name"));
        assertEquals(2L, DB.table("users").whereIn("name", List.of("Ada", "Linus")).count());
        assertEquals("select * from \"users\" where \"name\" = ?", DB.table("users").where("name", "x").toSql());
        assertEquals(1, DB.table("users").where("id", id).update(Map.of("age", 28)));
        assertEquals(28, DB.table("users").find(id).get("age"));

        Integer deleted = DB.transaction(tx -> tx.table("users").where("name", "Linus").delete());
        assertEquals(1, deleted);
        Page<Map<String, Object>> page = DB.table("users").orderBy("id").paginate(1, 2);
        assertEquals("Grace", page.getData().get(0).get("name"));
        assertEquals(2, page.getLastPage());
        assertEquals(2L, page.getTotal());
    }

    @Test
    void javaOverloadsAndStatics() throws Exception {
        assertTrue(Database.hasGlobal());
        assertEquals(database, Database.global());
        assertTrue(Database.fromConfig(new dev.kona.core.Config()).connectionNames().isEmpty());
        assertTrue(Database.fromMap(Map.of()).connectionNames().isEmpty());
        assertEquals(Grammar.MYSQL, Grammar.forUrl("jdbc:mysql://db"));
        assertEquals("blog_posts", Extract.tableNameFor("BlogPost"));
        assertEquals(10, new ConnectionConfig("jdbc:h2:mem:o1").getPoolSize());
        assertEquals("u", new ConnectionConfig("jdbc:h2:mem:o2", "u").getUsername());
        assertEquals("p", new ConnectionConfig("jdbc:h2:mem:o3", "u", "p").getPassword());
        assertEquals("d", new ConnectionConfig("jdbc:h2:mem:o4", "u", "p", "d").getDriver());
        assertEquals(3, new ConnectionConfig("jdbc:h2:mem:o5", "u", "p", null, 3).getPoolSize());
        assertFalse(new ConnectionConfig("jdbc:h2:mem:o6", "u", "p", null, 3, false).getQuoteIdentifiers());
        assertEquals(Database.DEFAULT, new Database().getDefaultConnection());

        DatabaseConnection base = database.connection();
        String url = base.withConnection(connection -> connection.getMetaData().getURL());
        org.h2.jdbcx.JdbcDataSource h2 = new org.h2.jdbcx.JdbcDataSource();
        h2.setURL(url);
        h2.setUser("sa");
        try (Database local = new Database("raw")) {
            local.addConnection("raw", h2);
            DatabaseConnection raw = local.connection();
            DatabaseConnection plain = new DatabaseConnection("plain", base.getDataSource());
            assertEquals(Grammar.ANSI, plain.getGrammar());

            assertTrue(raw.statement("create table j (id int auto_increment primary key, n varchar(10))"));
            assertTrue(raw.insert("insert into j (n) values ('a')"));
            assertEquals(2, raw.insertGetId("insert into j (n) values ('b')"));
            assertEquals(2, raw.select("select * from j").size());
            assertEquals("a", raw.selectOne("select n from j order by id").get("n"));
            assertEquals(2, raw.update("update j set n = 'c'"));
            assertEquals(2, raw.affectingStatement("update j set n = 'd'"));
            assertEquals(1, raw.delete("delete from j where id = 1"));
            assertTrue(DB.statement("insert into j (n) values ('e')"));
            assertTrue(DB.insert("insert into j (n) values ('f')"));
            assertEquals(3, DB.select("select * from j").size());
            assertEquals(3, DB.update("update j set n = 'g'"));
            assertEquals(3, DB.delete("delete from j"));
            assertEquals(base, DB.connection("default"));
        }

        DB.table("users").insert(Map.of("name", "Ada", "age", 36));
        QueryBuilder users = DB.table("users");
        assertEquals(1, users.copy().selectRaw("count(*) as c").get().size());
        assertEquals(1L, users.copy().whereRaw("age > 1").count());
        assertEquals(1L, users.copy().where("id", 0).orWhereRaw("age > 1").count());
        assertEquals("select * from \"users\" order by \"name\" asc", users.copy().orderBy("name").toSql());
        assertEquals("select * from \"users\" order by \"created_at\" desc", users.copy().latest().toSql());
        assertEquals("select * from \"users\" order by \"created_at\" asc", users.copy().oldest().toSql());
        assertEquals("Ada", users.copy().find(1).get("name"));
        assertEquals(15, users.copy().paginate().getPerPage());
        assertEquals(2, users.copy().paginate(2).getPerPage());
        assertEquals(1, users.copy().increment("age"));
        assertEquals(1, users.copy().decrement("age"));
        assertEquals(36, users.copy().value("age"));

        ModelBuilder<Member> members = Member.QUERY.query();
        assertEquals(1, members.whereRaw("age > 1").orderBy("id").latest().oldest().get().size());
        assertEquals(15, Member.QUERY.query().paginate().getPerPage());
        assertEquals(15, Member.QUERY.paginate().getPerPage());

        Timestamp born = Timestamp.valueOf(LocalDateTime.of(1990, 12, 10, 9, 30, 0));
        DB.table("users").where("name", "Ada").update(Map.of("born_at", born));
        assertTrue(users.copy().whereDate("born_at", Date.valueOf(LocalDate.of(1990, 12, 10))).toSql().contains("cast("));
        assertEquals(1L, users.copy().whereDate("born_at", Date.valueOf(LocalDate.of(1990, 12, 10))).count());
        assertEquals(1L, users.copy().whereYear("born_at", 1990).orWhereMonth("born_at", 12).count());
        assertEquals(1L, users.copy().whereTime("born_at", Time.valueOf(LocalTime.of(9, 30, 0))).count());
        assertTrue(users.copy().select("name").selectRaw("age as years").groupBy("name", "age")
                .having("age", ">", 0).orHaving("age", "=", 36)
                .havingRaw("count(*) >= ?", List.of(1)).orHavingRaw("count(*) = ?", List.of(1))
                .toSql().contains("having"));
        assertTrue(users.copy().groupBy("name").havingRaw("count(*) >= 1").orHavingRaw("count(*) >= 1").toSql().contains("having"));
        assertEquals(1L, Member.QUERY.query().whereDate("born_at", Date.valueOf(LocalDate.of(1990, 12, 10))).count());
        assertEquals(1L, Member.QUERY.query().where("name", "x").orWhereRaw("age = ?", List.of(36)).count());
        assertEquals(1L, Member.QUERY.query().where("name", "x").orWhereRaw("age > 0").count());
        assertTrue(Member.QUERY.query().addSelect("name").selectRaw("age as years").distinct()
                .groupBy("name").havingRaw("count(*) >= ?", List.of(1)).orHavingRaw("count(*) >= 1")
                .havingRaw("count(*) >= 1").toSql().contains("distinct"));

        class MemberObserver extends ModelObserver<Member> {
            boolean created;

            @Override
            public void created(Member model) {
                created = true;
            }
        }
        MemberObserver observer = new MemberObserver();
        Extract.observe(Member.class, observer);
        try {
            Member obs = Member.QUERY.create(Map.of("name", "Obs", "age", 20));
            assertTrue(observer.created);
            Member.QUERY.observe(new MemberObserver());
            obs.delete();
        } finally {
            ModelDispatcher.flush();
        }

        class AdultsOnly implements Scope {
            @Override
            public void apply(ModelBuilder<?> builder) {
                builder.where("age", ">=", 18);
            }
        }
        Extract.addGlobalScope(Member.class, "named", b -> b.where("name", "Ada"));
        Extract.addGlobalScope(Member.class, new AdultsOnly());
        try {
            assertTrue(Member.QUERY.hasGlobalScope("named"));
            assertNotNull(Member.QUERY.getGlobalScope("named"));
            assertTrue(Member.QUERY.getGlobalScopes().containsKey(AdultsOnly.class.getName()));
            assertEquals(1L, Member.QUERY.query().count());
            DB.table("users").insert(Map.of("name", "Kid", "age", 10));
            assertEquals(1L, Member.QUERY.query().count());
            assertEquals(2L, Member.QUERY.withoutGlobalScopes().count());
            assertEquals(1L, Member.QUERY.withoutGlobalScopes(List.of(AdultsOnly.class.getName())).count());
            assertEquals(1L, Member.QUERY.withoutGlobalScopesExcept(List.of("named")).count());
            Member.QUERY.removeGlobalScope("named");
            assertFalse(Member.QUERY.hasGlobalScope("named"));
        } finally {
            Member.QUERY.removeGlobalScope("named");
            Member.QUERY.removeGlobalScope(AdultsOnly.class.getName());
        }
    }

    @Test
    void modelsFromJava() {
        Member ada = Member.QUERY.create(Map.of("name", "Ada", "age", "36", "settings", Map.of("k", "v")));
        assertTrue(ada.exists());
        Member found = Member.QUERY.findOrFail(ada.getKey());
        assertEquals(36, found.get("age"));
        assertEquals(Map.of("k", "v"), found.get("settings"));
        String name = found.attribute("name");
        assertEquals("Ada", name);

        found.set("age", 40);
        assertTrue(found.isDirty("age"));
        assertTrue(found.save());
        List<Member> adults = Member.QUERY.query().where("age", ">", 30).orderBy("id").get();
        assertEquals(1, adults.size());
        Page<Member> page = Member.QUERY.paginate(10, 1);
        assertEquals(15, DB.table("users").simplePaginate().getPerPage());
        assertEquals(2, DB.table("users").simplePaginate(2).getPerPage());
        assertEquals(15, DB.table("users").cursorPaginate().getPerPage());
        assertEquals(2, DB.table("users").cursorPaginate(2).getPerPage());
        assertEquals(2, DB.table("users").cursorPaginate(2, null).getPerPage());
        assertEquals(1, Member.QUERY.simplePaginate().getData().size());
        assertEquals(1, Member.QUERY.simplePaginate(2).getData().size());
        assertEquals(15, Member.QUERY.query().cursorPaginate().getPerPage());
        assertEquals(1, Member.QUERY.query().cursorPaginate(2).getData().size());
        assertEquals(1, Member.QUERY.cursorPaginate(2, null, "id").getData().size());
        assertEquals("1", CursorPage.decode(CursorPage.encode(1)));
        assertEquals("null", CursorPage.decode(CursorPage.encode(null)));
        Relation.morphMap(Map.of());
        Relation.clearMorphMap();
        assertEquals(1, page.getData().size());
        assertNull(Member.QUERY.find(99));
        assertThrows(ModelNotFoundException.class, () -> Member.QUERY.findOrFail(99));
        assertThrows(MassAssignmentException.class, () -> Extract.of(Locked.class).create(Map.of("name", "x")));

        ExtractCompanion<Article> articles = Extract.of(Article.class);
        Article article = articles.create(Map.of("title", "Java"));
        assertEquals("deleted_at", article.getDeletedAtColumn());
        assertTrue(article.delete());
        assertTrue(article.trashed());
        assertEquals(0L, articles.count());
        assertEquals(1L, articles.withTrashed().count());
        assertTrue(article.restore());
        assertFalse(article.trashed());
        assertTrue(article.forceDelete());
        assertEquals(0L, articles.withTrashed().count());

        ExtractCollection<Member> fromOf = ExtractCollection.of(List.of(ada));
        assertEquals(1, fromOf.count());
        ExtractCollection<Member> fromMake = ExtractCollection.make(ada);
        assertEquals(1, fromMake.count());
    }

    @Test
    void firstOrCreateAndWhereHasFromJava() {
        Member ada = Member.QUERY.firstOrCreate(Map.of("email", "ada@j.io"), Map.of("name", "Ada", "age", 36));
        assertTrue(ada.exists());
        Member again = Member.QUERY.firstOrCreate(Map.of("email", "ada@j.io"), Map.of("name", "Nope"));
        assertEquals(ada.getKey(), again.getKey());
        Member oneArg = Member.QUERY.firstOrCreate(Map.of("email", "one@j.io", "name", "OneArg", "age", 21));
        assertEquals("OneArg", oneArg.get("name"));
        Member neu = Member.QUERY.firstOrNew(Map.of("email", "neu@j.io", "name", "Neu", "age", 22));
        assertFalse(neu.exists());
        Member updated = Member.QUERY.updateOrCreate(Map.of("email", "up@j.io"), Map.of("name", "Up", "age", 23));
        assertTrue(updated.exists());
        Member updateOne = Member.QUERY.updateOrCreate(Map.of("email", "up2@j.io", "name", "Up2", "age", 24));
        assertEquals("Up2", updateOne.get("name"));

        Extract.of(Article.class).create(Map.of("user_id", ada.getKey(), "title", "One"));
        Extract.of(Article.class).create(Map.of("user_id", ada.getKey(), "title", "Two"));
        Member lonely = Member.QUERY.create(Map.of("name", "Lonely", "email", "l@j.io", "age", 20));

        List<Member> withPosts = Member.QUERY.whereHas("posts").orderBy("id").get();
        assertEquals(1, withPosts.size());
        assertEquals("Ada", withPosts.get(0).get("name"));
        assertEquals(1, Member.QUERY.orWhereHas("posts").where("email", "ada@j.io").get().size());
        assertEquals(1, Member.QUERY.whereDoesntHave("posts").where("email", "l@j.io").get().size());
        assertTrue(Member.QUERY.orWhereDoesntHave("posts").count() >= 1);

        List<Member> counted = Member.QUERY.withCount("posts").orderBy("id").get();
        assertEquals(2L, ((Number) counted.get(0).get("posts_count")).longValue());
        assertEquals(0L, ((Number) counted.stream().filter(m -> m.getKey().equals(lonely.getKey())).findFirst().orElseThrow().get("posts_count")).longValue());

        assertTrue(DB.table("users").updateOrInsert(Map.of("email", "uo@j.io", "name", "UO", "age", 25)));
        assertTrue(Member.QUERY.query().updateOrInsert(Map.of("email", "uo2@j.io", "name", "UO2", "age", 26)));
        assertTrue(Member.QUERY.query().firstOrCreate(Map.of("email", "mb@j.io", "name", "MB", "age", 27)).exists());
        assertTrue(Member.QUERY.query().updateOrCreate(Map.of("email", "mb2@j.io", "name", "MB2", "age", 28)).exists());

        Member copy = ada.replicate();
        assertFalse(copy.exists());
        assertNull(copy.getKey());

        Member foundOrNew = Member.QUERY.findOrNew(ada.getKey());
        assertTrue(foundOrNew.exists());
        Member missing = Member.QUERY.findOrNew(9_999L);
        assertFalse(missing.exists());
        assertEquals(9_999L, ((Number) missing.getKey()).longValue());
        assertTrue(ada.isClean("age"));
        assertEquals(1, ada.increment("age"));
        assertEquals(37, ada.get("age"));
        assertEquals(1, ada.decrement("age"));
        assertEquals(36, ada.get("age"));
        assertTrue(Member.QUERY.query().tap(q -> q.where("email", "ada@j.io")).lockForUpdate().toSql().contains(" for update"));
        assertTrue(Member.QUERY.query().orderBy("id").lazy(10).iterator().hasNext());
        assertTrue(DB.table("users").orderBy("id").cursor().iterator().hasNext());
        assertTrue(DB.table("users").sharedLock().toSql().contains(" for share"));
        Grammar.registerUrlPrefix("jdbc:javaexample:", Grammar.NONE);
        assertEquals(Grammar.NONE, Grammar.forUrl("jdbc:javaexample://x"));
        Grammar.clearRegisteredUrlPrefixes();
        SchemaGrammar.registerProduct("JavaExample", ids -> new SchemaGrammar(ids));
        assertEquals("\"t\"", SchemaGrammar.forProduct("JavaExample").wrap("t"));
        SchemaGrammar.clearRegisteredProducts();
    }

    @Test
    void relationFirstOrCreateAndModelTruncateFromJava() {
        Member ada = Member.QUERY.create(Map.of("name", "Ada", "email", "rel@j.io", "age", 36));
        Article created = ada.posts().firstOrCreate(Map.of("title", "Hello"));
        assertTrue(created.exists());
        assertEquals(ada.getKey(), created.get("user_id"));
        assertEquals(created.getKey(), ada.posts().firstOrCreate(Map.of("title", "Hello"), Map.of("title", "Nope")).getKey());

        Article updated = ada.posts().updateOrCreate(Map.of("title", "Hello"), Map.of("title", "Hello2"));
        assertEquals(created.getKey(), updated.getKey());
        assertEquals("Hello2", updated.get("title"));

        Article brand = ada.posts().updateOrCreate(Map.of("title", "Brand"));
        assertTrue(brand.exists());
        assertEquals(ada.getKey(), brand.get("user_id"));
        assertEquals("Brand", brand.get("title"));

        Article neu = ada.posts().firstOrNew(Map.of("title", "Draft"));
        assertFalse(neu.exists());
        assertEquals(ada.getKey(), neu.get("user_id"));

        assertEquals(1L, Member.QUERY.count());
        Member.QUERY.truncate();
        assertEquals(0L, Member.QUERY.count());
    }
}
