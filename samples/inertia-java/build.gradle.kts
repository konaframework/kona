plugins {
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("bootstrap.App")
}

dependencies {
    implementation(project(":kona-inertia"))
    implementation(project(":kona-http"))
    implementation(project(":kona-crema"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
