package io.konaframework.cadence

import java.sql.SQLException

/** Base type for every Cadence (database / ORM) failure. */
open class CadenceException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** A statement failed; carries the SQL and its bindings for debugging. */
class QueryException(
    val sql: String,
    val bindings: List<Any?>,
    cause: SQLException,
) : CadenceException("${cause.message} (SQL: $sql)", cause)

/** Raised by `findOrFail` / `firstOrFail` / `sole` when no row matches. */
class ModelNotFoundException(
    val model: Class<*>,
    val ids: List<Any?> = emptyList(),
) : CadenceException(
    "No query results for model [${model.name}]" + if (ids.isEmpty()) "" else " " + ids.joinToString(", "),
)

/** Raised by `sole` when more than one row matches. */
class MultipleRecordsFoundException(
    val model: Class<*>,
    val count: Int,
) : CadenceException("Query returned $count results for model [${model.name}]; expected exactly one.")

/** Raised by `fill` when a key is not mass-assignable on a totally guarded model. */
class MassAssignmentException(
    val key: String,
    model: Class<*>,
) : CadenceException("Add [$key] to fillable property to allow mass assignment on [${model.name}].")
