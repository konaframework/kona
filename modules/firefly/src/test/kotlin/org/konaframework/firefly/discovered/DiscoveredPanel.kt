package org.konaframework.firefly.discovered

import org.konaframework.firefly.ListPage
import org.konaframework.firefly.PanelPage
import org.konaframework.firefly.Resource
import org.konaframework.firefly.widgets.Widget

class FoundPage : PanelPage("found")

abstract class SkipPage : PanelPage("skip")

class NotAPage

class NeedsArgPage(val n: Int) : PanelPage("needs")

class BoomPage : PanelPage("boom") {
    init {
        throw IllegalStateException("boom")
    }
}

class Desk : PanelPage()

class FoundResource : Resource("notes") {
    init {
        page(ListPage("all"))
    }
}

abstract class SkipResource : Resource("skip")

class PlainResource

class FoundWidget : Widget("found")
