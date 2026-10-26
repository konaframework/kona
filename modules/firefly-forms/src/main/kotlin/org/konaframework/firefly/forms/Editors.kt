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
        return buildString {
            append("""<div class="ff-rich" x-data="ffRichEditor" data-ff-path="${esc(path)}" data-ff-editor="tiptap"${boolAttr("data-ff-json", jsonFlag)}${boolAttr("data-ff-resizable", resizableFlag)}${attr("data-ff-disk", attachmentsDisk.get(get))}${attr("data-ff-directory", attachmentsDirectory.get(get))}${attr("data-ff-visibility", attachmentsVisibility.get(get))}${attr("data-ff-merge", if (mergeTags.isEmpty()) null else mergeTags.joinToString(","))}${attr("placeholder", placeholderValue(schema))}>""")
            append("""<div class="ff-toolbar ff-toolbar-sticky">""")
            for (group in groups) {
                append("""<div class="ff-toolbar-group">""")
                for (tool in group) {
                    if (tool in disabledTools) continue
                    append("""<button type="button" data-ff-tool="${esc(tool)}">${esc(tool)}</button>""")
                }
                append("</div>")
            }
            append("</div>")
            append("""<div class="ff-bubble">""")
            for (tool in BUBBLE) append("""<button type="button" data-ff-tool="${esc(tool)}">${esc(tool)}</button>""")
            append("</div>")
            append("""<div class="ff-prose">${if (jsonFlag) esc(shown) else sanitize(shown)}</div>""")
            append(Dialog.make("Link").body("""<input class="ff-input" data-ff-link-url="true" /><input class="ff-input" data-ff-link-label="true" />""").render())
            append(Dialog.make("Image").body("""<input class="ff-input" data-ff-image-url="true" /><input class="ff-input" data-ff-image-alt="true" />""").render())
            for (block in blocks) {
                append(Dialog.make(block.label).body(block.schema.render()).render())
                val edit = blockActions[block.name]
                if (edit != null) append(edit.render(schema))
            }
            append("</div>")
        }
    }

    companion object {
        private val BUBBLE = listOf("bold", "italic", "underline", "strike", "link")
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
            append("""<div class="ff-markdown" x-data="ffMarkdown" data-ff-path="${esc(path)}" data-ff-editor="codemirror" data-ff-lang="markdown" data-ff-markdown="gfm"${attr("data-ff-disk", attachmentsDisk.get(get))}${attr("data-ff-directory", attachmentsDirectory.get(get))}>""")
            append("""<div class="ff-markdown-switch"><button type="button" data-ff-mode="write">Write</button><button type="button" data-ff-mode="preview">Preview</button></div>""")
            append("""<div class="ff-toolbar">""")
            for (tool in tools) {
                if (tool in disabledTools) continue
                append("""<button type="button" data-ff-tool="${esc(tool)}">${esc(tool)}</button>""")
            }
            append("</div>")
            append("""<textarea class="ff-input" data-ff-write="true">${esc(shown)}</textarea>""")
            append(preview(shown))
            append("</div>")
        }
    }

    companion object {
        private val DEFAULT_TOOLS = listOf("bold", "italic", "strike", "link", "image", "code", "list", "quote")
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

    fun wrap(on: Boolean): CodeEditor = apply { wrapFlag = on }

    override fun kind(): String = "code-editor"

    override fun control(schema: Schema, get: Get, path: String, value: Any?): String {
        val shown = if (value == null) "" else value.toString()
        return """<div class="ff-code" x-data="ffCodeEditor" data-ff-path="${esc(path)}" data-ff-editor="codemirror" data-ff-highlighter="shiki"${attr("data-ff-language", languageName)}${boolAttr("data-ff-wrap", wrapFlag)}><textarea class="ff-input">${esc(shown)}</textarea></div>"""
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
