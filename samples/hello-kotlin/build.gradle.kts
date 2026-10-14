plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("hello.MainKt")
}

dependencies {
    implementation(project(":kona-http"))
    implementation(project(":kona-extract"))
    implementation(project(":kona-session"))
    implementation(project(":kona-auth"))
    runtimeOnly(libs.h2)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
