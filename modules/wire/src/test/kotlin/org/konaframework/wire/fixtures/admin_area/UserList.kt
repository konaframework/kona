package org.konaframework.wire.fixtures.admin_area

import org.konaframework.wire.WireComponent
import org.konaframework.wire.Component

class UserList : Component() {
    var users = listOf("ana", "kai")

    override fun template() = "<ul>@foreach(users as user)<li>{{ user }}</li>@endforeach</ul>"
}

@WireComponent("admin.custom")
class Named : Component() {
    override fun template() = "<b>named</b>"
}

abstract class BaseWidget : Component()

class NotAComponent
