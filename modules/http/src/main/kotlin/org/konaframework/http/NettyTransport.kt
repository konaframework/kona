package org.konaframework.http

import io.netty.channel.EventLoopGroup
import io.netty.channel.ServerChannel
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel

/**
 * Selects Netty epoll (Linux) or NIO. Override with system property `kona.http.transport`
 * (`auto` | `epoll` | `nio`).
 */
internal object NettyTransport {
    enum class Kind {
        EPOLL,
        NIO,
    }

    fun resolve(): Kind = resolve(System.getProperty("kona.http.transport"), Epoll.isAvailable())

    fun resolve(preference: String?): Kind = resolve(preference, Epoll.isAvailable())

    fun resolve(preference: String?, epollAvailable: Boolean): Kind {
        return when (preference?.lowercase()) {
            "nio" -> Kind.NIO
            "epoll" -> if (epollAvailable) Kind.EPOLL else Kind.NIO
            else -> if (epollAvailable) Kind.EPOLL else Kind.NIO
        }
    }

    fun bossGroup(kind: Kind, threads: Int = 1): EventLoopGroup =
        when (kind) {
            Kind.EPOLL -> EpollEventLoopGroup(threads)
            Kind.NIO -> NioEventLoopGroup(threads)
        }

    fun workerGroup(kind: Kind, threads: Int = 0): EventLoopGroup =
        when (kind) {
            Kind.EPOLL -> EpollEventLoopGroup(threads)
            Kind.NIO -> NioEventLoopGroup(threads)
        }

    fun channelClass(kind: Kind): Class<out ServerChannel> =
        when (kind) {
            Kind.EPOLL -> EpollServerSocketChannel::class.java
            Kind.NIO -> NioServerSocketChannel::class.java
        }
}
