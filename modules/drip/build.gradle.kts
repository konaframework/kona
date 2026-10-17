plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

dependencies {
    api(project(":kona-core"))
    api(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        total {
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

