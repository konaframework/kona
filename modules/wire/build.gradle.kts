plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kover)
    `java-library`
}

dependencies {
    api(project(":kona-core"))
    api(project(":kona-http"))
    api(project(":kona-crema"))
    api(project(":kona-session"))
    api(project(":kona-support"))
    api(project(":kona-auth"))
    api(project(":kona-cadence"))
    api(project(":kona-filesystem"))
    api(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${libs.versions.jackson.get()}")
    compileOnly(project(":kona-grind"))

    testImplementation(project(":kona-grind"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        javaParameters.set(true)
    }
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

tasks.register<JavaExec>("bench") {
    description = "Runs the Brew server hot-path baseline."
    group = "verification"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.konaframework.wire.bench.WireBench")
    providers.gradleProperty("benchIterations").orNull?.let { args(it) }
}
