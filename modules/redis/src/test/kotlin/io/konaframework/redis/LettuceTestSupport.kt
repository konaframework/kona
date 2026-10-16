package io.konaframework.redis

/**
 * Java-friendly hooks for exercising [LettuceRedisConnection] connect forwarders.
 */
object LettuceTestSupport {
    @JvmStatic
    fun fakeConnection(): LettuceRedisConnection =
        LettuceRedisConnection(
            object : RedisSync {
                override fun get(key: String): String? = null
                override fun set(key: String, value: String) {}
                override fun setex(key: String, seconds: Long, value: String) {}
                override fun del(vararg keys: String): Long = 0
                override fun exists(key: String): Long = 0
                override fun expire(key: String, seconds: Long): Boolean = false
                override fun ttl(key: String): Long = -2
                override fun keys(pattern: String): List<String> = emptyList()
                override fun flushdb() {}
                override fun ping(): String = "PONG"
            },
        ) {}

    @JvmStatic
    fun withOpener(fake: LettuceRedisConnection, block: Runnable) {
        val previous = LettuceRedisConnection.liveOpener
        LettuceRedisConnection.liveOpener = { fake }
        try {
            block.run()
        } finally {
            LettuceRedisConnection.liveOpener = previous
        }
    }
}
