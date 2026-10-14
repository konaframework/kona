package dev.kona.http

/**
 * Base HTTP controller with shared validation helpers.
 */
open class Controller {
    /** Validate request data using rule maps. */
    protected fun validate(request: Request, rules: Map<String, List<String>>): Map<String, Any?> =
        request.validate(rules)

    /** Validate via a [FormRequest] object. */
    protected fun <T : FormRequest> validate(request: Request, form: T): Map<String, Any?> =
        form.validate(request)
}

/**
 * Single-action invokable controller (`invoke`).
 */
fun interface InvokableController {
    fun invoke(request: Request): Response
}

/** Adapts an [InvokableController] to a [Handler]. */
fun InvokableController.asHandler(): Handler = Controllers.asHandler(this)

/** Java-friendly controller helpers. */
object Controllers {
    @JvmStatic
    fun asHandler(controller: InvokableController): Handler = Handler { controller.invoke(it) }
}

/**
 * Optional resource controller base that returns 404 for unimplemented actions.
 */
abstract class BaseResourceController : Controller(), ResourceController {
    override fun index(request: Request): Response = notImplemented()
    override fun show(request: Request): Response = notImplemented()
    override fun store(request: Request): Response = notImplemented()
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
