# {{appName}}

Kona application (Kotlin) — **Brew** starter kit.

```bash
./grind migrate
./grind db:seed
./grind serve
```

Includes `org.konaframework:kona-brew` and `kona-crema`, plus the same branded landing, session login, dashboard, and `User` model as the Crema kit.

## Adding a Brew component

```kotlin
// app/brew/Hello.kt
package app.brew

import org.konaframework.brew.Component
import org.konaframework.brew.template

class Hello : Component() {
    var name: String = "world"

    override fun template() = template {
        """
        <div>
          <h1>Hello, {{ name }}</h1>
        </div>
        """.trimIndent()
    }
}
```

Register it in `bootstrap.App` with `Brew.register(Hello::class.java)`, then embed with `<brew:hello />` (or `wire:name="hello"`) in a Crema view.
