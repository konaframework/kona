package hello

import dev.kona.extract.ConnectionConfig
import dev.kona.extract.Database
import dev.kona.extract.Extract
import dev.kona.extract.ExtractCompanion
import dev.kona.extract.factory.Factory
import dev.kona.extract.migrations.Migration
import dev.kona.extract.migrations.MigrationRegistry
import dev.kona.extract.migrations.Migrator
import dev.kona.extract.relations.BelongsTo
import dev.kona.extract.relations.HasMany
import dev.kona.extract.schema.Schema

class Notebook : Extract<Notebook>() {
    override val fillable = setOf("title")

    fun notes(): HasMany<Note> = hasMany(Note::class.java)

    companion object : ExtractCompanion<Notebook>(Notebook::class.java)
}

class Note : Extract<Note>() {
    override val fillable = setOf("body")

    fun notebook(): BelongsTo<Notebook> = belongsTo(Notebook::class.java)

    companion object : ExtractCompanion<Note>(Note::class.java)
}

class CreateNotebooksAndNotes : Migration {
    override fun up(schema: Schema) {
        schema.create("notebooks") {
            id()
            string("title")
            timestamps()
        }
        schema.create("notes") {
            id()
            foreignId("notebook_id").constrained().cascadeOnDelete()
            text("body")
            timestamps()
        }
    }

    override fun down(schema: Schema) {
        schema.dropIfExists("notes")
        schema.dropIfExists("notebooks")
    }
}

class NotebookFactory : Factory<Notebook>(Notebook::class.java) {
    override fun definition(): Map<String, Any?> = mapOf("title" to "Notebook ${index + 1}")
}

/** Extract end to end on an in-memory H2 database: migrate, seed with a factory, query with relations. */
object NotesDemo {
    @JvmStatic
    fun run(url: String = "jdbc:h2:mem:hello_notes;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"): List<String> {
        Database().addConnection("default", ConnectionConfig(url)).setAsGlobal().use { db ->
            val registry = MigrationRegistry(CreateNotebooksAndNotes())
            val migrated = Migrator.using(db.connection(), registry) { it.migrate() }

            NotebookFactory().count(2).createMany().forEachIndexed { i, notebook ->
                repeat(i + 1) { n -> notebook.notes().create(mapOf("body" to "note ${n + 1}")) }
            }

            val notebooks = Notebook.with("notes").orderBy("id").get()
            val first = Note.query().orderBy("id").first()!!
            val lines = listOf("migrated $migrated") +
                notebooks.map { "${it["title"]}: ${it.getRelationValue<List<Note>>("notes")!!.size} notes" } +
                "first note belongs to ${first.notebook().first()!!["title"]}"

            Migrator.using(db.connection(), registry) { it.reset() }
            return lines
        }
    }
}
