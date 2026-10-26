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
        val method = owner.javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }
            ?: throw FireflyException("Unknown relationship [$name] on [${owner.javaClass.name}]")
        val returned = method.invoke(owner)
            ?: throw FireflyException("Relationship [$name] on [${owner.javaClass.name}] returned null")
        if (returned is Relation<*, *>) {
            return loadCadence(returned.related.javaClass, modify)
        }
        if (isDocumentModel(returned.javaClass)) {
            val query = documentQuery(returned)
            if (query is ModelBuilder<*>) {
                throw documentError(returned.javaClass)
            }
            return finish(query, modify)
        }
        if (returned is ModelBuilder<*> && isDocumentModel(owner.javaClass)) {
            throw documentError(owner.javaClass)
        }
        return finish(returned, modify)
    }

    private fun finish(query: Any, modify: Consumer<Any?>?): OpenedRelation {
        if (modify != null) modify.accept(query)
        return OpenedRelation(query, recordsOf(query))
    }

    private fun documentQuery(model: Any): Any {
        val method = model.javaClass.methods.firstOrNull { it.name == "query" && it.parameterCount == 0 }
            ?: throw documentError(model.javaClass)
        return method.invoke(model) ?: throw documentError(model.javaClass)
    }

    private fun documentError(type: Class<*>): FireflyException =
        FireflyException("Document model [${type.name}] needs kona-firefly-document on the classpath")

    @Suppress("UNCHECKED_CAST")
    private fun loadCadence(model: Class<*>, modify: Consumer<Any?>?): OpenedRelation {
        val store = RecordStores.open(model) as RecordStore<Any, Any>
        val query = store.query()
        if (modify != null) modify.accept(query)
        return OpenedRelation(query, store.list(query))
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
