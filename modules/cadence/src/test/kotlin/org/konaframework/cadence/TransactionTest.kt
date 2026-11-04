package org.konaframework.cadence

import org.h2.jdbcx.JdbcDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.SQLException
import java.sql.Savepoint
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

class TransactionTest {
    @AfterEach
    fun reset() {
        Database.clearGlobal()
    }

    @Test
    fun retriesOnDeadlockSqlState() {
        val connection = TestDb.create().connection()
        val attempts = AtomicInteger()
        val result = connection.transaction({ tx ->
            attempts.incrementAndGet()
            if (attempts.get() < 3) {
                throw SQLException("serialization failure", "40001")
            }
            tx.table("tags").insert(mapOf("code" to "retry", "label" to "ok"))
            "done"
        }, 3)
        assertEquals("done", result)
        assertEquals(3, attempts.get())
        assertEquals(1L, connection.table("tags").where("code", "retry").count())
    }

    @Test
    fun retriesExhaustedRethrow() {
        val connection = TestDb.create().connection()
        val attempts = AtomicInteger()
        val error = assertThrows<SQLException> {
            connection.transaction<Unit>({ _ ->
                attempts.incrementAndGet()
                throw SQLException("deadlock detected", "40P01")
            }, 2)
        }
        assertEquals("40P01", error.sqlState)
        assertEquals(2, attempts.get())
        assertEquals(0L, connection.table("tags").count())
    }

    @Test
    fun rejectsNonPositiveAttempts() {
        val connection = TestDb.create().connection()
        assertThrows<IllegalArgumentException> {
            connection.transaction({ 1 }, 0)
        }
    }

    @Test
    fun nestedSavepointRollsBackOnlyInnerWork() {
        val connection = TestDb.create().connection()
        connection.transaction { outer ->
            outer.table("tags").insert(mapOf("code" to "keep", "label" to "outer"))
            val boom = assertThrows<IllegalStateException> {
                outer.transaction<Unit> { inner ->
                    inner.table("tags").insert(mapOf("code" to "drop", "label" to "inner"))
                    throw IllegalStateException("inner-fail")
                }
            }
            assertEquals("inner-fail", boom.message)
            assertEquals(1L, outer.table("tags").count())
            outer.table("tags").insert(mapOf("code" to "after", "label" to "outer"))
        }
        assertEquals(listOf("after", "keep"), connection.table("tags").orderBy("code").pluck("code"))
    }

    @Test
    fun afterCommitRunsOnlyAfterOutermostCommit() {
        val connection = TestDb.create().connection()
        val log = mutableListOf<String>()
        connection.afterCommit { log += "immediate" }
        assertEquals(listOf("immediate"), log)

        connection.transaction { outer ->
            outer.afterCommit { log += "outer" }
            outer.transaction { inner ->
                inner.afterCommit { log += "inner" }
                assertEquals(listOf("immediate"), log)
                1
            }
            assertEquals(listOf("immediate"), log)
            1
        }
        assertEquals(listOf("immediate", "outer", "inner"), log)
    }

    @Test
    fun afterCommitDiscardedOnRollback() {
        val connection = TestDb.create().connection()
        val log = mutableListOf<String>()
        assertThrows<IllegalStateException> {
            connection.transaction<Unit> { tx ->
                tx.afterCommit { log += "should-not-run" }
                tx.table("tags").insert(mapOf("code" to "x", "label" to "y"))
                throw IllegalStateException("nope")
            }
        }
        assertTrue(log.isEmpty())
        assertEquals(0L, connection.table("tags").count())
    }

    @Test
    fun afterCommitDiscardedWhenNestedSavepointRollsBack() {
        val connection = TestDb.create().connection()
        val log = mutableListOf<String>()
        connection.transaction { outer ->
            outer.afterCommit { log += "outer" }
            assertThrows<IllegalStateException> {
                outer.transaction<Unit> { inner ->
                    inner.afterCommit { log += "inner" }
                    throw IllegalStateException("inner")
                }
            }
            1
        }
        assertEquals(listOf("outer"), log)
    }

    @Test
    fun manualBeginCommitAndTransactionLevel() {
        val database = TestDb.create()
        val connection = database.connection()
        assertEquals(0, connection.transactionLevel())
        assertEquals(0, DB.transactionLevel())
        assertFalse(connection.inTransaction())

        DB.beginTransaction()
        assertEquals(1, DB.transactionLevel())
        assertTrue(connection.inTransaction())
        DB.table("tags").insert(mapOf("code" to "m", "label" to "n"))
        DB.beginTransaction()
        assertEquals(2, connection.transactionLevel())
        DB.table("tags").insert(mapOf("code" to "m2", "label" to "n2"))
        DB.rollBack()
        assertEquals(1, DB.transactionLevel())
        assertEquals(1L, DB.table("tags").count())
        DB.commit()
        assertEquals(0, DB.transactionLevel())
        assertEquals(1L, DB.table("tags").count())

        database.beginTransaction()
        database.table("tags").insert(mapOf("code" to "d", "label" to "b"))
        database.rollBack(0)
        assertEquals(0, database.transactionLevel())
        assertEquals(1L, database.table("tags").count())

        database.transaction({ 7 }, 1).also { assertEquals(7, it) }
        val ran = AtomicInteger()
        database.afterCommit { ran.incrementAndGet() }
        assertEquals(1, ran.get())
    }

    @Test
    fun commitAndRollBackWithoutTransactionThrow() {
        val connection = TestDb.create().connection()
        assertThrows<CadenceException> { connection.commit() }
        assertThrows<CadenceException> { connection.rollBack() }
        assertThrows<CadenceException> { connection.rollBack(0) }
    }

    @Test
    fun rollBackToLevelValidatesRange() {
        val connection = TestDb.create().connection()
        connection.beginTransaction()
        connection.beginTransaction()
        assertThrows<IllegalArgumentException> { connection.rollBack(2) }
        assertThrows<IllegalArgumentException> { connection.rollBack(-1) }
        connection.rollBack(0)
        connection.beginTransaction()
        connection.rollBack(0)
    }

    @Test
    fun concurrencyErrorMessagesAndCodes() {
        assertTrue(ConcurrencyErrorDetector.causedByConcurrencyError(SQLException("x", "40001")))
        assertTrue(ConcurrencyErrorDetector.causedByConcurrencyError(SQLException("x", "40P01")))
        assertTrue(ConcurrencyErrorDetector.causedByConcurrencyError(SQLException("Deadlock found when trying to get lock", "HY000", 1213)))
        assertTrue(ConcurrencyErrorDetector.causedByConcurrencyError(SQLException("Lock wait timeout exceeded; try restarting transaction", "HY000", 1205)))
        assertTrue(
            ConcurrencyErrorDetector.causedByConcurrencyError(
                QueryException("select 1", emptyList(), SQLException("deadlock detected", "40P01")),
            ),
        )
        assertTrue(ConcurrencyErrorDetector.causedByConcurrencyError(IllegalStateException("database is locked")))
        assertFalse(ConcurrencyErrorDetector.causedByConcurrencyError(IllegalStateException("plain failure")))
        assertFalse(ConcurrencyErrorDetector.causedByConcurrencyError(SQLException("other", "23000", 1062)))
        assertFalse(ConcurrencyErrorDetector.causedByConcurrencyError(object : RuntimeException(null as String?) {}))
    }

    @Test
    fun nestedConcurrencyErrorPropagatesWithoutRetryingInner() {
        val connection = TestDb.create().connection()
        val innerAttempts = AtomicInteger()
        val error = assertThrows<SQLException> {
            connection.transaction<Unit> { outer ->
                outer.table("tags").insert(mapOf("code" to "o", "label" to "1"))
                outer.transaction<Unit>({ _ ->
                    innerAttempts.incrementAndGet()
                    throw SQLException("deadlock", "40001")
                }, 5)
            }
        }
        assertEquals("40001", error.sqlState)
        assertEquals(1, innerAttempts.get())
        assertEquals(0L, connection.table("tags").count())
    }

    @Test
    fun dbFacadeAfterCommit() {
        TestDb.create()
        val log = mutableListOf<String>()
        DB.transaction({ _ ->
            DB.afterCommit { log += "facade" }
            1
        }, 2)
        assertEquals(listOf("facade"), log)
        assertEquals(3, DB.transaction({ 3 }, 1))

        DB.beginTransaction()
        DB.beginTransaction()
        assertEquals(2, DB.transactionLevel())
        DB.rollBack(0)
        assertEquals(0, DB.transactionLevel())
        assertThrows<CadenceException> { DB.commit() }
        assertThrows<CadenceException> { DB.rollBack() }
        assertThrows<CadenceException> { DB.rollBack(0) }
    }

    @Test
    fun databaseManagerTransactionOverloads() {
        val database = TestDb.create()
        assertEquals(9, database.transaction({ 9 }, 2))
        database.beginTransaction()
        database.table("tags").insert(mapOf("code" to "mgr", "label" to "1"))
        database.commit()
        assertEquals(1L, database.table("tags").where("code", "mgr").count())
        database.beginTransaction()
        database.table("tags").insert(mapOf("code" to "mgr2", "label" to "2"))
        database.rollBack()
        assertEquals(0L, database.table("tags").where("code", "mgr2").count())
    }

    @Test
    fun rollbackSqlFailuresAreWrapped() {
        val connection = DatabaseConnection("rb-fail", wrappingDataSource { RollbackFailingConnection(it) })
        connection.beginTransaction()
        val error = assertThrows<CadenceException> { connection.rollBack() }
        assertTrue(error.message!!.contains("roll back"))

        val nested = DatabaseConnection("rb-sp", wrappingDataSource { SavepointRollbackFailingConnection(it) })
        nested.beginTransaction()
        nested.beginTransaction()
        val spError = assertThrows<CadenceException> { nested.rollBack() }
        assertTrue(spError.message!!.contains("savepoint"))
        nested.rollBack(0)
    }

    @Test
    fun defensiveBranchesViaReflection() {
        val connection = TestDb.create().connection()
        val txLocal = threadLocal(connection, "tx") as ThreadLocal<*>
        val txState = txLocal.get()!!
        val levelField = txState.javaClass.getDeclaredField("level").also { it.isAccessible = true }
        val boundField = txState.javaClass.getDeclaredField("bound").also { it.isAccessible = true }
        val ownsField = txState.javaClass.getDeclaredField("ownsBound").also { it.isAccessible = true }
        val previousField = txState.javaClass.getDeclaredField("previousAutoCommit").also { it.isAccessible = true }
        val savepointsField = txState.javaClass.getDeclaredField("savepoints").also { it.isAccessible = true }

        levelField.setInt(txState, 2)
        boundField.set(txState, null)
        assertThrows<CadenceException> { connection.beginTransaction() }
        assertThrows<CadenceException> { connection.commit() }
        assertThrows<CadenceException> { connection.rollBack() }
        levelField.setInt(txState, 0)

        val raw = connection.dataSource.connection
        boundField.set(txState, raw)
        ownsField.setBoolean(txState, false)
        previousField.set(txState, true)
        connection.beginTransaction()
        assertEquals(1, connection.transactionLevel())
        connection.rollBack()
        assertEquals(0, connection.transactionLevel())
        assertEquals(raw, boundField.get(txState))
        boundField.set(txState, null)
        ownsField.setBoolean(txState, false)
        previousField.set(txState, null)
        raw.close()

        connection.beginTransaction()
        levelField.setInt(txState, 2)
        @Suppress("UNCHECKED_CAST")
        (savepointsField.get(txState) as ArrayDeque<Savepoint>).clear()
        val handleTx = DatabaseConnection::class.java.getDeclaredMethod(
            "handleTransactionException",
            Throwable::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        handleTx.isAccessible = true
        val nested = assertThrows<java.lang.reflect.InvocationTargetException> {
            handleTx.invoke(connection, SQLException("deadlock", "40001"), 1, 3)
        }
        assertTrue(nested.cause is SQLException)
        if (connection.transactionLevel() > 0) {
            connection.rollBack(0)
        }

        val handleCommit = DatabaseConnection::class.java.getDeclaredMethod(
            "handleCommitException",
            Throwable::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        handleCommit.isAccessible = true
        assertThrows<java.lang.reflect.InvocationTargetException> {
            handleCommit.invoke(connection, SQLException("deadlock", "40001"), 2, 2)
        }
        handleCommit.invoke(connection, SQLException("deadlock", "40001"), 1, 3)

        connection.beginTransaction()
        previousField.set(txState, null)
        connection.commit()
        assertEquals(0, connection.transactionLevel())

        connection.beginTransaction()
        connection.beginTransaction()
        @Suppress("UNCHECKED_CAST")
        assertEquals(1, (savepointsField.get(txState) as ArrayDeque<*>).size)
        val withSp = assertThrows<SQLException> {
            connection.transaction<Unit>({ throw SQLException("deadlock", "40001") }, 1)
        }
        assertEquals("40001", withSp.sqlState)
        if (connection.transactionLevel() > 0) {
            connection.rollBack(0)
        }

        for (dialect in Dialect.entries) {
            GrammarLookup.of(dialect)
            Grammar.of(dialect)
        }
        assertThrows<NullPointerException> { GrammarLookup.of(null) }
    }

    private fun threadLocal(target: Any, name: String): Any {
        val field = DatabaseConnection::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)!!
    }

    @Test
    fun savepointFailureWrapsCadenceException() {
        val connection = DatabaseConnection("fail-sp", wrappingDataSource { FailingSavepointConnection(it) })
        connection.beginTransaction()
        val error = assertThrows<CadenceException> { connection.beginTransaction() }
        assertTrue(error.message!!.contains("savepoint"))
        connection.rollBack()
    }

    @Test
    fun releaseSavepointFailureIsIgnoredOnNestedCommit() {
        val connection = DatabaseConnection("rel-fail", wrappingDataSource { ReleaseFailingConnection(it) })
        connection.transaction { outer ->
            outer.transaction { 1 }
            1
        }
    }

    @Test
    fun commitFailureIsWrapped() {
        val connection = DatabaseConnection("commit-fail", wrappingDataSource { CommitFailingConnection(it) })
        val error = assertThrows<CadenceException> {
            connection.transaction<Unit> { }
        }
        assertTrue(error.message!!.contains("commit"))
    }

    @Test
    fun releaseIgnoresAutoCommitAndCloseFailures() {
        val connection = DatabaseConnection(
            "release-fail",
            wrappingDataSource { ReleaseCleanupFailingConnection(it) },
        )
        connection.beginTransaction()
        connection.commit()
        connection.beginTransaction()
        connection.rollBack()
    }

    @Test
    fun commitFailureRetriesOnConcurrency() {
        val commits = AtomicInteger()
        val connection = DatabaseConnection(
            "commit-retry",
            wrappingDataSource { real ->
                object : Connection by real {
                    override fun commit() {
                        if (commits.incrementAndGet() < 2) {
                            throw SQLException("deadlock detected", "40001")
                        }
                        real.commit()
                    }

                    override fun close() {
                        real.close()
                    }
                }
            },
        )
        assertEquals(1, connection.transaction({ 1 }, 3))
        assertEquals(2, commits.get())
    }

    private fun wrappingDataSource(wrap: (Connection) -> Connection): DataSource {
        val url = TestDb.url()
        return object : DataSource {
            override fun getConnection(): Connection {
                val real = JdbcDataSource().apply {
                    setURL(url)
                    user = "sa"
                }.connection
                return wrap(real)
            }

            override fun getConnection(username: String?, password: String?): Connection = connection
            override fun getLogWriter() = null
            override fun setLogWriter(out: java.io.PrintWriter?) {}
            override fun setLoginTimeout(seconds: Int) {}
            override fun getLoginTimeout() = 0
            override fun getParentLogger() = java.util.logging.Logger.getGlobal()
            override fun <T> unwrap(iface: Class<T>): T = throw UnsupportedOperationException()
            override fun isWrapperFor(iface: Class<*>) = false
        }
    }

    private class FailingSavepointConnection(private val real: Connection) : Connection by real {
        override fun setSavepoint(name: String?): Savepoint {
            throw SQLException("no savepoints")
        }

        override fun close() {
            real.close()
        }
    }

    private class ReleaseFailingConnection(private val real: Connection) : Connection by real {
        override fun setSavepoint(name: String?): Savepoint = real.setSavepoint(name)

        override fun releaseSavepoint(savepoint: Savepoint?) {
            throw SQLException("cannot release")
        }

        override fun close() {
            real.close()
        }
    }

    private class CommitFailingConnection(private val real: Connection) : Connection by real {
        override fun commit() {
            throw SQLException("commit blew up", "HY000")
        }

        override fun close() {
            real.close()
        }
    }

    private class RollbackFailingConnection(private val real: Connection) : Connection by real {
        override fun rollback() {
            throw SQLException("rollback blew up")
        }

        override fun close() {
            real.close()
        }
    }

    private class SavepointRollbackFailingConnection(private val real: Connection) : Connection by real {
        override fun setSavepoint(name: String?): Savepoint = real.setSavepoint(name)

        override fun rollback(savepoint: Savepoint?) {
            throw SQLException("savepoint rollback blew up")
        }

        override fun close() {
            real.close()
        }
    }

    private class ReleaseCleanupFailingConnection(private val real: Connection) : Connection by real {
        override fun setAutoCommit(autoCommit: Boolean) {
            if (!autoCommit) {
                real.autoCommit = false
                return
            }
            throw SQLException("cannot restore autocommit")
        }

        override fun getAutoCommit(): Boolean = real.autoCommit

        override fun close() {
            try {
                real.close()
            } finally {
                throw SQLException("cannot close")
            }
        }
    }
}
