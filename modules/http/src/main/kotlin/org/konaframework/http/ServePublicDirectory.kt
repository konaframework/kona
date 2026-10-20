package org.konaframework.http

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Serves static files from one or more local directories (scaffold `paths.public`, and the
 * Vite public directory that holds `build/`).
 *
 * Maps `GET /branding/kona-logo.svg` → `{root}/branding/kona-logo.svg` and
 * `GET /build/assets/app-<hash>.js` → `{vitePublic}/build/assets/app-<hash>.js`.
 * Falls through when the file is missing from every root or the path escapes a root
 * (including via `..` or symlinks).
 */
class ServePublicDirectory(
    private val roots: List<Path>,
) : Middleware {
    constructor(root: Path) : this(listOf(root))

    /** True when [directory] is one of the roots this middleware reads (after normalization). */
    fun serves(directory: Path): Boolean {
        val target = directory.toAbsolutePath().normalize()
        return roots.any { it.toAbsolutePath().normalize() == target }
    }

    override fun handle(request: Request, next: Handler): Response {
        val method = request.method.uppercase()
        if (method != "GET" && method != "HEAD") {
            return next.handle(request)
        }
        val relative = request.path.trimStart('/')
        if (relative.isEmpty()) {
            return next.handle(request)
        }
        for (root in roots) {
            val file = fileUnder(root, relative)
            if (file != null) {
                val bytes = Files.readAllBytes(file)
                val filename = file.fileName.toString()
                return Response.file(bytes, contentType = MimeTypes.guess(filename), filename = filename)
            }
        }
        return next.handle(request)
    }

    private fun fileUnder(root: Path, relative: String): Path? {
        val base = root.toAbsolutePath().normalize()
        val file = base.resolve(relative).normalize()
        // NOFOLLOW_LINKS rejects symlink escapes without following them off-root.
        if (!file.startsWith(base) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return null
        }
        return file
    }
}
