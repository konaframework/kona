package io.konaframework.session

import io.konaframework.core.Application
import io.konaframework.core.ServiceProvider
import io.konaframework.redis.RedisConnection
import io.konaframework.redis.RedisManager

/**
 * Registers [SessionManager] as a container singleton (alias `session.manager`).
 *
 * When `session.driver=redis`, resolves [RedisConnection] or [RedisManager] from the container
 * (register [io.konaframework.redis.RedisServiceProvider] first).
 *
 * Cookie encryption: when `io.konaframework.security.Encrypter` is bound, or when
 * `session.encrypt=true` and `app.key` is set, encrypt/decrypt lambdas are wired into
 * the cookie session driver. Soft-depends on `kona-auth` (resolved reflectively).
 *
 * When encryption is unavailable, cookie payloads are HMAC-signed with `app.key` when present.
 */
class SessionServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(SessionManager::class.java) { container ->
            val redis = when {
                container.bound(RedisManager::class.java) ->
                    container.resolve(RedisManager::class.java).connection()
                container.bound(RedisConnection::class.java) ->
                    container.resolve(RedisConnection::class.java)
                else -> null
            }
            val (encrypt, decrypt) = resolveCookieCodec(app)
            val signKey = if (encrypt == null) resolveCookieSignKey(app) else null
            SessionManager(app.config, app.basePath, redis, encrypt, decrypt, signKey)
        }
        app.container.alias("session.manager", SessionManager::class.java.name)
    }

    companion object {
        private const val ENCRYPTER_CLASS = "io.konaframework.security.Encrypter"

        /**
         * Returns encrypt/decrypt pair when an Encrypter is available or
         * `session.encrypt=true` + `app.key` can build one.
         */
        internal fun resolveCookieCodec(
            app: Application,
        ): Pair<((String) -> String)?, ((String) -> String)?> {
            return try {
                val encClass = Class.forName(ENCRYPTER_CLASS)
                val instance = when {
                    app.container.bound(encClass.name) -> app.container.resolve<Any>(encClass.name)
                    app.config.getBoolean("session.encrypt", false) -> {
                        val key = app.config.getString("app.key")
                        if (key.isNullOrBlank()) {
                            return null to null
                        }
                        val fromBase64 = encClass.getMethod("fromBase64Key", String::class.java)
                        fromBase64.invoke(null, key)
                    }
                    else -> return null to null
                }
                val encryptMethod = encClass.getMethod("encryptString", String::class.java)
                val decryptMethod = encClass.getMethod("decryptString", String::class.java)
                val encrypt: (String) -> String = { value ->
                    encryptMethod.invoke(instance, value) as String
                }
                val decrypt: (String) -> String = { value ->
                    decryptMethod.invoke(instance, value) as String
                }
                encrypt to decrypt
            } catch (_: Exception) {
                // ClassNotFoundException when kona-auth is absent, or invalid app.key / reflective failures.
                null to null
            }
        }

        /** HMAC key from `app.key` when encryption codecs are not in use. */
        internal fun resolveCookieSignKey(app: Application): ByteArray? {
            val key = app.config.getString("app.key")
            if (key.isNullOrBlank()) {
                return null
            }
            return CookieSigner.keyFromAppKey(key)
        }
    }
}
