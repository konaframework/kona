package org.konaframework.firefly.tables

import org.konaframework.firefly.support.FireflyException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.function.Consumer

/** One operator of a [QueryConstraint]: `key` is submitted; `!key` is the inverse. */
class ConstraintOperator @JvmOverloads constructor(val key: String, val label: String, val inverseLabel: String, val needsValue: Boolean = true)

/** One rule: constraint name, operator key, inverse flag, and values. */
class QueryRule(val constraint: String, val operator: String, val negated: Boolean, val values: List<String>) {
    fun value(): String? = values.firstOrNull()
}

/**
 * A column or relationship the [QueryBuilderFilter] can constrain (Filament query-builder
 * constraints). Subclasses define operators, the value input, and the query.
 */
abstract class QueryConstraint protected constructor(val name: String) {
    private var labelText: String? = null
    private var attributeName: String? = null

    open fun label(value: String): QueryConstraint = apply { labelText = value }

    /** Column to constrain (default: the constraint name). */
    open fun attribute(name: String): QueryConstraint = apply { attributeName = name }

    fun heading(): String = labelText ?: Filter.humanize(name)

    protected fun column(): String = attributeName ?: name

    abstract fun kind(): String

    abstract fun operators(): List<ConstraintOperator>

    /** Constrain [query] for one rule. */
    abstract fun apply(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule)

    /** Value control (no `name`: the builder serializes rules into one field). */
    open fun inputHtml(store: RecordStore<*, *>?, values: List<String>): String =
        """<input class="ff-input" type="text" data-ff-qb-input value="${escapeHtml(values.firstOrNull().orEmpty())}" aria-label="Value" />"""

    /** Whether a submitted [value] is usable for [operator]; invalid rules are dropped, not run. */
    open fun accepts(operator: String, value: String): Boolean = true

    /** Value as shown in an indicator. */
    open fun describeValue(store: RecordStore<*, *>?, values: List<String>): String = values.joinToString(", ")

    fun operator(key: String): ConstraintOperator? = operators().firstOrNull { it.key == key }

    fun describe(store: RecordStore<*, *>?, rule: QueryRule): String {
        val op = operator(rule.operator) ?: return heading()
        val verb = if (rule.negated) op.inverseLabel else op.label
        if (!op.needsValue) return "${heading()} $verb"
        return "${heading()} $verb ${describeValue(store, rule.values)}"
    }
}

/** Text: contains, starts with, ends with, equals, is filled (and inverses). */
open class TextConstraint protected constructor(name: String) : QueryConstraint(name) {
    override fun kind(): String = "text"

    override fun operators(): List<ConstraintOperator> = listOf(
        ConstraintOperator("contains", "contains", "does not contain"),
        ConstraintOperator("startsWith", "starts with", "does not start with"),
        ConstraintOperator("endsWith", "ends with", "does not end with"),
        ConstraintOperator("equals", "equals", "does not equal"),
        ConstraintOperator("isFilled", "is filled", "is blank", needsValue = false),
    )

    override fun apply(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        val value = rule.value().orEmpty()
        val like = if (rule.negated) "not like" else "like"
        when (rule.operator) {
            "contains" -> store.constrain(query, column(), like, "%$value%")
            "startsWith" -> store.constrain(query, column(), like, "$value%")
            "endsWith" -> store.constrain(query, column(), like, "%$value")
            "equals" -> store.constrain(query, column(), if (rule.negated) "!=" else "=", value)
            else -> filled(query, store, rule.negated)
        }
    }

    private fun filled(query: Any, store: RecordStore<Any?, Any?>, negated: Boolean) {
        if (negated) {
            store.group(query, false) { inner ->
                store.constrain(inner, column(), "null", null)
                store.group(inner, true) { blank -> store.constrain(blank, column(), "=", "") }
            }
            return
        }
        store.constrain(query, column(), "not-null", null)
        store.constrain(query, column(), "!=", "")
    }

    companion object {
        @JvmStatic
        fun make(name: String): TextConstraint = TextConstraint(name)
    }
}

/** Numbers: equals, is at least, is at most, is filled (and inverses). */
open class NumberConstraint protected constructor(name: String) : QueryConstraint(name) {
    override fun kind(): String = "number"

    override fun operators(): List<ConstraintOperator> = listOf(
        ConstraintOperator("equals", "equals", "does not equal"),
        ConstraintOperator("isMin", "is at least", "is less than"),
        ConstraintOperator("isMax", "is at most", "is more than"),
        ConstraintOperator("isFilled", "is filled", "is blank", needsValue = false),
    )

    override fun accepts(operator: String, value: String): Boolean = value.trim().toDoubleOrNull() != null

    override fun apply(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        if (rule.operator == "isFilled") {
            store.constrain(query, column(), if (rule.negated) "null" else "not-null", null)
            return
        }
        val raw = rule.values.first().trim()
        val value: Any = raw.toLongOrNull() ?: raw.toDouble()
        val operator = when (rule.operator) {
            "equals" -> if (rule.negated) "!=" else "="
            "isMin" -> if (rule.negated) "<" else ">="
            else -> if (rule.negated) ">" else "<="
        }
        store.constrain(query, column(), operator, value)
    }

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>): String =
        """<input class="ff-input" type="number" step="any" data-ff-qb-input value="${escapeHtml(values.firstOrNull().orEmpty())}" aria-label="Value" />"""

    companion object {
        @JvmStatic
        fun make(name: String): NumberConstraint = NumberConstraint(name)
    }
}

/** Dates: is after, is before, is date, is month, is year, is filled (and inverses). */
open class DateConstraint protected constructor(name: String) : QueryConstraint(name) {
    override fun kind(): String = "date"

    override fun operators(): List<ConstraintOperator> = listOf(
        ConstraintOperator("isAfter", "is after", "is not after"),
        ConstraintOperator("isBefore", "is before", "is not before"),
        ConstraintOperator("isDate", "is date", "is not date"),
        ConstraintOperator("isMonth", "is month", "is not month"),
        ConstraintOperator("isYear", "is year", "is not year"),
        ConstraintOperator("isFilled", "is filled", "is blank", needsValue = false),
    )

    override fun apply(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        val value = rule.value().orEmpty()
        val equal = if (rule.negated) "!=" else "="
        when (rule.operator) {
            "isAfter" -> store.constrainDate(query, column(), "date", if (rule.negated) "<=" else ">", value)
            "isBefore" -> store.constrainDate(query, column(), "date", if (rule.negated) ">=" else "<", value)
            "isDate" -> store.constrainDate(query, column(), "date", equal, value)
            "isMonth" -> store.constrainDate(query, column(), "month", equal, monthOf(value))
            "isYear" -> store.constrainDate(query, column(), "year", equal, yearOf(value))
            else -> store.constrain(query, column(), if (rule.negated) "null" else "not-null", null)
        }
    }

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>): String =
        """<input class="ff-input" type="date" data-ff-qb-input value="${escapeHtml(values.firstOrNull().orEmpty())}" aria-label="Value" />"""

    override fun accepts(operator: String, value: String): Boolean = when (operator) {
        "isMonth" -> ISO.matches(value) || value.trim().toIntOrNull() in 1..12
        "isYear" -> ISO.matches(value) || value.trim().toIntOrNull() != null
        else -> ISO.matches(value)
    }

    private fun monthOf(value: String): Int = isoPart(value, 2)

    private fun yearOf(value: String): Int = isoPart(value, 1)

    private fun isoPart(value: String, group: Int): Int {
        val match = ISO.matchEntire(value) ?: return value.trim().toInt()
        return match.groupValues[group].toInt()
    }

    companion object {
        private val ISO = Regex("(\\d{4})-(\\d{2})-(\\d{2})")

        @JvmStatic
        fun make(name: String): DateConstraint = DateConstraint(name)
    }
}

/** Booleans: is true / is false. */
open class BooleanConstraint protected constructor(name: String) : QueryConstraint(name) {
    override fun kind(): String = "boolean"

    override fun operators(): List<ConstraintOperator> =
        listOf(ConstraintOperator("isTrue", "is true", "is false", needsValue = false))

    override fun apply(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        store.constrain(query, column(), "=", !rule.negated)
    }

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>): String = ""

    companion object {
        @JvmStatic
        fun make(name: String): BooleanConstraint = BooleanConstraint(name)
    }
}

/** Fixed options: is (one of) / is filled (and inverses). */
open class SelectConstraint protected constructor(name: String) : QueryConstraint(name) {
    private var choices: Map<String, String> = emptyMap()
    private var multipleFlag = false

    fun options(values: Map<String, String>): SelectConstraint = apply { choices = LinkedHashMap(values) }

    @JvmOverloads
    fun multiple(on: Boolean = true): SelectConstraint = apply { multipleFlag = on }

    override fun label(value: String): SelectConstraint = apply { super.label(value) }

    override fun attribute(name: String): SelectConstraint = apply { super.attribute(name) }

    override fun kind(): String = "select"

    override fun operators(): List<ConstraintOperator> = listOf(
        ConstraintOperator("is", "is", "is not"),
        ConstraintOperator("isFilled", "is filled", "is blank", needsValue = false),
    )

    override fun apply(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        if (rule.operator == "is") {
            store.constrain(query, column(), if (rule.negated) "not in" else "in", rule.values)
            return
        }
        store.constrain(query, column(), if (rule.negated) "null" else "not-null", null)
    }

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>): String =
        selectInput(choices, values, multipleFlag)

    override fun describeValue(store: RecordStore<*, *>?, values: List<String>): String =
        values.joinToString(", ") { choices[it] ?: it }

    companion object {
        @JvmStatic
        fun make(name: String): SelectConstraint = SelectConstraint(name)

        internal fun selectInput(choices: Map<String, String>, values: List<String>, multiple: Boolean): String = buildString {
            append("""<select class="ff-input" data-ff-qb-input aria-label="Value"${if (multiple) " multiple" else ""}>""")
            if (!multiple) append("""<option value="">Choose…</option>""")
            for ((value, label) in choices) {
                val mark = if (value in values) " selected" else ""
                append("""<option value="${escapeHtml(value)}"$mark>${escapeHtml(label)}</option>""")
            }
            append("</select>")
        }
    }
}

/** Relationship: has any related record / is related to (and inverses). */
open class RelationshipConstraint protected constructor(name: String) : QueryConstraint(name) {
    private var titleAttribute = "name"
    private var multipleFlag = true
    private var modify: Consumer<Any>? = null
    private var limit = 50

    fun titleAttribute(name: String): RelationshipConstraint = apply { titleAttribute = name }

    @JvmOverloads
    fun multiple(on: Boolean = true): RelationshipConstraint = apply { multipleFlag = on }

    fun modifyOptionsQueryUsing(hook: Consumer<Any>): RelationshipConstraint = apply { modify = hook }

    fun optionsLimit(count: Int): RelationshipConstraint = apply {
        if (count < 1) throw FireflyException("Options limit must be at least 1")
        limit = count
    }

    override fun label(value: String): RelationshipConstraint = apply { super.label(value) }

    override fun kind(): String = "relationship"

    override fun operators(): List<ConstraintOperator> = listOf(
        ConstraintOperator("isRelatedTo", "is", "is not"),
        ConstraintOperator("has", "has any", "has none", needsValue = false),
    )

    override fun apply(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        if (rule.operator == "isRelatedTo") {
            store.constrainRelationship(query, name, rule.values, rule.negated)
            return
        }
        store.constrainRelationship(query, name, null, rule.negated)
    }

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>): String {
        val choices = store?.relationshipOptions(name, titleAttribute, modify, null, limit, null).orEmpty()
        return SelectConstraint.selectInput(choices, values, multipleFlag)
    }

    override fun describeValue(store: RecordStore<*, *>?, values: List<String>): String {
        val labels = store?.relationshipOptions(name, titleAttribute, modify, null, null, values).orEmpty()
        return values.joinToString(", ") { labels[it] ?: it }
    }

    companion object {
        @JvmStatic
        fun make(name: String): RelationshipConstraint = RelationshipConstraint(name)
    }
}

/**
 * Advanced filter (Filament `QueryBuilder`): users add rules — constraint, operator
 * (or its inverse), value — inside groups. Rules in a group are `and`-ed; groups are `or`-ed.
 * State is one query-string field: groups split by `|`, rules by `;`, `constraint:op:v1,v2`
 * with URL-encoded parts and `!op` for the inverse.
 */
open class QueryBuilderFilter protected constructor(name: String) : Filter(name) {
    private val constraintList = ArrayList<QueryConstraint>()
    private var pickerColumns = 1

    fun constraints(vararg constraints: QueryConstraint): QueryBuilderFilter = apply {
        constraintList.clear()
        constraintList.addAll(constraints)
    }

    fun constraintPickerColumns(count: Int): QueryBuilderFilter = apply {
        if (count < 1) throw FireflyException("Constraint picker columns must be at least 1")
        pickerColumns = count
    }

    fun constraints(): List<QueryConstraint> = constraintList.toList()

    override fun label(value: String): QueryBuilderFilter = apply { super.label(value) }

    override fun indicator(value: String): QueryBuilderFilter = apply { super.indicator(value) }

    override fun modifyFormFieldUsing(modifier: Consumer<FilterField>): QueryBuilderFilter = apply { super.modifyFormFieldUsing(modifier) }

    override fun columnSpan(count: Int): QueryBuilderFilter = apply { super.columnSpan(count) }

    override fun columnSpanFull(): QueryBuilderFilter = apply { super.columnSpanFull() }

    override fun hidden(on: Boolean): QueryBuilderFilter = apply { super.hidden(on) }

    override fun visible(on: Boolean): QueryBuilderFilter = apply { super.visible(on) }

    override fun control(cell: Cell): FilterControl = FilterControl.TEXT

    override fun kind(cell: Cell): String = "query-builder"

    /** Parsed `or` groups of `and` rules; unknown constraints, operators, and empty values are dropped. */
    fun groups(data: FilterData): List<List<QueryRule>> {
        val raw = data.string(FilterData.VALUE) ?: return emptyList()
        return raw.split('|').map { group ->
            group.split(';').mapNotNull { parse(it) }
        }.filter { it.isNotEmpty() }
    }

    override fun isActive(data: FilterData): Boolean = groups(data).isNotEmpty()

    override fun applyToQuery(query: Any, store: RecordStore<*, *>, data: FilterData) {
        @Suppress("UNCHECKED_CAST")
        val typed = store as RecordStore<Any?, Any?>
        groups(data).forEachIndexed { index, rules ->
            typed.group(query, index > 0) { inner ->
                for (rule in rules) constraint(rule.constraint).apply(inner as Any, typed, rule)
            }
        }
    }

    override fun indicators(context: FilterRenderContext): List<FilterIndicator> {
        if (hasIndicatorOverride()) return super.indicators(context)
        val groups = groups(context.data)
        if (groups.isEmpty()) return emptyList()
        val text = groups.joinToString(" or ") { rules ->
            rules.joinToString(" and ") { rule -> constraint(rule.constraint).describe(context.store, rule) }
        }
        return listOf(FilterIndicator.make(text))
    }

    override fun renderControl(context: FilterRenderContext, field: FilterField): String {
        val name = escapeHtml(context.inputName())
        val stored = context.data.string(FilterData.VALUE).orEmpty()
        val groups = groups(context.data).ifEmpty { listOf(emptyList()) }
        val groupsHtml = groups.joinToString("") { rules -> groupHtml(context.store, rules) }
        val templates = constraintList.joinToString("") { constraint ->
            """<template data-ff-qb-template="${escapeHtml(constraint.name)}">${ruleHtml(context.store, constraint, null)}</template>"""
        }
        val groupTemplate = """<template data-ff-qb-group-template>${groupHtml(context.store, emptyList())}</template>"""
        return fieldLabel(field.labelText ?: heading(context.cell), field) +
            """<div class="ff-qb" data-ff-qb data-ff-qb-picker-columns="$pickerColumns"><input type="hidden" name="$name" value="${escapeHtml(stored)}" data-ff-qb-state data-ff-filter-input /><div class="ff-qb-groups" data-ff-qb-groups>$groupsHtml</div><button type="button" class="ff-qb-add-group" data-ff-qb-add-group>Add “or” group</button>$templates$groupTemplate</div>"""
    }

    private fun groupHtml(store: RecordStore<*, *>?, rules: List<QueryRule>): String {
        val rows = rules.joinToString("") { rule -> ruleHtml(store, constraint(rule.constraint), rule) }
        val picker = constraintList.joinToString("") { """<option value="${escapeHtml(it.name)}">${escapeHtml(it.heading())}</option>""" }
        return """<div class="ff-qb-group" data-ff-qb-group><div class="ff-qb-rules" data-ff-qb-rules>$rows</div><div class="ff-qb-group-actions"><select class="ff-input ff-qb-add" data-ff-qb-add aria-label="Add rule"><option value="">Add rule…</option>$picker</select><button type="button" class="ff-qb-remove-group" data-ff-qb-remove-group>Remove group</button></div></div>"""
    }

    private fun ruleHtml(store: RecordStore<*, *>?, constraint: QueryConstraint, rule: QueryRule?): String {
        val selected = rule?.let { if (it.negated) "!${it.operator}" else it.operator }
        val operators = constraint.operators().flatMap { op ->
            listOf("${op.key}" to op.label, "!${op.key}" to op.inverseLabel).map { (key, label) -> Triple(key, label, op.needsValue) }
        }
        val current = selected ?: operators.first().first
        val options = operators.joinToString("") { (key, label, needsValue) ->
            val mark = if (key == current) " selected" else ""
            """<option value="${escapeHtml(key)}" data-ff-qb-novalue="${!needsValue}"$mark>${escapeHtml(label)}</option>"""
        }
        val needsValue = operators.first { it.first == current }.third
        val input = constraint.inputHtml(store, rule?.values.orEmpty())
        val hidden = if (needsValue) "" else " hidden"
        return """<div class="ff-qb-rule" data-ff-qb-rule data-ff-qb-constraint="${escapeHtml(constraint.name)}" data-ff-qb-kind="${constraint.kind()}"><span class="ff-qb-rule-label">${escapeHtml(constraint.heading())}</span><select class="ff-input ff-qb-operator" data-ff-qb-operator aria-label="Operator">$options</select><span class="ff-qb-value" data-ff-qb-value$hidden>$input</span><button type="button" class="ff-qb-remove" data-ff-qb-remove aria-label="Remove rule">×</button></div>"""
    }

    private fun constraint(name: String): QueryConstraint = constraintList.first { it.name == name }

    private fun parse(raw: String): QueryRule? {
        val parts = raw.split(':')
        if (parts.size < 2) return null
        val constraint = constraintList.firstOrNull { it.name == decode(parts[0]) } ?: return null
        val negated = parts[1].startsWith("!")
        val key = parts[1].removePrefix("!")
        val op = constraint.operator(key) ?: return null
        val values = if (parts.size > 2) parts.drop(2).joinToString(":").split(',').map { decode(it) }.filter { it.isNotEmpty() } else emptyList()
        if (op.needsValue && (values.isEmpty() || values.any { !constraint.accepts(key, it) })) return null
        return QueryRule(constraint.name, key, negated, values)
    }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)

    companion object {
        @JvmStatic
        fun make(name: String): QueryBuilderFilter = finish(QueryBuilderFilter(name))

        /** Serialize rules into the query-string format the builder UI writes. */
        @JvmStatic
        fun encode(groups: List<List<QueryRule>>): String = groups.joinToString("|") { rules ->
            rules.joinToString(";") { rule ->
                val op = if (rule.negated) "!${rule.operator}" else rule.operator
                val values = rule.values.joinToString(",") { URLEncoder.encode(it, Charsets.UTF_8) }
                "${URLEncoder.encode(rule.constraint, Charsets.UTF_8)}:$op:$values"
            }
        }
    }
}
