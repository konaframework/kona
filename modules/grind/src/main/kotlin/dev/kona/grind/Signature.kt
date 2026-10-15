package dev.kona.grind

/**
 * Parsed grind command signature — name, arguments, and options.
 *
 * Examples:
 * - `mail:send {user} {--queue=}`
 * - `app:report {days?} {--force}`
 * - `import:rows {files*}`
 */
data class CommandSignature(
    val name: String,
    val arguments: List<ArgumentDefinition>,
    val options: List<OptionDefinition>,
    val description: String = "",
) {
    fun helpLines(): List<String> {
        val lines = mutableListOf<String>()
        if (description.isNotEmpty()) {
            lines += description
        }
        if (arguments.isNotEmpty()) {
            lines += "Arguments:"
            arguments.forEach { arg ->
                val mark = when {
                    arg.isArray -> "*"
                    arg.optional -> "?"
                    else -> ""
                }
                val desc = if (arg.description.isEmpty()) "" else " — ${arg.description}"
                lines += "  ${arg.name}$mark$desc"
            }
        }
        if (options.isNotEmpty()) {
            lines += "Options:"
            options.forEach { opt ->
                val shape = when {
                    !opt.acceptsValue -> "--${opt.name}"
                    opt.default != null -> "--${opt.name}=${opt.default}"
                    else -> "--${opt.name}="
                }
                val desc = if (opt.description.isEmpty()) "" else " — ${opt.description}"
                lines += "  $shape$desc"
            }
        }
        return lines
    }
}

data class ArgumentDefinition(
    val name: String,
    val optional: Boolean = false,
    val isArray: Boolean = false,
    val description: String = "",
)

data class OptionDefinition(
    val name: String,
    val acceptsValue: Boolean = false,
    val default: String? = null,
    val description: String = "",
)

data class CommandInput(
    val arguments: Map<String, String>,
    val argumentLists: Map<String, List<String>>,
    val options: Map<String, String>,
) {
    fun argument(name: String): String? = arguments[name]

    fun arguments(name: String): List<String> = argumentLists[name].orEmpty()

    fun option(name: String): String? = options[name]

    fun hasOption(name: String): Boolean = options.containsKey(name)
}

object SignatureParser {
    private val TOKEN = Regex("""\{([^{}]+)\}""")
    private val NAME = Regex("^[A-Za-z_][A-Za-z0-9_-]*$")

    fun parse(signature: String, description: String = ""): CommandSignature {
        val trimmed = signature.trim()
        require(trimmed.isNotEmpty()) { "signature must not be empty" }
        val nameEnd = trimmed.indexOf(' ').let { if (it < 0) trimmed.length else it }
        val name = trimmed.substring(0, nameEnd).trim()
        require(!name.startsWith("{")) { "signature must start with a command name" }
        val rest = trimmed.substring(nameEnd)
        val arguments = mutableListOf<ArgumentDefinition>()
        val options = mutableListOf<OptionDefinition>()
        TOKEN.findAll(rest).forEach { match ->
            val body = match.groupValues[1].trim()
            val (token, desc) = splitDescription(body)
            if (token.startsWith("--")) {
                options += parseOption(token.removePrefix("--"), desc)
            } else {
                arguments += parseArgument(token, desc)
            }
        }
        return CommandSignature(name, arguments, options, description)
    }

    fun bind(signature: CommandSignature, positional: List<String>, rawOptions: Map<String, String>): CommandInput {
        val args = linkedMapOf<String, String>()
        val lists = linkedMapOf<String, List<String>>()
        var index = 0
        signature.arguments.forEach { def ->
            when {
                def.isArray -> {
                    lists[def.name] = positional.drop(index)
                    index = positional.size
                }
                index < positional.size -> {
                    args[def.name] = positional[index]
                    index++
                }
                def.optional -> Unit
                else -> throw GrindException("Missing required argument [${def.name}] for ${signature.name}")
            }
        }
        if (index < positional.size) {
            throw GrindException("Too many arguments for ${signature.name}")
        }
        val opts = linkedMapOf<String, String>()
        signature.options.forEach { def ->
            if (rawOptions.containsKey(def.name)) {
                val value = rawOptions.getValue(def.name)
                if (!def.acceptsValue) {
                    opts[def.name] = "true"
                } else {
                    opts[def.name] = value
                }
            } else if (def.acceptsValue) {
                val default = def.default
                if (default != null) {
                    opts[def.name] = default
                }
            }
        }
        listOf("isolated", "help", "verbose", "quiet", "v", "q", "base", "bootstrap").forEach { key ->
            rawOptions[key]?.let { opts.putIfAbsent(key, it) }
        }
        return CommandInput(args, lists, opts)
    }

    private fun splitDescription(body: String): Pair<String, String> {
        val idx = body.indexOf(" : ")
        return if (idx < 0) {
            body.trim() to ""
        } else {
            body.substring(0, idx).trim() to body.substring(idx + 3).trim()
        }
    }

    private fun parseArgument(token: String, description: String): ArgumentDefinition {
        var name = token
        var optional = false
        var isArray = false
        when {
            name.endsWith("*") -> {
                isArray = true
                name = name.removeSuffix("*")
            }
            name.endsWith("?") -> {
                optional = true
                name = name.removeSuffix("?")
            }
        }
        require(name.isNotEmpty() && NAME.matches(name)) { "Invalid argument name [$token]" }
        return ArgumentDefinition(name, optional, isArray, description)
    }

    private fun parseOption(token: String, description: String): OptionDefinition {
        val eq = token.indexOf('=')
        return if (eq < 0) {
            require(NAME.matches(token)) { "Invalid option name [$token]" }
            OptionDefinition(token, acceptsValue = false, description = description)
        } else {
            val name = token.substring(0, eq)
            require(NAME.matches(name)) { "Invalid option name [$name]" }
            val defaultPart = token.substring(eq + 1)
            val default = defaultPart.ifEmpty { null }
            OptionDefinition(name, acceptsValue = true, default = default, description = description)
        }
    }
}
