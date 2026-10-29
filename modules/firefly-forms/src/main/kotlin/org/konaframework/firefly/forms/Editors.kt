package org.konaframework.firefly.forms

import org.konaframework.firefly.actions.Action
import org.konaframework.firefly.schemas.Get
import org.konaframework.firefly.schemas.Schema
import org.konaframework.firefly.support.Dialog
import org.konaframework.firefly.support.FireflyException
import java.util.function.Consumer
import java.util.function.Function

class EditorBlock(val name: String, val label: String, val schema: Schema)

open class RichEditor protected constructor(name: String) : Field<RichEditor>(name) {
    private val groups = ArrayList<List<String>>()
    private val disabledTools = ArrayList<String>()
    private var jsonFlag: Boolean = false
    private var resizableFlag: Boolean = false
    private val attachmentsDisk = Phrase()
    private val attachmentsDirectory = Phrase()
    private val attachmentsVisibility = Phrase()
    private val mergeTags = ArrayList<String>()
    private val mentionList = ArrayList<String>()
    private var mentionUsing: Function<String, List<String>>? = null
    private val blocks = ArrayList<EditorBlock>()
    private val blockActions = LinkedHashMap<String, Action>()

    init {
        groups.addAll(DEFAULT_GROUPS)
    }

    fun toolbarButtons(next: List<List<String>>): RichEditor = apply {
        groups.clear()
        groups.addAll(next)
    }

    fun disableToolbarButtons(vararg buttons: String): RichEditor = apply {
        disabledTools.clear()
        disabledTools.addAll(buttons)
    }

    fun json(): RichEditor = apply { jsonFlag = true }

    fun html(): RichEditor = apply { jsonFlag = false }

    fun resizable(on: Boolean): RichEditor = apply { resizableFlag = on }

    fun fileAttachmentsDisk(value: String): RichEditor = apply { attachmentsDisk.set(value) }

    fun fileAttachmentsDisk(value: Function<Get, String>): RichEditor = apply { attachmentsDisk.set(value) }

    fun fileAttachmentsDirectory(value: String): RichEditor = apply { attachmentsDirectory.set(value) }

    fun fileAttachmentsDirectory(value: Function<Get, String>): RichEditor = apply { attachmentsDirectory.set(value) }

    fun fileAttachmentsVisibility(value: String): RichEditor = apply { attachmentsVisibility.set(value) }

    fun fileAttachmentsVisibility(value: Function<Get, String>): RichEditor = apply { attachmentsVisibility.set(value) }

    fun mergeTags(vararg tags: String): RichEditor = apply {
        mergeTags.clear()
        mergeTags.addAll(tags)
    }

    fun mentions(vararg names: String): RichEditor = apply {
        mentionList.clear()
        mentionList.addAll(names)
        mentionUsing = null
    }

    fun mentions(provider: Function<String, List<String>>): RichEditor = apply { mentionUsing = provider }

    fun mention(query: String): List<String> {
        val provider = mentionUsing
        if (provider != null) return provider.apply(query)
        val needle = query.lowercase()
        return mentionList.filter { it.lowercase().contains(needle) }
    }

    fun customBlocks(vararg blocks: EditorBlock): RichEditor = apply {
        this.blocks.clear()
        this.blocks.addAll(blocks)
    }

    fun editBlockAction(blockName: String, action: Action): RichEditor = apply {
        blockActions[blockName] = action
    }

    fun sanitize(html: String): String = if (jsonFlag) html else HtmlSanitizer.clean(html)

    override fun kind(): String = "rich-editor"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val shown = if (value == null) "" else value.toString()
        val clean = if (jsonFlag) shown else sanitize(shown)
        val tools = groups.flatten().filter { it !in disabledTools }
        return buildString {
            append("""<div class="ff-rich shamar-rich-editor shamar-rich-editor--simple" x-data="ffRichEditor" data-ff-rich-editor data-ff-path="${esc(path)}" data-ff-editor="tiptap"${boolAttr("data-ff-json", jsonFlag)}${boolAttr("data-ff-resizable", resizableFlag)}${attr("data-ff-disk", attachmentsDisk.get(get))}${attr("data-ff-directory", attachmentsDirectory.get(get))}${attr("data-ff-visibility", attachmentsVisibility.get(get))}${attr("data-ff-merge", if (mergeTags.isEmpty()) null else mergeTags.joinToString(","))}${attr("data-ff-toolbar", tools.joinToString(","))}${attr("data-ff-disabled-tools", if (disabledTools.isEmpty()) null else disabledTools.joinToString(","))}${attr("placeholder", placeholderValue(schema))}${extraInputMarkup(get)}>""")
            append("""<input type="hidden" name="${esc(path)}" value="${esc(clean)}" data-ff-rich-value />""")
            append("""<div class="ff-rich-host" data-ff-rich-host><div class="ff-rich-loading ff-prose">${if (jsonFlag) esc(shown) else clean}</div></div>""")
            for (block in blocks) {
                append(Dialog.make(block.label).body(block.schema.render()).render())
                val edit = blockActions[block.name]
                if (edit != null) append(edit.render(schema))
            }
            append("</div>")
        }
    }

    companion object {
        private val DEFAULT_GROUPS = listOf(
            listOf("undo", "redo"),
            listOf("bold", "italic", "underline", "strike", "subscript", "superscript", "highlight"),
            listOf("h1", "h2", "h3"),
            listOf("align-left", "align-center", "align-right", "align-justify"),
            listOf("bullet", "ordered", "blockquote", "code"),
            listOf("link", "image", "table"),
        )
        private val maker = Maker { name -> RichEditor(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<RichEditor>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): RichEditor = maker.make(name)
    }
}

open class MarkdownEditor protected constructor(name: String) : Field<MarkdownEditor>(name) {
    private val tools = ArrayList<String>()
    private val disabledTools = ArrayList<String>()
    private val attachmentsDisk = Phrase()
    private val attachmentsDirectory = Phrase()

    init {
        tools.addAll(DEFAULT_TOOLS)
    }

    fun toolbarButtons(vararg buttons: String): MarkdownEditor = apply {
        tools.clear()
        tools.addAll(buttons)
    }

    fun disableToolbarButtons(vararg buttons: String): MarkdownEditor = apply {
        disabledTools.clear()
        disabledTools.addAll(buttons)
    }

    fun fileAttachmentsDisk(value: String): MarkdownEditor = apply { attachmentsDisk.set(value) }

    fun fileAttachmentsDisk(value: Function<Get, String>): MarkdownEditor = apply { attachmentsDisk.set(value) }

    fun fileAttachmentsDirectory(value: String): MarkdownEditor = apply { attachmentsDirectory.set(value) }

    fun fileAttachmentsDirectory(value: Function<Get, String>): MarkdownEditor = apply { attachmentsDirectory.set(value) }

    fun preview(source: String): String = MarkdownHtml.render(source)

    override fun kind(): String = "markdown-editor"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val shown = if (value == null) "" else value.toString()
        return buildString {
            append("""<div class="ff-markdown" x-data="ffMarkdown" data-ff-markdown-editor data-ff-path="${esc(path)}" data-ff-editor="codemirror" data-ff-lang="markdown" data-ff-markdown="gfm"${attr("data-ff-disk", attachmentsDisk.get(get))}${attr("data-ff-directory", attachmentsDirectory.get(get))}>""")
            append("""<div class="ff-markdown-head">""")
            append("""<div class="ff-markdown-tabs" role="tablist">""")
            append("""<button type="button" class="ff-markdown-tab" role="tab" aria-selected="true" data-ff-mode="write">Write</button>""")
            append("""<button type="button" class="ff-markdown-tab" role="tab" aria-selected="false" data-ff-mode="preview">Preview</button>""")
            append("</div>")
            append("""<div class="ff-markdown-toolbar" role="toolbar" aria-label="Formatting" data-ff-markdown-toolbar>""")
            for (tool in tools) {
                if (tool in disabledTools) continue
                val title = TOOL_TITLES[tool] ?: tool
                append("""<button type="button" class="ff-markdown-tool" data-ff-tool="${esc(tool)}" aria-label="${esc(title)}" title="${esc(title)}">${esc(TOOL_GLYPHS[tool] ?: tool)}</button>""")
            }
            append("</div></div>")
            append("""<div class="ff-markdown-body">""")
            append("""<textarea class="ff-markdown-input" name="${esc(path)}" rows="8" spellcheck="true" data-ff-write="true"${attr("placeholder", placeholderValue(schema))}${extraInputMarkup(get)}>${esc(shown)}</textarea>""")
            append("""<div class="ff-markdown-preview" data-ff-markdown-preview hidden>${preview(shown)}</div>""")
            append("</div>")
            append("""<div class="ff-markdown-foot">Markdown is supported</div>""")
            append("</div>")
        }
    }

    companion object {
        private val DEFAULT_TOOLS = listOf("heading", "bold", "italic", "strike", "quote", "code", "link", "image", "list", "ordered")
        private val TOOL_TITLES = mapOf(
            "heading" to "Heading",
            "bold" to "Bold",
            "italic" to "Italic",
            "strike" to "Strikethrough",
            "quote" to "Quote",
            "code" to "Code",
            "link" to "Link",
            "image" to "Image",
            "list" to "Bulleted list",
            "ordered" to "Numbered list",
            "task" to "Task list",
            "table" to "Table",
        )
        private val TOOL_GLYPHS = mapOf(
            "heading" to "H",
            "bold" to "B",
            "italic" to "I",
            "strike" to "S",
            "quote" to "❝",
            "code" to "</>",
            "link" to "🔗",
            "image" to "▣",
            "list" to "•",
            "ordered" to "1.",
            "task" to "☑",
            "table" to "▦",
        )
        private val maker = Maker { name -> MarkdownEditor(name) }

        @JvmStatic
        fun configureUsing(block: Consumer<MarkdownEditor>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): MarkdownEditor = maker.make(name)
    }
}

open class CodeEditor protected constructor(name: String) : Field<CodeEditor>(name) {
    private var languageName: String = "plaintext"
    private var wrapFlag: Boolean = false

    fun language(value: String): CodeEditor = apply {
        if (value.isBlank()) throw FireflyException("Code language is empty")
        languageName = value
    }

    fun language(): String = languageName

    fun wrap(): CodeEditor = wrap(true)

    fun wrap(on: Boolean): CodeEditor = apply { wrapFlag = on }

    override fun kind(): String = "code-editor"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val shown = if (value == null) "" else value.toString()
        return buildString {
            append("""<div class="ff-code-editor" x-data="ffCodeEditor" data-ff-code-editor data-ff-path="${esc(path)}" data-ff-editor="codemirror" data-ff-highlighter="shiki"${attr("data-ff-language", languageName)}${boolAttr("data-ff-wrap", wrapFlag)}>""")
            append("""<div class="ff-code-editor-head"><span class="ff-code-editor-lang">${esc(languageName)}</span></div>""")
            append("""<div class="ff-code-editor-body">""")
            append("""<pre class="ff-code-editor-highlight" aria-hidden="true" data-ff-code-highlight><code>${esc(shown)}</code></pre>""")
            append("""<textarea class="ff-code-editor-input" name="${esc(path)}" spellcheck="false" autocapitalize="off" autocomplete="off" wrap="${if (wrapFlag) "soft" else "off"}" data-ff-code-input${attr("placeholder", placeholderValue(schema))}${extraInputMarkup(get)}>${esc(shown)}</textarea>""")
            append("</div></div>")
        }
    }

    companion object {
        private val maker = Maker { name -> CodeEditor(name) }

        @JvmStatic
        fun languages(): List<String> = FormAssets.languages()

        @JvmStatic
        fun configureUsing(block: Consumer<CodeEditor>?) = maker.configureUsing(block)

        @JvmStatic
        fun make(name: String): CodeEditor = maker.make(name)
    }
}
