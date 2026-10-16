package io.konaframework.http

import io.konaframework.filesystem.FilesystemAdapter

/**
 * Serves files from the public disk under a URL prefix (default `/storage`).
 *
 * Pair with `grind storage:link` when a static web root also needs the symlink;
 * this middleware covers the JDK embedded server path.
 */
class ServePublicStorage @JvmOverloads constructor(
    private val disk: FilesystemAdapter,
    private val urlPrefix: String = "/storage",
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        val method = request.method.uppercase()
        if (method != "GET" && method != "HEAD") {
            return next.handle(request)
        }
        val prefix = urlPrefix.trimEnd('/')
        val path = request.path
        if (path != prefix && !path.startsWith("$prefix/")) {
            return next.handle(request)
        }
        val relative = path.removePrefix(prefix).trimStart('/')
        if (relative.isEmpty() || relative.contains("..")) {
            throw NotFoundException("File not found.")
        }
        val bytes = disk.get(relative) ?: throw NotFoundException("File not found.")
        val filename = relative.substringAfterLast('/')
        return Response.file(bytes, contentType = MimeTypes.guess(filename), filename = filename)
    }
}
