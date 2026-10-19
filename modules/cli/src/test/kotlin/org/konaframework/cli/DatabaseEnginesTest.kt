package org.konaframework.cli

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DatabaseEnginesTest {
    @TempDir
    lateinit var temp: Path

    @Test
    fun resolvesAliasesAndRejectsUnknown() {
        assertEquals("pgsql", DatabaseEngines.resolve("postgres").name)
        assertEquals("pgsql", DatabaseEngines.resolve("postgresql").name)
        assertEquals("pgsql", DatabaseEngines.resolve("pg").name)
        assertEquals("sqlsrv", DatabaseEngines.resolve("sqlserver").name)
        assertEquals("sqlsrv", DatabaseEngines.resolve("mssql").name)
        assertEquals("mariadb", DatabaseEngines.resolve("maria").name)
        assertEquals("mysql", DatabaseEngines.resolve("mysql").name)
        assertEquals("oracle", DatabaseEngines.resolve("oracle").name)
        assertEquals("sqlite", DatabaseEngines.resolve("sqlite").name)
        assertThrows<KonaCliException> { DatabaseEngines.resolve("mongo") }
        assertThrows<KonaCliException> { DatabaseEngines.require("mongo") }
        assertTrue(DatabaseEngines.names().containsAll(listOf("h2", "sqlite", "mysql", "mariadb", "pgsql", "sqlsrv", "oracle")))
    }

    @Test
    fun konaNewDatabaseFlagAddsDriverAndConnection() {
        val app = temp.resolve("pg-app")
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=kotlin", "--kit=api", "--database=pgsql")))
        val env = Files.readString(app.resolve(".env"))
        assertTrue(env.contains("DB_CONNECTION=pgsql"))
        assertTrue(env.contains("jdbc:postgresql://"))
        val gradle = Files.readString(app.resolve("build.gradle.kts"))
        assertTrue(gradle.contains("com.h2database:h2"))
        assertTrue(gradle.contains("org.postgresql:postgresql"))
        val database = Files.readString(app.resolve("src/main/kotlin/config/Database.kt"))
        assertTrue(database.contains("\"pgsql\""))
        assertTrue(database.contains("DB_CONNECTION"))
        assertTrue(database.contains("\"default\" to Env.getOrDefault(\"DB_CONNECTION\", \"pgsql\")"))
    }

    @Test
    fun defaultDatabaseKeepsMainConnection() {
        val app = temp.resolve("h2-app")
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=java", "--kit=api")))
        val database = Files.readString(app.resolve("src/main/java/config/Database.java"))
        assertTrue(database.contains("DB_CONNECTION\", \"main\""))
        assertTrue(database.contains("\"sqlite\""))
        assertTrue(database.contains("\"oracle\""))
        val gradle = Files.readString(app.resolve("build.gradle.kts"))
        assertTrue(gradle.contains("com.h2database:h2"))
        assertTrue(!gradle.contains("org.postgresql:postgresql"))
    }

    @Test
    fun explicitH2DatabaseFlag() {
        val app = temp.resolve("h2-flag")
        assertEquals(0, NewCommand().run(listOf(app.toString(), "--lang=kotlin", "--kit=api", "--database=h2")))
        assertTrue(Files.readString(app.resolve(".env")).contains("DB_CONNECTION=main"))
        assertTrue(!Files.readString(app.resolve("build.gradle.kts")).contains("org.postgresql:postgresql"))
    }

    @Test
    fun applyDatabaseSelectionCoversEnvAndGradleBranches() {
        val cmd = NewCommand()
        val engine = DatabaseEngines.require("mysql")

        val missingEnv = temp.resolve("no-env")
        Files.createDirectories(missingEnv)
        Files.writeString(missingEnv.resolve("build.gradle.kts"), "dependencies {\n}\n")
        cmd.applyDatabaseSelection(missingEnv, engine, "mysql")
        assertTrue(Files.readString(missingEnv.resolve("build.gradle.kts")).contains("mysql-connector-j"))

        val withEnv = temp.resolve("with-env")
        Files.createDirectories(withEnv)
        Files.writeString(withEnv.resolve(".env"), "APP_NAME=x\n")
        Files.writeString(withEnv.resolve(".env.example"), "APP_NAME=x\nDB_URL=jdbc:old\n")
        Files.writeString(
            withEnv.resolve("build.gradle.kts"),
            "dependencies {\n    runtimeOnly(\"com.h2database:h2:2.3.232\")\n}\n",
        )
        cmd.applyDatabaseSelection(withEnv, engine, "mysql")
        assertTrue(Files.readString(withEnv.resolve(".env")).contains("DB_CONNECTION=mysql"))
        assertTrue(Files.readString(withEnv.resolve(".env.example")).contains("jdbc:mysql://"))
        val gradle = Files.readString(withEnv.resolve("build.gradle.kts"))
        assertTrue(gradle.contains("mysql-connector-j"))
        // Second apply: dependency already present → early return.
        cmd.applyDatabaseSelection(withEnv, engine, "mysql")
        assertEquals(1, Regex("mysql-connector-j").findAll(Files.readString(withEnv.resolve("build.gradle.kts"))).count())

        val noBuild = temp.resolve("no-build")
        Files.createDirectories(noBuild)
        Files.writeString(noBuild.resolve(".env"), "DB_CONNECTION=h2\nDB_URL=jdbc:h2:mem:x\n")
        cmd.applyDatabaseSelection(noBuild, engine, "mysql")
        assertTrue(Files.readString(noBuild.resolve(".env")).contains("DB_CONNECTION=mysql"))
        assertTrue(Files.readString(noBuild.resolve(".env")).contains("jdbc:mysql://"))
    }
}
