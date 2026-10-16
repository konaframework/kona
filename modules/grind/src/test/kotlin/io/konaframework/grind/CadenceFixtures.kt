package io.konaframework.grind

import io.konaframework.core.Application
import io.konaframework.core.ConfigFile
import io.konaframework.core.Env
import io.konaframework.core.Kona
import io.konaframework.core.ServiceProvider
import io.konaframework.cadence.DB
import io.konaframework.cadence.CadenceServiceProvider
import io.konaframework.cadence.migrations.Migration
import io.konaframework.cadence.migrations.MigrationRegistry
import io.konaframework.cadence.schema.Schema
import io.konaframework.cadence.seeding.Seeder

class CreateWidgetsTable : Migration {
    override fun up(schema: Schema) {
        schema.create("widgets") {
            id()
            string("name")
        }
    }

    override fun down(schema: Schema) {
        schema.dropIfExists("widgets")
    }
}

class AddColorToWidgetsTable : Migration {
    override fun up(schema: Schema) {
        schema.table("widgets") { string("color").nullable() }
    }

    override fun down(schema: Schema) {
        schema.table("widgets") { dropColumn("color") }
    }
}

class MigrationsProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.instance(MigrationRegistry::class.java, MigrationRegistry(CreateWidgetsTable(), AddColorToWidgetsTable()))
    }

    override fun boot(app: Application) = Unit
}

internal fun fixtureDatabaseConfig(): ConfigFile =
    ConfigFile.of("database") {
        val name = Env.getOrDefault("GRIND_DB", "grind_config")
        mapOf(
            "default" to "main",
            "connections" to mapOf(
                "main" to mapOf(
                    "url" to "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                ),
                "other" to mapOf(
                    "url" to "jdbc:h2:mem:${name}_other;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                ),
            ),
        )
    }

/** Database bound by [CadenceServiceProvider]; migrations registered. */
object CadenceFixtureApp {
    @JvmStatic
    fun create(args: Array<String>): Application =
        Kona.create(args)
            .configs(fixtureDatabaseConfig())
            .providers(CadenceServiceProvider(), MigrationsProvider())
            .boot()
}

/** Migrations registered, but no Database binding: grind builds one from config. */
object ConfigOnlyFixtureApp {
    @JvmStatic
    fun create(args: Array<String>): Application =
        Kona.create(args)
            .configs(fixtureDatabaseConfig())
            .providers(MigrationsProvider())
            .boot()
}

/** Empty registry bound as a singleton factory. */
object EmptyRegistryApp {
    @JvmStatic
    fun create(args: Array<String>): Application {
        val app = Kona.create(args)
            .configs(fixtureDatabaseConfig())
            .providers(CadenceServiceProvider())
            .boot()
        app.container.singleton(MigrationRegistry::class.java) { MigrationRegistry() }
        return app
    }
}

class WidgetSeeder : Seeder() {
    override fun run() {
        DB.table("widgets").insert(mapOf("name" to "seeded"))
    }
}

class FixtureDatabaseSeeder : Seeder() {
    override fun run() {
        call(WidgetSeeder::class.java)
    }
}

class NotASeeder

internal fun databaseProject(dir: java.nio.file.Path, name: String): java.nio.file.Path =
    fixtureProject(dir, "GRIND_DB=$name\n")
