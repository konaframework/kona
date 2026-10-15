package dev.kona.http;

/** Static + arity overloads for Controllers.findMethod branch coverage. */
public final class JavaOverloadedController {
    public static Response ping() {
        return Response.text("static");
    }

    public Response ping(Request request, int extra) {
        return Response.text("extra");
    }

    public Response ping(Request request) {
        return Response.text("ok");
    }
}
