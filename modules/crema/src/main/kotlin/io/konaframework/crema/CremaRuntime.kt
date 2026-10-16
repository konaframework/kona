package io.konaframework.crema

import com.fasterxml.jackson.databind.ObjectMapper
import io.konaframework.support.lang.Lang

/**
 * Crema template runtime — directives and echoes for `*.crema.html`.
 * Kept in one file so the behavioral suite can drive coverage of the interpreter.
 */
internal class CremaRuntime(
    private val finder: ViewFinder,
    private val extensions: CremaExtensions = CremaExtensions(),
) : CremaEvaluator {
    private val jsonMapper = ObjectMapper()

    override fun evaluate(expression: String, context: CremaContext): Any? = eval(expression, context)

    override fun expandSource(source: String, context: CremaContext): String = expand(source, context)

    fun render(name: String, data: Map<String, Any?>): String =
        renderSource(finder.get(name), CremaContext().merge(data))

    fun render(name: String, context: CremaContext): String =
        renderSource(finder.get(name), context)

    fun renderSource(source: String, context: CremaContext): String {
        val cleaned = COMMENT.replace(source, "")
        val parent = EXTENDS.find(cleaned)
        val body = stripAndCollect(cleaned, context)
        return if (parent != null) {
            render(parent.groupValues[1], context)
        } else {
            expand(body, context)
        }
    }

    private fun stripAndCollect(source: String, context: CremaContext): String {
        var rest = EXTENDS.replace(source, "")
        rest = INLINE_SECTION.replace(rest) { m ->
            putSection(context, m.groupValues[1], m.groupValues[2])
            ""
        }
        while (true) {
            val m = OPEN_SECTION.find(rest) ?: break
            val name = m.groupValues[1]
            val start = m.range.last + 1
            val endSection = rest.indexOf("@endsection", start)
            val endShow = rest.indexOf("@show", start)
            val endIdx = when {
                endSection >= 0 && (endShow < 0 || endSection < endShow) -> endSection
                endShow >= 0 -> endShow
                else -> -1
            }
            require(endIdx >= 0) { "Missing @endsection for $name" }
            val closer = if (rest.startsWith("@show", endIdx)) "@show" else "@endsection"
            val raw = rest.substring(start, endIdx)
            val content = if (closer == "@show") expand(raw, context) else raw
            putSection(context, name, content)
            rest = rest.removeRange(m.range.first, endIdx + closer.length)
        }
        return rest
    }

    private fun putSection(context: CremaContext, name: String, content: String) {
        val section = context.section(name)
        if (section.isEmpty()) {
            section.append(content)
        } else {
            section.setParent(content)
        }
    }

    private fun expand(source: String, context: CremaContext): String {
        val (withoutVerbatim, verbatimSlots) = extractVerbatim(source)
        val stash = mutableListOf<String>()
        val text = expandBlocks(withoutVerbatim, context, stash)
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("{!!", i) -> {
                    val end = text.indexOf("!!}", i + 3)
                    require(end >= 0) { "Unclosed raw echo" }
                    out.append(value(text.substring(i + 3, end), context))
                    i = end + 3
                }
                text.startsWith("{{", i) -> {
                    val end = text.indexOf("}}", i + 2)
                    require(end >= 0) { "Unclosed echo" }
                    out.append(escape(value(text.substring(i + 2, end), context)))
                    i = end + 2
                }
                text.startsWith("@@", i) -> {
                    out.append('@')
                    i += 2
                }
                else -> {
                    out.append(text[i])
                    i++
                }
            }
        }
        return restoreVerbatim(restoreStash(out.toString(), stash), verbatimSlots)
    }

    /** Rendered fragments are parked behind placeholders so later passes never re-interpret their output. */
    private fun park(stash: MutableList<String>, rendered: String): String {
        stash += rendered
        return "$STASH_PREFIX${stash.lastIndex}$STASH_SUFFIX"
    }

    private fun restoreStash(source: String, stash: List<String>): String {
        if (stash.isEmpty()) return source
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val start = source.indexOf(STASH_PREFIX, i)
            if (start < 0) {
                out.append(source, i, source.length)
                break
            }
            out.append(source, i, start)
            val end = source.indexOf(STASH_SUFFIX, start)
            val idx = source.substring(start + STASH_PREFIX.length, end).toInt()
            out.append(stash[idx])
            i = end + STASH_SUFFIX.length
        }
        return out.toString()
    }

    private fun expandBlocks(source: String, context: CremaContext, stash: MutableList<String>): String {
        var text = expandStructural(source, context, stash)
        text = replaceDirective(text, "@csrf") {
            val token = context.get("_token")?.toString()
                ?: context.get("csrf_token")?.toString()
                ?: ""
            "<input type=\"hidden\" name=\"_token\" value=\"${escape(token)}\">"
        }
        text = replaceMethod(text)
        text = replaceAsset(text, context)
        text = replaceJson(text, context)
        text = replaceLang(text, context)
        text = replaceVite(text)
        text = replacePush(text, context)
        text = replaceYield(text, context)
        text = replaceInclude(text, context)
        text = replaceOnce(text, context)
        text = replaceComponent(text, context)
        text = replaceXComponents(text, context)
        text = replaceTags(text, context, stash)
        text = replaceInlineDirectives(text, context, stash)
        text = replaceStack(text, context)
        return text
    }

    // ---- Structural blocks (outermost first, so inner blocks see loop variables) ----

    private fun expandStructural(source: String, context: CremaContext, stash: MutableList<String>): String {
        if (source.indexOf('@') < 0) return source
        val out = StringBuilder(source.length)
        var rest = source
        while (true) {
            val found = nextStructural(rest) ?: break
            val (start, word, end) = found
            out.append(rest, 0, start)
            val segment = rest.substring(start, end)
            val rendered = evalStructural(word, segment, context)
            val wrap = word !in extensions.blockNames() &&
                truthy(context.get(CremaContext.MORPH_MARKERS)) &&
                !insideTag(out)
            out.append(
                park(
                    stash,
                    if (wrap) CremaContext.BLOCK_START + rendered + CremaContext.BLOCK_END else rendered,
                ),
            )
            rest = rest.substring(end)
        }
        out.append(rest)
        return out.toString()
    }

    private fun insideTag(before: CharSequence): Boolean {
        val lt = before.lastIndexOf('<')
        return lt >= 0 && before.lastIndexOf('>') < lt
    }

    private fun CharSequence.lastIndexOf(ch: Char): Int {
        for (i in length - 1 downTo 0) if (this[i] == ch) return i
        return -1
    }

    /** Earliest structural directive: (start, word, endExclusive). */
    private fun nextStructural(text: String): Triple<Int, String, Int>? {
        var i = 0
        while (true) {
            val at = text.indexOf('@', i)
            if (at < 0 || at + 1 >= text.length) return null
            if (text[at + 1] == '@') {
                i = at + 2
                continue
            }
            var j = at + 1
            while (j < text.length && text[j].isLetterOrDigit()) j++
            val word = text.substring(at + 1, j)
            if (word in STRUCTURAL || word in extensions.blockNames()) {
                val end = blockEnd(text, at, word)
                if (end != null) return Triple(at, word, end)
            }
            i = j.coerceAtLeast(at + 1)
        }
    }

    private fun closerFor(word: String): String =
        if (word == "if" || word == "hasSection" || word == "sectionMissing") "@endif" else "@end$word"

    /** Index after the matching closer, or null when this `@word` is not a directive use (e.g. no parens). */
    private fun blockEnd(text: String, start: Int, word: String): Int? {
        val open = "@$word"
        val close = closerFor(word)
        var j = start + open.length
        while (j < text.length && (text[j] == ' ' || text[j] == '\t')) j++
        val hasParen = j < text.length && text[j] == '('
        val bodyStart = when {
            hasParen -> (readParens(text, j) ?: error("Unclosed ( in $open")).second + 1
            word in NO_ARG_BLOCKS || word in extensions.blockNames() -> start + open.length
            else -> return null
        }
        val end = if (close == "@endif") {
            findMatchingEndif(text, bodyStart)
        } else {
            findMatchingClose(text, bodyStart, open, close, requireParen = word == "empty")
        }
        require(end >= 0) { "Missing $close" }
        return end + close.length
    }

    private fun evalStructural(word: String, segment: String, context: CremaContext): String {
        val open = "@$word"
        val close = closerFor(word)
        var bodyStart = open.length
        while (bodyStart < segment.length && (segment[bodyStart] == ' ' || segment[bodyStart] == '\t')) bodyStart++
        var arg = ""
        if (bodyStart < segment.length && segment[bodyStart] == '(') {
            val (a, closeParen) = readParens(segment, bodyStart)!!
            arg = a
            bodyStart = closeParen + 1
        } else {
            bodyStart = open.length
        }
        val body = segment.substring(bodyStart, segment.length - close.length)
        val custom = extensions.blockFor(word)
        if (custom != null) {
            return custom.render(CremaDirectiveCall(word, arg.trim(), body, context, this))
        }
        return when (word) {
            "if" -> evalIfBlock(arg, body, context)
            "unless" -> if (!truthy(eval(arg, context))) expand(body, context) else ""
            "isset" -> if (resolve(arg.trim().removePrefix("$"), context) != null) expand(body, context) else ""
            "empty" -> if (!truthy(eval(arg, context))) expand(body, context) else ""
            "auth" -> if (isAuthenticated(context)) expand(body, context) else ""
            "guest" -> if (!isAuthenticated(context)) expand(body, context) else ""
            "can" -> if (can(unquote(arg), context)) expand(body, context) else ""
            "cannot" -> if (!can(unquote(arg), context)) expand(body, context) else ""
            "error" -> if (hasError(unquote(arg), context)) expand(body, context) else ""
            "hasSection" -> if (context.hasSection(unquote(arg))) expand(body, context) else ""
            "sectionMissing" -> if (!context.hasSection(unquote(arg))) expand(body, context) else ""
            "switch" -> evalSwitchBody(body, eval(arg, context), context)
            "foreach", "each" -> {
                val kv = LOOP_KEY_VALUE_HEADER.matchEntire(arg.trim())
                if (kv != null) {
                    return renderKeyedLoop(eval(kv.groupValues[1], context), kv.groupValues[2], kv.groupValues[3], body, context)
                }
                val m = LOOP_AS_HEADER.matchEntire(arg.trim()) ?: error("Invalid $open header: $arg")
                renderLoop(eval(m.groupValues[1], context), m.groupValues[2], body, context)
            }
            "for" -> renderForHeader(arg.trim(), body, context)
            "forelse" -> evalForelse(arg, body, context)
            else -> evalWhile(arg, body, context)
        }
    }

    private fun unquote(arg: String): String = arg.trim().removeSurrounding("'").removeSurrounding("\"")

    private fun hasError(field: String, context: CremaContext): Boolean {
        val errors = context.get("errors") as? Map<*, *> ?: return false
        return when (val v = errors[field]) {
            null -> false
            is Collection<*> -> v.isNotEmpty()
            is CharSequence -> v.isNotEmpty()
            else -> true
        }
    }

    /** `@foreach($items as $key => $value)`: map entries, or index → item for lists. */
    private fun renderKeyedLoop(value: Any?, keyName: String, itemName: String, body: String, context: CremaContext): String {
        val pairs: List<Pair<Any?, Any?>> = if (value is Map<*, *>) {
            value.entries.map { it.key to it.value }
        } else {
            iterate(value).mapIndexed { idx, item -> idx to item }
        }
        val parent = parentLoop(context)
        val depth = ((parent?.get("depth") as? Number)?.toInt() ?: 0) + 1
        return buildString {
            pairs.forEachIndexed { idx, (key, item) ->
                val child = context.child(
                    mapOf(keyName to key, itemName to item, "loop" to loopMap(idx, pairs.size, depth, parent)),
                )
                append(expand(body, child))
            }
        }
    }

    private fun evalWhile(cond: String, body: String, context: CremaContext): String =
        buildString {
            var iteration = 0
            while (iteration < MAX_WHILE) {
                val loop = loopMap(iteration, MAX_WHILE, depth = 1, parent = parentLoop(context))
                val child = context.child(mapOf("loop" to loop))
                if (!truthy(eval(cond, child))) break
                append(expand(body, child))
                iteration++
            }
        }

    private fun evalForelse(header: String, block: String, context: CremaContext): String {
        val m = LOOP_AS_HEADER.matchEntire(header.trim()) ?: error("Invalid @forelse header: $header")
        val emptyAt = findForelseEmpty(block)
        require(emptyAt >= 0) { "Missing @empty in @forelse" }
        val body = block.substring(0, emptyAt)
        val empty = block.substring(emptyAt + "@empty".length)
        val items = iterate(eval(m.groupValues[1], context))
        return if (items.isEmpty()) expand(empty, context) else renderLoopItems(items, m.groupValues[2], body, context)
    }

    // ---- Extension tags and inline directives ----

    private fun replaceTags(source: String, context: CremaContext, stash: MutableList<String>): String {
        var text = source
        for (prefix in extensions.tagPrefixes()) {
            text = replaceTagPrefix(text, prefix, context, stash)
        }
        return text
    }

    private fun replaceTagPrefix(source: String, prefix: String, context: CremaContext, stash: MutableList<String>): String {
        val opener = "<$prefix:"
        if (!source.contains(opener)) return source
        val handler = extensions.tagFor(prefix)!!
        val out = StringBuilder(source.length)
        var i = 0
        while (true) {
            val start = source.indexOf(opener, i)
            if (start < 0) {
                out.append(source, i, source.length)
                break
            }
            out.append(source, i, start)
            val parsed = parseTagOpen(source, start + opener.length)
            if (parsed == null || parsed.name == "slot") {
                out.append(opener)
                i = start + opener.length
                continue
            }
            var body = ""
            var next = parsed.end
            if (!parsed.selfClosing) {
                val closeAt = findTagClose(source, parsed.end, prefix, parsed.name)
                require(closeAt >= 0) { "Missing </$prefix:${parsed.name}>" }
                body = source.substring(parsed.end, closeAt)
                next = closeAt + "</$prefix:${parsed.name}>".length
            }
            val (slot, slots) = splitSlots(body, prefix, context)
            val tag = CremaTag(
                prefix,
                parsed.name,
                tagAttributes(parsed.attributes, context),
                slot,
                slots,
                parsed.selfClosing,
                context,
                this,
            )
            out.append(park(stash, handler.render(tag)))
            i = next
        }
        return out.toString()
    }

    private class ParsedTag(val name: String, val attributes: String, val selfClosing: Boolean, val end: Int)

    private fun parseTagOpen(text: String, nameStart: Int): ParsedTag? {
        var j = nameStart
        while (j < text.length && (text[j].isLetterOrDigit() || text[j] in "-_.")) j++
        if (j == nameStart) return null
        val name = text.substring(nameStart, j)
        var quote: Char? = null
        var k = j
        while (k < text.length) {
            val ch = text[k]
            val q = quote
            if (q != null) {
                if (ch == q) quote = null
            } else if (ch == '"' || ch == '\'') {
                quote = ch
            } else if (ch == '>') {
                val selfClosing = text[k - 1] == '/'
                val attrEnd = if (selfClosing) k - 1 else k
                return ParsedTag(name, text.substring(j, attrEnd), selfClosing, k + 1)
            }
            k++
        }
        return null
    }

    private fun findTagClose(text: String, from: Int, prefix: String, name: String): Int {
        val open = "<$prefix:$name"
        val close = "</$prefix:$name>"
        var depth = 0
        var i = from
        while (true) {
            val nextOpen = indexOfTagOpen(text, open, i)
            val nextClose = text.indexOf(close, i)
            if (nextClose < 0) return -1
            if (nextOpen in 0 until nextClose) {
                val parsed = parseTagOpen(text, nextOpen + prefix.length + 2)!!
                if (!parsed.selfClosing) depth++
                i = parsed.end
                continue
            }
            if (depth == 0) return nextClose
            depth--
            i = nextClose + close.length
        }
    }

    private fun indexOfTagOpen(text: String, open: String, from: Int): Int {
        var i = from
        while (true) {
            val idx = text.indexOf(open, i)
            if (idx < 0) return -1
            val after = idx + open.length
            if (after < text.length && (text[after].isWhitespace() || text[after] == '>' || text[after] == '/')) {
                return idx
            }
            i = after
        }
    }

    private fun splitSlots(body: String, prefix: String, context: CremaContext): Pair<String, Map<String, String>> {
        if (body.isEmpty()) return "" to emptyMap()
        val slots = linkedMapOf<String, String>()
        var rest = body
        for (pattern in listOf(slotPattern(prefix), X_SLOT_NAMED, X_SLOT_COLON)) {
            rest = pattern.replace(rest) { m ->
                slots[m.groupValues[1]] = expand(m.groupValues[2], context)
                ""
            }
        }
        return expand(rest, context).trim() to slots
    }

    private fun slotPattern(prefix: String): Regex =
        Regex("""<${Regex.escape(prefix)}:slot\s+name=["']([^"']+)["']\s*>([\s\S]*?)</${Regex.escape(prefix)}:slot>""")

    private fun tagAttributes(raw: String, context: CremaContext): Map<String, Any?> {
        val attrs = linkedMapOf<String, Any?>()
        for (m in TAG_ATTRIBUTE.findAll(raw)) {
            val key = m.groupValues[1]
            val value = when {
                m.groups[2] != null -> m.groupValues[2]
                m.groups[3] != null -> m.groupValues[3]
                else -> null
            }
            when {
                key.startsWith("::") -> attrs[key.substring(1)] = value ?: ""
                key.startsWith(":$") -> attrs[key.substring(2)] = eval(key.substring(1), context)
                key.startsWith(":") -> attrs[key.substring(1)] = if (value == null) true else eval(value, context)
                value == null -> attrs[key] = true
                else -> attrs[key] = expand(value, context)
            }
        }
        return attrs
    }

    private fun replaceInlineDirectives(source: String, context: CremaContext, stash: MutableList<String>): String {
        val names = extensions.directiveNames()
        if (names.isEmpty() || source.indexOf('@') < 0) return source
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            val at = source.indexOf('@', i)
            if (at < 0) {
                out.append(source, i, source.length)
                break
            }
            out.append(source, i, at)
            if (at + 1 < source.length && source[at + 1] == '@') {
                out.append("@@")
                i = at + 2
                continue
            }
            var j = at + 1
            while (j < source.length && source[j].isLetterOrDigit()) j++
            val word = source.substring(at + 1, j)
            val handler = if (word in names) extensions.directiveFor(word) else null
            if (handler == null) {
                out.append('@')
                i = at + 1
                continue
            }
            var expression = ""
            var next = j
            var k = j
            while (k < source.length && (source[k] == ' ' || source[k] == '\t')) k++
            if (k < source.length && source[k] == '(') {
                val (expr, closeParen) = readParens(source, k) ?: error("Unclosed ( in @$word")
                expression = expr.trim()
                next = closeParen + 1
            }
            out.append(park(stash, handler.render(CremaDirectiveCall(word, expression, "", context, this))))
            i = next
        }
        return out.toString()
    }

    private fun extractVerbatim(source: String): Pair<String, List<String>> {
        val slots = mutableListOf<String>()
        val out = StringBuilder()
        var i = 0
        while (i < source.length) {
            val start = source.indexOf("@verbatim", i)
            if (start < 0) {
                out.append(source.substring(i))
                break
            }
            out.append(source.substring(i, start))
            val bodyStart = start + "@verbatim".length
            val end = source.indexOf("@endverbatim", bodyStart)
            require(end >= 0) { "Missing @endverbatim" }
            slots += source.substring(bodyStart, end)
            out.append(VERBATIM_PREFIX).append(slots.lastIndex).append(VERBATIM_SUFFIX)
            i = end + "@endverbatim".length
        }
        return out.toString() to slots
    }

    private fun restoreVerbatim(source: String, slots: List<String>): String {
        var text = source
        slots.forEachIndexed { idx, body ->
            text = text.replace("$VERBATIM_PREFIX$idx$VERBATIM_SUFFIX", body)
        }
        return text
    }

    private fun replaceMethod(source: String): String =
        METHOD.replace(source) { m ->
            val method = m.groupValues[1]
            "<input type=\"hidden\" name=\"_method\" value=\"${escape(method)}\">"
        }

    /** `@asset('css/app.css')` → URL under `asset_url` / `/` prefix (Prism/Blade shape). */
    private fun replaceAsset(source: String, context: CremaContext): String =
        ASSET.replace(source) { m ->
            val path = m.groupValues[1].trimStart('/')
            val base = (context.get("asset_url") ?: context.get("assetUrl") ?: "").toString().trimEnd('/')
            val url = if (base.isEmpty()) "/$path" else "$base/$path"
            escape(url)
        }

    private fun replaceJson(source: String, context: CremaContext): String =
        JSON.replace(source) { m ->
            val value = eval(m.groupValues[1], context)
            jsonMapper.writeValueAsString(value)
        }

    private fun replaceLang(source: String, context: CremaContext): String =
        LANG.replace(source) { m ->
            val key = m.groupValues[1]
            val replaceExpr = m.groupValues[2].trim()
            @Suppress("UNCHECKED_CAST")
            val replace: Map<String, Any?> =
                if (replaceExpr.isEmpty()) {
                    emptyMap()
                } else {
                    when (val v = eval(replaceExpr, context)) {
                        is Map<*, *> -> v as Map<String, Any?>
                        else -> emptyMap()
                    }
                }
            translate(key, replace, context)
        }

    private fun translate(key: String, replace: Map<String, Any?>, context: CremaContext): String {
        val fromCtx = (context.get("lang") as? Map<*, *>)?.get(key)?.toString()
        if (fromCtx != null) {
            return applyReplace(fromCtx, replace)
        }
        return Lang.get(key, replace)
    }

    private fun applyReplace(template: String, replace: Map<String, Any?>): String {
        var out = template
        replace.forEach { (k, v) ->
            out = out.replace(":$k", v?.toString() ?: "")
        }
        return out
    }

    private fun replaceVite(source: String): String {
        var text = VITE_LIST.replace(source) { m ->
            val entries = QUOTED_NAME.findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
            ViteTags.tags(entries)
        }
        text = VITE_SINGLE.replace(text) { m -> ViteTags.tag(m.groupValues[1]) }
        return text
    }

    private fun replaceYield(source: String, context: CremaContext): String =
        YIELD.replace(source) { m ->
            val name = m.groupValues[1]
            val default = m.groupValues[2].ifEmpty { null }
            val section = context.section(name)
            when {
                !section.isEmpty() -> expand(section.render(), context)
                default != null -> default
                else -> ""
            }
        }

    private fun replaceStack(source: String, context: CremaContext): String =
        STACK.replace(source) { m -> context.stack(m.groupValues[1]) }

    private fun replaceInclude(source: String, context: CremaContext): String {
        var text = INCLUDE_IF.replace(source) { m ->
            runCatching { render(m.groupValues[1], context.child()) }.getOrDefault("")
        }
        text = INCLUDE_WHEN.replace(text) { m ->
            if (truthy(eval(m.groupValues[1], context))) {
                render(m.groupValues[2], context.child())
            } else {
                ""
            }
        }
        text = INCLUDE_UNLESS.replace(text) { m ->
            if (!truthy(eval(m.groupValues[1], context))) {
                render(m.groupValues[2], context.child())
            } else {
                ""
            }
        }
        text = INCLUDE_FIRST.replace(text) { m ->
            val names = QUOTED_NAME.findAll(m.groupValues[1]).map { it.groupValues[1] }.toList()
            names.firstNotNullOfOrNull { name ->
                runCatching { render(name, context.child()) }.getOrNull()
            } ?: ""
        }
        return INCLUDE.replace(text) { m ->
            render(m.groupValues[1], context.child())
        }
    }

    private fun replaceOnce(source: String, context: CremaContext): String {
        var text = source
        while (true) {
            val start = text.indexOf("@once")
            if (start < 0) break
            val bodyStart = start + "@once".length
            val end = text.indexOf("@endonce", bodyStart)
            require(end >= 0) { "Missing @endonce" }
            val body = text.substring(bodyStart, end)
            val key = "once:$body"
            val replacement = if (context.markOnce(key)) expand(body, context) else ""
            text = text.replaceRange(start, end + "@endonce".length, replacement)
        }
        return text
    }


    private fun isAuthenticated(context: CremaContext): Boolean {
        val flag = context.get("__authenticated")
        if (flag != null) return truthy(flag)
        if (context.get("auth_user") != null) return true
        if (context.get("user") != null) return true
        return false
    }


    private fun can(ability: String, context: CremaContext): Boolean {
        val abilities = context.get("__abilities")
        when (abilities) {
            is Set<*> -> return ability in abilities
            is Collection<*> -> return ability in abilities
            is Map<*, *> -> {
                val v = abilities[ability]
                if (v != null) return truthy(v)
            }
        }
        val canMap = context.get("can")
        if (canMap is Map<*, *>) {
            val v = canMap[ability]
            if (v != null) return truthy(v)
        }
        return false
    }






    private fun evalSwitchBody(body: String, switchValue: Any?, context: CremaContext): String {
        var i = 0
        var active = false
        var anyMatched = false
        val out = StringBuilder()
        while (i < body.length) {
            when {
                body.startsWith("@case", i) -> {
                    val openParen = body.indexOf('(', i + "@case".length)
                    require(openParen >= 0) { "Missing ( after @case" }
                    val (arg, closeParen) = readParens(body, openParen) ?: error("Unclosed ( in @case")
                    i = closeParen + 1
                    active = "$switchValue" == "${eval(arg, context)}"
                    if (active) anyMatched = true
                }
                body.startsWith("@default", i) -> {
                    i += "@default".length
                    active = !anyMatched
                }
                body.startsWith("@break", i) -> {
                    if (active) return out.toString()
                    i += "@break".length
                }
                else -> {
                    val next = nextSwitchDirective(body, i)
                    if (active) out.append(expand(body.substring(i, next), context))
                    i = next
                }
            }
        }
        return out.toString()
    }

    private fun nextSwitchDirective(body: String, from: Int): Int {
        val markers = listOf("@case", "@default", "@break")
        var best = body.length
        for (m in markers) {
            val idx = body.indexOf(m, from)
            if (idx > from && idx < best) best = idx
        }
        return best
    }


    private fun evalIfBlock(firstCond: String, block: String, context: CremaContext): String {
        data class Branch(val cond: String?, val body: String)

        val branches = mutableListOf<Branch>()
        var cond: String? = firstCond
        var i = 0
        var depth = 0
        var bodyStart = 0
        while (i < block.length) {
            when {
                block.startsWith("@if", i) && isDirectiveAt(block, i, "@if") -> {
                    depth++
                    i += 3
                }
                block.startsWith("@endif", i) && depth > 0 -> {
                    depth--
                    i += 6
                }
                depth == 0 && block.startsWith("@elseif", i) -> {
                    branches += Branch(cond, block.substring(bodyStart, i))
                    val openParen = block.indexOf('(', i + "@elseif".length)
                    require(openParen >= 0) { "Missing ( after @elseif" }
                    val (c, closeParen) = readParens(block, openParen) ?: error("Unclosed ( in @elseif")
                    cond = c
                    i = closeParen + 1
                    bodyStart = i
                }
                depth == 0 && block.startsWith("@else", i) &&
                    !block.startsWith("@elseif", i) -> {
                    branches += Branch(cond, block.substring(bodyStart, i))
                    cond = null
                    i += "@else".length
                    bodyStart = i
                }
                else -> i++
            }
        }
        branches += Branch(cond, block.substring(bodyStart))
        for (branch in branches) {
            val c = branch.cond
            if (c == null || truthy(eval(c, context))) {
                return expand(branch.body, context)
            }
        }
        return ""
    }

    private fun isDirectiveAt(text: String, i: Int, tag: String): Boolean {
        if (!text.startsWith(tag, i)) return false
        val after = i + tag.length
        if (after < text.length && text[after].isLetterOrDigit()) return false
        return true
    }

    private fun findMatchingEndif(text: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < text.length) {
            when {
                isDirectiveAt(text, i, "@if") ||
                    isDirectiveAt(text, i, "@hasSection") ||
                    isDirectiveAt(text, i, "@sectionMissing") -> {
                    depth++
                    i++
                }
                isDirectiveAt(text, i, "@endif") -> {
                    if (depth == 0) return i
                    depth--
                    i += 6
                }
                else -> i++
            }
        }
        return -1
    }




    private fun renderForHeader(header: String, body: String, context: CremaContext): String {
        FOR_IN_HEADER.matchEntire(header)?.let { m ->
            return renderLoopItems(resolveRange(m.groupValues[2].trim(), context), m.groupValues[1], body, context)
        }
        FOR_C_HEADER.matchEntire(header)?.let { m ->
            val start = eval(m.groupValues[2], context).toIntIndex()
            val endExclusive = eval(m.groupValues[3], context).toIntIndex()
            return renderLoopItems((start until endExclusive).toList(), m.groupValues[1], body, context)
        }
        LOOP_AS_HEADER.matchEntire(header)?.let { m ->
            return renderLoop(eval(m.groupValues[1], context), m.groupValues[2], body, context)
        }
        error("Invalid @for header: $header")
    }



    private fun findMatchingClose(
        text: String,
        from: Int,
        open: String,
        close: String,
        requireParen: Boolean = false,
    ): Int {
        var depth = 0
        var i = from
        while (i < text.length) {
            when {
                isDirectiveAt(text, i, open) && (!requireParen || text.startsWith("(", i + open.length)) -> {
                    depth++
                    i += open.length
                }
                isDirectiveAt(text, i, close) -> {
                    if (depth == 0) return i
                    depth--
                    i += close.length
                }
                else -> i++
            }
        }
        return -1
    }

    private fun resolveRange(expr: String, context: CremaContext): List<Any?> {
        val parts = expr.split("..").map { it.trim() }
        require(parts.size == 2) { "Invalid range: $expr" }
        val start = eval(parts[0], context).toIntIndex()
        val end = eval(parts[1], context).toIntIndex()
        return (start..end).toList()
    }

    private fun Any?.toIntIndex(): Int =
        when (this) {
            is Number -> this.toInt()
            is String -> this.toInt()
            else -> error("Expected number, got $this")
        }


    private fun findForelseEmpty(block: String): Int {
        var depth = 0
        var i = 0
        while (i < block.length) {
            when {
                isDirectiveAt(block, i, "@forelse") -> {
                    depth++
                    i += 8
                }
                isDirectiveAt(block, i, "@endforelse") -> {
                    depth--
                    i += 11
                }
                depth == 0 && isDirectiveAt(block, i, "@empty") &&
                    (i + 6 >= block.length || block[i + 6] != '(') -> return i
                else -> i++
            }
        }
        return -1
    }

    private fun renderLoop(value: Any?, itemName: String, body: String, context: CremaContext): String =
        renderLoopItems(iterate(value), itemName, body, context)

    private fun renderLoopItems(
        items: List<Any?>,
        itemName: String,
        body: String,
        context: CremaContext,
    ): String {
        val parent = parentLoop(context)
        val depth = ((parent?.get("depth") as? Number)?.toInt() ?: 0) + 1
        return buildString {
            items.forEachIndexed { idx, item ->
                val child = context.child(
                    mapOf(
                        itemName to item,
                        "loop" to loopMap(idx, items.size, depth, parent),
                    ),
                )
                append(expand(body, child))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parentLoop(context: CremaContext): Map<String, Any?>? =
        context.get("loop") as? Map<String, Any?>

    private fun loopMap(
        idx: Int,
        size: Int,
        depth: Int,
        parent: Map<String, Any?>?,
    ): Map<String, Any?> =
        linkedMapOf(
            "index" to idx,
            "iteration" to idx + 1,
            "first" to (idx == 0),
            "last" to (idx == size - 1 || size == 0),
            "count" to size,
            "remaining" to (size - idx - 1).coerceAtLeast(0),
            "even" to (idx % 2 == 0),
            "odd" to (idx % 2 == 1),
            "depth" to depth,
            "parent" to parent,
        )

    private fun replacePush(source: String, context: CremaContext): String {
        var text = PUSH.replace(source) { m ->
            context.push(m.groupValues[1], expand(m.groupValues[2], context))
            ""
        }
        text = PREPEND.replace(text) { m ->
            context.prepend(m.groupValues[1], expand(m.groupValues[2], context))
            ""
        }
        return text
    }

    private fun replaceComponent(source: String, context: CremaContext): String =
        COMPONENT.replace(source) { m ->
            val name = m.groupValues[1]
            val body = m.groupValues[2]
            var slotRest = body
            val slots = linkedMapOf<String, String>()
            while (true) {
                val slot = SLOT.find(slotRest) ?: break
                slots[slot.groupValues[1]] = expand(slot.groupValues[2], context)
                slotRest = slotRest.removeRange(slot.range)
            }
            val child = context.child(mapOf("slot" to expand(slotRest, context)) + slots)
            render(name, child)
        }

    private fun replaceXComponents(source: String, context: CremaContext): String {
        var text = X_COMPONENT.replace(source) { m ->
            val name = m.groupValues[1].replace('/', '.')
            val body = m.groupValues[2]
            val view = if (name.startsWith("components.")) name else "components.$name"
            val (slot, slots) = splitSlots(body, "x", context)
            runCatching {
                render(view, context.child(mapOf("slot" to slot) + slots))
            }.getOrDefault("")
        }
        text = X_SELF_CLOSING.replace(text) { m ->
            val name = m.groupValues[1].replace('/', '.')
            val view = if (name.startsWith("components.")) name else "components.$name"
            runCatching { render(view, context.child(mapOf("slot" to ""))) }.getOrDefault("")
        }
        return text
    }



    private fun readParens(text: String, openParen: Int): Pair<String, Int>? {
        if (openParen >= text.length || text[openParen] != '(') return null
        var depth = 0
        var i = openParen
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(openParen + 1, i) to i
                    }
                }
            }
            i++
        }
        return null
    }

    private fun replaceDirective(source: String, tag: String, block: () -> String): String =
        if (source.contains(tag)) source.replace(tag, block()) else source

    private fun value(expr: String, context: CremaContext): String =
        eval(expr, context)?.toString() ?: ""

    private fun eval(raw: String, context: CremaContext): Any? {
        val expr = raw.trim().removePrefix("$")
        if (expr.isEmpty()) return null
        when {
            expr == "null" || expr == "nil" -> return null
            expr == "true" -> return true
            expr == "false" -> return false
            expr.toIntOrNull() != null -> return expr.toInt()
            expr.toDoubleOrNull() != null -> return expr.toDouble()
            expr.length >= 2 && expr.first() == '\'' && expr.last() == '\'' ->
                return expr.substring(1, expr.lastIndex)
            expr.length >= 2 && expr.first() == '"' && expr.last() == '"' ->
                return expr.substring(1, expr.lastIndex)
        }
        if (" and " in expr) return expr.split(" and ").all { truthy(eval(it, context)) }
        if (" or " in expr) return expr.split(" or ").any { truthy(eval(it, context)) }
        if (expr.startsWith("not ")) return !truthy(eval(expr.removePrefix("not "), context))
        if (expr.startsWith("!")) return !truthy(eval(expr.removePrefix("!"), context))
        if (expr.startsWith("empty(") && expr.endsWith(")")) {
            return !truthy(eval(expr.removePrefix("empty(").removeSuffix(")"), context))
        }
        if (expr.startsWith("isset(") && expr.endsWith(")")) {
            return resolve(expr.removePrefix("isset(").removeSuffix(")"), context) != null
        }
        for (op in listOf("==", "!=", "<=", ">=", "<", ">")) {
            val idx = expr.indexOf(op)
            if (idx > 0) {
                val left = eval(expr.substring(0, idx), context)
                val right = eval(expr.substring(idx + op.length), context)
                return when (op) {
                    "==" -> "$left" == "$right"
                    "!=" -> "$left" != "$right"
                    "<" -> compare(left, right) < 0
                    ">" -> compare(left, right) > 0
                    "<=" -> compare(left, right) <= 0
                    else -> compare(left, right) >= 0
                }
            }
        }
        return resolve(expr, context)
    }

    private fun compare(left: Any?, right: Any?): Int {
        if (left is Number && right is Number) {
            return left.toDouble().compareTo(right.toDouble())
        }
        return "$left".compareTo("$right")
    }

    private fun resolve(path: String, context: CremaContext): Any? {
        val parts = path.trim().split('.')
        var current: Any? = unwrap(context.get(parts[0]))
        for (part in parts.drop(1)) {
            current = when (current) {
                null -> return null
                is Map<*, *> -> unwrap(current[part])
                else -> unwrap(member(current, part))
            }
        }
        return current
    }

    private fun unwrap(value: Any?): Any? = if (value is CremaLazy) value.get() else value

    /** `getX()` / `isX()` getter, then a zero-arg method named [part], then a public field. */
    private fun member(target: Any, part: String): Any? {
        val cap = part.replaceFirstChar { c -> c.uppercaseChar() }
        val methods = target.javaClass.methods
        val method = methods.firstOrNull { it.parameterCount == 0 && it.name.equals("get$cap", true) }
            ?: methods.firstOrNull { it.parameterCount == 0 && it.name == "is$cap" }
            ?: methods.firstOrNull { it.parameterCount == 0 && it.name == part }
        if (method != null) return method.invoke(target)
        val field = target.javaClass.fields.firstOrNull { it.name == part } ?: return null
        return field.get(target)
    }

    private fun iterate(value: Any?): List<Any?> = when (value) {
        null -> emptyList()
        is Collection<*> -> value.toList()
        is Array<*> -> value.toList()
        is IntRange -> value.toList()
        is CharSequence -> value.toList()
        else -> listOf(value)
    }

    private fun truthy(value: Any?): Boolean = when (value) {
        null, false -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0
        is CharSequence -> value.isNotEmpty()
        is Collection<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        is Array<*> -> value.isNotEmpty()
        else -> true
    }

    private fun escape(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")

    private companion object {
        const val MAX_WHILE = 10_000
        const val VERBATIM_PREFIX = "\uE000VERBATIM:"
        const val VERBATIM_SUFFIX = "\uE001"
        const val STASH_PREFIX = "\uE002"
        const val STASH_SUFFIX = "\uE003"
        val STRUCTURAL = setOf(
            "foreach", "forelse", "for", "each", "while", "if", "unless", "switch", "isset", "empty",
            "auth", "guest", "can", "cannot", "error", "hasSection", "sectionMissing",
        )
        val NO_ARG_BLOCKS = setOf("auth", "guest")
        val X_SLOT_NAMED = Regex("""<x-slot\s+name=["']([^"']+)["']\s*>([\s\S]*?)</x-slot>""")
        val X_SLOT_COLON = Regex("""<x-slot:([a-zA-Z0-9_\-]+)\s*>([\s\S]*?)</x-slot(?::\1)?>""")
        val TAG_ATTRIBUTE = Regex("""([:@a-zA-Z_$][\w:.\-$]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'))?""")

        val COMMENT = Regex("""\{\{--[\s\S]*?--\}\}""")
        val EXTENDS = Regex("""@extends\(\s*'([^']+)'\s*\)""")
        val INLINE_SECTION = Regex("""@section\(\s*'([^']+)'\s*,\s*'([^']+)'\s*\)""")
        val OPEN_SECTION = Regex("""@section\(\s*'([^']+)'\s*\)""")
        val YIELD = Regex("""@yield\(\s*'([^']+)'\s*(?:,\s*'([^']*)'\s*)?\)""")
        val STACK = Regex("""@stack\(\s*'([^']+)'\s*\)""")
        val INCLUDE = Regex("""@include\(\s*'([^']+)'\s*\)""")
        val INCLUDE_IF = Regex("""@includeIf\(\s*'([^']+)'\s*\)""")
        val INCLUDE_WHEN = Regex("""@includeWhen\(\s*([^,]+)\s*,\s*'([^']+)'\s*\)""")
        val INCLUDE_UNLESS = Regex("""@includeUnless\(\s*([^,]+)\s*,\s*'([^']+)'\s*\)""")
        val INCLUDE_FIRST = Regex("""@includeFirst\(\s*\[([^\]]*)\]\s*\)""")
        val QUOTED_NAME = Regex("""'([^']+)'""")
        val METHOD = Regex("""@method\(\s*'([^']+)'\s*\)""")
        val ASSET = Regex("""@asset\(\s*'([^']+)'\s*\)""")
        val LANG = Regex("""@lang\(\s*'([^']+)'\s*(?:,\s*([^)]+))?\)""")
        val VITE_SINGLE = Regex("""@vite\(\s*'([^']+)'\s*\)""")
        val VITE_LIST = Regex("""@vite\(\s*\[([^\]]*)\]\s*\)""")
        val LOOP_AS_HEADER = Regex("""(.+?)\s+as\s+\$?(\w+)""")
        val LOOP_KEY_VALUE_HEADER = Regex("""(.+?)\s+as\s+\$?(\w+)\s*=>\s*\$?(\w+)""")
        val FOR_IN_HEADER = Regex("""\$?(\w+)\s+in\s+(.+)""")
        val FOR_C_HEADER = Regex("""\$?(\w+)\s*=\s*([^;]+);\s*\$?\w+\s*<\s*([^;]+);\s*\$?\w+\+\+""")
        val PUSH = Regex("""@push\(\s*'([^']+)'\s*\)([\s\S]*?)@endpush""")
        val PREPEND = Regex("""@prepend\(\s*'([^']+)'\s*\)([\s\S]*?)@endprepend""")
        val COMPONENT = Regex("""@component\(\s*'([^']+)'\s*\)([\s\S]*?)@endcomponent""")
        val SLOT = Regex("""@slot\(\s*'([^']+)'\s*\)([\s\S]*?)@endslot""")
        val JSON = Regex("""@json\(\s*([^)]+)\s*\)""")
        val X_COMPONENT = Regex("""<x-([a-zA-Z0-9_.\-/]+)>([\s\S]*?)</x-\1>""")
        val X_SELF_CLOSING = Regex("""<x-([a-zA-Z0-9_.\-/]+)\s*/>""")
    }
}
