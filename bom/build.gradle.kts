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
        api(project(":kona-icons"))
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
        api(project(":kona-inertia"))
        api(project(":kona-firefly-support"))
        api(project(":kona-firefly-schemas"))
        api(project(":kona-firefly-actions"))
        api(project(":kona-firefly-infolists"))
        api(project(":kona-firefly-tables"))
        api(project(":kona-firefly-forms"))
        api(project(":kona-firefly-notifications"))
        api(project(":kona-firefly-widgets"))
        api(project(":kona-firefly"))
        api(project(":kona-lsp"))
        api(project(":kona-wire"))
        api(project(":kona-brew"))

        // Recommended JDBC drivers for first-party Cadence engines (M25).
        api(libs.h2)
        api(libs.sqlite.jdbc)
        api(libs.postgresql)
        api(libs.mysql.connector.j)
        api(libs.mariadb.java.client)
        api(libs.mssql.jdbc)
        api(libs.ojdbc11)
    }
}
