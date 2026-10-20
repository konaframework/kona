package org.konaframework.grind

import org.konaframework.http.Handler
import org.konaframework.http.Middleware
import org.konaframework.http.Router
import org.konaframework.support.console.ConsoleIO
import org.konaframework.support.json.Json

/**
 * Builds, filters, and formats the grind `route:list` / `route:cache` table from a live [Router].
 */
internal object RouteListing {
    val DEFAULT_COLUMNS: List<String> = listOf("method", "uri", "name", "action", "middleware")

    val COMPACT_COLUMNS: List<String> = listOf("method", "uri", "action", "middleware")

    val COLUMNS: Set<String> = DEFAULT_COLUMNS.toSet()

    data class Row(
        val methods: List<String>,
        val uri: String,
        val name: String,
        val action: String,
        val middleware: List<String>,
        val vendor: Boolean,
    ) {
        val method: String get() = methods.joinToString("|")

        val middlewareLabel: String get() = middleware.joinToString(", ")

        fun column(key: String): String =
            when (key) {
                "method" -> method
                "uri" -> uri
                "name" -> name
                "action" -> action
                "middleware" -> middlewareLabel
                else -> ""
            }

        fun toMap(): Map<String, Any?> =
            mapOf(
                "method" to method,
                "uri" to uri,
                "name" to name.ifEmpty { null },
                "action" to action,
                "middleware" to middleware,
                "vendor" to vendor,
            )
    }

    fun rows(router: Router): List<Row> =
        router.all().map { route ->
            Row(
                methods = route.methods,
                uri = route.uri,
                name = route.name.orEmpty(),
                action = actionLabel(route.action),
                middleware = route.middleware.map { middlewareLabel(it) },
                vendor = isVendor(route.action),
            )
        }

    fun filter(rows: List<Row>, options: Map<String, String>): List<Row> {
        var selected = rows
        val methods = csv(options, "method")
        if (methods.isNotEmpty()) {
            selected = selected.filter { row -> methods.any { matchesMethod(row, it) } }
        }
        val names = csv(options, "name")
        if (names.isNotEmpty()) {
            selected = selected.filter { row -> names.any { needle -> row.name.contains(needle, ignoreCase = true) } }
        }
        val paths = csv(options, "path")
        if (paths.isNotEmpty()) {
            selected = selected.filter { row -> paths.any { needle -> row.uri.contains(needle) } }
        }
        val exceptPaths = csv(options, "except-path")
        if (exceptPaths.isNotEmpty()) {
            selected = selected.filter { row -> exceptPaths.none { needle -> row.uri.contains(needle) } }
        }
        if (MigrateCommand.wantsFlag(options, "except-vendor")) {
            selected = selected.filter { row -> !row.vendor }
        }
        if (MigrateCommand.wantsFlag(options, "only-vendor")) {
            selected = selected.filter { row -> row.vendor }
        }
        return selected
    }

    fun sort(rows: List<Row>, options: Map<String, String>): List<Row> {
        val raw = options["sort"]
        val key = if (raw.isNullOrBlank()) "uri" else raw.trim().lowercase()
        if (key !in COLUMNS) {
            throw GrindException("Invalid sort column [$key]. Allowed: ${COLUMNS.joinToString(", ")}")
        }
        val ordered = rows.sortedWith(compareBy<Row> { it.column(key) }.thenBy { it.uri }.thenBy { it.method })
        return if (MigrateCommand.wantsFlag(options, "reverse")) ordered.asReversed() else ordered
    }

    fun columns(options: Map<String, String>): List<String> {
        if (MigrateCommand.wantsFlag(options, "compact")) {
            return COMPACT_COLUMNS
        }
        val raw = options["columns"] ?: return DEFAULT_COLUMNS
        val cols = raw.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (cols.isEmpty()) {
            return DEFAULT_COLUMNS
        }
        val unknown = cols.firstOrNull { it !in COLUMNS }
        if (unknown != null) {
            throw GrindException("Invalid column [$unknown]. Allowed: ${COLUMNS.joinToString(", ")}")
        }
        return cols.distinct()
    }

    fun header(column: String): String =
        when (column) {
            "uri" -> "URI"
            else -> column.replaceFirstChar { it.uppercase() }
        }

    fun paintMethods(io: GrindIO, methods: String): String =
        methods.split("|").joinToString("|") { verb -> io.paint(methodColor(verb), verb) }

    fun methodColor(verb: String): String =
        when (verb.uppercase()) {
            "GET" -> ConsoleIO.GREEN
            "HEAD" -> ConsoleIO.GREEN
            "POST" -> ConsoleIO.YELLOW
            "PUT" -> ConsoleIO.CYAN
            "PATCH" -> ConsoleIO.CYAN
            "DELETE" -> ConsoleIO.RED
            else -> ConsoleIO.DIM
        }

    fun toJson(rows: List<Row>): String = Json.stringify(rows.map { it.toMap() })

    fun actionLabel(handler: Handler): String {
        val type = handler.javaClass
        if (looksLikeClosure(type)) {
            return "Closure"
        }
        return type.name
    }

    fun middlewareLabel(middleware: Middleware): String {
        val type = middleware.javaClass
        if (looksLikeClosure(type)) {
            return "Closure"
        }
        return type.simpleName
    }

    fun looksLikeClosure(type: Class<*>): Boolean {
        if (type.isAnonymousClass || type.isSynthetic) {
            return true
        }
        val name = type.name
        if (name.contains("\$\$Lambda")) {
            return true
        }
        // Kotlin lambdas (`Foo$create$1`) and anonymous (`Foo$1`); named inner classes (`Foo$Bar`) stay.
        return CLOSURE_NAME.containsMatchIn(name)
    }

    fun isVendor(handler: Handler): Boolean = handler.javaClass.name.startsWith("org.konaframework.")

    fun matchesMethod(row: Row, method: String): Boolean {
        val verb = method.uppercase()
        if (verb in row.methods) {
            return true
        }
        return verb == "HEAD" && "GET" in row.methods
    }

    fun csv(options: Map<String, String>, key: String): List<String> {
        val raw = options[key] ?: return emptyList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private val CLOSURE_NAME = Regex("""\$(\d+|[a-z])""")
}
