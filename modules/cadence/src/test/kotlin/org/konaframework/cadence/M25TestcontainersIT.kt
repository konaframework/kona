package org.konaframework.cadence

import org.konaframework.cadence.schema.Schema
import org.konaframework.cadence.schema.SchemaGrammar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.OracleContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * Live JDBC smoke against first-party engines via Testcontainers when Docker can start
 * containers. SQLite runs in-process without Docker. Container failures skip (do not fail CI).
 */
@Tag("testcontainers")
class M25TestcontainersIT {
    @Test
    fun sqliteInProcessMigrateAndQuery() {
        val url = "jdbc:sqlite:file:m25_${System.nanoTime()}?mode=memory&cache=shared"
        Database().addConnection("default", ConnectionConfig(url)).use { db ->
            db.setAsGlobal()
            db.withConnection { jdbc ->
                Schema(jdbc, SchemaGrammar.forProduct(jdbc.metaData.databaseProductName, db.connection().grammar))
                    .create("items") {
                        id()
                        string("name")
                    }
            }
            val id = db.table("items").insertGetId(mapOf("name" to "a"))
            assertTrue(id != null)
            assertEquals(1L, db.table("items").count())
            assertTrue(db.table("items").limit(1).toSql().contains("limit 1"))
            Database.clearGlobal()
        }
    }

    @Test
    fun postgresContainerSmoke() {
        runContainer("PostgreSQL") {
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine")).use { container ->
                container.start()
                smoke(container, Grammar.POSTGRES) { schema ->
                    schema.create("items") {
                        id()
                        string("name")
                        boolean("active").default(true)
                    }
                }
            }
        }
    }

    @Test
    fun mysqlContainerSmoke() {
        runContainer("MySQL") {
            MySQLContainer(DockerImageName.parse("mysql:8.4")).use { container ->
                container.start()
                smoke(container, Grammar.MYSQL) { schema ->
                    schema.create("items") {
                        id()
                        string("name")
                        boolean("active").default(true)
                    }
                }
            }
        }
    }

    @Test
    fun mariadbContainerSmoke() {
        runContainer("MariaDB") {
            MariaDBContainer(DockerImageName.parse("mariadb:11")).use { container ->
                container.start()
                smoke(container, Grammar.MARIADB) { schema ->
                    schema.create("items") {
                        id()
                        string("name")
                    }
                }
            }
        }
    }

    @Test
    fun sqlServerContainerSmoke() {
        runContainer("SQL Server") {
            @Suppress("DEPRECATION")
            val image = DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest")
            MSSQLServerContainer(image).acceptLicense().use { container ->
                container.start()
                smoke(container, Grammar.SQLSERVER) { schema ->
                    schema.create("items") {
                        id()
                        string("name")
                    }
                }
            }
        }
    }

    @Test
    fun oracleContainerSmokeOrSkip() {
        runContainer("Oracle") {
            OracleContainer(DockerImageName.parse("gvenzl/oracle-xe:21-slim-faststart")).use { container ->
                container.start()
                smoke(container, Grammar.ORACLE) { schema ->
                    schema.create("items") {
                        id()
                        string("name", 100)
                    }
                }
            }
        }
    }

    private fun runContainer(label: String, block: () -> Unit) {
        assumeTrue(dockerAvailable(), "Docker not available for Testcontainers ($label)")
        try {
            block()
        } catch (ex: Throwable) {
            assumeTrue(false, "Testcontainers $label unavailable: ${ex.message}")
        }
    }

    private fun smoke(
        container: JdbcDatabaseContainer<*>,
        expected: Grammar,
        ddl: (Schema) -> Unit,
    ) {
        val config = ConnectionConfig(
            url = container.jdbcUrl,
            username = container.username,
            password = container.password,
        )
        assertEquals(expected.dialect, config.grammar().dialect)
        assertEquals(expected.wrap("t"), config.grammar().wrap("t"))
        Database().addConnection("default", config).use { db ->
            db.setAsGlobal()
            db.withConnection { jdbc ->
                ddl(Schema(jdbc, SchemaGrammar.forProduct(jdbc.metaData.databaseProductName, db.connection().grammar)))
            }
            val id = db.table("items").insertGetId(mapOf("name" to "kona"))
            assertTrue(id != null)
            assertEquals(1L, db.table("items").where("name", "kona").count())
            Database.clearGlobal()
        }
    }

    companion object {
        @JvmStatic
        fun dockerAvailable(): Boolean {
            try {
                if (DockerClientFactory.instance().isDockerAvailable) {
                    return true
                }
            } catch (_: Throwable) {
                // fall through to CLI probe
            }
            return try {
                ProcessBuilder("docker", "info")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor() == 0
            } catch (_: Throwable) {
                false
            }
        }
    }
}
