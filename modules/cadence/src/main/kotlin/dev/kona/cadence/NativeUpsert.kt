package dev.kona.cadence

/** Native upsert SQL helpers (dialect compile) extracted for clear Kover line mapping. */
internal object NativeUpsert {
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

    fun compileSql(
        product: String,
        grammar: Grammar,
        table: String,
        columns: List<String>,
        uniqueBy: List<String>,
        updateCols: List<String>,
        rowCount: Int,
    ): String? {
        val values = (1..rowCount).joinToString(", ") {
            "(" + columns.joinToString(", ") { "?" } + ")"
        }
        val lower = product.lowercase()
        if (lower.contains("mysql") || lower.contains("mariadb")) {
            val updates = if (updateCols.isEmpty()) {
                uniqueBy.first().let { "${grammar.wrap(it)}=${grammar.wrap(it)}" }
            } else {
                updateCols.joinToString(", ") { col ->
                    "${grammar.wrap(col)}=VALUES(${grammar.wrap(col)})"
                }
            }
            return "insert into $table (${grammar.columnize(columns)}) values $values on duplicate key update $updates"
        }
        if (lower.contains("postgresql") || lower.contains("h2")) {
            val conflict = uniqueBy.joinToString(", ") { grammar.wrap(it) }
            val action = if (updateCols.isEmpty()) {
                "do nothing"
            } else {
                "do update set " + updateCols.joinToString(", ") { col ->
                    "${grammar.wrap(col)}=excluded.${grammar.wrap(col)}"
                }
            }
            return "insert into $table (${grammar.columnize(columns)}) values $values on conflict ($conflict) $action"
        }
        return null
    }
}
