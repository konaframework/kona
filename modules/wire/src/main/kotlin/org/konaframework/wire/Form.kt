package org.konaframework.wire

import org.konaframework.wire.hydration.Coercion
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

    @Transient
    private val validatorHooks = mutableListOf<Consumer<Validator>>()

    fun attach(component: Component, propertyName: String) {
        this.owner = component
        this.propertyName = propertyName
    }

    /** Property name on the owning component (`form` in `public form`). */
    fun getPropertyName(): String = propertyName

    /** Exposed for tests / advanced callers that need the owning component. */
    fun component(): Component {
        val attached = owner
        if (attached == null) {
            throw WireException("Form [${javaClass.simpleName}] is not attached to a component")
        }
        return attached
    }

    fun getComponent(): Component = component()

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

    /** `reset(['title', 'content'])`. An empty collection resets every field. */
    fun reset(properties: Collection<String>): Form =
        if (properties.isEmpty()) reset() else reset(*properties.toTypedArray())

    /** Reset every field except [properties] (`resetExcept`). */
    fun resetExcept(vararg properties: String): Form {
        val keep = properties.toSet()
        val names = FormMeta.of(this).fields.keys.filter { it !in keep }
        if (names.isNotEmpty()) reset(*names.toTypedArray())
        return this
    }

    fun pull(property: String): Any? {
        val value = all()[property]
        reset(property)
        return value
    }

    /** Return several fields and reset them (`pull(['title', 'content'])`; all fields when empty). */
    fun pull(vararg properties: String): Map<String, Any?> {
        val values = if (properties.isEmpty()) all() else only(*properties)
        reset(*properties)
        return values
    }

    fun validate(): Map<String, Any?> = validate(null)

    fun validate(rules: Map<String, List<String>>?): Map<String, Any?> {
        val resolved = rules ?: resolvedRules()
        val prefix = if (propertyName.isEmpty()) "" else "$propertyName."
        val prefixedRules = resolved.mapKeys { "$prefix${it.key}" }
        val data = all().mapKeys { "$prefix${it.key}" }
        val validator = Validator(data, prefixedRules)
        validatorHooks.forEach { it.accept(validator) }
        try {
            val validated = validator.validate()
            this.component().errorBag().clear()
            return validated.mapKeys { it.key.removePrefix(prefix) }
        } catch (e: ValidationException) {
            this.component().errorBag().clear().merge(e.errors)
            throw e
        }
    }

    fun addError(key: String, message: String) {
        component().addError(prefixed(key), message)
    }

    fun getErrorBag(): ErrorBag = component().errorBag()

    /** Clear this form's errors (`resetErrorBag` / `resetValidation`). */
    fun resetErrorBag(vararg fields: String) {
        val bag = component().errorBag()
        if (fields.isEmpty()) {
            if (propertyName.isEmpty()) {
                bag.clear()
                return
            }
            val prefix = "$propertyName."
            bag.all().keys.filter { it == propertyName || it.startsWith(prefix) }.forEach { bag.clear(it) }
            return
        }
        for (field in fields) bag.clear(prefixed(field))
    }

    fun resetValidation(vararg fields: String) {
        resetErrorBag(*fields)
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

    fun withValidator(block: Consumer<Validator>): Form {
        validatorHooks += block
        return this
    }

    /** Whether [field] should re-validate on `updated` (from `@Validate(onUpdate=…)`). */
    fun shouldValidateOnUpdate(field: String): Boolean = field in FormMeta.of(this).validateOnUpdate

    internal fun resolvedRules(): Map<String, List<String>> {
        val fromMethods = rules().mapValues { (_, v) -> v }
        val fromAnnotations = FormMeta.of(this).validationRules
        return fromAnnotations + fromMethods
    }

    private fun prefixed(key: String): String {
        if (propertyName.isEmpty()) return key
        val prefix = "$propertyName."
        return if (key.startsWith(prefix)) key else prefix + key
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
            val validate = field.getAnnotation(Validate::class.java)
            val rule = field.getAnnotation(Rule::class.java)
            if (validate == null && rule == null) return
            var live = false
            fun take(values: Array<out String>, onUpdateFlag: Boolean) {
                values.flatMap { it.split('|') }.filter { it.isNotBlank() }
                    .forEach { rules.getOrPut(field.name) { mutableListOf() }.add(it) }
                live = onUpdateFlag
            }
            if (validate != null) take(validate.value, validate.onUpdate)
            if (rule != null) take(rule.value, rule.onUpdate)
            if (live) onUpdate += field.name else onUpdate -= field.name
        }
    }
}
