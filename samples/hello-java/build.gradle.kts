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
    mainClass.set("hello.Main")
}

dependencies {
    implementation(project(":kona-http"))
}
