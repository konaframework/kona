package dev.kona.http

import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class NettyTransportTest {
    @Test
    fun resolveRespectsPreferenceAndAvailability() {
        assertEquals(NettyTransport.Kind.NIO, NettyTransport.resolve("nio", epollAvailable = true))
        assertEquals(NettyTransport.Kind.NIO, NettyTransport.resolve("NIO", epollAvailable = false))
        assertEquals(NettyTransport.Kind.EPOLL, NettyTransport.resolve("epoll", epollAvailable = true))
        assertEquals(NettyTransport.Kind.NIO, NettyTransport.resolve("epoll", epollAvailable = false))
        assertEquals(NettyTransport.Kind.EPOLL, NettyTransport.resolve("auto", epollAvailable = true))
        assertEquals(NettyTransport.Kind.NIO, NettyTransport.resolve("auto", epollAvailable = false))
        assertEquals(NettyTransport.Kind.EPOLL, NettyTransport.resolve(null, epollAvailable = true))
        assertEquals(NettyTransport.Kind.NIO, NettyTransport.resolve(null, epollAvailable = false))
        assertEquals(NettyTransport.Kind.NIO, NettyTransport.resolve("nio"))
        val auto = NettyTransport.resolve()
        if (Epoll.isAvailable()) {
            assertEquals(NettyTransport.Kind.EPOLL, auto)
        } else {
            assertEquals(NettyTransport.Kind.NIO, auto)
        }
    }

    @Test
    fun groupsAndChannelClass() {
        for (kind in listOf(NettyTransport.Kind.NIO, NettyTransport.Kind.EPOLL)) {
            if (kind == NettyTransport.Kind.EPOLL && !Epoll.isAvailable()) {
                continue
            }
            val boss = NettyTransport.bossGroup(kind, 1)
            val workers = NettyTransport.workerGroup(kind, 1)
            try {
                val channel = NettyTransport.channelClass(kind)
                if (kind == NettyTransport.Kind.EPOLL) {
                    assertEquals(EpollServerSocketChannel::class.java, channel)
                } else {
                    assertEquals(NioServerSocketChannel::class.java, channel)
                }
                assertTrue(!boss.isShutdown)
                assertTrue(!workers.isShutdown)
            } finally {
                boss.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly()
                workers.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).syncUninterruptibly()
            }
        }
    }
}
