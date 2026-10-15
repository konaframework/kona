package dev.kona.http;

/** Throws a checked exception with a null message for Controllers.invoke coverage. */
public final class NullMessageThrowingController {
    public Response ping(Request request) throws Exception {
        throw new Exception((String) null);
    }
}
