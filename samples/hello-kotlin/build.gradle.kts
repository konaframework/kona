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
    implementation(project(":kona-support"))
    implementation(project(":kona-redis"))
    implementation(project(":kona-cache"))
    runtimeOnly(libs.h2)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
