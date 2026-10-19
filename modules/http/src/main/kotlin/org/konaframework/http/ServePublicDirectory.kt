package org.konaframework.http

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Serves static files from a local [root] directory (scaffold `paths.public`).
 *
 * Maps `GET /branding/kona-logo.svg` → `{root}/branding/kona-logo.svg`. Falls through to
 * the next middleware/route when the file is missing or the path escapes [root]
 * (including via `..` or symlinks).
 */
class ServePublicDirectory(
    private val root: Path,
) : Middleware {
    override fun handle(request: Request, next: Handler): Response {
        val method = request.method.uppercase()
        if (method != "GET" && method != "HEAD") {
            return next.handle(request)
        }
        val relative = request.path.trimStart('/')
        if (relative.isEmpty()) {
            return next.handle(request)
        }
        val base = root.toAbsolutePath().normalize()
        val file = base.resolve(relative).normalize()
        // NOFOLLOW_LINKS rejects symlink escapes without following them off-root.
        if (!file.startsWith(base) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return next.handle(request)
        }
        val bytes = Files.readAllBytes(file)
        val filename = file.fileName.toString()
        return Response.file(bytes, contentType = MimeTypes.guess(filename), filename = filename)
    }
}
