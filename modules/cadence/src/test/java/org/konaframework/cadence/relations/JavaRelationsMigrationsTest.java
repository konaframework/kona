package org.konaframework.cadence.relations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.konaframework.core.Container;
import org.konaframework.cadence.ConnectionConfig;
import org.konaframework.cadence.Grammar;
import org.konaframework.cadence.Database;
import org.konaframework.cadence.Cadence;
import org.konaframework.cadence.CadenceCompanion;
import org.konaframework.cadence.factory.Factory;
import org.konaframework.cadence.migrations.Migration;
import org.konaframework.cadence.migrations.MigrationName;
import org.konaframework.cadence.migrations.MigrationOrder;
import org.konaframework.cadence.migrations.MigrationEvents;
import org.konaframework.cadence.migrations.MigrationRegistry;
import org.konaframework.cadence.migrations.Migrator;
import org.konaframework.cadence.schema.H2SchemaGrammar;
import org.konaframework.cadence.schema.Schema;
import org.konaframework.cadence.schema.SchemaGrammar;
import org.konaframework.cadence.seeding.Seeder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Java-callable relations, schema builder, migrator, factories, and seeders (Law 8). */
class JavaRelationsMigrationsTest {
    public static final class Team extends Cadence<Team> {
        public static final CadenceCompanion<Team> QUERY = Cadence.of(Team.class);

        @Override
        public Set<String> getFillable() {
            return Set.of("name");
        }

        public HasMany<Player> players() {
            return hasMany(Player.class, "team_id");
        }

        public BelongsToMany<Tag> tags() {
            return belongsToMany(Tag.class, "tag_team");
        }
    }

    public static final class Player extends Cadence<Player> {
        @Override
        public Set<String> getFillable() {
            return Set.of("name");
        }

        public BelongsTo<Team> team() {
            return belongsTo(Team.class);
        }
    }

    public static final class Tag extends Cadence<Tag> {
        @Override
        public boolean getTimestamps() {
            return false;
        }
    }

    public static final class CreateTeams implements Migration {
        @Override
        public void up(Schema schema) {
            schema.create("teams", table -> {
                table.id();
                table.string("name").unique();
                table.timestamps();
            });
            schema.create("players", table -> {
                table.id();
                table.foreignId("team_id").constrained().cascadeOnDelete();
                table.string("name");
                table.timestamps();
            });
            schema.create("tags", table -> {
                table.id();
                table.string("label", 40);
                table.bool("pinned").withDefault(false);
                table.text("notes").nullable();
                table.timestamp("seen_at").useCurrent();
            });
            schema.create("tag_team", table -> {
                table.foreignId("team_id").constrained();
                table.foreignId("tag_id").constrained();
                table.unique("team_id", "tag_id");
            });
        }

        @Override
        public void down(Schema schema) {
            schema.dropIfExists("tag_team");
            schema.dropIfExists("tags");
            schema.dropIfExists("players");
            schema.dropIfExists("teams");
        }
    }

    public static final class TeamSeeder extends Seeder {
        @Override
        public void run() {
            Factory<Team> teams = Factory.of(Team.class, () -> Map.of("name", "Team " + System.nanoTime()));
            Team team = teams.create(Map.of("name", "Lions"));
            team.players().createMany(List.of(Map.of("name", "Ann"), Map.of("name", "Ben")));
        }
    }

    public static final class RootSeeder extends Seeder {
        @Override
        public void run() {
            call(TeamSeeder.class);
        }
    }

    private Database database;

    @AfterEach
    void tearDown() {
        database.close();
        Database.clearGlobal();
    }

    @Test
    void migrateSeedAndQueryRelationsFromJava() {
        database = new Database()
                .addConnection("default", new ConnectionConfig("jdbc:h2:mem:java_rel;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"))
                .setAsGlobal();
        MigrationRegistry registry = new MigrationRegistry(new CreateTeams());

        List<String> ran = Migrator.using(database.connection(), registry, Migrator::migrate);
        assertEquals(List.of("CreateTeams"), ran);
        boolean ran0 = Migrator.using(database.connection(), registry, migrator -> migrator.status().get(0).getRan());
        assertTrue(ran0);

        RootSeeder root = new RootSeeder();
        root.run();
        assertEquals(List.of("TeamSeeder"), root.getCalled());

        Team lions = Team.QUERY.with("players").first();
        @SuppressWarnings("unchecked")
        List<Player> players = (List<Player>) lions.getRelation("players");
        assertEquals(2, players.size());
        assertEquals("Lions", players.get(0).team().first().getAttribute("name"));
        assertEquals(2L, lions.players().count());

        Tag tag = new Tag().forceFill(Map.of("label", "east"));
        tag.save();
        lions.tags().attach(tag);
        assertEquals("east", lions.tags().get().get(0).getAttribute("label"));
        assertEquals(false, lions.tags().get().get(0).getAttribute("pinned"));
        assertEquals(new SyncResult(List.of(), List.of(tag.getKey())), lions.tags().sync(List.of()));

        assertEquals(List.of("CreateTeams"), Migrator.using(database.connection(), registry, "extract_migrations", Migrator::rollback));
        boolean hasTeams = Migrator.using(database.connection(), registry, migrator -> migrator.getSchema().hasTable("teams"));
        assertFalse(hasTeams);
    }

    @Test
    void javaOverloadsAndStatics() throws Exception {
        database = new Database()
                .addConnection("default", new ConnectionConfig("jdbc:h2:mem:java_overloads;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"))
                .setAsGlobal();
        try (Connection jdbc = DriverManager.getConnection("jdbc:h2:mem:java_overloads;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")) {
            Schema schema = new Schema(jdbc);
            assertSame(SchemaGrammar.H2, schema.getGrammar());
            assertSame(SchemaGrammar.POSTGRES, SchemaGrammar.forProduct("PostgreSQL"));
            assertSame(SchemaGrammar.H2, SchemaGrammar.Companion.forProduct("H2"));
            assertInstanceOf(H2SchemaGrammar.class, SchemaGrammar.forProduct("H2", Grammar.NONE));

            Migrator migrator = new Migrator(jdbc, new MigrationRegistry(new CreateTeams()), SchemaGrammar.H2);
            java.util.ArrayList<String> started = new java.util.ArrayList<>();
            MigrationEvents events = new MigrationEvents() {
                @Override
                public void starting(String name) {
                    started.add(name);
                }

                @Override
                public void finished(String name, long millis) {
                    started.add(name + ":" + millis);
                }
            };
            assertEquals(List.of("CreateTeams"), migrator.migrate(events));
            assertEquals("CreateTeams", started.get(0));
            assertTrue(started.get(1).startsWith("CreateTeams:"));
            assertEquals(List.of(), migrator.migrate(new MigrationEvents() {}));
            assertEquals(List.of("CreateTeams"), migrator.rollback(1, events));
            assertEquals(List.of("CreateTeams"), migrator.migrate(new MigrationEvents() {}));
            assertEquals(List.of("CreateTeams"), migrator.reset(new MigrationEvents() {}));
            assertEquals(List.of("CreateTeams"), migrator.migrate());
        }

        Factory<Team> teams = Factory.of(Team.class, () -> Map.of("name", "Team"));
        assertEquals(Map.of("name", "Team"), teams.raw());
        assertFalse(teams.make().exists());
        Team created = teams.create();
        assertTrue(created.exists());

        Tag tag = new Tag().forceFill(Map.of("label", "west"));
        tag.save();
        created.tags().attach(List.of(tag.getKey()));
        assertEquals(1, created.tags().get().size());

        assertNotNull(Seeder.instantiate(TeamSeeder.class, new Container()));
        assertEquals("tag_team", Relation.snake("TagTeam"));
        assertEquals(7L, Relation.normalizeKey(7));
        assertEquals("taggables", created.morphToMany(Tag.class, "taggable").getPivotTable());
        assertEquals("taggables", tag.morphedByMany(Team.class, "taggable", "taggables").getPivotTable());

        assertEquals("2026_10_20_141530", MigrationOrder.format(Instant.parse("2026-10-20T14:15:30Z")));
        assertEquals("M2026_10_20_110000_A", MigrationOrder.className("2026_10_20_110000", "A"));
        assertEquals(
            "2026_10_20_153950_add_status_to_users_table",
            MigrationOrder.fileStem("2026_10_20_153950", "add_status_to_users_table")
        );
        assertEquals("2000_01_01_000000", MigrationOrder.DEFAULT_USERS_TIMESTAMP);
        assertEquals(
            List.of("CreateUsersTable", "M2026_10_20_110000_A", "M2026_10_20_120000_B"),
            MigrationOrder.sort(List.of("M2026_10_20_120000_B", "CreateUsersTable", "M2026_10_20_110000_A"))
        );
        assertEquals(
            List.of("2000_01_01_000000_create_users_table", "2026_10_20_153950_add_status_to_users_table"),
            MigrationOrder.sort(List.of(
                "2026_10_20_153950_add_status_to_users_table",
                "2000_01_01_000000_create_users_table"
            ))
        );
        assertEquals("2026_10_20_110000", MigrationOrder.timestampOf("database.migrations.M2026_10_20_110000_A"));
        assertEquals(
            "2026_10_20_153950",
            MigrationOrder.timestampOf("2026_10_20_153950_add_status_to_users_table")
        );
        assertEquals(Instant.parse("2026-10-20T14:15:30Z"), MigrationOrder.parse("2026_10_20_141530"));
        assertEquals(
            "2026_10_20_090000_package_private",
            MigrationOrder.recordedName(new JavaNamedMigration())
        );
    }
}

@MigrationName("2026_10_20_090000_package_private")
class JavaNamedMigration implements Migration {
    @Override
    public void up(Schema schema) {}

    @Override
    public void down(Schema schema) {}
}
