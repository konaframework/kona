package org.konaframework.firefly.tables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.ModelBuilder
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class RecordScopeTest {
    @Test
    fun scopeConstrainsQueriesAndWrapsCreates() {
        val url = "jdbc:h2:mem:rs${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
        val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
        database.connection().statement("create table rs_items (id bigint auto_increment primary key, name varchar(40), team varchar(20))")
        database.setAsGlobal()

        val stamped = AtomicInteger()
        val scope = object : RecordScope {
            override fun constrain(model: Class<*>, query: ModelBuilder<*>) {
                query.where("team", "acme")
            }

            override fun create(record: Cadence<*>, save: Runnable) {
                record.setAttribute("team", "acme")
                stamped.incrementAndGet()
                save.run()
            }
        }

        assertNull(RecordScopes.current())
        RecordScopes.use(scope, Supplier {
            assertEquals(scope, RecordScopes.current())
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(RsItem::class.java) as RecordStore<RsItem, ModelBuilder<RsItem>>
            store.save(store.fill(store.newRecord(), mapOf("name" to "A")))
            val first = store.query().first()!!
            store.save(first.fill(mapOf("name" to "A2"))) // update path skips create()
            assertEquals(1, stamped.get())
            assertEquals(1L, store.query().where("name", "A2").count())
            assertEquals("acme", first.getAttribute("team"))
            true
        })
        assertNull(RecordScopes.current())
        // Unscoped insert of a beta row, then scoped query must hide it.
        @Suppress("UNCHECKED_CAST")
        val open = RecordStores.open(RsItem::class.java) as RecordStore<RsItem, ModelBuilder<RsItem>>
        open.save(open.fill(open.newRecord(), mapOf("name" to "B", "team" to "beta")))
        RecordScopes.use(scope, Supplier {
            @Suppress("UNCHECKED_CAST")
            val store = RecordStores.open(RsItem::class.java) as RecordStore<RsItem, ModelBuilder<RsItem>>
            assertEquals(0L, store.query().where("name", "B").count())
            assertEquals(1L, store.query().where("name", "A2").count())
            true
        })
        RecordScopes.use(null, Supplier {
            assertNull(RecordScopes.current())
            assertTrue(open.query().count() >= 2)
            true
        })
        database.close()
    }
}

class RsItem : Cadence<RsItem>() {
    override val table = "rs_items"
    override val timestamps = false
    override val fillable = setOf("name", "team")
}
