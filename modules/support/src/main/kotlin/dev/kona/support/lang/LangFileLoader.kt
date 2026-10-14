package dev.kona.support.lang

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.stream.Collectors

/**
 * Loads localization maps from a lang directory tree into a [Translator].
 *
 * Supported layouts:
 * - lang/{locale}.json — flat or nested JSON object (nested keys joined with `.`)
 * - lang/{locale}/file.json — keys prefixed with the file stem (`messages.welcome`)
 * - lang/{locale}.properties or lang/{locale}/file.properties — `key=value` lines
 * - plain `key=value` text files under lang/{locale}/
 */
class LangFileLoader(
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) {
    fun load(translator: Translator, langPath: Path): Translator {
        if (!Files.exists(langPath)) {
            return translator
        }
        if (Files.isRegularFile(langPath)) {
            loadFile(translator, localeFromFileName(langPath), langPath, keyPrefix = null)
            return translator
        }
        if (Files.isDirectory(langPath)) {
            loadDirectory(translator, langPath)
        }
        return translator
    }

    private fun loadDirectory(translator: Translator, langPath: Path) {
        Files.list(langPath).use { stream ->
            for (entry in stream.collect(Collectors.toList())) {
                val name = entry.fileName.toString()
                when {
                    Files.isDirectory(entry) -> loadLocaleDirectory(translator, name, entry)
                    isJson(name) ->
                        loadFile(translator, localeFromFileName(entry), entry, keyPrefix = null)
                    isProperties(name) ->
                        loadFile(translator, localeFromFileName(entry), entry, keyPrefix = null)
                }
            }
        }
    }

    private fun loadLocaleDirectory(translator: Translator, locale: String, dir: Path) {
        Files.list(dir).use { stream ->
            for (file in stream.collect(Collectors.toList())) {
                if (!Files.isRegularFile(file)) {
                    continue
                }
                val fileName = file.fileName.toString()
                val stem = fileName.substringBeforeLast('.', fileName)
                val prefix = when {
                    isJson(fileName) -> stem
                    isProperties(fileName) -> stem
                    else -> null
                }
                loadFile(translator, locale, file, keyPrefix = prefix)
            }
        }
    }

    private fun loadFile(
        translator: Translator,
        locale: String,
        file: Path,
        keyPrefix: String?,
    ) {
        val name = file.fileName.toString()
        val lines = if (isJson(name)) readJson(file) else readProperties(file)
        if (lines.isEmpty()) {
            return
        }
        val mapped = if (keyPrefix.isNullOrBlank()) {
            lines
        } else {
            lines.mapKeys { (key, _) -> "$keyPrefix.$key" }
        }
        translator.addLines(locale, mapped)
    }

    private fun readJson(file: Path): Map<String, String> {
        return try {
            val tree = mapper.readTree(Files.readString(file))
            if (!tree.isObject) {
                emptyMap()
            } else {
                flattenJson(tree, prefix = "")
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun flattenJson(node: JsonNode, prefix: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val fields = node.fields()
        while (fields.hasNext()) {
            val entry = fields.next()
            val key = if (prefix.isEmpty()) entry.key else "$prefix.${entry.key}"
            val value = entry.value
            when {
                value.isObject -> out.putAll(flattenJson(value, key))
                value.isNull -> Unit
                value.isValueNode -> out[key] = value.asText()
                else -> out[key] = value.toString()
            }
        }
        return out
    }

    private fun readProperties(file: Path): Map<String, String> {
        return try {
            val out = linkedMapOf<String, String>()
            for (raw in Files.readAllLines(file)) {
                val line = raw.trim()
                when {
                    line.isEmpty() -> continue
                    line.startsWith("#") -> continue
                    line.startsWith("!") -> continue
                }
                val eq = line.indexOf('=')
                if (eq <= 0) {
                    continue
                }
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1).trim()
                out[key] = value
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun localeFromFileName(file: Path): String {
        val name = file.fileName.toString()
        return name.substringBeforeLast('.', name).lowercase(Locale.ROOT)
    }

    private fun isJson(name: String): Boolean = name.endsWith(".json", ignoreCase = true)

    private fun isProperties(name: String): Boolean =
        name.endsWith(".properties", ignoreCase = true)
}

/** Java-friendly entry: `LangFiles.loadInto(translator, path)`. */
object LangFiles {
    @JvmStatic
    fun loadInto(translator: Translator, langPath: Path): Translator =
        LangFileLoader().load(translator, langPath)
}
