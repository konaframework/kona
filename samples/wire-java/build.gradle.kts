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
    implementation(project(":kona-wire"))
    implementation(project(":kona-http"))
    implementation(project(":kona-session"))
    implementation(project(":kona-crema"))

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile> {
    // Brew maps positional action and mount params by name.
    options.compilerArgs.add("-parameters")
}

tasks.test {
    useJUnitPlatform()
}
