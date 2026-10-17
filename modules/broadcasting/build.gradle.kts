plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

dependencies {
    api(project(":kona-core"))
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // Boolean factory constants confuse JaCoCo branch maps on companion one-liners.
                    classes("io.konaframework.broadcasting.Channel")
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

