plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    application
}

application {
    mainClass.set("org.konaframework.grind.GrindKt")
}

dependencies {
    api(project(":kona-http"))
    api(project(":kona-cadence"))
    api(project(":kona-crema"))
    api(project(":kona-queue"))
    api(project(":kona-schedule"))
    api(project(":kona-support"))
    implementation(libs.logback.classic)
    implementation(libs.kotlin.scripting.jsr223)

    testImplementation(libs.h2)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kover {
    reports {
        // Common filters only. `total { filters }` *replaces* this block (Kover 0.9);
        // when koverCachedVerify drops the variant filters on CI the replacement is empty
        // and verification runs unfiltered. SteamCommand and Prompts are not excluded:
        // CI has counted them, and GrindM8Test covers both (including the null-engine fallback).
        // Inner `$*` matches http's MultipartParser pattern.
        filters {
            excludes {
                classes(
                    "org.konaframework.grind.GrindKt",
                    "org.konaframework.grind.GrindKt\$*",
                    // ClassLoader resource enumeration — covered by GrindM8Test.
                    "org.konaframework.grind.PackageDiscovery",
                    "org.konaframework.grind.PackageDiscovery\$*",
                    // Top-level command router — exercised by GrindTest / GrindM8Test suites.
                    "org.konaframework.grind.GrindApplication",
                    "org.konaframework.grind.GrindApplication\$*",
                    "org.konaframework.grind.InstallCommand",
                    "org.konaframework.grind.InstallCommand\$*",
                )
                annotatedBy("org.konaframework.support.KoverExclude")
            }
        }
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

// koverCachedVerify is @CacheableTask; a stale unfiltered result (~94% branches) has
// blocked PRs that do not touch grind. Never reuse cached verification for this module.
tasks.matching { it.name == "koverCachedVerify" }.configureEach {
    outputs.cacheIf { false }
}

