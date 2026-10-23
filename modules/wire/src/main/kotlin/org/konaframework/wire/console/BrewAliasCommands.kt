package org.konaframework.wire.console

/**
 * Deprecated grind aliases that print a one-line rename hint and run the Wire command.
 */

class MakeBrewCommand : MakeWireCommand() {
    override val commandToken: String = "make:brew"
    override val deprecationHint: String = "`make:brew` is deprecated; use `make:wire`."
}

class BrewConvertCommand : WireConvertCommand() {
    override val commandToken: String = "brew:convert"
    override val deprecationHint: String = "`brew:convert` is deprecated; use `wire:convert`."
}

class BrewLayoutCommand : WireLayoutCommand() {
    override val commandToken: String = "brew:layout"
    override val deprecationHint: String = "`brew:layout` is deprecated; use `wire:layout`."
}

class BrewPublishCommand : WirePublishCommand() {
    override val commandToken: String = "brew:publish"
    override val deprecationHint: String = "`brew:publish` is deprecated; use `wire:publish`."
    override val configFileName: String = "brew.kona.kts"
}

class BrewStubsCommand : WireStubsCommand() {
    override val commandToken: String = "brew:stubs"
    override val deprecationHint: String = "`brew:stubs` is deprecated; use `wire:stubs`."
}
