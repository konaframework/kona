import com.vanniktech.maven.publish.JavaPlatform
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import java.io.File
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = "org.konaframework"
    version = providers.gradleProperty("kona.version").get()

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }

    if (name.startsWith("kona-")) {
        pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            configureKonaLibraryPublishing()
        }
    }
}

project(":bom") {
    pluginManager.withPlugin("java-platform") {
        configureKonaBomPublishing()
    }
}

fun Project.configureKonaLibraryPublishing() {
    pluginManager.apply("com.vanniktech.maven.publish")
    extensions.configure<MavenPublishBaseExtension> {
        coordinates(group.toString(), name, version.toString())
        configure(
            KotlinJvm(
                javadocJar = JavadocJar.Empty(),
                sourcesJar = true,
            ),
        )
        configureKonaPom(
            displayName = name,
            description = "Kona framework module $name",
        )
        configureCentralAndSigning(project)
    }
}

fun Project.configureKonaBomPublishing() {
    pluginManager.apply("com.vanniktech.maven.publish")
    extensions.configure<MavenPublishBaseExtension> {
        coordinates(group.toString(), "kona-bom", version.toString())
        configure(JavaPlatform())
        configureKonaPom(
            displayName = "Kona BOM",
            description = "Bill of Materials for org.konaframework artifacts",
        )
        configureCentralAndSigning(project)
    }
}

fun MavenPublishBaseExtension.configureKonaPom(displayName: String, description: String) {
    pom {
        name.set(displayName)
        this.description.set(description)
        url.set("https://konaframework.org")
        inceptionYear.set("2025")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("konaframework")
                name.set("Kona Framework contributors")
                url.set("https://konaframework.org")
            }
        }
        scm {
            url.set("https://github.com/konaframework/kona")
            connection.set("scm:git:git://github.com/konaframework/kona.git")
            developerConnection.set("scm:git:ssh://git@github.com/konaframework/kona.git")
        }
    }
}

fun MavenPublishBaseExtension.configureCentralAndSigning(project: Project) {
    // Central Portal (legacy OSSRH shut down). Needs mavenCentralUsername / mavenCentralPassword.
    publishToMavenCentral(automaticRelease = false)
    val hasInMemoryKey =
        project.findProperty("signingInMemoryKey") != null ||
            System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null
    val hasKeyId = project.findProperty("signing.keyId") != null
    if (hasInMemoryKey || hasKeyId) {
        signAllPublications()
    }
    dropRedundantMavenCentralChecksums(project)
}

/**
 * Central counts every uploaded file. Gradle 8.14 writes `sha256`/`sha512` and checksums of
 * `.asc` signatures, which Maven Central does not require (same set vanniktech 0.37 drops).
 * The Portal bundle is zipped from this staging repo at the end of the build, so deleting
 * those files after publish keeps jars, POMs, sources, Javadoc, Gradle module metadata,
 * `.asc` signatures, and `md5`/`sha1`.
 */
fun dropRedundantMavenCentralChecksums(project: Project) {
    project.tasks.withType<PublishToMavenRepository>().configureEach {
        if (!name.endsWith("ToMavenCentralRepository")) return@configureEach
        doLast {
            val repo = repository.url
            if (repo.scheme != "file") return@doLast
            val root = File(repo)
            if (!root.isDirectory) return@doLast
            root.walkTopDown().filter { it.isFile && isRedundantCentralChecksum(it.name) }.forEach { it.delete() }
        }
    }
}

private fun isRedundantCentralChecksum(name: String): Boolean =
    name.endsWith(".sha256") || name.endsWith(".sha512") || name.contains(".asc.")
