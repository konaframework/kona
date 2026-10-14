package dev.kona.extract

import java.sql.Connection
import java.sql.SQLException

/** Work run with a borrowed JDBC connection; may throw [SQLException] (wrapped in [ExtractException]). */
fun interface ConnectionCallback<R> {
    @Throws(SQLException::class)
    fun withConnection(connection: Connection): R
}
