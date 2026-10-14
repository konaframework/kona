package dev.kona.brew

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Reads and writes public instance fields on the concrete component class
 * (Java `public` fields or Kotlin `@JvmField`).
 */
object BrewState {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    private val PRIMITIVE_WRAPPERS: Map<Class<*>, Class<*>> = mapOf(
        java.lang.Boolean.TYPE to java.lang.Boolean::class.java,
        java.lang.Byte.TYPE to java.lang.Byte::class.java,
        java.lang.Short.TYPE to java.lang.Short::class.java,
        java.lang.Integer.TYPE to java.lang.Integer::class.java,
        java.lang.Long.TYPE to java.lang.Long::class.java,
        java.lang.Float.TYPE to java.lang.Float::class.java,
        java.lang.Double.TYPE to java.lang.Double::class.java,
        java.lang.Character.TYPE to java.lang.Character::class.java,
    )

    @JvmStatic
    fun read(component: Component): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        for (field in publicStateFields(component.javaClass)) {
            out[field.name] = field.get(component)
        }
        return out
    }

    @JvmStatic
    fun write(component: Component, values: Map<String, Any?>) {
        if (values.isEmpty()) {
            return
        }
        val fields = publicStateFields(component.javaClass).associateBy { it.name }
        for ((key, raw) in values) {
            if (key == "brewId" || key == "name") {
                continue
            }
            val field = fields[key] ?: continue
            if (raw == null) {
                if (field.type.isPrimitive) {
                    continue
                }
                field.set(component, null)
                continue
            }
            field.set(component, coerce(raw, field.type))
        }
    }

    private fun coerce(raw: Any, type: Class<*>): Any? {
        if (type.isInstance(raw)) {
            return raw
        }
        val target = PRIMITIVE_WRAPPERS[type] ?: type
        return mapper.convertValue(raw, target)
    }

    private fun publicStateFields(type: Class<*>): List<Field> {
        val fields = mutableListOf<Field>()
        for (field in type.declaredFields) {
            if (!Modifier.isPublic(field.modifiers)) {
                continue
            }
            if (Modifier.isStatic(field.modifiers)) {
                continue
            }
            field.isAccessible = true
            fields += field
        }
        return fields
    }
}
