plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("BenchAppKt")
}

dependencies {
    implementation(project(":kona-http"))
    implementation(project(":kona-core"))
}
