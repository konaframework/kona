package org.konaframework.firefly.forms

import org.konaframework.cadence.Cadence
import org.konaframework.cadence.ModelBuilder
import org.konaframework.cadence.relations.Relation
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordStore
import org.konaframework.firefly.tables.RecordStores
import java.util.function.Consumer

internal class OpenedRelation(val query: Any, val records: List<Any?>)

/**
 * Loads select options for a relationship name on the schema record.
 * Cadence relations receive a [ModelBuilder]. Any other related model receives that model's query.
 */
internal object RelationshipOptions {
    fun open(owner: Any, name: String, modify: Consumer<Any?>?): OpenedRelation {
        val query = openQuery(owner, name, modify)
        return OpenedRelation(query, recordsOf(query))
    }

    /**
     * Search related records for a combobox. Unlike [open], applies a title `like` filter when
     * possible and never inherits the preload option cap from the Select — [limit] is the search
     * result cap (or null for uncapped).
     */
    fun search(
        owner: Any,
        name: String,
        title: String,
        modify: Consumer<Any?>?,
        needle: String,
        limit: Int?,
    ): List<Any?> {
        val query = openQuery(owner, name, modify)
        val pushed = applyTitleSearch(query, title, needle)
        var rows = recordsOf(query)
        if (!pushed && needle.isNotBlank()) {
            val q = needle.lowercase()
            rows = rows.filter { record ->
                val raw = recordAttribute(record, title)
                val label = if (raw == null) "" else raw.toString()
                label.lowercase().contains(q) || recordKey(record).lowercase().contains(q)
            }
        }
        if (limit != null && rows.size > limit) rows = rows.take(limit)
        return rows
    }

    private fun openQuery(owner: Any, name: String, modify: Consumer<Any?>?): Any {
        val method = owner.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            ?: throw FireflyException("Unknown relationship [$name] on [${owner.javaClass.name}]")
        val returned = method.invoke(owner)
            ?: throw FireflyException("Relationship [$name] on [${owner.javaClass.name}] returned null")
        if (returned is Relation<*, *>) {
            return cadenceQuery(returned.related.javaClass, modify)
        }
        if (isDocumentModel(returned.javaClass)) {
            val query = documentQuery(returned)
            if (query is ModelBuilder<*>) {
                throw documentError(returned.javaClass)
            }
            if (modify != null) modify.accept(query)
            return query
        }
        if (returned is ModelBuilder<*> && isDocumentModel(owner.javaClass)) {
            throw documentError(owner.javaClass)
        }
        if (modify != null) modify.accept(returned)
        return returned
    }

    private fun documentQuery(model: Any): Any {
        val method = model.javaClass.methods.firstOrNull { it.name == "query" && it.parameterCount == 0 }
            ?: throw documentError(model.javaClass)
        return method.invoke(model) ?: throw documentError(model.javaClass)
    }

    private fun documentError(type: Class<*>): FireflyException =
        FireflyException("Document model [${type.name}] needs kona-firefly-document on the classpath")

    @Suppress("UNCHECKED_CAST")
    private fun cadenceQuery(model: Class<*>, modify: Consumer<Any?>?): Any {
        val store = RecordStores.open(model) as RecordStore<Any, Any>
        val query = store.query()
        if (modify != null) modify.accept(query)
        return query
    }

    /**
     * Returns true when the query itself was constrained (SQL `like`).
     * Blank needles leave the query untouched and return false so callers skip client filtering too.
     */
    private fun applyTitleSearch(query: Any, title: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        val pattern = "%$needle%"
        if (query is ModelBuilder<*>) {
            query.where(title, "like", pattern)
            return true
        }
        val method = query.javaClass.methods.firstOrNull { method ->
            method.name == "where" &&
                method.parameterCount == 3 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1] == String::class.java
        } ?: return false
        method.invoke(query, title, "like", pattern)
        return true
    }

    private fun recordsOf(query: Any): List<Any?> {
        val method = query.javaClass.methods.firstOrNull {
            (it.name == "get" || it.name == "list") && it.parameterCount == 0
        } ?: throw FireflyException("Query [${query.javaClass.name}] cannot list records")
        val result = method.invoke(query)
        if (result is Collection<*>) return result.toList()
        throw FireflyException("Query [${query.javaClass.name}] did not return a list")
    }
}

internal fun isDocumentModel(type: Class<*>): Boolean {
    var current: Class<*>? = type
    while (current != null) {
        if (current.name == "org.konaframework.cadence.document.Document") return true
        current = current.superclass
    }
    return false
}

internal fun recordKey(record: Any?): String {
    if (record == null) return ""
    if (record is Cadence<*>) {
        val key = record.getKey()
        return if (key == null) "" else key.toString()
    }
    if (record is Map<*, *>) {
        val id = record["id"] ?: return ""
        return id.toString()
    }
    val id = recordAttribute(record, "id")
    return if (id == null) "" else id.toString()
}

internal fun recordAttribute(record: Any?, name: String): Any? {
    if (record == null) return null
    if (record is Map<*, *>) return record[name]
    if (record is Cadence<*>) return record.getAttribute(name)
    val method = record.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
    if (method != null) return method.invoke(record)
    return null
}
