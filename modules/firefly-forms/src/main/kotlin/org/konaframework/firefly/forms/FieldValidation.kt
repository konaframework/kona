package org.konaframework.firefly.forms

import org.konaframework.firefly.schemas.Component
import org.konaframework.firefly.schemas.Schema
import org.konaframework.http.Validator

object FieldValidation {
    @JvmStatic
    fun validate(schema: Schema, roots: List<Component>): Map<String, List<String>> {
        val data = LinkedHashMap<String, Any?>()
        val rules = LinkedHashMap<String, List<String>>()
        val labels = LinkedHashMap<String, String>()
        val messages = LinkedHashMap<String, Map<String, String>>()
        val extras = LinkedHashMap<String, List<String>>()
        for (component in Reads.walk(roots)) {
            if (component !is Field<*>) continue
            if (component.isHidden(schema)) continue
            if (!component.isDehydrated()) continue
            val path = component.absolutePath() ?: component.name
            if (path.isEmpty()) continue
            data[path] = schema.get(path)
            val fieldRules = component.validationRules(schema)
            if (fieldRules.isNotEmpty()) rules[path] = fieldRules
            val label = component.attributeLabel(schema)
            if (label != null) labels[path] = label
            for (rule in fieldRules) {
                val message = component.messageFor(rule.substringBefore(':'))
                if (message != null) {
                    val existing = messages[path] ?: emptyMap()
                    messages[path] = existing + (rule.substringBefore(':') to message)
                }
            }
            val extra = component.extraErrors(schema)
            for ((key, value) in extra) extras[key] = value
        }
        val errors = LinkedHashMap<String, MutableList<String>>()
        if (rules.isNotEmpty()) {
            val validator = Validator(data, rules)
            if (validator.fails()) {
                for ((field, fieldErrors) in validator.errors()) {
                    val label = labels[field]
                    val rewritten = if (label == null) {
                        fieldErrors
                    } else {
                        fieldErrors.map { it.replace("The ${field.replace('_', ' ')} field", "The $label field") }
                    }
                    errors.getOrPut(field) { ArrayList() }.addAll(rewritten)
                }
            }
        }
        for ((field, fieldErrors) in extras) {
            errors.getOrPut(field) { ArrayList() }.addAll(fieldErrors)
        }
        return errors.mapValues { it.value.toList() }
    }
}
