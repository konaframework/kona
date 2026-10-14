package dev.kona.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class JavaApiTest {
    @Test
    void staticFactoriesAndValidator() {
        assertEquals("ok", Response.text("ok").getBody());
        assertEquals(201, Response.text("ok", 201).getStatus());
        assertEquals("h", Response.html("h").getBody());
        assertEquals(201, Response.html("h", 201).getStatus());
        assertTrue(Response.json(Map.of("a", 1)).getBody().contains("a"));
        assertEquals(201, Response.json(Map.of("a", 1), 201).getStatus());
        assertEquals(204, Response.noContent().getStatus());

        Request get = Request.get("/x");
        assertEquals("GET", get.getMethod());
        assertEquals(null, get.getAttribute("missing"));
        get.setAttribute("k", "v");
        assertEquals("v", get.getAttribute("k"));
        Request post = Request.post("/y", "a=b");
        assertEquals("POST", post.getMethod());

        Validator v = Validator.make(Map.of("n", "Ada"), Map.of("n", List.of("required", "min:2")));
        assertTrue(v.passes());
        Validator v2 = Validator.make(Request.get("/"), Map.of("n", List.of("required")));
        assertTrue(v2.fails());
    }

    @Test
    void handlersMiddlewareAndGroupsWithoutKotlinDsl() {
        List<String> log = new ArrayList<>();
        Middleware audit = (request, next) -> {
            log.add("before " + request.getPath());
            Response response = next.handle(request);
            log.add("after " + response.getStatus());
            return response.header("X-Audit", "1");
        };
        Middleware auth = (request, next) -> next.handle(request).header("X-Auth", "1");
        Handler hello = request -> Response.text("hello " + request.getAttribute("name"));

        Router router = new Router();
        router.get("/hello/{name}", hello).name("hello");
        router.group("/api", List.of(auth), r -> {
            r.get("/ping", request -> Response.json(Map.of("pong", true))).name("api.ping");
            r.post("/items", request -> Response.text("created", 201));
        });
        router.prefix("/admin").name("admin.").middleware(auth).group(r ->
            r.get("/dashboard", request -> Response.text("dash")).name("dashboard")
        );
        router.group("/v1", r -> r.any("/echo", request -> Response.text(request.getMethod())));
        router.group("/v2", "v2.", List.of(auth), r -> r.options("/o", request -> Response.text("o")).name("o"));

        HttpKernel kernel = new HttpKernel(router);
        kernel.pushMiddleware(audit);
        assertTrue(kernel.hasMiddleware(audit));
        assertEquals(List.of(audit), kernel.getGlobalMiddleware());

        assertEquals("hello ada", kernel.handle(Request.get("/hello/ada")).getBody());
        Response ping = kernel.handle(Request.get("/api/ping"));
        assertEquals("1", ping.getHeaders().get("X-Auth"));
        assertEquals("1", ping.getHeaders().get("X-Audit"));
        assertEquals("/admin/dashboard", router.route("admin.dashboard", Map.of()));
        assertEquals("/v2/o", router.route("v2.o", Map.of()));
        assertEquals("DELETE", kernel.handle(new Request("DELETE", "/v1/echo")).getBody());

        log.clear();
        Response missing = kernel.handle(Request.get("/nowhere"));
        assertEquals(404, missing.getStatus());
        assertEquals("1", missing.getHeaders().get("X-Audit"));
        assertEquals(List.of("before /nowhere", "after 404"), log);

        Response head = kernel.handle(new Request("HEAD", "/admin/dashboard"));
        assertEquals(200, head.getStatus());
        assertEquals("", head.getBody());

        Response notAllowed = kernel.handle(Request.get("/api/items"));
        assertEquals(405, notAllowed.getStatus());
        assertEquals("POST", notAllowed.getHeaders().get("Allow"));

        HttpKernel configured = new HttpKernel(router, List.of(auth), new DefaultExceptionHandler(true));
        assertFalse(configured.handle(Request.get("/nowhere")).getHeaders().containsKey("X-Audit"));
        assertEquals(7, Router.VERBS.size());

        Handler piped = MiddlewarePipeline.pipe(List.of(auth), hello);
        assertEquals("1", piped.handle(Request.get("/")).getHeaders().get("X-Auth"));

        Route route = new Route(List.of("get", "post"), "/r/{id}", hello, List.of(auth));
        assertEquals(List.of("GET", "POST"), route.getMethods());
        assertEquals(List.of(auth), route.getMiddleware());
        assertEquals(Map.of("id", "9"), route.match("POST", "/r/9"));
    }

    @Test
    void validationAndExceptions() {
        Map<String, Object> data = Map.of("email", "ada@example.com", "age", 36, "tags", List.of("a", "b"));
        Map<String, Object> valid = Validator.make(data, Map.of(
            "email", List.of("required", "email"),
            "age", List.of("integer", "min:18"),
            "tags", List.of("array", "size:2")
        )).validate();
        assertEquals(36, valid.get("age"));

        ValidationException ex = assertThrows(ValidationException.class, () ->
            Validator.make(Map.of("age", 3), Map.of("age", List.of("integer|min:18"), "x", List.of("bogus"))).validate()
        );
        assertEquals(List.of("The age field must be at least 18."), ex.getErrors().get("age"));
        assertEquals(List.of("Unknown validation rule [bogus]."), ex.getErrors().get("x"));

        Response envelope = new DefaultExceptionHandler().handle(
            Request.get("/"), new MethodNotAllowedException(List.of("GET", "HEAD"))
        );
        assertEquals(405, envelope.getStatus());
        assertEquals("GET, HEAD", envelope.getHeaders().get("Allow"));
        assertEquals(404, new NotFoundException().getStatus());
        assertEquals(400, new HttpException(400, "bad").getStatus());
    }
}
