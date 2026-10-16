package io.konaframework.http

/**
 * Lightweight content-type guesses from file extensions (download / public storage).
 */
object MimeTypes {
    private val BY_EXTENSION = mapOf(
        "txt" to "text/plain; charset=utf-8",
        "html" to "text/html; charset=utf-8",
        "htm" to "text/html; charset=utf-8",
        "css" to "text/css; charset=utf-8",
        "js" to "text/javascript; charset=utf-8",
        "json" to "application/json; charset=utf-8",
        "xml" to "application/xml",
        "pdf" to "application/pdf",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "svg" to "image/svg+xml",
        "ico" to "image/x-icon",
        "bmp" to "image/bmp",
        "mp3" to "audio/mpeg",
        "mp4" to "video/mp4",
        "zip" to "application/zip",
        "gz" to "application/gzip",
        "csv" to "text/csv; charset=utf-8",
        "bin" to "application/octet-stream",
    )

    @JvmStatic
    fun guess(filename: String): String {
        val name = filename.substringAfterLast('/').substringAfterLast('\\')
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (ext.isEmpty()) {
            return "application/octet-stream"
        }
        return BY_EXTENSION[ext] ?: "application/octet-stream"
    }
}
