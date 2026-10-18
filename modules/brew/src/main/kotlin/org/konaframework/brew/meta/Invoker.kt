package org.konaframework.brew.meta

import org.konaframework.brew.hydration.Coercion
import org.konaframework.core.Container
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.reflect.KParameter
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.jvm.kotlinFunction

/**
 * Calls component methods with Livewire's parameter rules: positional values first (coerced to the declared types),
 * then named values, then container-resolved dependencies, then Kotlin default arguments, then `null`.
 */
class Invoker(private val container: Container?) {
    /** Invoke [method] with positional arguments. */
    fun call(target: Any, method: Method, positional: List<Any?>): Any? = call(target, method, positional, emptyMap())

    fun call(target: Any, method: Method, positional: List<Any?>, named: Map<String, Any?>): Any? {
        val params = method.parameters
        val names = if (named.isEmpty()) emptyList() else parameterNames(method)
        val args = arrayOfNulls<Any?>(params.size)
        val missing = BooleanArray(params.size)
        var anyMissing = false
        for (i in params.indices) {
            val p = params[i]
            val name = names.getOrNull(i)
            when {
                i < positional.size -> args[i] = Coercion.toType(positional[i], p.type, p.parameterizedType)
                name != null && named.containsKey(name) ->
                    args[i] = Coercion.toType(named[name], p.type, p.parameterizedType)
                else -> {
                    val resolved = resolve(p.type)
                    if (resolved != null) {
                        args[i] = resolved
                    } else {
                        missing[i] = true
                        anyMissing = true
                        args[i] = Coercion.defaultFor(p.type)
                    }
                }
            }
        }
        return try {
            if (anyMissing) callWithDefaults(target, method, args, missing) else method.invoke(target, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    /** Parameter names for mount/hook binding (Java needs `-parameters`; Kotlin uses metadata). */
    fun parameterNames(method: Method): List<String> {
        if (method.declaringClass.isAnnotationPresent(Metadata::class.java)) {
            val kotlin = method.kotlinFunction!!
            return kotlin.parameters.filter { it.kind == KParameter.Kind.VALUE }.map { it.name!! }
        }
        return method.parameters.map { if (it.isNamePresent) it.name else "" }
    }

    private fun callWithDefaults(target: Any, method: Method, args: Array<Any?>, missing: BooleanArray): Any? {
        if (!method.declaringClass.isAnnotationPresent(Metadata::class.java)) return method.invoke(target, *args)
        val fn = method.kotlinFunction!!
        val values = fn.parameters.filter { it.kind == KParameter.Kind.VALUE }
        val call = linkedMapOf<KParameter, Any?>(fn.instanceParameter!! to target)
        values.forEachIndexed { i, p ->
            if (!(missing[i] && p.isOptional)) call[p] = args[i]
        }
        return fn.callBy(call)
    }

    private fun resolve(type: Class<*>): Any? {
        val c = container ?: return null
        if (type.isPrimitive || type.name.startsWith("java.") || type.name.startsWith("kotlin.")) return null
        return try {
            c.make(type)
        } catch (_: Exception) {
            null
        }
    }
}
