package org.konaframework.http

/**
 * RFC 7578 `multipart/form-data` parser producing text fields and [UploadedFile]s.
 */
internal object MultipartParser {
    data class Result(
        val fields: Map<String, String>,
        val files: Map<String, UploadedFile>,
    )

    fun parse(body: ByteArray, contentType: String): Result {
        val boundary = boundaryOf(contentType) ?: return Result(emptyMap(), emptyMap())
        val delimiter = ("--$boundary").toByteArray(Charsets.UTF_8)
        val parts = splitParts(body, delimiter)
        val fields = linkedMapOf<String, String>()
        val files = linkedMapOf<String, UploadedFile>()
        for (part in parts) {
            if (part.isEmpty()) {
                continue
            }
            val headerEnd = indexOf(part, CRLFCRLF)
            if (headerEnd < 0) {
                continue
            }
            val headerBytes = part.copyOfRange(0, headerEnd)
            var content = part.copyOfRange(headerEnd + 4, part.size)
            if (content.size >= 2 && content[content.size - 2] == '\r'.code.toByte() &&
                content[content.size - 1] == '\n'.code.toByte()
            ) {
                content = content.copyOfRange(0, content.size - 2)
            }
            val headers = String(headerBytes, Charsets.UTF_8)
            val disposition = headerValue(headers, "Content-Disposition") ?: continue
            val name = param(disposition, "name") ?: continue
            val filename = param(disposition, "filename")
            if (filename != null) {
                val mime = headerValue(headers, "Content-Type")
                files[name] = UploadedFile(
                    clientOriginalName = filename,
                    clientMimeType = mime,
                    contents = content,
                    error = if (content.isEmpty() && filename.isEmpty()) {
                        UploadedFile.UPLOAD_ERR_NO_FILE
                    } else {
                        UploadedFile.UPLOAD_ERR_OK
                    },
                )
            } else {
                fields[name] = String(content, Charsets.UTF_8)
            }
        }
        return Result(fields, files)
    }

    private fun boundaryOf(contentType: String): String? {
        val parts = contentType.split(';')
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("boundary=", ignoreCase = true)) {
                return trimmed.substringAfter('=').trim().trim('"')
            }
        }
        return null
    }

    private fun splitParts(body: ByteArray, delimiter: ByteArray): List<ByteArray> {
        val parts = mutableListOf<ByteArray>()
        var start = indexOf(body, delimiter)
        if (start < 0) {
            return emptyList()
        }
        start += delimiter.size
        if (start < body.size && body[start] == '\r'.code.toByte()) {
            start++
        }
        if (start < body.size && body[start] == '\n'.code.toByte()) {
            start++
        }
        while (start < body.size) {
            val next = indexOf(body, delimiter, start)
            if (next < 0) {
                break
            }
            var end = next
            if (end >= 2 && body[end - 2] == '\r'.code.toByte() && body[end - 1] == '\n'.code.toByte()) {
                end -= 2
            }
            parts += body.copyOfRange(start, end)
            start = next + delimiter.size
            // Closing delimiter ends with "--"
            if (start + 1 < body.size && body[start] == '-'.code.toByte() && body[start + 1] == '-'.code.toByte()) {
                break
            }
            if (start < body.size && body[start] == '\r'.code.toByte()) {
                start++
            }
            if (start < body.size && body[start] == '\n'.code.toByte()) {
                start++
            }
        }
        return parts
    }

    private fun headerValue(headers: String, name: String): String? {
        for (line in headers.split("\r\n", "\n")) {
            val idx = line.indexOf(':')
            if (idx < 0) {
                continue
            }
            if (line.substring(0, idx).trim().equals(name, ignoreCase = true)) {
                return line.substring(idx + 1).trim()
            }
        }
        return null
    }

    private fun param(header: String, key: String): String? {
        val pattern = Regex("""\b$key="([^"]*)"|\b$key=([^;\s]+)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(header) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray, from: Int = 0): Int {
        if (needle.isEmpty() || from > haystack.size - needle.size) {
            return -1
        }
        outer@ for (i in from..haystack.size - needle.size) {
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    continue@outer
                }
            }
            return i
        }
        return -1
    }

    private val CRLFCRLF = byteArrayOf(13, 10, 13, 10)
}
