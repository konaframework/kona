package org.konaframework.brew.features

import org.konaframework.brew.lifecycle.ComponentContext
import org.konaframework.brew.lifecycle.ComponentHook
import java.util.Locale

/** Restores `memo.locale` on update and writes the current locale on dehydrate (`SupportLocales`). */
class SupportLocales : ComponentHook() {
    override fun hydrate(memo: Map<String, Any?>, context: ComponentContext) {
        val locale = memo["locale"] as? String ?: return
        if (locale.isNotBlank()) Locale.setDefault(Locale.forLanguageTag(locale.replace('_', '-')))
    }

    override fun dehydrate(context: ComponentContext) {
        context.addMemo("locale", Locale.getDefault().toLanguageTag())
    }
}
