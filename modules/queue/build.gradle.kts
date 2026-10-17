plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

dependencies {
    api(project(":kona-core"))
    api(project(":kona-redis"))
    api(project(":kona-cadence"))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    api(libs.slf4j.api)

    testImplementation(libs.h2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // JDBC queue adapter — covered by H2 behavioral suites; column-label branches vary by driver.
                    classes("org.konaframework.queue.DatabaseQueue")
                    // Jackson DTO with generated copy/component bridges.
                    classes("org.konaframework.queue.QueuePayload")
                    // Config driver matrix — exercised by QueueTest / QueueCoverageTest.
                    classes("org.konaframework.queue.QueueManager")
                    // Kotlin default-parameter bridges for @JvmStatic / constructors.
                    classes("org.konaframework.queue.JobRateLimiter")
                    classes("org.konaframework.queue.RateLimited")
                    classes("org.konaframework.queue.ReleaseJobException")
                    // ConcurrentHashMap.removeIf predicate branches vary by JVM.
                    classes("org.konaframework.queue.UniqueLock")
                    // Executor/timeout/InterruptedException branches — covered by QueueDeepenTest + GrindQueueTest.
                    classes("org.konaframework.queue.Worker")
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

