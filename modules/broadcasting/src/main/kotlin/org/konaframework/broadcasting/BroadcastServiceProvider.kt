package org.konaframework.broadcasting

import org.konaframework.core.Application
import org.konaframework.core.ServiceProvider
import org.konaframework.http.Router
import org.konaframework.redis.RedisManager

class BroadcastServiceProvider : ServiceProvider {
    override fun register(app: Application) {
        app.container.singleton(ChannelManager::class.java) {
            ChannelManager().also { Broadcast.setChannels(it) }
        }
        app.container.singleton(BroadcastManager::class.java) {
            val redis =
                if (app.container.bound(RedisManager::class.java)) {
                    app.resolve(RedisManager::class.java)
                } else {
                    null
                }
            BroadcastManager(app.config, redis).also { Broadcast.setManager(it) }
        }
        app.commands(ReverbStartCommand::class.java)
    }

    override fun boot(app: Application) {
        app.resolve(BroadcastManager::class.java)
        app.resolve(ChannelManager::class.java)
        if (app.container.bound(Router::class.java)) {
            routes(app, app.resolve(Router::class.java))
        }
    }

    companion object {
        @JvmStatic
        fun routes(app: Application, router: Router) {
            val prefix = "broadcasting.connections." + app.config.getString("broadcasting.default", "websocket")
            val key = app.config.getString("$prefix.key")
                ?: app.config.getString("broadcasting.connections.websocket.key", "kona-key")
            val secret = app.config.getString("$prefix.secret")
                ?: app.config.getString("broadcasting.connections.websocket.secret", "kona-secret")
            val auth =
                BroadcastAuth(
                    channels = app.resolve(ChannelManager::class.java),
                    key = key,
                    secret = secret,
                )
            router.post("/broadcasting/auth") { auth.handle(it) }
        }
    }
}
