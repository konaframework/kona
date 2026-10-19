package org.konaframework.http

import org.konaframework.core.Log
import java.util.function.BiFunction
import java.util.function.Consumer

fun interface ExceptionHandler {
    fun handle(request: Request, throwable: Throwable): Response
}

/**
 * Report/render callbacks plus JSON or HTML envelopes (Laravel Handler deepen).
 */
open class DefaultExceptionHandler @JvmOverloads constructor(
    private val debug: Boolean = false,
) : ExceptionHandler {
    private var reportCallback: Consumer<Throwable>? = null
    private var renderCallback: BiFunction<Request, Throwable, Response?>? = null

    fun report(callback: Consumer<Throwable>): DefaultExceptionHandler {
        reportCallback = callback
        return this
    }

    fun render(callback: BiFunction<Request, Throwable, Response?>): DefaultExceptionHandler {
        renderCallback = callback
        return this
    }

    override fun handle(request: Request, throwable: Throwable): Response {
        reportThrowable(throwable)
        val custom = renderCallback?.apply(request, throwable)
        if (custom != null) {
            return custom
        }
        return renderDefault(request, throwable)
    }

    protected open fun reportThrowable(throwable: Throwable) {
        val callback = reportCallback
        if (callback != null) {
            callback.accept(throwable)
            return
        }
        if (throwable !is HttpException && throwable !is ValidationException) {
            Log.error("Unhandled exception", throwable)
        }
    }

    protected open fun renderDefault(request: Request, throwable: Throwable): Response {
        val wantsHtml = wantsHtml(request)
        return when (throwable) {
            is ValidationException -> {
                if (!request.expectsJson()) {
                    Response.back(request)
                        .withErrors(throwable.errors)
                        .withInput(request.all())
                } else {
                    Response.json(mapOf("message" to throwable.message, "errors" to throwable.errors), 422)
                }
            }
            is HttpException -> {
                val response = if (wantsHtml) {
                    htmlError(throwable.status, throwable.message!!)
                } else {
                    Response.json(envelope(throwable.message, throwable), throwable.status)
                }
                throwable.headers.forEach { (k, v) -> response.header(k, v) }
                response
            }
            else -> {
                val detail = throwable.message
                if (wantsHtml) {
                    htmlError(500, if (debug && detail != null) detail else "Server Error")
                } else {
                    Response.json(envelope(if (debug) detail else "Server Error", throwable), 500)
                }
            }
        }
    }

    private fun wantsHtml(request: Request): Boolean {
        val accept = request.header("Accept").orEmpty()
        return accept.contains("text/html", ignoreCase = true) &&
            !accept.contains("application/json", ignoreCase = true)
    }

    private fun htmlError(status: Int, message: String): Response {
        val title = HttpStatus.reasonPhrase(status) ?: "Error"
        val safe = escape(message)
        val body =
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>$status · $title</title>
              <style>
                :root{--caramel:#DF8723;--accent:#74453F;--cream:#E8D4B8;--ink:#2A1A12;--bg:#FAF6F0}
                *{box-sizing:border-box}body{margin:0;min-height:100vh;font-family:ui-sans-serif,system-ui,sans-serif;
                  color:var(--ink);background:radial-gradient(1200px 600px at 10% -10%,#F3E0C8 0%,var(--bg) 55%)}
                main{max-width:36rem;margin:0 auto;padding:4.5rem 1.5rem 3rem;text-align:center}
                .mark{width:4.5rem;height:4.5rem;margin:0 auto 1.25rem;display:block}
                .code{font-size:.85rem;letter-spacing:.12em;text-transform:uppercase;color:var(--caramel);font-weight:700}
                h1{font-size:clamp(1.75rem,4vw,2.35rem);margin:.5rem 0 .75rem;color:var(--accent)}
                p{margin:0 0 1.75rem;line-height:1.55;opacity:.88}
                a{display:inline-block;padding:.7rem 1.15rem;border-radius:.55rem;background:var(--caramel);
                  color:#fff;text-decoration:none;font-weight:600}
                a:hover{filter:brightness(1.05)}
                .home{margin-left:.5rem;background:transparent;color:var(--accent);border:1px solid #D8C4A8}
              </style>
            </head>
            <body>
              <main>
                <svg class="mark" viewBox="-43 8 284 284" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <path fill="#DF8723" fill-rule="evenodd" d="M 113.344 21.663 C 118.089 28.286, 120.341 35.629, 119.816 42.763 C 119.100 52.490, 115.550 58.448, 102.488 71.843 C 85.556 89.208, 82.844 93.929, 82.754 106.189 C 82.690 115.016, 86.038 122.006, 93.670 128.981 C 96.740 131.787, 99.401 133.932, 99.585 133.748 C 99.769 133.564, 99.016 130.513, 97.912 126.966 C 95.488 119.176, 95.938 114.983, 100.140 106.225 C 102.515 101.273, 106.053 97.055, 116.913 86.225 C 128.141 75.027, 131.296 71.230, 134.048 65.601 C 136.896 59.776, 137.426 57.597, 137.453 51.601 C 137.490 43.277, 134.533 35.810, 128.598 29.240 C 124.631 24.849, 115.850 18.567, 112.189 17.500 C 110.035 16.872, 110.113 17.154, 113.344 21.663 M 145 54.166 C 145 54.486, 145.675 56.662, 146.500 59 C 150.395 70.040, 146.055 80.375, 132.720 91.809 C 119.995 102.720, 113.937 109.385, 110.731 116 C 108.032 121.569, 107.587 123.574, 107.627 130 C 107.665 136.171, 108.168 138.475, 110.465 143 C 112 146.025, 114.099 149.694, 115.128 151.154 C 116.158 152.614, 117 154.077, 117 154.404 C 117 154.732, 111.848 155, 105.551 155 C 92.496 155, 74.880 157.076, 64.250 159.868 L 57 161.771 57 113.886 L 57 66 38.500 66 L 20 66 20 146.042 C 20 234.344, 20.034 234.898, 26.242 248 C 30.100 256.141, 40.019 266.280, 49 271.262 C 64.310 279.755, 82.791 283.541, 104.938 282.720 C 121.668 282.101, 131.946 279.600, 144.916 272.991 C 159.952 265.331, 170.963 253.739, 177.071 239.138 C 182.145 227.011, 182.963 220.959, 182.983 195.446 L 183 172.392 179.750 169.308 C 175.867 165.623, 172.654 163.965, 164 161.181 C 155.469 158.437, 143.442 156.256, 132.131 155.402 L 123 154.713 121.888 150.607 C 121.277 148.348, 121.001 143.683, 121.274 140.240 C 122.060 130.346, 125.697 124.393, 138.794 111.563 C 151.454 99.162, 155.076 94.150, 157.518 85.653 C 160.421 75.552, 157.644 65.992, 149.477 57.978 C 147.015 55.561, 145 53.846, 145 54.166 M 87 163.038 C 75.279 164.437, 61.449 167.897, 57.813 170.340 C 53.071 173.524, 52.826 177.306, 57.186 179.998 C 66.377 185.674, 87.075 189.002, 113.128 188.994 C 132.845 188.988, 146.742 187.431, 158.500 183.912 C 165.685 181.762, 172 177.885, 172 175.623 C 172 171.489, 162.053 167.059, 146.819 164.410 C 135.916 162.514, 98.566 161.657, 87 163.038"/>
                </svg>
                <div class="code">$status</div>
                <h1>$title</h1>
                <p>$safe</p>
                <p>
                  <a href="https://docs.konaframework.org/">Kona docs</a>
                  <a class="home" href="/">Home</a>
                </p>
              </main>
            </body>
            </html>
            """.trimIndent()
        return Response(status, body, linkedMapOf("Content-Type" to "text/html; charset=utf-8"))
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun envelope(message: String?, throwable: Throwable): Map<String, Any?> {
        val payload = linkedMapOf<String, Any?>("message" to message)
        if (debug) {
            payload["exception"] = throwable::class.java.name
        }
        return payload
    }
}
