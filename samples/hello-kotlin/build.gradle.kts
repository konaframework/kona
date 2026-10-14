plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("hello.MainKt")
}

dependencies {
    implementation(project(":kona-http"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
