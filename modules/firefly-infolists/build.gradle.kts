plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
        javaParameters.set(true)
    }
}

dependencies {
    api(project(":kona-firefly-schemas"))
    api(project(":kona-firefly-support"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
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
