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
    api(project(":kona-http"))
    api(project(":kona-auth"))
    api(project(":kona-firefly-support"))
    api(project(":kona-firefly-schemas"))
    api(project(":kona-firefly-actions"))
    api(project(":kona-firefly-infolists"))
    api(project(":kona-firefly-tables"))
    api(project(":kona-firefly-forms"))
    api(project(":kona-firefly-widgets"))
    api(project(":kona-firefly-notifications"))
    api(project(":kona-queue"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.h2)
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
