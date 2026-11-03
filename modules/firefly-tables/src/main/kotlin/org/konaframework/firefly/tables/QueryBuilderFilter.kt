package org.konaframework.firefly.tables

import org.konaframework.firefly.support.FireflyException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.function.Consumer

/** Applies a custom [ConstraintOperator] to the store query (`column` is the constraint attribute). */
fun interface ConstraintOperatorApply {
    fun apply(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule, column: String)
}

/** One operator of a [QueryConstraint]: `key` is submitted; `!key` is the inverse. */
class ConstraintOperator @JvmOverloads constructor(
    val key: String,
    val label: String,
    val inverseLabel: String,
    val needsValue: Boolean = true,
    private val applyUsing: ConstraintOperatorApply? = null,
) {
    /** Run a custom apply hook when set; returns false so the constraint uses its built-in apply. */
    fun applyCustom(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule, column: String): Boolean {
        val hook = applyUsing ?: return false
        hook.apply(query, store, rule, column)
        return true
    }
}

/** One rule: constraint name, operator key, inverse flag, and values. */
class QueryRule(val constraint: String, val operator: String, val negated: Boolean, val values: List<String>) {
    fun value(): String? = values.firstOrNull()
}

/**
 * Parsed query-builder expression tree. Flat `a;b|c` is an [Or] of [And]s; parentheses nest
 * (`a;(b|c)` is an [And] of a leaf and an [Or]).
 */
sealed class QueryNode {
    data class Leaf(val rule: QueryRule) : QueryNode()
    data class And(val children: List<QueryNode>) : QueryNode()
    data class Or(val children: List<QueryNode>) : QueryNode()

    fun isEmpty(): Boolean = when (this) {
        is Leaf -> false
        is And -> children.isEmpty()
        is Or -> children.isEmpty()
    }
}

/**
 * A column or relationship the [QueryBuilderFilter] can constrain (Filament query-builder
 * constraints). Subclasses define operators, the value input, and the query.
 */
abstract class QueryConstraint protected constructor(val name: String) {
    private var labelText: String? = null
    private var attributeName: String? = null
    private var operatorOverride: List<ConstraintOperator>? = null

    open fun label(value: String): QueryConstraint = apply { labelText = value }

    /** Column to constrain (default: the constraint name). */
    open fun attribute(name: String): QueryConstraint = apply { attributeName = name }

    /**
     * Replace the built-in operator list (Filament `->operators([...])`). Custom operators that
     * supply an [ConstraintOperatorApply] run that hook; others still use the constraint's
     * built-in [applyDefault] when the key matches.
     */
    open fun operators(vararg ops: ConstraintOperator): QueryConstraint = apply {
        if (ops.isEmpty()) throw FireflyException("operators must not be empty")
        operatorOverride = ops.toList()
    }

    fun heading(): String = labelText ?: Filter.humanize(name)

    protected fun column(): String = attributeName ?: name

    abstract fun kind(): String

    fun operators(): List<ConstraintOperator> = operatorOverride ?: defaultOperators()

    /** Built-in operators when [operators] has not replaced them. */
    protected abstract fun defaultOperators(): List<ConstraintOperator>

    /** Constrain [query] for one rule (custom operator hooks first, then [applyDefault]). */
    fun apply(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        val op = operator(rule.operator) ?: return
        if (op.applyCustom(query, store, rule, column())) return
        if (defaultOperators().none { it.key == rule.operator }) return
        applyDefault(query, store, rule)
    }

    /** Built-in query for this constraint's default operator keys. */
    protected abstract fun applyDefault(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule)

    /**
     * Value control (no `name`: the builder serializes rules into one field).
     * [optionsPath] is the form-options field for searchable relationship pickers
     * (`filters.query.author`).
     */
    open fun inputHtml(store: RecordStore<*, *>?, values: List<String>, optionsPath: String? = null): String =
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
    private var insensitiveFlag = false

    /** Use case-insensitive matching (`ilike` on SQL; memory `like` is already case-insensitive). */
    @JvmOverloads
    fun insensitive(on: Boolean = true): TextConstraint = apply { insensitiveFlag = on }

    override fun label(value: String): TextConstraint = apply { super.label(value) }

    override fun attribute(name: String): TextConstraint = apply { super.attribute(name) }

    override fun operators(vararg ops: ConstraintOperator): TextConstraint = apply { super.operators(*ops) }

    override fun kind(): String = "text"

    override fun defaultOperators(): List<ConstraintOperator> = listOf(
        ConstraintOperator("contains", "contains", "does not contain"),
        ConstraintOperator("startsWith", "starts with", "does not start with"),
        ConstraintOperator("endsWith", "ends with", "does not end with"),
        ConstraintOperator("equals", "equals", "does not equal"),
        ConstraintOperator("isFilled", "is filled", "is blank", needsValue = false),
    )

    override fun applyDefault(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        val value = rule.value().orEmpty()
        val like = when {
            insensitiveFlag && rule.negated -> "not ilike"
            insensitiveFlag -> "ilike"
            rule.negated -> "not like"
            else -> "like"
        }
        when (rule.operator) {
            "contains" -> store.constrain(query, column(), like, "%$value%")
            "startsWith" -> store.constrain(query, column(), like, "$value%")
            "endsWith" -> store.constrain(query, column(), like, "%$value")
            "equals" -> {
                if (insensitiveFlag) {
                    store.constrain(query, column(), like, value)
                } else {
                    store.constrain(query, column(), if (rule.negated) "!=" else "=", value)
                }
            }
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

    override fun defaultOperators(): List<ConstraintOperator> = listOf(
        ConstraintOperator("equals", "equals", "does not equal"),
        ConstraintOperator("isMin", "is at least", "is less than"),
        ConstraintOperator("isMax", "is at most", "is more than"),
        ConstraintOperator("isFilled", "is filled", "is blank", needsValue = false),
    )

    override fun accepts(operator: String, value: String): Boolean = value.trim().toDoubleOrNull() != null

    override fun applyDefault(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
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

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>, optionsPath: String?): String =
        """<input class="ff-input" type="number" step="any" data-ff-qb-input value="${escapeHtml(values.firstOrNull().orEmpty())}" aria-label="Value" />"""

    companion object {
        @JvmStatic
        fun make(name: String): NumberConstraint = NumberConstraint(name)
    }
}

/** Dates: is after, is before, is date, is month, is year, is filled (and inverses). */
open class DateConstraint protected constructor(name: String) : QueryConstraint(name) {
    override fun kind(): String = "date"

    override fun defaultOperators(): List<ConstraintOperator> = listOf(
        ConstraintOperator("isAfter", "is after", "is not after"),
        ConstraintOperator("isBefore", "is before", "is not before"),
        ConstraintOperator("isDate", "is date", "is not date"),
        ConstraintOperator("isMonth", "is month", "is not month"),
        ConstraintOperator("isYear", "is year", "is not year"),
        ConstraintOperator("isFilled", "is filled", "is blank", needsValue = false),
    )

    override fun applyDefault(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
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

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>, optionsPath: String?): String =
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

    override fun defaultOperators(): List<ConstraintOperator> =
        listOf(ConstraintOperator("isTrue", "is true", "is false", needsValue = false))

    override fun applyDefault(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        store.constrain(query, column(), "=", !rule.negated)
    }

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>, optionsPath: String?): String = ""

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

    override fun defaultOperators(): List<ConstraintOperator> = listOf(
        ConstraintOperator("is", "is", "is not"),
        ConstraintOperator("isFilled", "is filled", "is blank", needsValue = false),
    )

    override fun applyDefault(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        if (rule.operator == "is") {
            store.constrain(query, column(), if (rule.negated) "not in" else "in", rule.values)
            return
        }
        store.constrain(query, column(), if (rule.negated) "null" else "not-null", null)
    }

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>, optionsPath: String?): String =
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

/** Relationship: has any related record / is related to (and inverses); count ops when [multiple]. */
open class RelationshipConstraint protected constructor(name: String) : QueryConstraint(name) {
    private var titleAttribute = "name"
    private var multipleFlag = true
    private var modify: Consumer<Any>? = null
    private var limit = 50
    private var searchableFlag = false
    private var preloadFlag = false

    fun titleAttribute(name: String): RelationshipConstraint = apply { titleAttribute = name }

    @JvmOverloads
    fun multiple(on: Boolean = true): RelationshipConstraint = apply { multipleFlag = on }

    /** Searchable value combobox (Filament `searchable()`); options load via table `form-options`. */
    @JvmOverloads
    fun searchable(on: Boolean = true): RelationshipConstraint = apply { searchableFlag = on }

    /** Prefill options when [searchable] (Filament `preload()`). */
    @JvmOverloads
    fun preload(on: Boolean = true): RelationshipConstraint = apply { preloadFlag = on }

    fun modifyOptionsQueryUsing(hook: Consumer<Any>): RelationshipConstraint = apply { modify = hook }

    fun optionsLimit(count: Int): RelationshipConstraint = apply {
        if (count < 1) throw FireflyException("Options limit must be at least 1")
        limit = count
    }

    override fun label(value: String): RelationshipConstraint = apply { super.label(value) }

    override fun kind(): String = "relationship"

    fun isSearchable(): Boolean = searchableFlag

    fun isPreload(): Boolean = preloadFlag

    fun titleAttribute(): String = titleAttribute

    fun modifyOptionsQuery(): Consumer<Any>? = modify

    fun optionsLimit(): Int = limit

    override fun defaultOperators(): List<ConstraintOperator> {
        val base = listOf(
            ConstraintOperator("isRelatedTo", "is", "is not"),
            ConstraintOperator("has", "has any", "has none", needsValue = false),
        )
        if (!multipleFlag) return base
        return base + listOf(
            ConstraintOperator("hasMin", "has minimum", "has fewer than"),
            ConstraintOperator("hasMax", "has maximum", "has more than"),
            ConstraintOperator("hasCount", "has exactly", "does not have exactly"),
        )
    }

    override fun accepts(operator: String, value: String): Boolean = when (operator) {
        "hasMin", "hasMax", "hasCount" -> value.trim().toIntOrNull()?.let { it >= 0 } == true
        else -> true
    }

    override fun applyDefault(query: Any, store: RecordStore<Any?, Any?>, rule: QueryRule) {
        when (rule.operator) {
            "isRelatedTo" -> store.constrainRelationship(query, name, rule.values, rule.negated)
            "has" -> store.constrainRelationship(query, name, null, rule.negated)
            "hasMin", "hasMax", "hasCount" -> {
                val count = rule.values.first().trim().toInt()
                val operator = when (rule.operator) {
                    "hasMin" -> if (rule.negated) "<" else ">="
                    "hasMax" -> if (rule.negated) ">" else "<="
                    else -> if (rule.negated) "!=" else "="
                }
                store.constrainRelationshipCount(query, name, operator, count)
            }
        }
    }

    override fun inputHtml(store: RecordStore<*, *>?, values: List<String>, optionsPath: String?): String {
        if (searchableFlag) {
            val choices = when {
                store == null -> emptyMap()
                preloadFlag || values.isNotEmpty() -> {
                    val preloaded = if (preloadFlag) {
                        store.relationshipOptions(name, titleAttribute, modify, null, limit, null)
                    } else {
                        emptyMap()
                    }
                    val selected = if (values.isNotEmpty()) {
                        store.relationshipOptions(name, titleAttribute, modify, null, null, values)
                    } else {
                        emptyMap()
                    }
                    LinkedHashMap<String, String>().apply {
                        putAll(preloaded)
                        putAll(selected)
                    }
                }
                else -> emptyMap()
            }
            val remote = store != null
            return FilterSelectMarkup.combobox(
                name = optionsPath.orEmpty(),
                choices = choices,
                selected = values.toSet(),
                placeholder = "Choose…",
                multiple = multipleFlag,
                searchable = true,
                allowClear = !multipleFlag,
                remoteRelation = if (remote) name else null,
                remote = remote,
                extra = "",
                queryBuilder = true,
                optionsPath = optionsPath,
            )
        }
        val choices = store?.relationshipOptions(name, titleAttribute, modify, null, limit, null).orEmpty()
        return SelectConstraint.selectInput(choices, values, multipleFlag)
    }

    /** Options for remote search (`Table.filterOptions` on `queryBuilder.constraint`). */
    fun searchOptions(store: RecordStore<*, *>?, search: String?): Map<String, String> {
        if (store == null) return emptyMap()
        return store.relationshipOptions(name, titleAttribute, modify, search, limit, null)
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
 * Parentheses nest deeper (`a;(b|c)`). State is one query-string field: `|` / `;` / `constraint:op:v1,v2`
 * with URL-encoded parts and `!op` for the inverse.
 */
open class QueryBuilderFilter protected constructor(name: String) : Filter(name) {
    private val constraintList = ArrayList<QueryConstraint>()
    private var pickerColumns = 1
    private var pickerWidth: String? = null
    private var searchablePicker = false

    fun constraints(vararg constraints: QueryConstraint): QueryBuilderFilter = apply {
        constraintList.clear()
        constraintList.addAll(constraints)
    }

    fun constraintPickerColumns(count: Int): QueryBuilderFilter = apply {
        if (count < 1) throw FireflyException("Constraint picker columns must be at least 1")
        pickerColumns = count
    }

    /** Max width of the "Add rule…" constraint picker (CSS length like `18rem`). */
    fun constraintPickerWidth(value: String): QueryBuilderFilter = apply {
        if (!PICKER_WIDTH.matches(value)) throw FireflyException("Constraint picker width must be a CSS length like 18rem")
        pickerWidth = value
    }

    /** Searchable "Add rule…" picker for long constraint lists. */
    @JvmOverloads
    fun searchableConstraintPicker(on: Boolean = true): QueryBuilderFilter = apply { searchablePicker = on }

    fun constraints(): List<QueryConstraint> = constraintList.toList()

    /** Named constraint (for `Table.filterOptions("query.author", …)`). */
    fun constraintNamed(name: String): QueryConstraint? = constraintList.firstOrNull { it.name == name }

    override fun label(value: String): QueryBuilderFilter = apply { super.label(value) }

    override fun indicator(value: String): QueryBuilderFilter = apply { super.indicator(value) }

    override fun modifyFormFieldUsing(modifier: Consumer<FilterField>): QueryBuilderFilter = apply { super.modifyFormFieldUsing(modifier) }

    override fun columnSpan(count: Int): QueryBuilderFilter = apply { super.columnSpan(count) }

    override fun columnSpanFull(): QueryBuilderFilter = apply { super.columnSpanFull() }

    override fun hidden(on: Boolean): QueryBuilderFilter = apply { super.hidden(on) }

    override fun visible(on: Boolean): QueryBuilderFilter = apply { super.visible(on) }

    override fun control(cell: Cell): FilterControl = FilterControl.TEXT

    override fun kind(cell: Cell): String = "query-builder"

    /** Expression tree for [data]; empty when inactive. */
    fun tree(data: FilterData): QueryNode? {
        // [FilterData.string] already trims and maps blanks to null.
        val raw = data.string(FilterData.VALUE) ?: return null
        val parsed = parseExpression(raw) ?: return null
        return ensureGrouped(parsed)
    }

    /** Keep a top-level OR of AND groups so indicator paths stay `__qb:group:rule`. */
    private fun ensureGrouped(node: QueryNode): QueryNode = when (node) {
        is QueryNode.Or -> QueryNode.Or(
            node.children.map { child ->
                when (child) {
                    is QueryNode.And -> child
                    is QueryNode.Or -> child
                    is QueryNode.Leaf -> QueryNode.And(listOf(child))
                }
            },
        )
        is QueryNode.And -> QueryNode.Or(listOf(node))
        is QueryNode.Leaf -> QueryNode.Or(listOf(QueryNode.And(listOf(node))))
    }

    /**
     * Flat `or` groups of `and` rules when the expression has no nested parentheses;
     * otherwise each top-level OR child is expanded to its leaf rules only when that child is a flat AND.
     * Prefer [tree] for nested expressions.
     */
    fun groups(data: FilterData): List<List<QueryRule>> {
        val root = tree(data) ?: return emptyList()
        return flattenOrOfAnd(root)
    }

    override fun isActive(data: FilterData): Boolean = tree(data) != null

    override fun applyToQuery(query: Any, store: RecordStore<*, *>, data: FilterData) {
        @Suppress("UNCHECKED_CAST")
        val typed = store as RecordStore<Any?, Any?>
        // Caller ([Filter.apply]) only invokes this when [isActive]; tree is therefore non-null.
        applyNode(query, typed, tree(data)!!, orJoin = false)
    }

    override fun indicators(context: FilterRenderContext): List<FilterIndicator> {
        if (hasIndicatorOverride()) return super.indicators(context)
        val root = tree(context.data) ?: return emptyList()
        val out = ArrayList<FilterIndicator>()
        walkLeaves(root, emptyList()) { path, rule ->
            out.add(
                FilterIndicator.make(constraint(rule.constraint).describe(context.store, rule))
                    .removeField(ruleField(path)),
            )
        }
        return out
    }

    override fun applyIndicatorRemoval(data: FilterData, fields: List<String>): FilterData {
        if (fields.isEmpty()) return FilterData(emptyMap())
        val targets = fields.mapNotNull { parseRuleField(it) }.toSet()
        if (targets.isEmpty()) return data.without(fields)
        val root = tree(data) ?: return FilterData(emptyMap())
        val next = removeLeaves(root, emptyList(), targets) ?: return FilterData(emptyMap())
        return FilterData.single(encodeNode(next))
    }

    override fun renderControl(context: FilterRenderContext, field: FilterField): String {
        val name = escapeHtml(context.inputName())
        val stored = context.data.string(FilterData.VALUE).orEmpty()
        val root = tree(context.data)
        val groupsHtml = if (root == null) {
            groupHtml(context.store, emptyList(), context.inputName(), orGroup = false)
        } else {
            // [tree] always wraps as a top-level OR of groups.
            (root as QueryNode.Or).children.joinToString("") { child ->
                renderChildGroup(context.store, child, context.inputName())
            }
        }
        val optionsPathPrefix = context.inputName()
        val templates = constraintList.joinToString("") { c ->
            """<template data-ff-qb-template="${escapeHtml(c.name)}">${ruleHtml(context.store, c, null, optionsPathPrefix)}</template>"""
        }
        val groupTemplate =
            """<template data-ff-qb-group-template>${groupHtml(context.store, emptyList(), optionsPathPrefix, orGroup = false)}</template>"""
        val nestedOrTemplate =
            """<template data-ff-qb-nested-or-template>${groupHtml(context.store, emptyList(), optionsPathPrefix, orGroup = true)}</template>"""
        val width = pickerWidth?.let { """ style="--ff-qb-picker-width: ${escapeHtml(it)}"""" }.orEmpty()
        val searchable = if (searchablePicker) """ data-ff-qb-searchable-picker="true"""" else ""
        return fieldLabel(field.labelText ?: heading(context.cell), field) +
            """<div class="ff-qb" data-ff-qb data-ff-qb-picker-columns="$pickerColumns"$searchable$width><input type="hidden" name="$name" value="${escapeHtml(stored)}" data-ff-qb-state data-ff-filter-input /><div class="ff-qb-groups" data-ff-qb-groups>$groupsHtml</div><button type="button" class="ff-qb-add-group" data-ff-qb-add-group>Add “or” group</button>$templates$groupTemplate$nestedOrTemplate</div>"""
    }

    private fun renderChildGroup(store: RecordStore<*, *>?, node: QueryNode, optionsPathPrefix: String): String =
        when (node) {
            is QueryNode.And -> groupHtml(
                store,
                node.children.mapNotNull { (it as? QueryNode.Leaf)?.rule },
                optionsPathPrefix,
                orGroup = false,
                nested = node.children.filter { it !is QueryNode.Leaf },
            )
            is QueryNode.Or -> groupHtml(
                store,
                emptyList(),
                optionsPathPrefix,
                orGroup = true,
                nested = node.children,
            )
            is QueryNode.Leaf -> renderChildGroup(store, QueryNode.And(listOf(node)), optionsPathPrefix)
        }

    private fun groupHtml(
        store: RecordStore<*, *>?,
        rules: List<QueryRule>,
        optionsPathPrefix: String,
        orGroup: Boolean,
        nested: List<QueryNode> = emptyList(),
    ): String {
        val rows = rules.joinToString("") { rule -> ruleHtml(store, constraint(rule.constraint), rule, optionsPathPrefix) }
        val nestedHtml = nested.joinToString("") { renderChildGroup(store, it, optionsPathPrefix) }
        val picker = pickerHtml()
        val orAttr = if (orGroup) """ data-ff-qb-or="true"""" else ""
        val nestLabel = if (orGroup) "or" else "and"
        return """<div class="ff-qb-group" data-ff-qb-group$orAttr><div class="ff-qb-rules" data-ff-qb-rules>$rows$nestedHtml</div><div class="ff-qb-group-actions">$picker<button type="button" class="ff-qb-add-nested" data-ff-qb-add-nested aria-label="Add nested $nestLabel group">Add nested group</button><button type="button" class="ff-qb-remove-group" data-ff-qb-remove-group>Remove group</button></div></div>"""
    }

    private fun pickerHtml(): String {
        val options = constraintList.joinToString("") {
            """<option value="${escapeHtml(it.name)}">${escapeHtml(it.heading())}</option>"""
        }
        if (!searchablePicker) {
            return """<select class="ff-input ff-qb-add" data-ff-qb-add aria-label="Add rule"><option value="">Add rule…</option>$options</select>"""
        }
        val items = constraintList.joinToString("") {
            """<button type="button" class="ff-qb-picker-option" data-ff-qb-pick="${escapeHtml(it.name)}" hidden>${escapeHtml(it.heading())}</button>"""
        }
        return """<div class="ff-qb-picker" data-ff-qb-picker><input class="ff-input ff-qb-add" type="search" data-ff-qb-add-search placeholder="Add rule…" aria-label="Add rule" autocomplete="off" /><div class="ff-qb-picker-menu" data-ff-qb-picker-menu hidden>$items</div></div>"""
    }

    private fun ruleHtml(
        store: RecordStore<*, *>?,
        constraint: QueryConstraint,
        rule: QueryRule?,
        optionsPathPrefix: String,
    ): String {
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
        val path = "$optionsPathPrefix.${constraint.name}"
        val input = constraint.inputHtml(store, rule?.values.orEmpty(), path)
        val hidden = if (needsValue) "" else " hidden"
        return """<div class="ff-qb-rule" data-ff-qb-rule data-ff-qb-constraint="${escapeHtml(constraint.name)}" data-ff-qb-kind="${constraint.kind()}"><span class="ff-qb-rule-label">${escapeHtml(constraint.heading())}</span><select class="ff-input ff-qb-operator" data-ff-qb-operator aria-label="Operator">$options</select><span class="ff-qb-value" data-ff-qb-value$hidden>$input</span><button type="button" class="ff-qb-remove" data-ff-qb-remove aria-label="Remove rule">×</button></div>"""
    }

    private fun applyNode(query: Any, store: RecordStore<Any?, Any?>, node: QueryNode, orJoin: Boolean) {
        when (node) {
            is QueryNode.Leaf -> constraint(node.rule.constraint).apply(query, store, node.rule)
            is QueryNode.And -> store.group(query, orJoin) { inner ->
                node.children.forEach { child -> applyNode(inner as Any, store, child, orJoin = false) }
            }
            is QueryNode.Or -> store.group(query, orJoin) { inner ->
                node.children.forEachIndexed { index, child ->
                    // Leaf arms need their own group so `orJoin` can attach as orWhereNested.
                    val arm = if (child is QueryNode.Leaf) QueryNode.And(listOf(child)) else child
                    applyNode(inner as Any, store, arm, orJoin = index > 0)
                }
            }
        }
    }

    private fun flattenOrOfAnd(node: QueryNode): List<List<QueryRule>> =
        (node as QueryNode.Or).children.map { flattenAnd(it) }.filter { it.isNotEmpty() }

    private fun flattenAnd(node: QueryNode): List<QueryRule> = when (node) {
        is QueryNode.Leaf -> listOf(node.rule)
        is QueryNode.And -> node.children.flatMap { flattenAnd(it) }
        is QueryNode.Or -> node.children.flatMap { flattenAnd(it) }
    }

    private fun walkLeaves(node: QueryNode, path: List<Int>, visit: (List<Int>, QueryRule) -> Unit) {
        when (node) {
            is QueryNode.Leaf -> visit(path, node.rule)
            is QueryNode.And -> node.children.forEachIndexed { i, child -> walkLeaves(child, path + i, visit) }
            is QueryNode.Or -> node.children.forEachIndexed { i, child -> walkLeaves(child, path + i, visit) }
        }
    }

    private fun removeLeaves(node: QueryNode, path: List<Int>, targets: Set<List<Int>>): QueryNode? {
        when (node) {
            is QueryNode.Leaf -> return if (path in targets) null else node
            is QueryNode.And -> {
                val kept = node.children.mapIndexedNotNull { i, child -> removeLeaves(child, path + i, targets) }
                return when {
                    kept.isEmpty() -> null
                    kept.size == 1 -> kept[0]
                    else -> QueryNode.And(kept)
                }
            }
            is QueryNode.Or -> {
                val kept = node.children.mapIndexedNotNull { i, child -> removeLeaves(child, path + i, targets) }
                return when {
                    kept.isEmpty() -> null
                    kept.size == 1 -> kept[0]
                    else -> QueryNode.Or(kept)
                }
            }
        }
    }

    private fun constraint(name: String): QueryConstraint = constraintList.first { it.name == name }

    private fun parseExpression(raw: String): QueryNode? {
        val parser = ExpressionParser(raw, ::parseRule)
        return try {
            normalize(parser.parse())
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun normalize(node: QueryNode): QueryNode? = when (node) {
        is QueryNode.Leaf -> node
        is QueryNode.And -> {
            val kids = node.children.mapNotNull { normalize(it) }
            when {
                kids.isEmpty() -> null
                kids.size == 1 -> kids[0]
                else -> QueryNode.And(kids)
            }
        }
        is QueryNode.Or -> {
            val kids = node.children.mapNotNull { normalize(it) }
            when {
                kids.isEmpty() -> null
                kids.size == 1 -> kids[0]
                else -> QueryNode.Or(kids)
            }
        }
    }

    private fun parseRule(raw: String): QueryRule? {
        val parts = raw.split(':')
        if (parts.size < 2) return null
        val constraint = constraintList.firstOrNull { it.name == decode(parts[0]) } ?: return null
        val negated = parts[1].startsWith("!")
        val key = parts[1].removePrefix("!")
        val op = constraint.operator(key) ?: return null
        val values = if (parts.size > 2) {
            parts.drop(2).joinToString(":").split(',').map { decode(it) }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
        if (op.needsValue && (values.isEmpty() || values.any { !constraint.accepts(key, it) })) return null
        return QueryRule(constraint.name, key, negated, values)
    }

    private fun decode(value: String): String = URLDecoder.decode(value, Charsets.UTF_8)

    companion object {
        private const val RULE_FIELD = "__qb"
        private val PICKER_WIDTH = Regex("\\d+(\\.\\d+)?(px|rem|em|vh|%)")

        @JvmStatic
        fun make(name: String): QueryBuilderFilter = finish(QueryBuilderFilter(name))

        /** Serialize flat OR-of-AND groups (legacy / tests). */
        @JvmStatic
        fun encode(groups: List<List<QueryRule>>): String = groups.joinToString("|") { rules ->
            rules.joinToString(";") { encodeRule(it) }
        }

        /** Serialize an arbitrary [QueryNode] (`a;(b|c)` nesting). */
        @JvmStatic
        fun encodeNode(node: QueryNode): String = encodeNodeInner(node, wrap = false)

        private fun encodeNodeInner(node: QueryNode, wrap: Boolean): String = when (node) {
            is QueryNode.Leaf -> encodeRule(node.rule)
            is QueryNode.And -> {
                val body = node.children.joinToString(";") { child ->
                    encodeNodeInner(child, wrap = child is QueryNode.Or)
                }
                if (wrap) "($body)" else body
            }
            is QueryNode.Or -> {
                val body = node.children.joinToString("|") { child ->
                    encodeNodeInner(child, wrap = child is QueryNode.And && child.children.size > 1)
                }
                if (wrap) "($body)" else body
            }
        }

        private fun encodeRule(rule: QueryRule): String {
            val op = if (rule.negated) "!${rule.operator}" else rule.operator
            val values = rule.values.joinToString(",") { URLEncoder.encode(it, Charsets.UTF_8) }
            return "${URLEncoder.encode(rule.constraint, Charsets.UTF_8)}:$op:$values"
        }

        private fun ruleField(path: List<Int>): String = "$RULE_FIELD:" + path.joinToString(":")

        private fun parseRuleField(field: String): List<Int>? {
            val parts = field.split(':')
            if (parts.size < 2 || parts[0] != RULE_FIELD) return null
            return parts.drop(1).map { it.toIntOrNull() ?: return null }
        }
    }

    /** Recursive descent: `expr := term ('|' term)*`, `term := factor (';' factor)*`, `factor := rule | '(' expr ')'`. */
    private class ExpressionParser(private val raw: String, private val ruleOf: (String) -> QueryRule?) {
        private var i = 0

        fun parse(): QueryNode {
            skip()
            val node = parseExpr()
            skip()
            if (i < raw.length) throw IllegalArgumentException("trailing")
            return node
        }

        private fun parseExpr(): QueryNode {
            val parts = ArrayList<QueryNode>()
            parseTerm()?.let { parts.add(it) }
            while (true) {
                skip()
                if (!match('|')) break
                parseTerm()?.let { parts.add(it) }
            }
            if (parts.isEmpty()) throw IllegalArgumentException("empty")
            return if (parts.size == 1) parts[0] else QueryNode.Or(parts)
        }

        private fun parseTerm(): QueryNode? {
            val parts = ArrayList<QueryNode>()
            parseFactor()?.let { parts.add(it) }
            while (true) {
                skip()
                if (!match(';')) break
                parseFactor()?.let { parts.add(it) }
            }
            if (parts.isEmpty()) return null
            return if (parts.size == 1) parts[0] else QueryNode.And(parts)
        }

        private fun parseFactor(): QueryNode? {
            skip()
            if (match('(')) {
                skip()
                if (i < raw.length && raw[i] == ')') {
                    i++
                    return QueryNode.And(emptyList())
                }
                val inner = parseExpr()
                skip()
                if (!match(')')) throw IllegalArgumentException(")")
                return inner
            }
            val start = i
            while (i < raw.length && raw[i] !in ";|()") i++
            val token = raw.substring(start, i).trim()
            if (token.isEmpty()) return null
            return ruleOf(token)?.let { QueryNode.Leaf(it) }
        }

        private fun match(c: Char): Boolean {
            if (i < raw.length && raw[i] == c) {
                i++
                return true
            }
            return false
        }

        private fun skip() {
            while (i < raw.length && raw[i].isWhitespace()) i++
        }
    }
}
