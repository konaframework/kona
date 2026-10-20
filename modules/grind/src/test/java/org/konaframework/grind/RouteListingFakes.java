package org.konaframework.grind;

import org.konaframework.http.Handler;
import org.konaframework.http.Request;
import org.konaframework.http.Response;

/** Name contains {@code $$Lambda} so listing treats it like a JVM lambda class. */
class Fake$$LambdaSample implements Handler {
    @Override
    public Response handle(Request request) {
        return Response.text("lambda");
    }
}

/** Name matches {@code $1} so listing treats it like an anonymous class. */
class Closure$1 implements Handler {
    @Override
    public Response handle(Request request) {
        return Response.text("anon");
    }
}
