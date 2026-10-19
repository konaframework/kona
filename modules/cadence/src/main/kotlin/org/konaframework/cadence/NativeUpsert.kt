package org.konaframework.cadence

/** Native upsert SQL helpers (dialect compile) extracted for clear Kover line mapping. */
internal object NativeUpsert {
    private const val SOURCE = "kona_source"
    private const val TARGET = "kona_target"

    fun rowsHaveUniformKeys(rows: List<Map<String, Any?>>): Boolean {
        if (rows.isEmpty()) {
            return false
        }
        val first = rows.first().keys
        for (row in rows) {
            if (row.keys != first) {
                return false
            }
        }
        return true
    }

    /**
     * Multi-row upsert for the JDBC [product]; null when the engine has no native form.
     * MySQL / MariaDB: `on duplicate key update`. PostgreSQL / SQLite: `on conflict`.
     * H2 / PostgreSQL / SQLite: `on conflict`. SQL Server: `merge … using (values …)`.
     * Oracle: `merge … using (select … from dual)`.
     */
    fun compileSql(
        product: String,
        grammar: Grammar,
        table: String,
        columns: List<String>,
        uniqueBy: List<String>,
        updateCols: List<String>,
        rowCount: Int,
    ): String? = when (Grammar.dialectForProduct(product)) {
        Dialect.MYSQL, Dialect.MARIADB -> onDuplicateKey(grammar, table, columns, uniqueBy, updateCols, rowCount)
        Dialect.POSTGRES, Dialect.SQLITE, Dialect.H2 ->
            onConflict(grammar, table, columns, uniqueBy, updateCols, rowCount)
        Dialect.SQLSERVER -> merge(grammar, table, columns, uniqueBy, updateCols, rowCount, terminator = ";")
        Dialect.ORACLE -> oracleMerge(grammar, table, columns, uniqueBy, updateCols, rowCount)
        Dialect.STANDARD -> null
    }

    private fun values(columns: List<String>, rowCount: Int): String =
        (1..rowCount).joinToString(", ") { "(" + columns.joinToString(", ") { "?" } + ")" }

    private fun onDuplicateKey(
        grammar: Grammar,
        table: String,
        columns: List<String>,
        uniqueBy: List<String>,
        updateCols: List<String>,
        rowCount: Int,
    ): String {
        val updates = if (updateCols.isEmpty()) {
            uniqueBy.first().let { "${grammar.wrap(it)}=${grammar.wrap(it)}" }
        } else {
            updateCols.joinToString(", ") { col -> "${grammar.wrap(col)}=VALUES(${grammar.wrap(col)})" }
        }
        return "insert into $table (${grammar.columnize(columns)}) values ${values(columns, rowCount)} " +
            "on duplicate key update $updates"
    }

    private fun onConflict(
        grammar: Grammar,
        table: String,
        columns: List<String>,
        uniqueBy: List<String>,
        updateCols: List<String>,
        rowCount: Int,
    ): String {
        val conflict = uniqueBy.joinToString(", ") { grammar.wrap(it) }
        val action = if (updateCols.isEmpty()) {
            "do nothing"
        } else {
            "do update set " + updateCols.joinToString(", ") { col -> "${grammar.wrap(col)}=excluded.${grammar.wrap(col)}" }
        }
        return "insert into $table (${grammar.columnize(columns)}) values ${values(columns, rowCount)} " +
            "on conflict ($conflict) $action"
    }

    private fun merge(
        grammar: Grammar,
        table: String,
        columns: List<String>,
        uniqueBy: List<String>,
        updateCols: List<String>,
        rowCount: Int,
        terminator: String,
    ): String {
        val on = uniqueBy.joinToString(" and ") { "$TARGET.${grammar.wrap(it)} = $SOURCE.${grammar.wrap(it)}" }
        return "merge into $table as $TARGET using (values ${values(columns, rowCount)}) as $SOURCE " +
            "(${grammar.columnize(columns)}) on $on" + mergeActions(grammar, columns, uniqueBy, updateCols) + terminator
    }

    private fun oracleMerge(
        grammar: Grammar,
        table: String,
        columns: List<String>,
        uniqueBy: List<String>,
        updateCols: List<String>,
        rowCount: Int,
    ): String {
        val first = "select " + columns.joinToString(", ") { "? as ${grammar.wrap(it)}" } + " from dual"
        val rest = "select " + columns.joinToString(", ") { "?" } + " from dual"
        val source = (listOf(first) + List(rowCount - 1) { rest }).joinToString(" union all ")
        val on = uniqueBy.joinToString(" and ") { "$TARGET.${grammar.wrap(it)} = $SOURCE.${grammar.wrap(it)}" }
        return "merge into $table $TARGET using ($source) $SOURCE on ($on)" +
            mergeActions(grammar, columns, uniqueBy, updateCols)
    }

    /** Matched columns used in `on` cannot be updated (Oracle rejects it; elsewhere it is a no-op). */
    private fun mergeActions(grammar: Grammar, columns: List<String>, uniqueBy: List<String>, updateCols: List<String>): String {
        val sql = StringBuilder()
        val updates = updateCols.filter { it !in uniqueBy }
        if (updates.isNotEmpty()) {
            sql.append(" when matched then update set ")
                .append(updates.joinToString(", ") { "$TARGET.${grammar.wrap(it)} = $SOURCE.${grammar.wrap(it)}" })
        }
        sql.append(" when not matched then insert (").append(grammar.columnize(columns)).append(") values (")
            .append(columns.joinToString(", ") { "$SOURCE.${grammar.wrap(it)}" }).append(")")
        return sql.toString()
    }
}
