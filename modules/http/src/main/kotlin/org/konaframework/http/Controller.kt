package org.konaframework.http

/**
 * Base HTTP controller with shared validation helpers and optional middleware.
 *
 * Middleware registered via [middleware] applies when routes are built with
 * [action] / [Handler] wrappers that call [middlewareFor].
 */
open class Controller {
    private val middlewareEntries = mutableListOf<ControllerMiddleware>()

    /** Validate request data using rule maps. */
    protected fun validate(request: Request, rules: Map<String, List<String>>): Map<String, Any?> =
        request.validate(rules)

    /** Validate via a [FormRequest] object. */
    protected fun <T : FormRequest> validate(request: Request, form: T): Map<String, Any?> =
        form.validate(request)

    /**
     * Register middleware for this controller (Laravel `$this->middleware(...)`).
     *
     * @param only method names that receive the middleware (null = all)
     * @param except method names that skip the middleware
     */
    @JvmOverloads
    protected fun middleware(
        vararg middleware: Middleware,
        only: Collection<String>? = null,
        except: Collection<String>? = null,
    ) {
        middlewareEntries += ControllerMiddleware(
            middleware = middleware.toList(),
            only = only?.map { it.lowercase() }?.toSet(),
            except = except?.map { it.lowercase() }?.toSet(),
        )
    }

    /** Middleware that should run for the given controller action name. */
    fun middlewareFor(method: String): List<Middleware> {
        val key = method.lowercase()
        val result = mutableListOf<Middleware>()
        for (entry in middlewareEntries) {
            if (entry.except != null && key in entry.except) {
                continue
            }
            if (entry.only != null && key !in entry.only) {
                continue
            }
            result += entry.middleware
        }
        return result
    }

    /**
     * Wrap [handler] with this controller's middleware for [method]
     * (use when registering typed controller actions on the router).
     */
    fun action(method: String, handler: Handler): Handler {
        val mw = middlewareFor(method)
        if (mw.isEmpty()) {
            return handler
        }
        return MiddlewarePipeline.pipe(mw, handler)
    }

    private data class ControllerMiddleware(
        val middleware: List<Middleware>,
        val only: Set<String>?,
        val except: Set<String>?,
    )
}

/**
 * Single-action invokable controller (`invoke`).
 */
fun interface InvokableController {
    fun invoke(request: Request): Response
}

/**
 * Optional resource controller base that returns 404 for unimplemented actions.
 */
abstract class BaseResourceController : Controller(), ResourceController {
    override fun index(request: Request): Response = notImplemented()
    override fun create(request: Request): Response = notImplemented()
    override fun store(request: Request): Response = notImplemented()
    override fun show(request: Request): Response = notImplemented()
    override fun edit(request: Request): Response = notImplemented()
    override fun update(request: Request): Response = notImplemented()
    override fun destroy(request: Request): Response = notImplemented()

    private fun notImplemented(): Response = throw NotFoundException()
}

/**
 * Form-request style validation object.
 */
abstract class FormRequest {
    abstract fun rules(): Map<String, List<String>>

    open fun authorize(request: Request): Boolean = true

    fun validate(request: Request): Map<String, Any?> {
        if (!authorize(request)) {
            throw HttpException(403, "This action is unauthorized.")
        }
        return Validator.make(request, rules()).validate()
    }
}
