package io.konaframework.cadence

import java.util.concurrent.atomic.AtomicInteger

object TestDb {
    private val counter = AtomicInteger()

    fun url(): String = "jdbc:h2:mem:extract${counter.incrementAndGet()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"

    /** Fresh in-memory database with the shared schema, set as global. */
    fun create(): Database {
        val database = Database().addConnection("default", ConnectionConfig(url(), "sa", "", poolSize = 2))
        val connection = database.connection()
        SCHEMA.forEach { connection.statement(it) }
        return database.setAsGlobal()
    }

    val SCHEMA = listOf(
        """
        create table users (
            id bigint auto_increment primary key,
            name varchar(100), email varchar(100), age int, admin boolean,
            settings varchar(1000), born_at timestamp,
            created_at timestamp, updated_at timestamp
        )
        """,
        """
        create table posts (
            id bigint auto_increment primary key,
            user_id bigint, title varchar(100), votes int default 0,
            deleted_at timestamp, created_at timestamp, updated_at timestamp
        )
        """,
        "create table tags (code varchar(20) primary key, label varchar(50))",
        "create table archives (id bigint auto_increment primary key, name varchar(50), removed_at timestamp)",
        "create table plain (name varchar(20))",
        "create table files (id bigint auto_increment primary key, body clob, data blob, kind varchar(10), at timestamp)",
    )
}
