package dev.kona.http

import dev.kona.core.Application
import dev.kona.core.BindingException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Controller helpers: invokable adapters and Laravel-shaped action resolution
 * (`Class` + method name, or `"fully.qualified.Controller@method"`).
 */
object Controllers {
    @JvmStatic
    fun asHandler(controller: InvokableController): Handler = Handler { controller.invoke(it) }

    /**
     * Resolve [type] from [app] (container autowire / binding) and invoke [method] with the request.
     * Method must accept a single [Request] and return [Response] (or a subtype).
     */
    @JvmStatic
    fun action(app: Application, type: Class<*>, method: String): Handler =
        Handler { request -> invoke(app, type, method, request) }

    /**
     * Parse `"pkg.Controller@method"` (or `"Controller@method"` when the class is loadable by simple name)
     * and dispatch like [action].
     */
    @JvmStatic
    fun action(app: Application, action: String): Handler {
        val (type, method) = parseAction(action)
        return action(app, type, method)
    }

    @JvmStatic
    fun invoke(app: Application, type: Class<*>, method: String, request: Request): Response {
        val instance = instantiate(app, type)
        val target = findMethod(type, method)
        return try {
            val result = target.invoke(instance, request)
            result as? Response
                ?: throw IllegalStateException(
                    "Controller method [${type.name}@$method] must return ${Response::class.java.name}",
                )
        } catch (ex: InvocationTargetException) {
            throw unwrapInvocation(type, method, ex.cause)
        }
    }

    /** Visible for tests: map a reflected controller failure to the public exception type. */
    internal fun unwrapInvocation(type: Class<*>, method: String, cause: Throwable?): Nothing {
        if (cause is RuntimeException) {
            throw cause
        }
        if (cause is Error) {
            throw cause
        }
        throw IllegalStateException(
            "Controller method [${type.name}@$method] failed: ${cause?.message}",
            cause,
        )
    }

    internal fun instantiate(app: Application, type: Class<*>): Any {
        return try {
            app.make(type)
        } catch (ex: BindingException) {
            reflectiveFallback(app, type, ex)
        }
    }

    private fun reflectiveFallback(app: Application, type: Class<*>, original: BindingException): Any {
        val constructors = type.constructors.filter { !it.isSynthetic }
        val withApp = constructors.firstOrNull {
            it.parameterCount == 1 && Application::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        if (withApp != null) {
            return withApp.newInstance(app)
        }
        val noArg = constructors.firstOrNull { it.parameterCount == 0 }
        if (noArg != null) {
            return noArg.newInstance()
        }
        throw original
    }

    internal fun findMethod(type: Class<*>, name: String): Method {
        val matches = type.methods.filter {
            it.name == name &&
                !Modifier.isStatic(it.modifiers) &&
                it.parameterCount == 1 &&
                Request::class.java.isAssignableFrom(it.parameterTypes[0])
        }
        return matches.firstOrNull()
            ?: throw IllegalArgumentException("Controller method [${type.name}@$name] not found")
    }

    internal fun parseAction(action: String): Pair<Class<*>, String> {
        val at = action.lastIndexOf('@')
        if (at <= 0 || at == action.lastIndex) {
            throw IllegalArgumentException("Invalid controller action [$action]; expected Controller@method")
        }
        val className = action.substring(0, at)
        val method = action.substring(at + 1)
        val loaders = listOfNotNull(
            Thread.currentThread().contextClassLoader,
            Controllers::class.java.classLoader,
        ).distinct()
        for (loader in loaders) {
            try {
                return Class.forName(className, true, loader) to method
            } catch (_: ClassNotFoundException) {
                // try next loader
            }
        }
        throw IllegalArgumentException("Controller class [$className] not found")
    }
}

/** Adapts an [InvokableController] to a [Handler]. */
fun InvokableController.asHandler(): Handler = Controllers.asHandler(this)
