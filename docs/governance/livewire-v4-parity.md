# Livewire v4 parity ledger

Source of truth: Livewire v4 docs (`livewire/livewire` `docs/` — 4.x nav) and package behaviour (`src/Features/Support*`, `js/`).
Every row maps to exactly one milestone in [MILESTONES.md](../../MILESTONES.md). The program is complete only when every row is **done**.

Naming map: `Livewire` → `Wire`, `<livewire:x>` → `<wire:x>`, `@livewire` → `@brew`, `@livewireStyles/Scripts` → `@wireStyles/Scripts`,
`Livewire::test` → `Wire.test`, `#[Attr]` → Kotlin/Java annotation `@Attr`, Blade → Crema, `make:livewire` → `./grind make:wire`.
`wire:*` directives, `$wire`, and the JS event names keep Livewire's spelling (prefix `brew:` instead of `livewire:` for global DOM events).

Status: `todo` · `done`.

## Protocol, hydration, security

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| PRO-1 | Snapshot `{data, memo{id,name,path,method,children,scripts,assets,errors,locale}, checksum}` | `Snapshot` JSON, same keys | M1 | done |
| PRO-2 | `wire:snapshot`, `wire:effects`, `wire:id`, `wire:name` on root element | `HtmlRoot.insertAttributes` | M1 | done |
| PRO-3 | `POST /livewire/update` `{_token, components:[{snapshot,updates,calls}]}` → `{components:[{snapshot,effects}], assets}` | `POST /wire/update` | M1 | done |
| PRO-4 | Checksum (HMAC of snapshot) verified on every update; corrupt payload → 419/exception | `Checksum` (HMAC-SHA256, app key) | M1 | done |
| PRO-5 | `X-Livewire` header required (`RequireLivewireHeaders`) | `X-Brew` header guard | M1 | done |
| PRO-6 | CSRF on update endpoint (`web` middleware) | `_token` + session CSRF check | M1 | done |
| PRO-7 | Payload guards: `max_size`, `max_components`, `max_calls`, `max_nesting_depth` | `WireConfig.payload*` | M1 | done |
| PRO-8 | Custom update route (`Livewire::setUpdateRoute`) / script route (`setScriptRoute`) | `Wire.setUpdateRoute` / `setScriptRoute` | M1 | done |
| PRO-9 | Multiple root element detection error | `MultipleRootElementsException` | M1 | done |
| PRO-10 | Effects `html`, `returns` | `Effects` map | M1 | done |
| PRO-10b | Effect `errors` | `Effects` map | M2 | done |
| PRO-10c | Effects `dispatches`, `redirect`, `xjs`, `download`, `streams`, `scripts`, `assets` | `Effects` map | M3 | done |
| PRO-10d | Effects `url`, `title` | `Effects` map | M5 | done |
| PRO-11 | Locale memo (`SupportLocales`) — request locale restored on update | `memo.locale` → `Lang.setLocale` | M6 | done |
| PRO-12 | Release tokens (`SupportReleaseTokens`) — invalidate snapshots after deploy | `memo.release` + `Wire.releaseToken` | M6 | done |
| PRO-13 | Large payload handling (`SupportLargePayloads`) | payload limits + chunked snapshot errors | M6 | done |
| HYD-1 | Synthesizers: int, float, string, bool, null, array (list/assoc), stdClass, enum, Carbon/DateTime, Collection, Stringable | `IntSynth`… `java.time`, `List`/`Map`/`Set`, enums, `BigDecimal`/`BigInteger`, `UUID` | M1 | done |
| HYD-2 | Tuple dehydration `[value, {s: key, …}]` for non-scalars | same | M1 | done |
| HYD-3 | `Wireable` objects (`toLivewire` / `fromLivewire`) | `Wireable` interface (`toBrew` / `fromBrew`) | M1 | done |
| HYD-4 | Custom synthesizers (`Livewire::propertySynthesizer`) incl. `get`/`set` for data binding, array-shaped synths | `Wire.propertySynthesizer(Synth)` | M6 | done |
| HYD-5 | Plain objects / DTOs (`std`) and data classes | Jackson-backed `ObjectSynth` (class pinned in meta) | M1 | done |
| HYD-6 | Eloquent model / collection synth (`SupportModels`, `SupportLegacyModels`) — models re-queried by key | Cadence `Model` synth (class + key) | M6 | done |
| HYD-7 | Deep `wire:model` paths (`todos.0.title`) via synth `get`/`set`, `__rm__` removal marker | `Synths.setPath` | M1 | done |
| HYD-8 | Typed property guard: null/'' to non-nullable → unset/ignore; wrong type → 419 | `Coercion.toType` + `PropertyTypeMismatchException` | M1 | done |
| SEC-1 | Only public props defined on subclass are state; base members hidden | `ComponentMeta` | M1 | done |
| SEC-2 | Only public methods defined on subclass callable; `render`/lifecycle/`mount` not callable | `ComponentMeta.actions` | M1 | done |
| SEC-3 | `#[Locked]` props reject client updates (`CannotUpdateLockedPropertyException`) | `@Locked` | M1 | done |
| SEC-4 | Persistent middleware re-applied on update (`Livewire::addPersistentMiddleware`) | `Wire.addPersistentMiddleware` | M6 | done |
| SEC-5 | Global Livewire middleware on update route | `Wire.updateMiddleware(...)` | M6 | done |
| SEC-6 | Checksum failure debugging (`SupportChecksumErrorDebugging`) | debug-mode diff message | M6 | done |
| SEC-7 | `#[Authorize('ability', arg)]` on actions → 403 | `@Authorize` via Kona Gate | M6 | done |
| SEC-8 | `$this->authorize()` inside actions (AuthorizesRequests) | `Component.authorize(...)` | M6 | done |
| SEC-9 | Security policy: denied classes for synth hydration | `SecurityPolicy` allow-list | M1 | done |
| SEC-10 | Disable back-button cache (`SupportDisablingBackButtonCache`) | `Component.disableBackButtonCache()` | M5 | done |

## Components

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| CMP-1 | Class-based components (`Component` subclass + `render()`) | `abstract class Component` (Kotlin) / Java subclass | M1 | done |
| CMP-2 | Single-file components (view + class in one file, `⚡name.blade.php`) | Kotlin class with inline `template()` Crema source | M1 | done |
| CMP-3 | Multi-file components (`name/name.php` + `.blade.php` + `.js` + `.css`) | class + `views/wire/<name>.crema.html` + `<name>.js` + `<name>.css` | M6 | done |
| CMP-4 | Convention view lookup (`livewire.<name>` view path) when `render()` omitted | `views/wire/<name>.crema.html` | M1 | done |
| CMP-5 | Name ↔ class mapping (kebab, dot-nesting `posts.create`), namespaces (`pages::`, `layouts::`) | `ComponentRegistry` names, `ns::name` | M1 | done |
| CMP-6 | Programmatic registration (`Livewire::component`, `addLocation`, `addNamespace`) | `Wire.component`, `Wire.addLocation`, `Wire.addNamespace` | M1 | done |
| CMP-7 | Component discovery by package/classpath scan | `Wire.discover("app.wire")` | M1 | done |
| CMP-8 | `<livewire:name />`, `@livewire('name', [...])` rendering | `<wire:name />`, `@wire('name', ...)` | M1 | done |
| CMP-9 | Passing props (static, `:prop="$expr"`, `:$prop` shorthand, kebab → camel) | tag attributes → mount params/props | M1 | done |
| CMP-10 | Non-prop params become HTML attributes on root | `HtmlAttributeForwarding` | M4 | done |
| CMP-11 | `render()` returning view with data (`->with()`), string templates, `view()` helpers | `view(name, data)`, `template(src)` | M1 | done |
| CMP-11b | `->title()` / `->layout()` on the rendered view | `View.title/layout` | M5 | done |
| CMP-12 | Component stubs customization (`livewire:stubs`) | `./grind wire:stubs` | M7 | done |
| CMP-13 | Default component type config (`make_command.type`) | `brew.make.type` config | M7 | done |
| CMP-14 | `$this->getId()`, `getName()`, `$this->id()` | `id()`, `name()` | M1 | done |
| CMP-15 | Troubleshooting errors: component not found, blank render, class conflicts | `ComponentNotFoundException`, root checks | M1 | done |

## Properties

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| PRP-1 | Initialize in `mount()` / defaults | same | M1 | done |
| PRP-2 | Bulk assignment `fill()` | `fill(map)` | M1 | done |
| PRP-3 | `reset(...)`, `resetExcept(...)` | `reset(vararg)`, `resetExcept(vararg)` | M1 | done |
| PRP-4 | `pull(...)` | `pull(...)` | M1 | done |
| PRP-5 | `only()` / `all()` | `only(...)`, `all()` | M1 | done |
| PRP-6 | Supported types (primitives, arrays, enums, dates, collections, Wireables, models) | see HYD-* | M1 | done |
| PRP-7 | `$wire.prop` read/write from JS; `$wire.$set`, `$wire.$get` | JS `$wire` proxy | M1 | done |
| PRP-8 | Public Kotlin `var` props and Java public fields both supported | `PropertyAccessor` (getter/setter or field) | M1 | done |

## Actions

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| ACT-1 | `wire:click="method"`, params `wire:click="m(1, 'a')"` | same | M1 | done |
| ACT-2 | Dependency injection in action params (container-resolved trailing params) | Kona container params | M1 | done |
| ACT-3 | Return values to JS (`await $wire.method()`) | `effects.returns` | M1 | done |
| ACT-4 | Event listeners `wire:<event>` any DOM event + key modifiers (`.enter`, `.shift.enter`, `.ctrl`, `.cmd`, `.meta`, `.alt`, `.escape`, `.space`, `.tab`, `.up/.down/.left/.right`, `.page-up/down`, `.slash` …) | JS `wire:*` wildcard | M1 | done |
| ACT-5 | Event modifiers `.prevent .stop .window .document .outside .once .debounce(.Xms) .throttle(.Xms) .self .camel .dot .passive .capture` | same | M1 | done |
| ACT-6 | Third-party / custom events (`wire:custom-event`, `wire:trix-change`) | same | M1 | done |
| ACT-7 | Disabling inputs during form submit (`SupportDisablingFormsDuringRequest`) | `wire:submit` disables submit buttons/inputs | M2 | done |
| ACT-8 | `$refresh` / `$wire.$refresh()` | same | M1 | done |
| ACT-9 | `wire:confirm` (+ `.prompt`) | same | M2 | done |
| ACT-10 | Calling actions from Alpine (`$wire.save()`, params, return values) | same | M1 | done |
| ACT-11 | JavaScript actions (`#[Js]` methods, `$js.name`, `$this->js()`) | `@Js`, `js()` | M3 | done |
| ACT-12 | Magic actions `$set`, `$refresh`, `$toggle`, `$event` | same | M1 | done |
| ACT-12b | Magic action `$dispatch` | same | M3 | done |
| ACT-12c | Magic action `$parent` | same | M4 | done |
| ACT-13 | `skipRender()` / `#[Renderless]` / `.renderless` modifier | `skipRender()`, `@Renderless` | M1 | done |
| ACT-14 | `.async` modifier and `#[Async]` — parallel, non-blocking actions | `@Async`, `.async` | M3 | done |
| ACT-15 | `.preserve-scroll` (`SupportPreserveScroll`) | same | M3 | done |
| ACT-16 | Middleware applied to actions (persistent middleware) | see SEC-4 | M6 | done |
| ACT-17 | Protected/private methods not callable; `MethodNotFoundException` | same | M1 | done |
| ACT-18 | Request bundling: interactions queued per component, `wire:model.live` updates coalesced, actions interrupt/queue (v4 `SupportRequestInteractions`) | JS message bus | M1 | done |

## Forms & validation

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| FRM-1 | `wire:submit` (+ `.prevent` implicit) | same | M1 | done |
| FRM-2 | Form objects (`Livewire\Form`, `public PostForm $form`, `$this->form->validate()`, `->all()`, `->reset()`, `->pull()`, `->only()`, `->except()`, `->fill()`) | `Form` base class | M2 | done |
| FRM-3 | `wire:model.live`, `.blur`, `.change`, `.lazy`, `.debounce.Xms`, `.throttle.Xms`, `.number`, `.boolean`, `.fill`, `.self`, `.deep` | same | M1 | done |
| FRM-4 | Real-time validation on update (`#[Validate]` props validate on `updated`) | same | M2 | done |
| FRM-5 | Real-time saving (`updated()` hook) | lifecycle hooks | M1 | done |
| FRM-6 | Dirty indicators (`wire:dirty`, `.class`, `.attr`, `.remove`, targets) | same | M2 | done |
| FRM-7 | Custom form controls (`wire:model` on Blade components / `x-modelable`) | `x-modelable` + Crema components | M2 | done |
| FRM-8 | Input types: text, textarea, checkbox (bool + array), radio, select (single/multiple), number, date | JS `wire:model` value adapters | M1 | done |
| VAL-1 | `#[Validate('rules')]`, array syntax, custom `as:`, `message:`, `onUpdate: false`, `translate: false`, custom key | `@Validate(...)` | M2 | done |
| VAL-2 | `rules()`, `messages()`, `validationAttributes()` methods | same | M2 | done |
| VAL-3 | `$this->validate()`, `validate([...])`, `validateOnly()` | same, via Kona `Validator` | M2 | done |
| VAL-4 | Rule objects (`Rule::unique(...)->ignore()`) | Kona rule strings/objects | M2 | done |
| VAL-5 | `addError`, `resetErrorBag`, `resetValidation`, `getErrorBag`, `setErrorBag`, `withValidator` | same | M2 | done |
| VAL-6 | `$errors` in views (`@error`), errors persisted in `memo.errors` | Crema `errors` + memo | M2 | done |
| VAL-7 | `$wire.$errors` (JS: `has`, `first`, `get`, `all`, `clear`) / Alpine `$errors` magic | same | M2 | done |
| VAL-8 | ValidationException thrown in action → errors bag, no exception | same | M2 | done |
| VAL-9 | Testing: `assertHasErrors`, `assertHasNoErrors` with rule keys | Testable | M2 | done |

## Events

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| EVT-1 | `$this->dispatch('name', key: value)` | `dispatch(name, params)` | M3 | done |
| EVT-2 | `#[On('name')]`, dynamic names `#[On('post-updated.{post.id}')]`, multiple | `@On` | M3 | done |
| EVT-3 | `->to(Component::class / name)`, `->self()`, `->component()` | fluent `Dispatch` | M3 | done |
| EVT-4 | `$dispatch` / `$dispatchTo` / `$dispatchSelf` from templates & Alpine | same | M3 | done |
| EVT-5 | Listening for events from specific children `@post-saved="..."` on `<livewire:child>` | `SupportNestedComponentListeners` | M4 | done |
| EVT-6 | JS: `Livewire.on`, `Livewire.dispatch`, `Livewire.dispatchTo`, `$wire.$on`, `$wire.dispatch*`, `this.$on` in component scripts | same on `Wire` global | M3 | done |
| EVT-7 | Alpine `x-on:name.window` receives Livewire events (browser `CustomEvent`) | same | M3 | done |
| EVT-8 | Echo events `#[On('echo:channel,Event')]`, private/presence, `broadcastAs` | Kona broadcasting adapter | M3 | done |
| EVT-9 | Testing `assertDispatched` / `assertNotDispatched` with param matchers, `dispatch()` in tests | Testable | M3 | done |
| EVT-10 | `getListeners()` / `$listeners` legacy | `listeners()` override | M3 | done |

## Lifecycle hooks

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| LCH-1 | `mount(...)` with route/props params | same | M1 | done |
| LCH-2 | `boot()` every request | same | M1 | done |
| LCH-3 | `updating`, `updated`, `updatingFoo`, `updatedFoo`, array keys (`updatedFoo($value, $key)`) | same | M1 | done |
| LCH-4 | `hydrate`, `dehydrate`, `hydrateFoo`, `dehydrateFoo` | same | M1 | done |
| LCH-5 | `rendering($view, $data)`, `rendered($view, $html)` | same | M1 | done |
| LCH-6 | `exception($e, $stopPropagation)` | same | M1 | done |
| LCH-7 | Trait hooks (`bootWithX`, `mountWithX`, `hydrateWithX`, `updatingWithX` …) | interface-mixin hooks (`boot<Interface>`) | M1 | done |
| LCH-8 | Hooks inside form objects | `Form` hooks | M2 | done |

## Nesting

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| NST-1 | Nesting child components, children tracked in `memo.children` (tag + id), skipped re-render when parent re-renders | same | M4 | done |
| NST-2 | Loops with `wire:key` / `:key`, compiled deterministic keys | same | M4 | done |
| NST-3 | `#[Reactive]` props | `@Reactive` | M4 | done |
| NST-4 | `wire:model` on child + `#[Modelable]` | `@Modelable` | M4 | done |
| NST-5 | Slots: default + named `<livewire:slot name="x">`, `$slots->has()`, slots evaluated in parent context, slot morphing | `<wire:slot>` | M4 | done |
| NST-6 | Forwarding HTML attributes `$attributes` (`SupportBladeAttributes`) | `attributes` bag | M4 | done |
| NST-7 | `$parent` access from child (`SupportAccessingParent`) | `$parent` magic | M4 | done |
| NST-8 | Dynamic components `<livewire:dynamic-component :is="$name" />` / `:component` | `<wire:dynamic-component>` | M4 | done |
| NST-9 | Recursive components | same | M4 | done |
| NST-10 | Forcing child re-render (changing key) | same | M4 | done |
| NST-11 | `#[Isolate]` (no bundling) | `@Isolate` | M4 | done |

## Lazy, defer, islands, teleport

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| LZY-1 | `<livewire:x lazy />`, `#[Lazy]`, `defer` / `#[Defer]` | same | M4 | done |
| LZY-2 | `placeholder()` method, `@placeholder` directive, default placeholder view config | same | M4 | done |
| LZY-3 | Bundling lazy loads (`lazy.bundle`, `#[Lazy(bundle: true)]`) | same | M4 | done |
| LZY-4 | Lazy/defer props passing | same | M4 | done |
| LZY-5 | Full-page lazy/defer, `lazy: false` to disable | same | M5 | done |
| LZY-6 | `Livewire::withoutLazyLoading()` in tests | `Wire.withoutLazyLoading()` | M4 | done |
| ISL-1 | `@island … @endisland` (anonymous + `name:`) | `@island` Crema block | M4 | done |
| ISL-2 | `lazy: true`, `defer: true`, `@placeholder` within island | same | M4 | done |
| ISL-3 | `wire:island="name"` targeting, `.append` / `.prepend` modes | same | M4 | done |
| ISL-4 | Nested islands, `always: true`, `skip: true` | same | M4 | done |
| ISL-5 | Island polling (`wire:poll` inside island) | same | M4 | done |
| ISL-6 | Triggering islands from JS (`$wire.$island('name')` / `.island` modifier) | same | M4 | done |
| TLP-1 | `@teleport('selector') … @endteleport` | same | M4 | done |

## Pages, navigation, URL, session, pagination

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| PAG-1 | `Route::livewire('/path', 'name')` full-page components | `router.brew("/path", "name")` / `Wire.route` | M5 | done |
| PAG-2 | Layouts: default `layouts::app`, `#[Layout]`, `->layout()`, `{{ $slot }}` | `@Layout`, layout view with `slot` | M5 | done |
| PAG-3 | `#[Title]`, `->title()` | `@Title` | M5 | done |
| PAG-4 | Additional layout slots (`->slot('x')` / `<x-slot>` from page) | `View.slot(name)` | M5 | done |
| PAG-5 | Route params → mount params, route model binding | route params; Cadence binding | M5 | done |
| NAV-1 | `wire:navigate` SPA visits, `.hover` prefetch, redirects with `navigate: true` | same | M5 | done |
| NAV-2 | `@persist('key')` elements across visits | same | M5 | done |
| NAV-3 | `wire:current` (`.exact`, `.strict`) + `data-current` | same | M5 | done |
| NAV-4 | Scroll preservation `wire:navigate:scroll`, `wire:scroll` | same | M5 | done |
| NAV-5 | JS hooks `livewire:navigate`, `navigating`, `navigated`; `Livewire.navigate(url)` | `brew:*` events, `Wire.navigate` | M5 | done |
| NAV-6 | View transitions (`wire:transition` on navigate, `livewire.navigate.view_transitions`) | same | M5 | done |
| NAV-7 | Script evaluation rules (head scripts once, `data-navigate-track`, `data-navigate-once`, body scripts re-run) | same | M5 | done |
| NAV-8 | Progress bar (`navigate.show_progress_bar`, `progress_bar_color`) | built-in bar | M5 | done |
| URL-1 | `#[Url]` (`as:`, `except:`, `keep:`, `history:`, nullable init) | `@Url` | M5 | done |
| URL-2 | `queryString()` method + trait hooks (`queryStringWithX`) | same | M5 | done |
| URL-3 | Testing `withQueryParams` | Testable | M5 | done |
| SES-1 | `#[Session]` props (+ `key:` with dynamic segments) | `@Session` | M5 | done |
| PGN-1 | `WithPagination` (`page` in URL, `previousPage`, `nextPage`, `gotoPage`, `resetPage`, `setPage`) | `WithPagination` interface | M5 | done |
| PGN-2 | `WithoutUrlPagination`, multiple paginators (`pageName`), scroll behaviour (`scrollTo`) | same | M5 | done |
| PGN-3 | Hooks `updatingPage`, `updatedPage`, `updatingPaginators` … | same | M5 | done |
| PGN-4 | Simple + cursor pagination, Tailwind/Bootstrap themes, custom pagination views (`paginationView()`) | Cadence paginators + Crema views | M5 | done |

## Directives (`wire:*`)

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| DIR-1 | `wire:click` | same | M1 | done |
| DIR-2 | `wire:submit` | same | M1 | done |
| DIR-3 | `wire:model` (all modifiers, nested paths) | same | M1 | done |
| DIR-4 | `wire:loading` (`.class`, `.attr`, `.remove`, `.delay.*`, `.flex/.grid/.inline…`, `wire:target` incl. params/props/`except`) | same | M2 | done |
| DIR-5 | `data-loading` attribute on request-triggering elements (`SupportDataLoading`) | same | M2 | done |
| DIR-6 | `wire:navigate` | same | M5 | done |
| DIR-7 | `wire:current` | same | M5 | done |
| DIR-8 | `wire:cloak` | same | M2 | done |
| DIR-9 | `wire:dirty` | same | M2 | done |
| DIR-10 | `wire:confirm` | same | M2 | done |
| DIR-11 | `wire:transition` + `#[Transition]` (`type:`, `skip`) | same | M2 | done |
| DIR-12 | `wire:init` | same | M1 | done |
| DIR-13 | `wire:intersect` (`.once`, `.half`, `.full`, `.margin`, `:enter`, `:leave`) | same | M6 | done |
| DIR-14 | `wire:poll` (`.Xs`, `.keep-alive`, `.visible`) | same | M2 | done |
| DIR-15 | `wire:offline` (`.class`, `.attr`, `.remove`) | same | M2 | done |
| DIR-16 | `wire:ignore` (`.self`, `.children`) | same | M1 | done |
| DIR-17 | `wire:ref` + `$refs` / `$wire.$refs` / `->ref()` targeting events & streams | same | M2 | done |
| DIR-18 | `wire:replace` (`.self`) | same (shipped with the M1 morph) | M2 | done |
| DIR-19 | `wire:show` (+ `.transition`) | same | M2 | done |
| DIR-20 | `wire:sort` (`wire:sort:item`, `:handle`, `:group`, `:ignore`) | same | M6 | done |
| DIR-21 | `wire:stream` (`.replace`) + `$this->stream()` | same | M3 | done |
| DIR-22 | `wire:text` | same | M2 | done |
| DIR-23 | `wire:bind:attr` | same | M2 | done |
| DIR-24 | `wire:key` | same | M1 | done |
| DIR-25 | `wire:model` on any element with `x-modelable` | same | M2 | done |

## Attributes

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| ATR-1 | `#[Async]` | `@Async` | M3 | done |
| ATR-2 | `#[Computed]` (`persist:`, `seconds:`, `cache:`, `key:`, `tags:`), `unset($this->prop)` | `@Computed` + `forgetComputed()` | M1 | done |
| ATR-3 | `#[Defer]` | `@Defer` | M4 | done |
| ATR-4 | `#[Isolate]` | `@Isolate` | M4 | done |
| ATR-5 | `#[Js]` | `@Js` | M3 | done |
| ATR-6 | `#[Json]` — method returns JSON to JS, renderless, rejects on validation error | `@Json` | M3 | done |
| ATR-7 | `#[Layout]` | `@Layout` | M5 | done |
| ATR-8 | `#[Lazy]` | `@Lazy` | M4 | done |
| ATR-9 | `#[Locked]` | `@Locked` | M1 | done |
| ATR-10 | `#[Modelable]` | `@Modelable` | M4 | done |
| ATR-11 | `#[On]` | `@On` | M3 | done |
| ATR-12 | `#[Reactive]` | `@Reactive` | M4 | done |
| ATR-13 | `#[Renderless]` | `@Renderless` | M1 | done |
| ATR-14 | `#[Session]` | `@Session` | M5 | done |
| ATR-15 | `#[Title]` | `@Title` | M5 | done |
| ATR-16 | `#[Transition]` | `@Transition` | M2 | done |
| ATR-17 | `#[Url]` | `@Url` | M5 | done |
| ATR-18 | `#[Validate]` (+ deprecated `#[Rule]`) | `@Validate`, `@Rule` alias | M2 | done |
| ATR-19 | `#[Authorize]` | `@Authorize` | M6 | done |

## Crema (Blade) directives & assets

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| BLD-1 | `@livewireStyles`, `@livewireScripts`, `@livewireScriptConfig`, auto-injection when absent (`inject_assets`) | `@wireStyles`, `@wireScripts`, `@wireScriptConfig`, auto-inject | M1 | done |
| BLD-2 | `@island` | see ISL-* | M4 | done |
| BLD-3 | `@placeholder` | see LZY-2 | M4 | done |
| BLD-4 | `@persist` | see NAV-2 | M5 | done |
| BLD-5 | `@teleport` | see TLP-1 | M4 | done |
| BLD-6 | `@script … @endscript` component scripts (run once per component init, `$wire` available, `this.$js`, `$intercept`) | same | M3 | done |
| BLD-7 | `@assets … @endassets` (loaded once per page) | same | M3 | done |
| BLD-8 | `@js($value)` | `@js(...)` | M1 | done |
| BLD-9 | `@entangle('prop')` / `$wire.entangle()` (`.live`) | same | M3 | done |
| BLD-10 | `@this` (legacy `$wire` alias) | `@this` | M3 | done |
| BLD-11 | Morph markers `<!--[if BLOCK]><![endif]-->` around conditionals/loops (`inject_morph_markers`) | Crema `MORPH_MARKERS` | M1 | done |
| BLD-12 | Asset routes `/livewire/livewire.js` (+ `.min.js`, `.js.map`), cache-busting `?id=` | `/wire/brew.js` (+ `.min.js`, `.map`) | M1 | done |
| BLD-12b | CSP asset route `livewire.csp.min.js` | `/wire/brew.csp.min.js` | M6 | done |
| STY-1 | Scoped component styles (`<style>` in SFC, `name.css` in MFC), `:scope` root targeting | same | M6 | done |
| STY-2 | Global styles (`<style global>` / `name.global.css`), deduplication | same | M6 | done |
| STY-3 | CSS modules (`SupportCssModules`) | same | M6 | done |
| JSM-1 | Component JS modules (`<script>` in SFC → `name.js` in MFC, `this.$wire`) (`SupportJsModules`, `SupportJsComponent`) | same | M6 | done |

## JavaScript runtime

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| JS-1 | Bundled Alpine (+ morph, intersect, collapse, focus, persist, anchor, mask, sort, resize), `window.Alpine`, `window.Livewire` | `window.Wire`, Alpine + plugins | M1 | done |
| JS-2 | ESM build for manual bundling (`livewire.esm`, `Livewire.start()`) | `@konaframework/wire` ESM + `Wire.start()` | M1 | done |
| JS-3 | `$wire` core: props, methods, `$get`, `$set(prop, value, live)` (deep paths), `$toggle`, `$call`, `$refresh`, `$commit`, `$el`, `$id`, `$watch`, `$hook` | `$wire` | M1 | done |
| JS-3b | `$wire.$errors`, `$wire.$refs` | `$wire` | M2 | done |
| JS-3c | `$wire.$entangle`, `$on`, `$dispatch*`, `$js`, `$intercept` | `$wire` | M3 | done |
| JS-3d | `$wire.$parent`, `$island` | `$wire` | M4 | done |
| JS-3e | `$wire.$upload*` | `$wire` | M6 | done |
| JS-4 | `Livewire.find(id)`, `first()`, `getByName(name)`, `all()` | same | M1 | done |
| JS-5 | `Livewire.hook(...)`: `component.init`, `element.init`, `morph.updating/updated/removing/removed/adding/added`, `morph`, `commit`, `request`, `effect` | same | M1 | done |
| JS-6 | `Livewire.directive(name, fn)` custom `wire:` directives | same | M1 | done |
| JS-7 | Interceptors: `Livewire.interceptAction`, `interceptMessage`, `interceptRequest`, `$wire.$intercept` (onSend, onSuccess, onError, onFailure, onCancel, onFinish, `abort`) | same | M3 | done |
| JS-8 | Global events `livewire:init`, `livewire:initialized` | `brew:init`, `brew:initialized` | M1 | done |
| JS-9 | Server-side JS evaluation `$this->js('...')` / `xjs` effect | `js()` | M3 | done |
| JS-10 | Error handling: 419 page-expired prompt, 500 modal (debug), `onError` | same | M1 | done |
| JS-11 | Offline detection | see DIR-15 | M2 | done |
| JS-12 | Morph algorithm: keyed (wire:key/id) Alpine morph, block markers, `wire:ignore`, `wire:replace`, lookahead | Alpine morph + Wire hooks | M1 | done |
| JS-13 | File downloads effect | M3 | done |
| CSP-1 | CSP-safe build (`livewire.csp_safe`, Alpine CSP build, no `eval`) | `brew.csp.min.js` | M6 | done |

## Uploads

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| UPL-1 | `WithFileUploads`, `wire:model` on `<input type=file>` single + `multiple` | `WithFileUploads` | M6 | done |
| UPL-2 | Temporary uploads (signed upload URL, temp dir, `TemporaryUploadedFile`), `store`, `storeAs`, `storePublicly` | `TemporaryUploadedFile` via Kona Storage | M6 | done |
| UPL-3 | Validation (`max`, `mimes`, `image`, global `temporary_file_upload.rules`) | same | M6 | done |
| UPL-4 | Preview URLs (`temporaryUrl()`, `preview_mimes`) | same | M6 | done |
| UPL-5 | Loading/progress events (`livewire-upload-start/finish/error/cancel/progress`), cancel | `brew-upload-*` | M6 | done |
| UPL-6 | JS upload API (`$wire.$upload`, `$uploadMultiple`, `$cancelUpload`, `$removeUpload`) | same | M6 | done |
| UPL-7 | S3 direct upload (pre-signed), cleanup of old temp files | Kona Storage S3 driver + cleanup | M6 | done |
| UPL-8 | Testing uploads (`UploadedFile::fake()`) | `UploadedFile.fake` in Testable | M6 | done |
| UPL-9 | Upload middleware / throttle config | config | M6 | done |

## Redirects, downloads, streaming

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| RDR-1 | `$this->redirect(url, navigate:)`, `redirectRoute`, `redirectIntended`, `redirectAction` | same | M3 | done |
| RDR-2 | Redirect to full-page component (`redirect(Component::class)`) | same | M5 | done |
| RDR-3 | Flash messages persist across redirect | Kona session flash | M3 | done |
| DWN-1 | Return download response from action (`response()->download`, `streamDownload`) | `Response` download effect | M3 | done |
| DWN-2 | `assertFileDownloaded` | Testable | M3 | done |
| STR-1 | `$this->stream(to:, content:, replace:)` streaming responses | chunked HTTP | M3 | done |

## Testing

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| TST-1 | `Livewire::test(name|class, params)`, `->set`, `->call`, `->toggle`, `->refresh` | `Wire.test(...)` | M1 | done |
| TST-2 | `assertSet`, `assertNotSet`, `assertCount`, `assertSee`, `assertDontSee`, `assertSeeHtml`, `assertDontSeeHtml`, `assertSeeInOrder`, `assertViewHas`, `assertViewIs` | same | M1 | done |
| TST-3 | `assertRenderSkipped`, `assertRenderNotSkipped`, `assertStatus`, `assertForbidden`, `assertUnauthorized` | same | M1 | done |
| TST-3b | Status assertions driven by `#[Authorize]` / `authorize()` failures | same | M6 | done |
| TST-4 | `actingAs`, `withQueryParams`, `withCookie(s)`, `withHeaders`, `withoutLazyLoading` | same | M5/M6 | done |
| TST-5 | `assertJs`, `assertNoJs`, `assertRedirect*`, `assertNoRedirect`, `assertDispatched*`, `assertHasErrors*`, `assertFileDownloaded` | same | M2/M3 | done |
| TST-6 | Page contains component (`assertSeeLivewire`, `assertDontSeeLivewire`) | `assertSeeBrew` on Kona `HttpTestClient` | M5 | done |
| TST-7 | Browser testing (Pest browser) | Playwright smoke suite for JS runtime | M7 | done |

## Developer experience

| ID | Livewire v4 surface | Wire | M | Status |
|----|--------------------|------|---|--------|
| DX-1 | `make:livewire` class + single-file components | `./grind make:wire` | M1 | done |
| DX-1b | `make:livewire` all flags (`--mfc`, `--class`, `--test`, `--emoji`, `pages::`) | `./grind make:wire` | M7 | done |
| DX-2 | `livewire:convert` (SFC ↔ MFC ↔ class) | `./grind wire:convert` | M7 | done |
| DX-3 | `livewire:publish --config`, `livewire:stubs`, `livewire:layout` | `wire:publish`, `wire:stubs`, `wire:layout` | M7 | done |
| DX-4 | Config core keys (component locations/namespaces, inject_assets, inject_morph_markers, payload) | `config/brew.*` | M1 | done |
| DX-4b | Remaining config keys (layout, lazy placeholder, temp uploads, render_on_redirect, legacy_model_binding, navigate, smart_wire_keys, release_token, csp_safe) | `config/brew.*` | per feature (M4–M6) | done |
| DX-5 | Package development (register components/namespaces from packages) | `WireServiceProvider` hooks | M6 | done |
| DX-6 | IDE support: Kona Idea / LSP completions for `<wire:…>`, `wire:*` | LSP contract notes | M7 | done |
| DX-7 | Docs for the M1 slice (`/docs/wire/*`: installation, components, properties, actions, templates, lifecycle, testing, JS runtime) | apps/docs in Kona | M1 | done |
| DX-7b | Docs pages for every remaining topic in the Livewire v4 nav | apps/docs in Kona | each M (M2–M7) | done |
| DX-8 | Starter kit `kona new --kit=wire` uses kona-wire | Kona CLI | M1 cutover | done |
| PERF-1 | Hot paths benchmarked (mount, update round-trip, morph), per-class metadata cached, no per-request reflection scans | `:kona-wire:bench`, `js/wire` `npm run bench`, [baseline](../performance/brew-baseline.md) | M1 | done |
| PERF-1b | Performance report (Chromium + concurrent load) | report | M7 | done |
