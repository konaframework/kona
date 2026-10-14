package hello

import dev.kona.core.Application
import dev.kona.core.Kona
import dev.kona.http.BaseResourceController
import dev.kona.http.Controllers
import dev.kona.http.DefaultExceptionHandler
import dev.kona.http.FormRequest
import dev.kona.http.HttpKernel
import dev.kona.http.HttpServiceProvider
import dev.kona.http.InvokableController
import dev.kona.http.JsonResource
import dev.kona.http.Request
import dev.kona.http.Response
import dev.kona.http.Router
import dev.kona.http.UrlGenerator

object HttpPolishDemo {
    class UserResource(private val id: Int, private val name: String) : JsonResource(name) {
        override fun toArray(request: Request): Map<String, Any?> =
            mapOf("id" to id, "name" to name)
    }

    class StoreNoteRequest : FormRequest() {
        override fun rules(): Map<String, List<String>> =
            mapOf("title" to listOf("required", "min:3"))
    }

    class NotesController : BaseResourceController() {
        override fun index(request: Request): Response =
            JsonResource.collection(listOf(UserResource(1, "Ada"))).toResponse(request)

        override fun store(request: Request): Response {
            val data = validate(request, StoreNoteRequest())
            return Response.json(data, 201)
        }
    }

    @JvmStatic
    fun run(args: Array<String>): Map<String, String> {
        val app = Kona.create(args).providers(HttpServiceProvider()).boot()
        return run(app)
    }

    @JvmStatic
    fun run(app: Application): Map<String, String> {
        val router = app.resolve(Router::class.java)
        val urls = app.resolve(UrlGenerator::class.java).setRoot("https://demo.test").setKey("demo")
        val notes = NotesController()
        router.get("/notes") { notes.index(it) }.name("notes.index")
        router.post("/notes") { notes.store(it) }
        router.get("/ping", Controllers.asHandler(InvokableController { Response.text("pong") })).name("ping")

        val kernel = HttpKernel(router, exceptionHandler = DefaultExceptionHandler())
        val index = kernel.handle(Request.get("/notes"))
        val created = kernel.handle(Request.post("/notes", body = "title=Hello"))
        val signed = urls.signedRoute("notes.index", expirationSeconds = 60)
        return mapOf(
            "index" to "${index.status}:${index.body}",
            "created" to "${created.status}:${created.body}",
            "route" to urls.route("notes.index"),
            "signed" to signed.substringBefore('?'),
            "ping" to kernel.handle(Request.get("/ping")).body,
        )
    }
}
