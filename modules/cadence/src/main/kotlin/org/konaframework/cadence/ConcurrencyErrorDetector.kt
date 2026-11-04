package org.konaframework.cadence

import java.sql.SQLException

/**
 * Detects deadlock / serialization failures so [DatabaseConnection.transaction] can retry.
 *
 * Recognizes SQLSTATE `40001` and `40P01`, common vendor error codes, and well-known
 * deadlock / lock-timeout message fragments across first-party engines.
 */
object ConcurrencyErrorDetector {
    private val messageFragments = listOf(
        "Deadlock found when trying to get lock",
        "deadlock detected",
        "The database file is locked",
        "database is locked",
        "database table is locked",
        "A table in the database is locked",
        "has been chosen as the deadlock victim",
        "Lock wait timeout exceeded; try restarting transaction",
        "WSREP detected deadlock/conflict and aborted the transaction",
        "Record has changed since last read in table",
    )

    /** True when [error] (or any cause) looks like a concurrency failure worth retrying. */
    @JvmStatic
    fun causedByConcurrencyError(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SQLException && isConcurrencySqlException(current)) {
                return true
            }
            val message = current.message
            if (message != null && messageFragments.any { fragment -> message.contains(fragment, ignoreCase = true) }) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun isConcurrencySqlException(error: SQLException): Boolean {
        val state = error.sqlState
        if (state == "40001" || state == "40P01") {
            return true
        }
        // MySQL / MariaDB deadlock (1213) and lock wait timeout (1205); SQL Server deadlock victim (1205).
        val code = error.errorCode
        return code == 1213 || code == 1205
    }
}
