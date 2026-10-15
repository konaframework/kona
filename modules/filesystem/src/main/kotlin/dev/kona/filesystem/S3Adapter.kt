package dev.kona.filesystem

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * In-memory S3-compatible store for tests and local fakes.
 */
class MemoryS3Client {
    private val objects = ConcurrentHashMap<String, ByteArray>()

    fun put(key: String, bytes: ByteArray) {
        objects[key] = bytes.copyOf()
    }

    fun get(key: String): ByteArray? = objects[key]?.copyOf()

    fun delete(key: String): Boolean = objects.remove(key) != null

    fun keys(): List<String> = keys("")

    fun keys(prefix: String): List<String> =
        objects.keys.filter { it.startsWith(prefix) }.sorted()
}

interface S3Client {
    fun putObject(key: String, bytes: ByteArray)

    fun getObject(key: String): ByteArray?

    fun deleteObject(key: String): Boolean

    fun listKeys(prefix: String = ""): List<String>
}

/**
 * Adapter over an [S3Client] (memory or HTTP).
 *
 * [pathPrefix] is prepended to every object key (cloud root prefix).
 */
class S3Adapter @JvmOverloads constructor(
    private val client: S3Client,
    private val bucket: String,
    private val urlPrefix: String = "",
    private val pathPrefix: String = "",
    private val directories: MutableSet<String> = ConcurrentHashMap.newKeySet(),
) : FilesystemAdapter {
    @Volatile
    private var temporaryUrlBuilder: BiFunction<String, Long, String>? = null

    private val visibility = ConcurrentHashMap<String, String>()

    fun buildTemporaryUrlsUsing(builder: BiFunction<String, Long, String>) {
        temporaryUrlBuilder = builder
    }

    override fun put(path: String, contents: ByteArray) {
        val key = key(path)
        ensureParent(key)
        client.putObject(key, contents)
    }

    override fun put(path: String, contents: String) {
        put(path, contents.toByteArray(Charsets.UTF_8))
    }

    override fun get(path: String): ByteArray? = client.getObject(key(path))

    override fun getString(path: String): String? = get(path)?.toString(Charsets.UTF_8)

    override fun exists(path: String): Boolean =
        client.getObject(key(path)) != null || directories.contains(key(path).trimEnd('/'))

    override fun missing(path: String): Boolean = !exists(path)

    override fun delete(path: String): Boolean {
        visibility.remove(key(path))
        return client.deleteObject(key(path))
    }

    override fun copy(from: String, to: String): Boolean {
        val bytes = client.getObject(key(from)) ?: return false
        put(to, bytes)
        visibility[key(from)]?.let { visibility[key(to)] = it }
        return true
    }

    override fun move(from: String, to: String): Boolean {
        if (!copy(from, to)) return false
        delete(from)
        return true
    }

    override fun size(path: String): Long =
        client.getObject(key(path))?.size?.toLong()
            ?: throw IllegalStateException("S3 object [$path] not found")

    override fun lastModified(path: String): Long =
        if (exists(path)) System.currentTimeMillis() else throw IllegalStateException("S3 object [$path] not found")

    override fun mimeType(path: String): String? {
        if (client.getObject(key(path)) == null) return null
        return AdapterSupport.mimeFromPath(path)
    }

    override fun checksum(path: String, algorithm: String): String? {
        val bytes = get(path) ?: return null
        return AdapterSupport.checksum(bytes, algorithm)
    }

    override fun files(directory: String): List<String> {
        val prefix = listPrefix(directory)
        return client.listKeys(prefix)
            .filter { objectKey ->
                val rest = objectKey.removePrefix(prefix)
                rest.isNotEmpty() && !rest.contains('/')
            }
            .map { it.removePrefix(prefix) }
    }

    override fun allFiles(directory: String): List<String> {
        val prefix = listPrefix(directory)
        val base = if (directory.isBlank()) "" else directory.replace('\\', '/').trim('/') + "/"
        return client.listKeys(prefix)
            .map { base + it.removePrefix(prefix) }
            .sorted()
    }

    override fun directories(directory: String): List<String> {
        val prefix = listPrefix(directory)
        return client.listKeys(prefix)
            .mapNotNull { objectKey ->
                val rest = objectKey.removePrefix(prefix)
                val slash = rest.indexOf('/')
                if (slash > 0) rest.substring(0, slash) else null
            }
            .distinct()
            .sorted()
    }

    override fun allDirectories(directory: String): List<String> {
        val prefix = listPrefix(directory)
        val base = if (directory.isBlank()) "" else directory.replace('\\', '/').trim('/') + "/"
        val found = linkedSetOf<String>()
        client.listKeys(prefix).forEach { objectKey ->
            val rest = objectKey.removePrefix(prefix)
            var idx = rest.indexOf('/')
            while (idx > 0) {
                found += base + rest.substring(0, idx)
                idx = rest.indexOf('/', idx + 1)
            }
        }
        for (dir in directories) {
            if (!dir.startsWith(prefix.trimEnd('/')) && prefix.isNotEmpty()) continue
            val relative = unprefixKey(dir)
            if (relative.isEmpty()) continue
            found += if (base.isEmpty()) relative else base + relative.removePrefix(base)
        }
        return found.sorted()
    }

    override fun makeDirectory(path: String): Boolean {
        directories += key(path).trimEnd('/')
        return true
    }

    override fun deleteDirectory(path: String): Boolean {
        val prefix = listPrefix(path)
        client.listKeys(prefix).forEach {
            client.deleteObject(it)
            visibility.remove(it)
        }
        directories.remove(key(path).trimEnd('/'))
        return true
    }

    override fun url(path: String): String {
        val normalized = path.trimStart('/')
        return if (urlPrefix.isBlank()) {
            "s3://$bucket/${key(path)}"
        } else {
            "${urlPrefix.trimEnd('/')}/$normalized"
        }
    }

    override fun path(path: String): String = "$bucket/${key(path)}"

    override fun append(path: String, data: String) = AdapterSupport.append(this, path, data)

    override fun prepend(path: String, data: String) = AdapterSupport.prepend(this, path, data)

    override fun readStream(path: String): InputStream? {
        val bytes = get(path) ?: return null
        return ByteArrayInputStream(bytes)
    }

    override fun writeStream(path: String, input: InputStream) {
        AdapterSupport.writeStream(this, path, input)
    }

    override fun putFile(directory: String, source: Path): String =
        AdapterSupport.putFile(this, directory, source)

    override fun putFileAs(directory: String, source: Path, name: String): String =
        AdapterSupport.putFileAs(this, directory, source, name)

    override fun getVisibility(path: String): String =
        visibility[key(path)] ?: Visibility.PRIVATE

    override fun setVisibility(path: String, visibility: String) {
        this.visibility[key(path)] = visibility
    }

    override fun providesTemporaryUrls(): Boolean = temporaryUrlBuilder != null

    override fun temporaryUrl(path: String, expirationSeconds: Long): String {
        val builder = temporaryUrlBuilder
            ?: throw IllegalStateException("This disk does not support creating temporary URLs.")
        return builder.apply(url(path), expirationSeconds)
    }

    private fun rootPrefix(): String {
        val p = pathPrefix.replace('\\', '/').trim('/').trimEnd('/')
        return if (p.isEmpty()) "" else "$p/"
    }

    private fun key(path: String): String {
        val relative = path.replace('\\', '/').trimStart('/')
        return rootPrefix() + relative
    }

    private fun unprefixKey(objectKey: String): String {
        val root = rootPrefix()
        return if (root.isEmpty()) objectKey else objectKey.removePrefix(root)
    }

    private fun listPrefix(directory: String): String {
        val d = directory.replace('\\', '/').trimStart('/').trimEnd('/')
        val relative = if (d.isEmpty()) "" else "$d/"
        return rootPrefix() + relative
    }

    private fun ensureParent(key: String) {
        val slash = key.lastIndexOf('/')
        if (slash > 0) {
            directories += key.substring(0, slash)
        }
    }
}

/**
 * Minimal path-style S3-compatible HTTP client (AWS SigV4).
 */
class HttpS3Client @JvmOverloads constructor(
    private val endpoint: String,
    private val bucket: String,
    private val accessKey: String,
    private val secretKey: String,
    private val region: String = "us-east-1",
    private val http: HttpClient = HttpClient.newHttpClient(),
) : S3Client {
    override fun putObject(key: String, bytes: ByteArray) {
        val uri = objectUri(key)
        val headers = signedHeaders("PUT", uri, bytes, "application/octet-stream")
        val request = HttpRequest.newBuilder(uri)
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .headers(*headers)
            .build()
        val response = send(request, HttpResponse.BodyHandlers.discarding())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("S3 PUT failed: HTTP ${response.statusCode()}")
        }
    }

    override fun getObject(key: String): ByteArray? {
        val uri = objectUri(key)
        val headers = signedHeaders("GET", uri, ByteArray(0), null)
        val request = HttpRequest.newBuilder(uri).GET().headers(*headers).build()
        val response = send(request, HttpResponse.BodyHandlers.ofByteArray())
        return when (response.statusCode()) {
            200 -> response.body()
            404 -> null
            else -> throw IllegalStateException("S3 GET failed: HTTP ${response.statusCode()}")
        }
    }

    override fun deleteObject(key: String): Boolean {
        val uri = objectUri(key)
        val headers = signedHeaders("DELETE", uri, ByteArray(0), null)
        val request = HttpRequest.newBuilder(uri).DELETE().headers(*headers).build()
        val response = send(request, HttpResponse.BodyHandlers.discarding())
        return response.statusCode() in 200..299 || response.statusCode() == 404
    }

    override fun listKeys(prefix: String): List<String> {
        val encoded = URLEncoder.encode(prefix, StandardCharsets.UTF_8)
        val uri = URI.create("${endpoint.trimEnd('/')}/$bucket?list-type=2&prefix=$encoded")
        val headers = signedHeaders("GET", uri, ByteArray(0), null)
        val request = HttpRequest.newBuilder(uri).GET().headers(*headers).build()
        val response = send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("S3 LIST failed: HTTP ${response.statusCode()}")
        }
        return Regex("<Key>(.*?)</Key>").findAll(response.body()).map { it.groupValues[1] }.toList()
    }

    private fun <T> send(request: HttpRequest, handler: HttpResponse.BodyHandler<T>): HttpResponse<T> =
        try {
            http.send(request, handler)
        } catch (ex: Exception) {
            throw IllegalStateException("S3 request failed: ${ex.message}", ex)
        }

    private fun objectUri(key: String): URI =
        URI.create("${endpoint.trimEnd('/')}/$bucket/${key.trimStart('/')}")

    private fun signedHeaders(
        method: String,
        uri: URI,
        payload: ByteArray,
        contentType: String?,
    ): Array<String> {
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        val amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").format(now)
        val dateStamp = DateTimeFormatter.ofPattern("yyyyMMdd").format(now)
        val payloadHash = sha256Hex(payload)
        val headers = TreeMap<String, String>()
        headers["host"] = uri.host + if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
        headers["x-amz-content-sha256"] = payloadHash
        headers["x-amz-date"] = amzDate
        if (contentType != null) headers["content-type"] = contentType
        val signedHeaderNames = headers.keys.joinToString(";")
        val canonicalHeaders = headers.entries.joinToString("") { "${it.key}:${it.value}\n" }
        val canonicalQuery = uri.rawQuery ?: ""
        val canonicalRequest = listOf(
            method,
            uri.path,
            canonicalQuery,
            canonicalHeaders,
            signedHeaderNames,
            payloadHash,
        ).joinToString("\n")
        val credentialScope = "$dateStamp/$region/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(StandardCharsets.UTF_8)),
        ).joinToString("\n")
        val signingKey = getSignatureKey(secretKey, dateStamp, region, "s3")
        val signature = hmacHex(signingKey, stringToSign)
        val authorization =
            "AWS4-HMAC-SHA256 Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeaderNames, Signature=$signature"
        val flat = mutableListOf<String>()
        headers.forEach { (k, v) ->
            if (k != "host") {
                flat += k
                flat += v
            }
        }
        flat += "authorization"
        flat += authorization
        return flat.toTypedArray()
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hmacHex(key: ByteArray, data: String): String =
        hmac(key, data).joinToString("") { "%02x".format(it) }

    private fun getSignatureKey(key: String, dateStamp: String, regionName: String, serviceName: String): ByteArray {
        val kDate = hmac("AWS4$key".toByteArray(StandardCharsets.UTF_8), dateStamp)
        val kRegion = hmac(kDate, regionName)
        val kService = hmac(kRegion, serviceName)
        return hmac(kService, "aws4_request")
    }
}

/** Wraps [MemoryS3Client] as an [S3Client]. */
class MemoryS3ClientAdapter(private val memory: MemoryS3Client = MemoryS3Client()) : S3Client {
    override fun putObject(key: String, bytes: ByteArray) = memory.put(key, bytes)

    override fun getObject(key: String): ByteArray? = memory.get(key)

    override fun deleteObject(key: String): Boolean = memory.delete(key)

    override fun listKeys(prefix: String): List<String> = memory.keys(prefix)
}
