package dev.kona.extract

import java.sql.SQLException

/** Base type for every Extract (database / ORM) failure. */
open class ExtractException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** A statement failed; carries the SQL and its bindings for debugging. */
class QueryException(
    val sql: String,
    val bindings: List<Any?>,
    cause: SQLException,
) : ExtractException("${cause.message} (SQL: $sql)", cause)

/** Raised by `findOrFail` / `firstOrFail` when no row matches. */
class ModelNotFoundException(
    val model: Class<*>,
    val ids: List<Any?> = emptyList(),
) : ExtractException(
    "No query results for model [${model.name}]" + if (ids.isEmpty()) "" else " " + ids.joinToString(", "),
)

/** Raised by `fill` when a key is not mass-assignable on a totally guarded model. */
class MassAssignmentException(
    val key: String,
    model: Class<*>,
) : ExtractException("Add [$key] to fillable property to allow mass assignment on [${model.name}].")
