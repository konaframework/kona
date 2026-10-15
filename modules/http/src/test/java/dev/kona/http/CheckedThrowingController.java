package dev.kona.http;

/** Throws a checked exception so Controllers.invoke wraps it as IllegalStateException. */
public final class CheckedThrowingController {
    public Response ping(Request request) throws Exception {
        throw new Exception("checked");
    }
}
