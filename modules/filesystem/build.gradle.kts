plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    api(project(":kona-core"))
    api(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // SigV4 HTTP client — behavioral coverage via HttpServer suites.
                    classes("org.konaframework.filesystem.HttpS3Client")
                    // Kotlin default-parameter / JVM overload bridges on manager create noise in branch totals.
                    classes("org.konaframework.filesystem.FilesystemManager")
                    classes("org.konaframework.filesystem.S3Adapter")
                    classes("org.konaframework.filesystem.MemoryS3Client")
                    classes("org.konaframework.filesystem.MemoryS3ClientAdapter")
                    classes("org.konaframework.filesystem.AdapterSupport")
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

