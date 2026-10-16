package io.konaframework.session

import io.konaframework.core.Config
import io.konaframework.http.Cookie
import io.konaframework.http.Request
import io.konaframework.http.Response
import io.konaframework.redis.RedisConnection
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves session drivers from config and starts/saves sessions on requests.
 *
 * Config keys:
 * - `session.driver` — `array` / `memory`, `cookie`, `file`, `redis` (default `array`)
 * - `session.lifetime` — minutes (default `120`)
 * - `session.cookie` — cookie name (default `kona_session`)
 * - `session.path` — cookie path (default `/`)
 * - `session.files` — file store directory (default `{base}/storage/framework/sessions`)
 * - `session.secure` / `session.http_only` / `session.same_site` — cookie flags
 * - `session.encrypt` — when `true` (and an app key / Encrypter is available), cookie
 *   payloads are encrypted; [SessionServiceProvider] also wires encryption when an
 *   `Encrypter` is already bound in the container
 *
 * When encryption is off and `app.key` is set, cookie payloads are HMAC-signed instead.
 */
class SessionManager @JvmOverloads constructor(
    private val config: Config,
    private val basePath: Path,
    private val redis: RedisConnection? = null,
    private val cookieEncrypt: ((String) -> String)? = null,
    private val cookieDecrypt: ((String) -> String)? = null,
    private val cookieSignKey: ByteArray? = null,
) {
    private val stores = ConcurrentHashMap<String, SessionStore>()

    fun driverName(): String = config.getString("session.driver", "array")

    fun lifetime(): Int = config.getInt("session.lifetime", 120)

    fun cookieName(): String = config.getString("session.cookie", "kona_session")

    fun cookiePath(): String = config.getString("session.path", "/")

    fun driver(): SessionStore = driver(driverName())

    fun driver(name: String): SessionStore {
        val key = name.lowercase()
        return stores.getOrPut(key) { createDriver(key) }
    }

    /** Loads (or creates) a session for [request] without attaching attributes. */
    fun start(request: Request): StoreSession = start(request, driverName())

    fun start(request: Request, driverName: String): StoreSession {
        val store = driver(driverName)
        val cookieValue = request.cookie(cookieName())
        val id = resolveId(store, cookieValue)
        val data = store.read(id)
        val known = when {
            data.isNotEmpty() -> true
            store is CookieSessionStore -> !cookieValue.isNullOrBlank()
            else -> cookieValue != null && SessionId.isValid(cookieValue)
        }
        return StoreSession(id, store, LinkedHashMap(data)).start(exists = known)
    }

    /** Persists [session] and queues the session cookie on [response]. */
    fun save(session: StoreSession, response: Response): Unit = save(session, response, driverName())

    fun save(session: StoreSession, response: Response, driverName: String) {
        val store = driver(driverName)
        val minutes = lifetime()
        val userDataEmpty = session.all().filterKeys { !it.startsWith("_flash") }.isEmpty()
        if (userDataEmpty && store is CookieSessionStore) {
            store.destroy(session.id)
        } else {
            session.save(minutes)
        }
        val value = when (store) {
            is CookieSessionStore -> store.outboundPayload
            else -> session.id
        }
        val maxAge = if (value.isEmpty()) 0L else minutes.toLong().coerceAtLeast(0) * 60L
        val sameSite = when ((config.getString("session.same_site") ?: "Lax").lowercase()) {
            "strict" -> Cookie.SameSite.Strict
            "none" -> Cookie.SameSite.None
            else -> Cookie.SameSite.Lax
        }
        response.cookie(
            name = cookieName(),
            value = value,
            maxAgeSeconds = maxAge,
            path = cookiePath(),
            secure = config.getBoolean("session.secure", false),
            httpOnly = config.getBoolean("session.http_only", true),
            sameSite = sameSite,
        )
    }

    fun forgetDriver(name: String) {
        stores.remove(name.lowercase())
    }

    private fun resolveId(store: SessionStore, cookieValue: String?): String {
        if (store is CookieSessionStore) {
            store.inboundPayload = cookieValue
            store.outboundPayload = ""
            val embedded = store.readId(cookieValue)
            if (embedded != null && SessionId.isValid(embedded)) {
                return embedded
            }
            return SessionId.generate()
        }
        if (cookieValue != null && SessionId.isValid(cookieValue)) {
            return cookieValue
        }
        return SessionId.generate()
    }

    private fun createDriver(name: String): SessionStore =
        when (name) {
            "array", "memory" -> ArraySessionStore()
            "cookie" -> CookieSessionStore(
                encrypt = cookieEncrypt,
                decrypt = cookieDecrypt,
                signKey = if (cookieEncrypt == null) cookieSignKey else null,
            )
            "file" -> FileSessionStore(filesPath())
            "redis" -> {
                val connection = redis
                    ?: throw IllegalStateException("Redis session driver requires a RedisConnection")
                val prefix = config.getString("session.connection_prefix", "kona_session:")
                RedisSessionStore(connection, prefix)
            }
            else -> throw IllegalArgumentException("Unsupported session driver [$name]")
        }

    private fun filesPath(): Path {
        val configured = config.getString("session.files")
        return if (configured.isNullOrBlank()) {
            basePath.resolve("storage").resolve("framework").resolve("sessions")
        } else {
            val path = Path.of(configured)
            if (path.isAbsolute) path else basePath.resolve(configured)
        }
    }

    companion object {
        @JvmStatic
        fun fromConfig(config: Config, basePath: Path): SessionManager =
            SessionManager(config, basePath)
    }
}
