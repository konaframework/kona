package dev.kona.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class JavaControllerMiddlewareTest {
    static class Marker implements Middleware {
        @Override
        public Response handle(Request request, Handler next) {
            request.setAttribute("java-mw", true);
            return next.handle(request);
        }
    }

    static class Ctrl extends Controller {
        Ctrl() {
            // Hit default-arg bridges for only/except.
            middleware(new Marker());
        }

        Response ping(Request request) {
            return Response.text("ok");
        }
    }

    @Test
    void middlewareDefaultsAndActionFromJava() {
        Ctrl ctrl = new Ctrl();
        assertEquals(1, ctrl.middlewareFor("ping").size());
        Request request = Request.get("/");
        Response response = ctrl.action("ping", req -> ctrl.ping(req)).handle(request);
        assertEquals("ok", response.getBody());
        assertTrue(Boolean.TRUE.equals(request.getAttribute("java-mw")));
    }
}
