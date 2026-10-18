package org.konaframework.brew.fixtures

import org.konaframework.brew.Async
import org.konaframework.brew.Component
import org.konaframework.brew.Form
import org.konaframework.brew.Js
import org.konaframework.brew.Json
import org.konaframework.brew.On
import org.konaframework.brew.Rule
import org.konaframework.brew.Rules
import org.konaframework.brew.Transition
import org.konaframework.brew.Validate

class ProfileForm : Form() {
    @JvmField
    @field:Validate("required", "min:2")
    var name: String = ""

    @JvmField
    @field:Validate("max:40")
    var nickname: String = ""

    @JvmField
    @field:Validate("max:5", onUpdate = false)
    var code: String = ""

    // Non-public field forces FormMeta's visibility continue branch.
    @Suppress("unused")
    private var hidden: Int = 0
}

class EmptyRulesForm : Form() {
    @JvmField
    var value: String = ""
}

class ValidatedCounter : Component() {
    @field:Validate("required", "email", onUpdate = true)
    var email: String = ""

    @JvmField
    @field:Validate("max:10", onUpdate = false)
    var note: String = ""

    var profile = ProfileForm()

    override fun rules(): Map<String, List<String>> = mapOf("email" to listOf("required", "email"))

    fun save() {
        validate()
    }

    override fun template() = """
        <div>
            <span>{{ email }}</span>
            @if(errors['email'])
                <em>{{ errors['email'][0] }}</em>
            @endif
        </div>
    """.trimIndent()
}

class RuleOnlyComponent : Component() {
    @JvmField
    @field:Rule("required", "min:2")
    var title: String = ""

    fun save() {
        validate()
    }

    override fun template() = "<div>{{ title }}</div>"
}

class UniqueEmailComponent : Component() {
    var email: String = "ada@example.com"
    var id: Int = 7

    override fun rules(): Map<String, List<String>> = mapOf(
        "email" to listOf(
            "required",
            "email",
            Rules.unique("users", "email").ignore(id).toString(),
        ),
    )

    fun save() {
        validate()
    }

    override fun template() = "<div>{{ email }}</div>"
}

class BrokenListener : Component() {
    override fun listeners() = mapOf("ghost" to "noSuchMethod")
    override fun template() = "<div></div>"
}

class EventBus : Component() {
    var lastTitle: String = ""
    var postId: Int = 3
    var meta: Map<String, Any?> = emptyMap()

    @On("post-saved")
    fun onSaved(title: String) {
        lastTitle = title
    }

    @On("post-saved")
    fun onSaved(title: String, extra: String) {
        lastTitle = "$title:$extra"
    }

    @On("")
    fun blankOnIgnored() {
        // blank @On values are skipped
    }

    @On("post-{postId}")
    fun onDynamic() {
        lastTitle = "dynamic"
    }

    @On("refresh-me")
    fun ignoredNameForRefresh() {
        // listener map override below points refresh-me → $refresh
    }

    override fun listeners(): Map<String, String> = mapOf(
        "refresh-me" to "\$refresh",
        "echo-private:inbox,Mail" to "onEchoOrder",
    )

    @Js
    fun blankNamedJs(): String = "1+1"

    @Js("emptyJs")
    fun emptyJs(): String? = null

    @On("echo-presence:lobby,Joined")
    fun onPresence(payload: Any? = null) {
        lastTitle = "presence"
    }

    @Async
    fun parallel() {
        lastTitle = "async"
    }

    @Js("confirmSave")
    fun confirmSaveJs(): String = "confirm('save?')"

    @Json
    fun asJson(): Map<String, Any?> = mapOf("ok" to true, "title" to lastTitle)

    @On("echo:orders,OrderShipped")
    fun onEchoOrder(payload: Any? = null) {
        lastTitle = "echo"
    }

    fun announce() {
        dispatch("saved", "id" to 7)
        dispatch("ping").self()
        dispatch("targeted").to("event-bus")
    }

    fun runJs() {
        js("alert(1)")
    }

    fun goAway() {
        redirect("/home")
    }

    fun goAction() {
        redirectAction("home.show", mapOf("id" to 1), navigate = true)
    }

    fun goFlash() {
        flash("status", "saved")
        redirect("/next")
    }

    fun sendFile() {
        download("hello".toByteArray(), "hello.txt", "text/plain")
    }

    fun streamFile() {
        streamDownload("streamed", "out.txt")
    }

    fun pushStream() {
        stream("chunk", to = "log")
    }

    override fun template() = "<div>{{ lastTitle }}</div>"
}

class TransitionedCounter : Component() {
    var count = 0

    @Transition(type = "forward")
    fun next() {
        count++
    }

    @Transition(skip = true)
    fun quiet() {
        count++
    }

    fun manualForward() {
        transition("forward")
        count++
    }

    fun manualSkip() {
        skipTransition()
        count++
    }

    override fun template() = """<div wire:transition="panel">{{ count }}</div>"""
}
