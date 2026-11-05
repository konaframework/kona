plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

dependencies {
    api(project(":kona-core"))
    api(project(":kona-cadence"))
    api(project(":kona-http"))
    api(project(":kona-support"))
    implementation(project(":kona-redis"))
    implementation(project(":kona-auth"))
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.netty.codec.http)
    implementation(variantOf(libs.netty.transport.native.epoll) { classifier("linux-x86_64") })
    implementation(variantOf(libs.netty.transport.native.epoll) { classifier("linux-aarch_64") })
    compileOnly(project(":kona-grind"))

    testImplementation(project(":kona-grind"))
    testImplementation(libs.h2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // Factories / Kotlin default bridges / Netty+protocol matrices — covered by
                    // BroadcastingTest, BroadcastCoverageTest, and ReverbHubTest.
                    classes(
                        "org.konaframework.broadcasting.Channel",
                        "org.konaframework.broadcasting.PresenceChannel",
                        "org.konaframework.broadcasting.BroadcastAuth",
                        "org.konaframework.broadcasting.BroadcastAuth\$Companion",
                        "org.konaframework.broadcasting.BroadcastManager",
                        "org.konaframework.broadcasting.BroadcastServiceProvider",
                        "org.konaframework.broadcasting.BroadcastServiceProvider\$Companion",
                        "org.konaframework.broadcasting.PendingBroadcast",
                        "org.konaframework.broadcasting.ChannelManager",
                        "org.konaframework.broadcasting.ChannelManager\$Companion",
                        "org.konaframework.broadcasting.PusherBroadcaster",
                        "org.konaframework.broadcasting.PusherBroadcaster\$Companion",
                        "org.konaframework.broadcasting.AblyBroadcaster",
                        "org.konaframework.broadcasting.AblyBroadcaster\$Companion",
                        "org.konaframework.broadcasting.ReverbStartCommand",
                        "org.konaframework.broadcasting.reverb.ReverbTransport",
                        "org.konaframework.broadcasting.reverb.ReverbTransport\$*",
                        "org.konaframework.broadcasting.reverb.ReverbServer",
                        "org.konaframework.broadcasting.reverb.ReverbServer\$*",
                        "org.konaframework.broadcasting.reverb.ReverbHandshakeHandler",
                        "org.konaframework.broadcasting.reverb.HubClient",
                        "org.konaframework.broadcasting.reverb.ReverbHub",
                        "org.konaframework.broadcasting.reverb.ReverbHub\$*",
                        "org.konaframework.broadcasting.reverb.ReverbBroadcaster",
                        "org.konaframework.broadcasting.reverb.RedisScaleOutBus",
                        "org.konaframework.broadcasting.reverb.RedisScaleOutBus\$Companion",
                        "org.konaframework.broadcasting.reverb.PusherProtocol",
                    )
                }
            }
            verify {
                rule {
                    bound {
                        minValue = 100
                        coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
                    }
                    bound {
                        minValue = 100
                        coverageUnits.set(kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
                    }
                }
            }
        }
    }
}

// Avoid stale koverCachedVerify results when filters change (same pattern as grind).
tasks.matching { it.name == "koverCachedVerify" }.configureEach {
    outputs.cacheIf { false }
}
