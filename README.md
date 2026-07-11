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

## What's here

Two modules:

**`navigatorkit-core`** — a **pure JVM module** (no Android SDK needed) built
on Compose's multiplatform `runtime` artifact, so the state tree is
snapshot-backed and Compose-observable while every test runs headlessly with
`./gradlew :navigatorkit-core:test`.

**`navigatorkit-compose`** — the Android library that renders the tree as
real Compose UI and wires system back. It builds to an AAR
(`./gradlew :navigatorkit-compose:assembleDebug`).

| Piece | Module | Status |
|---|---|---|
| `Route` + polymorphic serialization (`@SerialName` = stable type id) | core | ✅ |
| State tree: `SceneNavigator`, `NavigationContext`, `TabsLayout`, `SplitLayout` | core | ✅ |
| `navigationIntent { }` DSL + `RoutePlacement` semantics | core | ✅ |
| `IntentResolver` (Planner semantics, single-pass) | core | ✅ |
| Deep links: `deepLinkMap { }`, `UrlPattern`, typed params | core | ✅ |
| System back: `scene.goBack()` / `canGoBack()` | core | ✅ |
| Restoration: lossy versioned snapshots (`NavigationSnapshotCoder`) | core | ✅ |
| Ported test matrix | core | ✅ 38 tests |
| `Navigator` facade + `LocalNavigator` | compose | ✅ |
| `DestinationRegistry` / `RoutableFeature` (route → `@Composable`) | compose | ✅ |
| `RoutedScene` / `RoutedRoot` / `RoutedStack` (Navigation-3-style renderer) | compose | ✅ |
| Tab bar, responsive list-detail split | compose | ✅ |
| `ModalBottomSheet` / full-screen / `AlertDialog` binding (recursive) | compose | ✅ |
| Predictive/system back via `BackHandler` | compose | ✅ |
| `sample-app`: tabs + split + nested sheets + deep links (installable APK) | sample | ✅ |
| Multi-window scene coordination | — | ⏳ next (see roadmap) |

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

## Rendering the tree (compose module)

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val registry = destinationRegistry {
            feature(ProductsFeature)
            feature(ReviewsFeature)
            feature(SettingsFeature)
        }
        val scene = ShopBlueprint.newScene()          // per-window state tree
        val navigator = Navigator(scene, registry)

        // Deep links (App Links / custom scheme / notification intents):
        intent?.data?.let { uri ->
            deepLinks.intentFor(uri.toString())?.let(navigator::perform)
        }

        setContent {
            MaterialTheme { RoutedScene(navigator) }    // renders + wires back
        }
    }
}
```

A feature registers destinations knowing nothing about the app shell:

```kotlin
object ProductsFeature : RoutableFeature {
    override fun destinations(builder: DestinationRegistryBuilder) = with(builder) {
        destination<ProductRoute.List> { ProductListScreen() }
        destination<ProductRoute.Detail> { ProductDetailScreen(it.id) }
    }
}
```

And a screen sees only the navigator:

```kotlin
@Composable
fun ProductDetailScreen(id: Int) {
    val navigator = LocalNavigator.current
    Button(onClick = { navigator.navigate(ReviewRoute.Compose(id)) }) { Text("Review") }
}
```

The renderer is Navigation-3 in spirit: `RoutedStack` treats the context's
`backStack` as the source of truth and animates push/pop with `AnimatedContent`.
No Navigation 3 dependency is pulled — the tree already *is* the back stack,
which is exactly what Nav3 asks you to own.

## Sample app

`sample-app` is a runnable single-activity app wiring the binding layer to
real screens: a Shop stack, a Settings list-detail split, a Playground tab
that fires the canonical hard intent (tab → stack → sheet → nested sheet →
alert), a review bottom sheet with a nested photo-picker sheet and a discard
confirmation dialog, and `shopapp://` deep links declared in the manifest.
Four `RoutableFeature` objects register their destinations independently.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :navigatorkit-core:test          # headless, no emulator
./gradlew :navigatorkit-compose:assembleDebug   # the UI library AAR
./gradlew :sample-app:assembleDebug        # installable APK

# On a device/emulator:
adb install sample-app/build/outputs/apk/debug/sample-app-debug.apk
adb shell am start -a android.intent.action.VIEW -d "shopapp://products/42/review"
```

`sample-app/src/debug/.../Previews.kt` holds `@Preview` composables of the
Shop list, product detail, Playground, and the wide settings split for
Android Studio's preview pane.

## Roadmap

1. Scene coordination for multi-window (tablets, DeX, desktop windowing) —
   the `AppNavigator` + scene-policy counterpart.
2. `rememberSaveable` / `SavedStateHandle` glue so restoration is automatic
   rather than manual `encode` / `decodeAndRestore` calls.
3. An instrumentation/screenshot smoke test once the Compose preview
   screenshot plugin supports AGP's built-in Kotlin compilation.
