package org.konaframework.grind

import org.konaframework.support.packages.ProviderManifestEditor
import java.nio.file.Files
import java.nio.file.Path

/**
 * Files `./grind install firefly` writes after the Maven dependency:
 * the config module, an app provider that can call `Firefly.register`,
 * and a `bootstrap.App` configs entry when that file is present.
 */
internal object FireflyProjectInstall {
    const val APP_PROVIDER = "app.providers.FireflyServiceProvider"

    fun install(root: Path, lang: String) {
        val java = lang == "java"
        writeConfig(root, java)
        writeProvider(root, java)
        ProviderManifestEditor.ensureProviders(root, listOf(APP_PROVIDER))
        patchBootstrap(root, java)
    }

    private fun writeConfig(root: Path, java: Boolean) {
        val relative = if (java) "src/main/java/config/Firefly.java" else "src/main/kotlin/config/Firefly.kt"
        val file = root.resolve(relative)
        if (Files.exists(file)) return
        Files.createDirectories(file.parent)
        Files.writeString(file, if (java) JAVA_CONFIG else KOTLIN_CONFIG)
    }

    private fun writeProvider(root: Path, java: Boolean) {
        val relative = if (java) {
            "src/main/java/app/providers/FireflyServiceProvider.java"
        } else {
            "src/main/kotlin/app/providers/FireflyServiceProvider.kt"
        }
        val file = root.resolve(relative)
        if (Files.exists(file)) return
        Files.createDirectories(file.parent)
        Files.writeString(file, if (java) JAVA_PROVIDER else KOTLIN_PROVIDER)
    }

    private fun patchBootstrap(root: Path, java: Boolean) {
        val file = root.resolve(
            if (java) "src/main/java/bootstrap/App.java" else "src/main/kotlin/bootstrap/App.kt",
        )
        if (!Files.isRegularFile(file)) return
        val text = Files.readString(file)
        if (java && text.contains("new config.Firefly()")) return
        if (!java && text.contains("Firefly,")) return
        val updated = if (java) patchJava(text) else patchKotlin(text)
        if (updated == text) return
        Files.writeString(file, updated)
    }

    private fun patchKotlin(text: String): String {
        if (!text.contains("Http, Paths,")) return text
        val imported = if (!text.contains("import config.Firefly")) {
            if (text.contains("import config.Hash")) {
                text.replace("import config.Hash", "import config.Firefly\nimport config.Hash")
            } else {
                text
            }
        } else {
            text
        }
        return imported.replace("Http, Paths,", "Http, Paths, Firefly,")
    }

    private fun patchJava(text: String): String {
        if (!text.contains("new Paths()")) return text
        return text.replace(
            "new Paths()",
            "new Paths(),\n                new config.Firefly()",
        )
    }

    private const val KOTLIN_CONFIG = """package config

import org.konaframework.core.ConfigFile

/** Firefly (`config/firefly`). `discovery` lists extra panel directories beside `app/panels`. */
object Firefly : ConfigFile {
    override fun key(): String = "firefly"

    override fun config(): Map<String, Any?> = mapOf(
        "discovery" to emptyList<String>(),
    )
}
"""

    private const val JAVA_CONFIG = """package config;

import org.konaframework.core.ConfigFile;

/** Firefly (`config/firefly`). `discovery` lists extra panel directories beside `app/panels`. */
public class Firefly implements ConfigFile {
    @Override
    public String key() {
        return "firefly";
    }

    @Override
    public Map<String, Object> config() {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("discovery", java.util.List.of());
        return values;
    }
}
"""

    private const val KOTLIN_PROVIDER = """package app.providers

import org.konaframework.core.Application

/**
 * Boots with the framework provider.
 * Panels under `app/panels` are discovered.
 * Call `org.konaframework.firefly.Firefly.register` here for a panel you build by hand.
 */
class FireflyServiceProvider : org.konaframework.firefly.FireflyServiceProvider() {
    override fun register(app: Application) {
        super.register(app)
        // Firefly.register(org.konaframework.firefly.Panel().id("billing"))
    }
}
"""

    private const val JAVA_PROVIDER = """package app.providers;

import org.konaframework.core.Application;

/**
 * Boots with the framework provider.
 * Panels under `app/panels` are discovered.
 * Call `org.konaframework.firefly.Firefly.register` here for a panel you build by hand.
 */
public class FireflyServiceProvider extends org.konaframework.firefly.FireflyServiceProvider {
    @Override
    public void register(Application app) {
        super.register(app);
        // Firefly.register(new org.konaframework.firefly.Panel().id("billing"));
    }
}
"""
}
