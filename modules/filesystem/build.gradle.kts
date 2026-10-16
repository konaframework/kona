plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
    `maven-publish`
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
                    classes("io.konaframework.filesystem.HttpS3Client")
                    // Kotlin default-parameter / JVM overload bridges on manager create noise in branch totals.
                    classes("io.konaframework.filesystem.FilesystemManager")
                    classes("io.konaframework.filesystem.S3Adapter")
                    classes("io.konaframework.filesystem.MemoryS3Client")
                    classes("io.konaframework.filesystem.MemoryS3ClientAdapter")
                    classes("io.konaframework.filesystem.AdapterSupport")
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "kona-filesystem"
        }
    }
}
