package dev.kona.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.kona.extract.relations.HasMany;
import java.sql.SQLException;
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
        public Map<String, CastType> getCasts() {
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
    }
}
