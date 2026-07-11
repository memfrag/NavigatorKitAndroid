# NavigatorKit for Android

The Android counterpart of [NavigatorKit](https://github.com/memfrag/NavigatorKit)
(Swift/SwiftUI): programmatic, deeply-linkable navigation across tabs, stacks,
list-detail splits, bottom sheets, full-screen modals, and dialogs — driven by
one declarative intent:

```kotlin
IntentResolver.resolve(
    navigationIntent {
        selectTab(AppTab.Shop)
        setStack(ProductRoute.List, ProductRoute.Detail(42))
        present(ReviewRoute.Compose(42), PresentationStyle.BottomSheet())
        push(ReviewRoute.PhotoPicker)
        alert("Arrived!")
    },
    scene,
)
```

Both packages implement **the same navigation contract** — same intent
vocabulary, same dismissal semantics, same URL pattern grammar, same
restoration behavior — verified by the same test matrix, ported
test-for-test. A deep link spec written once drives both apps.

## What's here (v0 — the shared core)

`navigatorkit-core` is a **pure JVM module** (no Android SDK needed) built on
Compose's multiplatform `runtime` artifact, so the state tree is
snapshot-backed and Compose-observable while every test runs headlessly with
`./gradlew test`:

| Piece | Status |
|---|---|
| `Route` + polymorphic serialization (`@SerialName` = stable type id) | ✅ |
| State tree: `SceneNavigator`, `NavigationContext`, `TabsLayout`, `SplitLayout` | ✅ |
| `navigationIntent { }` DSL + `RoutePlacement` semantics | ✅ |
| `IntentResolver` (Planner semantics, single-pass) | ✅ |
| Deep links: `deepLinkMap { }`, `UrlPattern`, typed params | ✅ |
| System back: `scene.goBack()` / `canGoBack()` | ✅ |
| Restoration: lossy versioned snapshots (`NavigationSnapshotCoder`) | ✅ |
| Ported test matrix | ✅ 38 tests |
| `navigatorkit-compose` UI module | ⏳ next (see roadmap) |

## Where the platforms differ — by design

**No staged executor.** The Swift package needs a Planner → stages →
transition-awaiting executor because SwiftUI cannot materialize nested
presentations from one state mutation. Compose can: it recomposes from
whatever tree you hand it. So `IntentResolver` applies an intent in a single
pass — but preserves the Planner's *semantics* verbatim (cursor walk,
dismissal rules, activate, sibling-tab invariant), because those are contract,
not workaround. Notably: switching tabs still dismisses the outgoing tab's
presentations even though Compose wouldn't force it — same intent, same end
state, both platforms.

**System back is Android-only.** One rule owned by the tree instead of
scattered `BackHandler`s: dismiss overlay → pop the deepest visible stack →
dismiss the empty presentation → back-to-first-tab (optional) → hand off to
the system. Wire it up once:

```kotlin
BackHandler(enabled = scene.canGoBack()) { scene.goBack() }
```

**No `AnyRoute`.** Kotlin's polymorphic serialization erases and restores
heterogeneous route types natively; `routeSerializersModule { }` is the
decoding registry (Swift's `RouteTypeRegistry`), and `@SerialName` is the
stable type id (Swift's `routeTypeID`).

## Defining routes

```kotlin
@Serializable
sealed interface ProductRoute : Route {
    @Serializable @SerialName("product.list")   data object List : ProductRoute
    @Serializable @SerialName("product.detail") data class Detail(val id: Int) : ProductRoute
}

val routesJson = Json {
    serializersModule = routeSerializersModule {
        subclass(ProductRoute.List::class)
        subclass(ProductRoute.Detail::class)
    }
}
```

## Deep links

```kotlin
val deepLinks = deepLinkMap {
    pattern("/products/:id") { params ->
        navigationIntent {
            selectTab(AppTab.Shop)
            setStack(ProductRoute.List, ProductRoute.Detail(params.int("id")))
        }
    }
    pattern("shopapp://settings/**") { navigationIntent { selectTab(AppTab.Settings) } }
}

// Activity.onNewIntent / App Links / notification PendingIntents:
deepLinks.intentFor(uri)?.let { IntentResolver.resolve(it, scene) }
```

Same grammar as iOS: `:name` typed throwing capture, `*` one component,
trailing `**` the rest; custom-scheme hosts fold into the path so one pattern
serves schemes and App Links; most-specific match wins; a throwing handler
falls through.

## Restoration

```kotlin
// Persist (e.g. SavedStateHandle / onSaveInstanceState):
val data = NavigationSnapshotCoder.encode(scene, routesJson)

// Restore into a blueprint-fresh tree:
val report = NavigationSnapshotCoder.decodeAndRestore(scene, data, routesJson)
```

Unknown route types truncate the path before them and drop presentations
stacked above — restoration is lossy, never brittle. Snapshots are versioned.

## Building

Pure JVM; any JDK 17+ works (Android Studio's bundled JBR is fine):

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew test
```

## Roadmap

1. **`navigatorkit-compose`** (Android library): `RoutedNavDisplay` binding
   the tree to Navigation 3's `NavDisplay`, `NavigationSuiteScaffold` tab
   root, `NavigableListDetailPaneScaffold` split root, `ModalBottomSheet` /
   dialog binding, predictive-back integration, per-feature `entryProvider`
   registration via DI multibindings (the `DestinationRegistry` counterpart).
2. Scene coordination for multi-window (tablets, DeX, desktop windowing).
3. Sample app mirroring ShopExample, driven by the same deep link table.
