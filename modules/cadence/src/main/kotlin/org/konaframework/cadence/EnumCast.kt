package org.konaframework.cadence

/**
 * Single-enum attribute cast.
 *
 * Stores the enum's `value` property when present (Kotlin `val value` / Java `getValue()`),
 * otherwise the enum constant [Enum.name]. Unknown database values throw [CadenceException].
 */
class EnumCast(
    private val enumClass: Class<out Enum<*>>,
) : CastsAttributes {
    init {
        require(enumClass.isEnum) { "Enum cast requires an enum class (got ${enumClass.name})" }
    }

    override fun get(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? {
        if (value == null) return null
        if (enumClass.isInstance(value)) return value
        return resolveEnum(enumClass, value)
    }

    override fun set(model: Cadence<*>, key: String, value: Any?, attributes: Map<String, Any?>): Any? {
        if (value == null) return null
        val constant =
            if (enumClass.isInstance(value)) {
                value as Enum<*>
            } else {
                resolveEnum(enumClass, value)
            }
        return enumStoredValue(constant)
    }

    companion object {
        /** Resolves a stored value to an enum constant or throws a clear error. */
        @JvmStatic
        fun resolveEnum(enumClass: Class<out Enum<*>>, raw: Any): Enum<*> {
            if (enumClass.isInstance(raw)) {
                return raw as Enum<*>
            }
            val text = raw.toString()
            for (constant in enumClass.enumConstants!!) {
                val stored = enumStoredValue(constant)
                if (stored == raw) {
                    return constant
                }
                if (stored.toString() == text) {
                    return constant
                }
                if (constant.name == text) {
                    return constant
                }
            }
            throw CadenceException("Unknown enum value [$raw] for ${enumClass.name}")
        }

        /**
         * Storage form for [constant]: declared `value` / `getValue()` when present, else [Enum.name].
         */
        @JvmStatic
        fun enumStoredValue(constant: Enum<*>): Any {
            val type = enumClassOf(constant)
            try {
                return type.getMethod("getValue").invoke(constant) ?: constant.name
            } catch (_: ReflectiveOperationException) {
                // Fall through to a `value` field or the constant name.
            }
            try {
                val field = type.getDeclaredField("value")
                field.isAccessible = true
                return field.get(constant) ?: constant.name
            } catch (_: NoSuchFieldException) {
                return constant.name
            }
        }

        /** Alias used when serializing models to maps / JSON. */
        @JvmStatic
        fun backingValue(constant: Enum<*>): Any = enumStoredValue(constant)

        private fun enumClassOf(constant: Enum<*>): Class<*> {
            val type = constant.javaClass
            return if (type.enumConstants != null) type else type.superclass
        }
    }
}
