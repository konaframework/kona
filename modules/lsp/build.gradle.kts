plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    application
    `java-library`
}

application {
    applicationName = "kona-lsp"
    mainClass.set("org.konaframework.lsp.LspKt")
}

dependencies {
    api(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)

    testImplementation(libs.junit.jupiter)
    testImplementation(project(":kona-firefly"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.konaframework.lsp.LspKt"
    }
}

kover {
    reports {
        total {
            filters {
                excludes {
                    // Process entrypoint only — LspServer is fully covered via injected streams.
                    classes("org.konaframework.lsp.LspKt")
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

