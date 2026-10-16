package io.konaframework.cadence.relations

import io.konaframework.cadence.Database
import io.konaframework.cadence.Cadence
import io.konaframework.cadence.CadenceCompanion
import io.konaframework.cadence.Grammar
import io.konaframework.cadence.SoftDeletes
import io.konaframework.cadence.schema.Schema
import org.h2.jdbcx.JdbcDataSource
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

class Author : Cadence<Author>() {
    override val table = "authors"

    fun profile(): HasOne<Profile> = hasOne(Profile::class.java)

    fun books(): HasMany<Book> = hasMany(Book::class.java)

    fun genres(): BelongsToMany<Genre> = belongsToMany(Genre::class.java).withPivot("weight").withTimestamps()

    fun notARelation(): String = "nope"

    fun broken(): HasMany<Book> = throw IllegalStateException("broken relation")

    fun dummy(): DummyRelation = DummyRelation(this, Book())

    companion object : CadenceCompanion<Author>(Author::class.java)
}

/** Unsupported relation shape used to cover whereHas / withCount rejection. */
class DummyRelation(
    parent: Cadence<*>,
    related: Book,
) : Relation<Book, List<Book>>(parent, related) {
    override fun getResults(): List<Book> = emptyList()

    override fun addConstraints(): Boolean = false

    override fun addEagerConstraints(models: List<Cadence<*>>) {}

    override fun match(models: List<Cadence<*>>, results: List<Book>, relation: String) {}
}

class Profile : Cadence<Profile>() {
    override val timestamps = false
    override val fillable = setOf("bio")

    fun author(): BelongsTo<Author> = belongsTo(Author::class.java)

    companion object : CadenceCompanion<Profile>(Profile::class.java)
}

class Book : Cadence<Book>(), SoftDeletes {
    override val fillable = setOf("title")

    fun author(): BelongsTo<Author> = belongsTo(Author::class.java)

    fun chapters(): HasMany<Chapter> = hasMany(Chapter::class.java).apply { orderBy("position") }

    companion object : CadenceCompanion<Book>(Book::class.java)
}

class Chapter : Cadence<Chapter>() {
    override val timestamps = false

    fun book(): BelongsTo<Book> = belongsTo(Book::class.java, "book_id", "id", "book")

    companion object : CadenceCompanion<Chapter>(Chapter::class.java)
}

class Genre : Cadence<Genre>() {
    override val timestamps = false

    fun authors(): BelongsToMany<Author> = belongsToMany(Author::class.java)

    companion object : CadenceCompanion<Genre>(Genre::class.java)
}

/** H2 database whose connections count prepared statements, to prove eager loads avoid N+1. */
object RelationDb {
    private val counter = AtomicInteger()
    val statements = AtomicInteger()

    fun create(): Database {
        val h2 = JdbcDataSource()
        h2.setURL("jdbc:h2:mem:relations${counter.incrementAndGet()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
        val database = Database().addConnection("default", counting(h2), Grammar.ANSI).setAsGlobal()
        database.withConnection { connection ->
            val schema = Schema(connection)
            schema.create("authors") {
                id()
                string("name")
                timestamps()
            }
            schema.create("profiles") {
                id()
                foreignId("author_id").nullable().constrained()
                string("bio")
            }
            schema.create("books") {
                id()
                foreignId("author_id").nullable()
                string("title")
                timestamps()
                softDeletes()
            }
            schema.create("chapters") {
                id()
                integer("book_id")
                integer("position")
                string("name")
            }
            schema.create("genres") {
                id()
                string("name")
            }
            schema.create("author_genre") {
                foreignId("author_id")
                foreignId("genre_id")
                integer("weight").nullable()
                timestamps()
            }
        }
        statements.set(0)
        return database
    }

    private fun counting(target: DataSource): DataSource = Proxy.newProxyInstance(
        DataSource::class.java.classLoader,
        arrayOf(DataSource::class.java),
    ) { _, method, args ->
        val result = method.invoke(target, *(args ?: emptyArray()))
        if (method.name == "getConnection") countingConnection(result as Connection) else result
    } as DataSource

    private fun countingConnection(target: Connection): Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, args ->
        if (method.name == "prepareStatement") {
            statements.incrementAndGet()
        }
        method.invoke(target, *(args ?: emptyArray()))
    } as Connection
}
