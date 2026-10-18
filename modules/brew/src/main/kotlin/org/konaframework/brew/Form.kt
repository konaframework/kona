package org.konaframework.brew

import org.konaframework.brew.hydration.Coercion
import org.konaframework.http.ValidationException
import org.konaframework.http.Validator
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.function.Consumer

/**
 * Nested form object (`Livewire\Form`). Declare as a public property on a [Component];
 * public fields are state (dehydrated with the parent). Call [validate] / [validateOnly] to run rules.
 */
abstract class Form {
    @Transient
    private var owner: Component? = null

    @Transient
    private var propertyName: String = ""

    fun attach(component: Component, propertyName: String) {
        this.owner = component
        this.propertyName = propertyName
    }

    /** Exposed for tests / advanced callers that need the owning component. */
    fun component(): Component {
        val attached = owner
        if (attached == null) {
            throw BrewException("Form [${javaClass.simpleName}] is not attached to a component")
        }
        return attached
    }

    /** Override for rule map (`rules()`). Keys are local field names (`title`), not `form.title`. */
    open fun rules(): Map<String, List<String>> = emptyMap()

    open fun messages(): Map<String, String> = emptyMap()

    open fun validationAttributes(): Map<String, String> = emptyMap()

    fun fill(values: Map<String, Any?>): Form {
        val meta = FormMeta.of(this)
        for ((key, value) in values) {
            val field = meta.fields[key] ?: continue
            field.set(this, Coercion.toType(value, field.type, field.genericType))
        }
        return this
    }

    fun all(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((name, field) in FormMeta.of(this).fields) out[name] = field.get(this)
        return out
    }

    fun only(vararg properties: String): Map<String, Any?> {
        val all = all()
        return properties.filter { all.containsKey(it) }.associateWithTo(LinkedHashMap()) { all[it] }
    }

    fun except(vararg properties: String): Map<String, Any?> {
        val skip = properties.toSet()
        return all().filterKeys { it !in skip }
    }

    fun reset(vararg properties: String): Form {
        val fresh = javaClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance() as Form
        val meta = FormMeta.of(this)
        val names = if (properties.isEmpty()) meta.fields.keys else properties.toList()
        for (name in names) {
            val field = meta.fields[name] ?: continue
            field.set(this, field.get(fresh))
        }
        return this
    }

    fun pull(property: String): Any? {
        val value = all()[property]
        reset(property)
        return value
    }

    fun validate(): Map<String, Any?> = validate(null)

    fun validate(rules: Map<String, List<String>>?): Map<String, Any?> {
        val resolved = rules ?: resolvedRules()
        val prefix = if (propertyName.isEmpty()) "" else "$propertyName."
        val prefixedRules = resolved.mapKeys { "$prefix${it.key}" }
        val data = all().mapKeys { "$prefix${it.key}" }
        val validator = Validator(data, prefixedRules)
        try {
            val validated = validator.validate()
            this.component().errorBag().clear()
            return validated.mapKeys { it.key.removePrefix(prefix) }
        } catch (e: ValidationException) {
            this.component().errorBag().clear().merge(e.errors)
            throw e
        }
    }

    fun validateOnly(field: String): Map<String, Any?> {
        val prefix = if (propertyName.isEmpty()) "" else "$propertyName."
        val key = field.removePrefix(prefix)
        val rules = resolvedRules().filterKeys { it == key || it.startsWith("$key.") }
        return validate(rules)
    }

    open fun boot() {}

    open fun hydrate() {}

    open fun dehydrate() {}

    open fun updating(property: String, value: Any?) {}

    open fun updated(property: String, value: Any?) {}

    fun withValidator(block: Consumer<Validator>): Form = this

    /** Whether [field] should re-validate on `updated` (from `@Validate(onUpdate=…)`). */
    fun shouldValidateOnUpdate(field: String): Boolean = field in FormMeta.of(this).validateOnUpdate

    private fun resolvedRules(): Map<String, List<String>> {
        val fromMethods = rules().mapValues { (_, v) -> v }
        val fromAnnotations = FormMeta.of(this).validationRules
        return fromAnnotations + fromMethods
    }
}

/** Public fields + `@Validate` / `@Rule` metadata for a [Form] subclass. */
internal class FormMeta private constructor(
    val fields: Map<String, Field>,
    val validationRules: Map<String, List<String>>,
    val validateOnUpdate: Set<String>,
) {
    companion object {
        private val cache = object : ClassValue<FormMeta>() {
            override fun computeValue(type: Class<*>): FormMeta = introspect(type)
        }

        fun of(form: Form): FormMeta = cache[form.javaClass]

        private fun introspect(type: Class<*>): FormMeta {
            val fields = linkedMapOf<String, Field>()
            val rules = linkedMapOf<String, MutableList<String>>()
            val onUpdate = mutableSetOf<String>()
            var cls: Class<*> = type
            while (cls != Form::class.java) {
                for (field in cls.declaredFields) {
                    if (!Modifier.isPublic(field.modifiers)) continue
                    field.isAccessible = true
                    fields[field.name] = field
                    collectRules(field, rules, onUpdate)
                }
                cls = cls.superclass!!
            }
            return FormMeta(fields, rules.mapValues { it.value.toList() }, onUpdate)
        }

        private fun collectRules(field: Field, rules: MutableMap<String, MutableList<String>>, onUpdate: MutableSet<String>) {
            val ann = field.getAnnotation(Validate::class.java) ?: return
            ann.value.flatMap { it.split('|') }.filter { it.isNotBlank() }
                .forEach { rules.getOrPut(field.name) { mutableListOf() }.add(it) }
            if (ann.onUpdate) onUpdate += field.name
        }
    }
}
