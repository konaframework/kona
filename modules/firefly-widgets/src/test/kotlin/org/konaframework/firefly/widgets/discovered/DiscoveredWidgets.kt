package org.konaframework.firefly.widgets.discovered

import org.konaframework.firefly.widgets.Widget

class FoundWidget : Widget("found")

abstract class SkipWidget : Widget("skip")

class NotAWidget

class NeedsArg(val n: Int) : Widget("needs")

class BoomWidget : Widget("boom") {
    init {
        throw IllegalStateException("boom")
    }
}
