package dev.kona.crema

import com.fasterxml.jackson.databind.ObjectMapper
import dev.kona.support.lang.Lang

/**
 * Crema template runtime — directives and echoes for `*.crema.html`.
 * Kept in one file so the behavioral suite can drive coverage of the interpreter.
 */
internal class CremaRuntime(private val finder: ViewFinder) {
    private val jsonMapper = ObjectMapper()

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
        var text = expandBlocks(withoutVerbatim, context)
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
        return restoreVerbatim(out.toString(), verbatimSlots)
    }

    private fun expandBlocks(source: String, context: CremaContext): String {
        var text = source
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
        text = replaceAuthGuest(text, context)
        text = replaceCan(text, context)
        text = replaceIssetEmpty(text, context)
        text = replaceError(text, context)
        text = replaceHasSection(text, context)
        text = replaceSwitch(text, context)
        text = replaceIf(text, context)
        text = replaceUnless(text, context)
        text = replaceWhile(text, context)
        text = replaceForeach(text, context)
        text = replaceForelse(text, context)
        text = replaceComponent(text, context)
        text = replaceXComponents(text, context)
        text = replaceStack(text, context)
        return text
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

    private fun replaceAuthGuest(source: String, context: CremaContext): String {
        var text = replacePairedBlock(source, "@auth", "@endauth") { body ->
            if (isAuthenticated(context)) expand(body, context) else ""
        }
        text = replacePairedBlock(text, "@guest", "@endguest") { body ->
            if (!isAuthenticated(context)) expand(body, context) else ""
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

    private fun replaceCan(source: String, context: CremaContext): String {
        var text = replaceArgBlock(source, "@can", "@endcan") { ability, body ->
            if (can(ability, context)) expand(body, context) else ""
        }
        text = replaceArgBlock(text, "@cannot", "@endcannot") { ability, body ->
            if (!can(ability, context)) expand(body, context) else ""
        }
        return text
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

    private fun replaceIssetEmpty(source: String, context: CremaContext): String {
        var text = replaceArgBlock(source, "@isset", "@endisset") { expr, body ->
            if (resolve(expr.trim().removePrefix("$"), context) != null) expand(body, context) else ""
        }
        text = replaceArgBlock(text, "@empty", "@endempty") { expr, body ->
            if (!truthy(eval(expr, context))) expand(body, context) else ""
        }
        return text
    }

    private fun replaceError(source: String, context: CremaContext): String =
        replaceArgBlock(source, "@error", "@enderror") { field, body ->
            val errors = context.get("errors")
            val has =
                when (errors) {
                    is Map<*, *> -> {
                        val v = errors[field]
                        when (v) {
                            null -> false
                            is Collection<*> -> v.isNotEmpty()
                            is CharSequence -> v.isNotEmpty()
                            else -> true
                        }
                    }
                    else -> false
                }
            if (has) expand(body, context) else ""
        }

    private fun replaceHasSection(source: String, context: CremaContext): String {
        var text = replaceNamedIf(source, "@hasSection", context) { name -> context.hasSection(name) }
        text = replaceNamedIf(text, "@sectionMissing", context) { name -> !context.hasSection(name) }
        return text
    }

    /** `@hasSection('name')…@endif` / `@sectionMissing('name')…@endif` */
    private fun replaceNamedIf(
        source: String,
        tag: String,
        context: CremaContext,
        predicate: (String) -> Boolean,
    ): String {
        var text = source
        while (true) {
            val start = text.indexOf(tag)
            if (start < 0) break
            val openParen = text.indexOf('(', start + tag.length)
            require(openParen >= 0) { "Missing ( after $tag" }
            val (arg, closeParen) = readParens(text, openParen) ?: error("Unclosed ( in $tag")
            val name = arg.trim().removeSurrounding("'").removeSurrounding("\"")
            val bodyStart = closeParen + 1
            val end = findMatchingEndif(text, bodyStart)
            require(end >= 0) { "Missing @endif for $tag" }
            val body = text.substring(bodyStart, end)
            val replacement = if (predicate(name)) expand(body, context) else ""
            text = text.replaceRange(start, end + "@endif".length, replacement)
        }
        return text
    }

    private fun replaceSwitch(source: String, context: CremaContext): String {
        var text = source
        while (true) {
            val start = text.indexOf("@switch")
            if (start < 0) break
            val openParen = text.indexOf('(', start + "@switch".length)
            require(openParen >= 0) { "Missing ( after @switch" }
            val (expr, closeParen) = readParens(text, openParen) ?: error("Unclosed ( in @switch")
            val bodyStart = closeParen + 1
            val end = text.indexOf("@endswitch", bodyStart)
            require(end >= 0) { "Missing @endswitch" }
            val body = text.substring(bodyStart, end)
            val switchValue = eval(expr, context)
            val replacement = evalSwitchBody(body, switchValue, context)
            text = text.replaceRange(start, end + "@endswitch".length, replacement)
        }
        return text
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

    private fun replaceIf(source: String, context: CremaContext): String {
        var text = source
        while (true) {
            val start = text.indexOf("@if")
            if (start < 0) break
            // avoid matching @ifdef-style; require '(' soon after
            if (start + 3 < text.length && text[start + 3].isLetterOrDigit()) {
                // false positive inside something else — skip
                text = text.replaceRange(start, start + 3, "\u0000if")
                continue
            }
            val openParen = text.indexOf('(', start + 3)
            if (openParen < 0 || openParen > start + 8) {
                text = text.replaceRange(start, start + 3, "\u0000if")
                continue
            }
            val (cond, closeParen) = readParens(text, openParen) ?: error("Unclosed ( in @if")
            val bodyStart = closeParen + 1
            val end = findMatchingEndif(text, bodyStart)
            require(end >= 0) { "Missing @endif" }
            val block = text.substring(bodyStart, end)
            val replacement = evalIfBlock(cond, block, context)
            text = text.replaceRange(start, end + "@endif".length, replacement)
        }
        return text.replace("\u0000if", "@if")
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

    private fun replaceUnless(source: String, context: CremaContext): String =
        replaceArgBlock(source, "@unless", "@endunless") { cond, body ->
            if (!truthy(eval(cond, context))) expand(body, context) else ""
        }

    private fun replaceWhile(source: String, context: CremaContext): String =
        replaceArgBlock(source, "@while", "@endwhile") { cond, body ->
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
        }

    private fun replaceForeach(source: String, context: CremaContext): String {
        var text = source
        text = replaceNestedLoop(text, "@foreach", "@endforeach", context) { header, body, ctx ->
            val m = LOOP_AS_HEADER.matchEntire(header.trim())
                ?: error("Invalid @foreach header: $header")
            renderLoop(eval(m.groupValues[1], ctx), m.groupValues[2], body, ctx)
        }
        text = replaceNestedLoop(text, "@each", "@endeach", context) { header, body, ctx ->
            val m = LOOP_AS_HEADER.matchEntire(header.trim())
                ?: error("Invalid @each header: $header")
            renderLoop(eval(m.groupValues[1], ctx), m.groupValues[2], body, ctx)
        }
        text = replaceNestedLoop(text, "@for", "@endfor", context) { header, body, ctx ->
            renderForHeader(header.trim(), body, ctx)
        }
        return text
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

    private fun replaceNestedLoop(
        source: String,
        open: String,
        close: String,
        context: CremaContext,
        transform: (header: String, body: String, context: CremaContext) -> String,
    ): String {
        var text = source
        while (true) {
            val start = findDirective(text, open, 0) ?: break
            val openParen = text.indexOf('(', start + open.length)
            require(openParen >= 0) { "Missing ( after $open" }
            val (header, closeParen) = readParens(text, openParen) ?: error("Unclosed ( in $open")
            val bodyStart = closeParen + 1
            val end = findMatchingClose(text, bodyStart, open, close)
            require(end >= 0) { "Missing $close" }
            val body = text.substring(bodyStart, end)
            text = text.replaceRange(start, end + close.length, transform(header, body, context))
        }
        return text
    }

    private fun findDirective(text: String, tag: String, from: Int): Int? {
        var i = from
        while (true) {
            val idx = text.indexOf(tag, i)
            if (idx < 0) return null
            if (isDirectiveAt(text, idx, tag)) return idx
            i = idx + tag.length
        }
    }

    private fun findMatchingClose(text: String, from: Int, open: String, close: String): Int {
        var depth = 0
        var i = from
        while (i < text.length) {
            when {
                isDirectiveAt(text, i, open) -> {
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

    private fun replaceForelse(source: String, context: CremaContext): String {
        var text = source
        while (true) {
            val start = findDirective(text, "@forelse", 0) ?: break
            val openParen = text.indexOf('(', start + "@forelse".length)
            require(openParen >= 0) { "Missing ( after @forelse" }
            val (header, closeParen) = readParens(text, openParen) ?: error("Unclosed ( in @forelse")
            val m = LOOP_AS_HEADER.matchEntire(header.trim())
                ?: error("Invalid @forelse header: $header")
            val bodyStart = closeParen + 1
            val end = findMatchingClose(text, bodyStart, "@forelse", "@endforelse")
            require(end >= 0) { "Missing @endforelse" }
            val block = text.substring(bodyStart, end)
            val emptyAt = findForelseEmpty(block)
            require(emptyAt >= 0) { "Missing @empty in @forelse" }
            val body = block.substring(0, emptyAt)
            val empty = block.substring(emptyAt + "@empty".length)
            val items = iterate(eval(m.groupValues[1], context))
            val replacement =
                if (items.isEmpty()) {
                    expand(empty, context)
                } else {
                    renderLoopItems(items, m.groupValues[2], body, context)
                }
            text = text.replaceRange(start, end + "@endforelse".length, replacement)
        }
        return text
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
            runCatching {
                render(view, context.child(mapOf("slot" to expand(body, context))))
            }.getOrDefault("")
        }
        text = X_SELF_CLOSING.replace(text) { m ->
            val name = m.groupValues[1].replace('/', '.')
            val view = if (name.startsWith("components.")) name else "components.$name"
            runCatching { render(view, context.child(mapOf("slot" to ""))) }.getOrDefault("")
        }
        return text
    }

    private fun replacePairedBlock(
        source: String,
        open: String,
        close: String,
        transform: (String) -> String,
    ): String {
        var text = source
        while (true) {
            val start = text.indexOf(open)
            if (start < 0) break
            if (start + open.length < text.length && text[start + open.length].isLetterOrDigit()) {
                text = text.replaceRange(start, start + open.length, "\u0000" + open.drop(1))
                continue
            }
            val bodyStart = start + open.length
            val end = text.indexOf(close, bodyStart)
            require(end >= 0) { "Missing $close" }
            val body = text.substring(bodyStart, end)
            text = text.replaceRange(start, end + close.length, transform(body))
        }
        return text.replace("\u0000", "@")
    }

    private fun replaceArgBlock(
        source: String,
        open: String,
        close: String,
        transform: (arg: String, body: String) -> String,
    ): String {
        var text = source
        while (true) {
            val start = text.indexOf(open)
            if (start < 0) break
            if (start + open.length < text.length && text[start + open.length].isLetterOrDigit()) {
                text = text.replaceRange(start, start + open.length, "\u0000" + open.drop(1))
                continue
            }
            var j = start + open.length
            while (j < text.length && text[j].isWhitespace()) j++
            if (j >= text.length || text[j] != '(') {
                // e.g. forelse `@empty` without args — skip
                text = text.replaceRange(start, start + open.length, "\u0000" + open.drop(1))
                continue
            }
            val (arg, closeParen) = readParens(text, j) ?: error("Unclosed ( in $open")
            val bodyStart = closeParen + 1
            val end = text.indexOf(close, bodyStart)
            require(end >= 0) { "Missing $close" }
            val body = text.substring(bodyStart, end)
            val ability =
                if (open == "@can" || open == "@cannot" || open == "@error") {
                    arg.trim().removeSurrounding("'").removeSurrounding("\"")
                } else {
                    arg
                }
            text = text.replaceRange(start, end + close.length, transform(ability, body))
        }
        return text.replace("\u0000", "@")
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
        var current: Any? = context.get(parts[0])
        for (part in parts.drop(1)) {
            current = when (current) {
                null -> return null
                is Map<*, *> -> current[part]
                else -> {
                    val getter = current.javaClass.methods.firstOrNull {
                        it.parameterCount == 0 &&
                            it.name.equals("get${part.replaceFirstChar { c -> c.uppercaseChar() }}", true)
                    }
                    getter?.invoke(current)
                }
            }
        }
        return current
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
        val LOOP_AS_HEADER = Regex("""(.+?)\s+as\s+(\w+)""")
        val FOR_IN_HEADER = Regex("""(\w+)\s+in\s+(.+)""")
        val FOR_C_HEADER = Regex("""(\w+)\s*=\s*([^;]+);\s*\w+\s*<\s*([^;]+);\s*\w+\+\+""")
        val PUSH = Regex("""@push\(\s*'([^']+)'\s*\)([\s\S]*?)@endpush""")
        val PREPEND = Regex("""@prepend\(\s*'([^']+)'\s*\)([\s\S]*?)@endprepend""")
        val COMPONENT = Regex("""@component\(\s*'([^']+)'\s*\)([\s\S]*?)@endcomponent""")
        val SLOT = Regex("""@slot\(\s*'([^']+)'\s*\)([\s\S]*?)@endslot""")
        val JSON = Regex("""@json\(\s*([^)]+)\s*\)""")
        val X_COMPONENT = Regex("""<x-([a-zA-Z0-9_.\-/]+)>([\s\S]*?)</x-\1>""")
        val X_SELF_CLOSING = Regex("""<x-([a-zA-Z0-9_.\-/]+)\s*/>""")
    }
}
