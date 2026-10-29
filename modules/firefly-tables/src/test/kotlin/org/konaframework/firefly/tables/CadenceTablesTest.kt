package org.konaframework.firefly.tables

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ConnectionConfig
import org.konaframework.cadence.Database
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.SoftDeletes
import org.konaframework.cadence.relations.BelongsTo
import org.konaframework.cadence.relations.HasMany
import org.konaframework.firefly.support.FireflyException

class CadenceTablesTest {
    @Test
    fun modifyQueryUsingReceivesModelBuilder() {
        database()
        val customers = CadenceRecordStore(Customer::class.java)
        val orders = CadenceRecordStore(Order::class.java)
        val accounts = CadenceRecordStore(Account::class.java)
        val ada = customers.save(customers.fill(customers.newRecord(), mapOf("name" to "Ada", "active" to true, "score" to 2, "sort" to 1)))
        val bea = customers.save(customers.fill(customers.newRecord(), mapOf("name" to "Bea", "active" to true, "score" to 4, "sort" to 2)))
        val cy = customers.save(customers.fill(customers.newRecord(), mapOf("name" to "Cy", "active" to true, "score" to 1, "sort" to 3)))
        val dee = customers.save(customers.fill(customers.newRecord(), mapOf("name" to "Dee", "active" to false, "score" to 9, "sort" to 4)))
        orders.save(orders.fill(orders.newRecord(), mapOf("customer_id" to ada.getKey(), "total" to 10)))
        orders.save(orders.fill(orders.newRecord(), mapOf("customer_id" to bea.getKey(), "total" to 10)))
        orders.save(orders.fill(orders.newRecord(), mapOf("customer_id" to cy.getKey(), "total" to 0)))
        orders.save(orders.fill(orders.newRecord(), mapOf("customer_id" to dee.getKey(), "total" to 10)))
        accounts.save(accounts.fill(accounts.newRecord(), mapOf("customer_id" to ada.getKey(), "active" to true, "label" to "Ada")))
        accounts.save(accounts.fill(accounts.newRecord(), mapOf("customer_id" to cy.getKey(), "active" to true, "label" to "Cy")))
        accounts.save(accounts.fill(accounts.newRecord(), mapOf("customer_id" to dee.getKey(), "active" to true, "label" to "Dee")))
        accounts.save(accounts.fill(accounts.newRecord(), mapOf("customer_id" to ada.getKey(), "active" to false, "label" to "Old")))

        var seen: ModelBuilder<Customer>? = null
        var sql = ""
        val table = Table.cadence(Customer::class.java)
            .columns(
                TextColumn.make("name").searchable(true).sortable(true).summarize(Summary.COUNT),
                TextColumn.make("score").summarize(Summary.SUM).summarize(Summary.AVG).summarize(Summary.MIN).summarize(Summary.MAX),
            )
            .modifyQueryUsing { query ->
                seen = query
                query.select("ff_customers.*")
                    .scope { it.where("ff_customers.active", true) }
                    .with("orders")
                    .whereHas("orders") { nested -> nested.where("total", ">", 0) }
                    .join("ff_accounts", "ff_accounts.customer_id", "=", "ff_customers.id")
                    .where("ff_accounts.active", true)
                sql = query.toSql()
            }
        val loaded = table.load()
        assertNotNull(seen)
        assertTrue(sql.contains("join"))
        assertTrue(sql.contains("exists"))
        assertEquals(listOf("Ada"), loaded.records.map { it.getAttribute("name") })
        assertTrue(loaded.records.first().relationLoaded("orders"))
        assertEquals(1L, loaded.summaries["name:COUNT"])

        val options = table.relationship(Account::class.java) { query ->
            query.where("active", true).whereHas("customer") { customer -> customer.where("name", "Ada") }
        }
        assertEquals(listOf("Ada"), options.get().map { it.getAttribute("label") })
        assertTrue(table.relationship(Account::class.java).toSql().contains("ff_accounts"))

        // FK column.relationship() eager-loads and renders the related title.
        val orderTable = Table.cadence(Order::class.java)
            .columns(TextColumn.make("customer_id").relationship("customer", "name"))
        val orderHtml = orderTable.render()
        assertTrue(orderHtml.contains("Ada") || orderHtml.contains("Bea") || orderHtml.contains("Cy") || orderHtml.contains("Dee"))
        assertTrue(orderHtml.contains(">Customer<") || orderHtml.contains("Customer"))
        assertTrue(orderTable.load().records.first().relationLoaded("customer"))
        val orderStore = CadenceRecordStore(Order::class.java)
        val firstOrder = orderTable.load().records.first()
        val attrs = orderStore.attributes(firstOrder)
        assertTrue(attrs.containsKey("customer"))
        assertTrue(attrs["customer"] is Map<*, *>)
        val relatedCustomer = firstOrder.getRelation("customer") as Customer
        assertEquals(
            relatedCustomer.getAttribute("name")?.toString(),
            Column.relationshipTitle(
                Cell { if (it == "customer") relatedCustomer else null },
                "customer",
                "name",
                null,
            ),
        )
        firstOrder.setRelation("customer", null)
        assertNull(orderStore.attributes(firstOrder)["customer"])
        firstOrder.setRelation("tag", "plain")
        assertEquals("plain", orderStore.attributes(firstOrder)["tag"])
        val withOrders = Table.cadence(Customer::class.java)
            .columns(TextColumn.make("name"), TextColumn.make("id").relationship("orders", "total"))
            .load()
        val customerAttrs = CadenceRecordStore(Customer::class.java).attributes(withOrders.records.first())
        assertTrue(customerAttrs["orders"] is List<*>)
        // Nested dig uses Cadence.getRelations() for mid-path segments (#350).
        val customerWithOrders = withOrders.records.first()
        assertNotNull(
            Column.relationshipTitle(
                Cell { if (it == "customer") customerWithOrders else null },
                "customer.orders",
                "total",
                "fallback",
            ),
        )
        // Cadence attributeOf: relation hit vs attribute fallback (elvis both sides).
        val solo = Customer()
        solo.setAttribute("name", "Solo")
        assertNull(solo.getRelations()["name"])
        assertEquals(
            "Solo",
            Column.relationshipTitle(Cell { if (it == "c") solo else null }, "c", "name", "?"),
        )
        solo.setRelation("tag", "vip")
        assertEquals(
            "vip",
            Column.relationshipTitle(Cell { if (it == "c") solo else null }, "c", "tag", "?"),
        )
        // Mixed collection members (non-Cadence) keep their raw value.
        withOrders.records.first().setRelation("mixed", listOf("x", withOrders.records.first()))
        val mixed = CadenceRecordStore(Customer::class.java).attributes(withOrders.records.first())["mixed"] as List<*>
        assertEquals("x", mixed[0])
        assertTrue(mixed[1] is Map<*, *>)

        table.search("Ada").sort("name", "desc")
        assertEquals("Ada", table.load().records.single().getAttribute("name"))
        customers.search(customers.query(), "", emptyList())
        customers.search(customers.query(), "ada", emptyList())
        val both = customers.query()
        customers.search(both, "a", listOf("name", "status"))
        assertTrue(both.toSql().contains("or"))
        assertThrows(FireflyException::class.java) { customers.sort(customers.query(), "name", "sideways") }
        customers.sort(customers.query(), "name", "asc")
        customers.sort(customers.query(), "name", "desc")
        assertFalse(customers.list(customers.query()).isEmpty())
        assertThrows(FireflyException::class.java) { customers.paginate(customers.query(), 0, 1) }
        assertThrows(FireflyException::class.java) { customers.paginate(customers.query(), 1, 0) }
        assertTrue(customers.paginate(customers.query(), 1, 1).hasMorePages())
        customers.constrain(customers.query(), "status", "null", null)
        customers.constrain(customers.query(), "name", "not-null", null)
        customers.constrain(customers.query(), "name", "=", "Ada")
        customers.trashed(customers.query(), "with")
        customers.trashed(customers.query(), "only")
        customers.trashed(customers.query(), "without")
        customers.trashed(customers.query(), "")
        assertThrows(FireflyException::class.java) { customers.trashed(customers.query(), "nope") }
        assertThrows(FireflyException::class.java) { customers.set("999", "name", "Nope") }
        assertThrows(FireflyException::class.java) { customers.reorder("sort", listOf("999")) }
        assertThrows(FireflyException::class.java) { customers.keyOf(customers.newRecord()) }
        assertThrows(FireflyException::class.java) { customers.delete(customers.newRecord()) }
        assertNull(customers.find("missing-key"))
        assertNotNull(customers.find(customers.keyOf(ada)))
        customers.set(customers.keyOf(ada), "name", "Ada")
        customers.reorder("sort", listOf(customers.keyOf(ada), customers.keyOf(bea)))
        assertEquals(0, customers.find(customers.keyOf(ada))!!.getAttribute("sort"))
        assertFalse(customers.supportsSoftDeletes())

        val notes = CadenceRecordStore(ArchivedNote::class.java)
        assertTrue(notes.supportsSoftDeletes())
        val kept = notes.save(notes.fill(notes.newRecord(), mapOf("name" to "Keep")))
        val gone = notes.save(notes.fill(notes.newRecord(), mapOf("name" to "Gone")))
        notes.delete(gone)
        val only = notes.query()
        notes.trashed(only, "only")
        assertEquals(listOf("Gone"), only.get().map { it.getAttribute("name") })
        assertThrows(IllegalStateException::class.java) {
            notes.transaction {
                notes.save(notes.fill(notes.newRecord(), mapOf("name" to "Temp")))
                throw IllegalStateException("rollback")
            }
        }
        assertNull(notes.query().where("name", "Temp").first())
        notes.transaction<ArchivedNote> {
            notes.save(notes.fill(notes.newRecord(), mapOf("name" to "Saved")))
        }
        assertNotNull(notes.query().where("name", "Saved").first())
        assertEquals("Keep", notes.attributes(kept)["name"])
        assertEquals(1L, notes.summarize(notes.query().where("name", "Keep"), "id", Summary.COUNT))
        notes.summarize(notes.query(), "id", Summary.SUM)
        notes.summarize(notes.query(), "id", Summary.AVG)
        notes.summarize(notes.query(), "id", Summary.MIN)
        notes.summarize(notes.query(), "id", Summary.MAX)

        val hooked = Table.cadence(Customer::class.java).filters(Filter.make("name").query { query, value ->
            (query as ModelBuilder<*>).where("name", value)
        })
        hooked.filter("name", "Ada")
        assertEquals("Ada", hooked.load().records.single().getAttribute("name"))
    }
}

private fun database(): Database {
    val url = "jdbc:h2:mem:ff${System.nanoTime()};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    val database = Database().addConnection("default", ConnectionConfig(url, "sa", "", poolSize = 2))
    val connection = database.connection()
    listOf(
        """
        create table ff_customers (
            id bigint auto_increment primary key,
            name varchar(100), active boolean, score int, sort int, status varchar(20)
        )
        """,
        """
        create table ff_orders (
            id bigint auto_increment primary key,
            customer_id bigint, total int
        )
        """,
        """
        create table ff_accounts (
            id bigint auto_increment primary key,
            customer_id bigint, active boolean, label varchar(50)
        )
        """,
        """
        create table ff_notes (
            id bigint auto_increment primary key,
            name varchar(100), deleted_at timestamp
        )
        """,
    ).forEach { connection.statement(it.trim()) }
    return database.setAsGlobal()
}

class Customer : Cadence<Customer>() {
    override val table = "ff_customers"
    override val timestamps = false
    override val fillable = setOf("name", "active", "score", "sort", "status")

    fun orders(): HasMany<Order> = hasMany(Order::class.java, "customer_id", "id")
}

class Order : Cadence<Order>() {
    override val table = "ff_orders"
    override val timestamps = false
    override val fillable = setOf("customer_id", "total")

    fun customer(): BelongsTo<Customer> = belongsTo(Customer::class.java, "customer_id", "id")
}

class Account : Cadence<Account>() {
    override val table = "ff_accounts"
    override val timestamps = false
    override val fillable = setOf("customer_id", "active", "label")

    fun customer(): BelongsTo<Customer> = belongsTo(Customer::class.java, "customer_id", "id")
}

class ArchivedNote : Cadence<ArchivedNote>(), SoftDeletes {
    override val table = "ff_notes"
    override val timestamps = false
    override val fillable = setOf("name")
}
