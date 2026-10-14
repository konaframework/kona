package dev.kona.brew

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Invokes public action methods by name. Supported signatures:
 * - no arguments
 * - single [Map] (or MutableMap) of params
 * - single [String] argument (uses `params["value"]` or the first map value)
 *
 * Action methods must be **public** instance methods on the component class.
 */
object BrewActions {
    @JvmStatic
    fun invoke(component: Component, methodName: String): Unit =
        invoke(component, methodName, emptyMap())

    @JvmStatic
    fun invoke(component: Component, methodName: String, params: Map<String, Any?>) {
        if (methodName.isBlank()) {
            throw BrewException("Brew action method name is required")
        }
        if (methodName == "sync") {
            component.sync(params)
            return
        }
        execute(component, methodName, params)
    }

    private fun execute(component: Component, methodName: String, params: Map<String, Any?>) {
        val method = findMethod(component.javaClass, methodName)
            ?: throw BrewException("Unknown Brew action: $methodName")
        try {
            dispatch(component, method, methodName, params)
        } catch (ex: InvocationTargetException) {
            val cause = ex.cause!!
            if (cause is BrewException) {
                throw cause
            }
            throw BrewException("Brew action $methodName failed: ${cause.message}", cause)
        }
    }

    private fun dispatch(
        component: Component,
        method: Method,
        methodName: String,
        params: Map<String, Any?>,
    ) {
        when (method.parameterCount) {
            0 -> method.invoke(component)
            1 -> invokeSingleArg(component, method, methodName, params)
            else -> throw BrewException("Brew action $methodName must take 0 or 1 parameters")
        }
    }

    private fun invokeSingleArg(
        component: Component,
        method: Method,
        methodName: String,
        params: Map<String, Any?>,
    ) {
        val type = method.parameterTypes[0]
        if (Map::class.java.isAssignableFrom(type)) {
            method.invoke(component, params)
            return
        }
        if (type == String::class.java) {
            method.invoke(component, stringArg(params))
            return
        }
        throw BrewException("Unsupported Brew action signature for $methodName: ${type.simpleName}")
    }

    private fun stringArg(params: Map<String, Any?>): String {
        if (params.containsKey("value")) {
            val value = params["value"]
            if (value == null) {
                return ""
            }
            return value.toString()
        }
        if (params.isEmpty()) {
            return ""
        }
        return params.values.first().toString()
    }

    private fun findMethod(type: Class<*>, name: String): Method? {
        val candidates = type.methods.filter { method ->
            method.name == name &&
                method.declaringClass != Any::class.java &&
                method.declaringClass != Component::class.java
        }
        return candidates.minByOrNull { it.parameterCount }
    }
}
