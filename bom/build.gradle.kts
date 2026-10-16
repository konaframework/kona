plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api(project(":kona-core"))
        api(project(":kona-http"))
        api(project(":kona-cadence"))
        api(project(":kona-auth"))
        api(project(":kona-session"))
        api(project(":kona-support"))
        api(project(":kona-redis"))
        api(project(":kona-cache"))
        api(project(":kona-crema"))
        api(project(":kona-events"))
        api(project(":kona-queue"))
        api(project(":kona-mail"))
        api(project(":kona-notifications"))
        api(project(":kona-filesystem"))
        api(project(":kona-schedule"))
        api(project(":kona-grind"))
        api(project(":kona-cli"))
        api(project(":kona-drip"))
        api(project(":kona-process"))
        api(project(":kona-concurrency"))
        api(project(":kona-testing"))
        api(project(":kona-broadcasting"))
        api(project(":kona-search"))
        // kona-inertia lives in https://github.com/konaframework/kona-inertia (Maven: dev.kona:kona-inertia)
        api(project(":kona-lsp"))
        // kona-brew lives in https://github.com/konaframework/kona-brew (Maven: dev.kona:kona-brew)
    }
}
