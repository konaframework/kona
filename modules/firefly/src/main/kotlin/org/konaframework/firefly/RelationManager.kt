package org.konaframework.firefly

import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.schemas.htmlAttributes
import org.konaframework.firefly.support.FireflyException
import org.konaframework.firefly.tables.RecordStores
import org.konaframework.firefly.tables.Table
import java.util.function.Function

/** A related record list on a full resource. Simple resources do not use one. */
open class RelationManager(private val name: String) {
    private var titleLiteral = "id"
    private var titleUsing: Function<Get, String>? = null
    private var relatedClass: Class<*>? = null
    private var relatedUsing: Function<Get, Class<*>?>? = null
    private var formSchema: Schema? = null
    private var tableRef: Table<*, *>? = null
    private var attributesLiteral: Map<String, String>? = null
    private var attributesUsing: Function<Get, Map<String, String>>? = null

    init {
        if (name.isBlank()) throw FireflyException("Relation name is blank")
    }

    fun name(): String = name

    fun titleAttribute(value: String): RelationManager = apply {
        if (value.isBlank()) throw FireflyException("Relation title attribute is blank")
        titleLiteral = value
        titleUsing = null
    }

    fun titleAttribute(value: Function<Get, String>): RelationManager = apply { titleUsing = value }

    fun related(type: Class<*>): RelationManager = apply {
        relatedClass = type
        relatedUsing = null
    }

    fun related(type: Function<Get, Class<*>?>): RelationManager = apply {
        relatedUsing = type
        relatedClass = null
    }

    fun form(schema: Schema): RelationManager = apply { formSchema = schema }

    fun table(table: Table<*, *>): RelationManager = apply { tableRef = table }

    fun extraAttributes(values: Map<String, String>): RelationManager = apply {
        attributesLiteral = values
        attributesUsing = null
    }

    fun extraAttributes(values: Function<Get, Map<String, String>>): RelationManager = apply {
        attributesUsing = values
        attributesLiteral = null
    }

    fun query(): Any = RecordStores.open(boundModel()).query() as Any

    fun render(): String {
        val table = tableRef
        val tableHtml = if (table == null) "" else table.render()
        val form = formSchema
        val formHtml = if (form == null) "" else form.render()
        return """<section class="ff-relation" data-ff-relation="${escapeHtml(name)}" data-ff-title="${escapeHtml(titleText())}"${attributeText()}>$tableHtml$formHtml</section>"""
    }

    private fun boundModel(): Class<*> {
        val using = relatedUsing
        if (using != null) {
            val type = using.apply(SchemaBridge.open(emptyMap()))
            if (type == null) throw FireflyException("Relation [$name] has no model")
            return type
        }
        val type = relatedClass
        if (type == null) throw FireflyException("Relation [$name] has no model")
        return type
    }

    private fun titleText(): String {
        val using = titleUsing
        if (using == null) return titleLiteral
        val text = using.apply(SchemaBridge.open(emptyMap()))
        if (text.isBlank()) throw FireflyException("Relation title attribute is blank")
        return text
    }

    private fun attributeText(): String {
        val using = attributesUsing
        val values = if (using != null) using.apply(SchemaBridge.open(emptyMap())) else attributesLiteral
        return htmlAttributes(values)
    }
}
