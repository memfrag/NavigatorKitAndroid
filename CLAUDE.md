# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**NavigatorKit for Android** — programmatic navigation for Jetpack Compose. One declarative
`NavigationIntent` expresses a destination across the whole container hierarchy at once: tab
selection → back stack → list-detail split → bottom sheets/full-screen (recursively) →
alerts/dialogs. Also does deep links (URL-pattern DSL), system/predictive back, and lossy state
restoration. **Android 7+ (`minSdk 24`), built against Android 16 (`compileSdk 36`), Kotlin 2.2.**

There is a sibling repo, **NavigatorKit** (Swift/SwiftUI), that implements the *same navigation
contract* — intent vocabulary, dismissal semantics, URL grammar, test scenarios. There is **no code
sharing**; parity is maintained by hand. A change to the contract (a new placement, a dismissal
rule, URL syntax) must be mirrored in both repos and both test suites. The two overview pages
(`docs/`) and READMEs cross-link and must stay consistent.

## Commands

Needs a JDK 17+. Android Studio's bundled JBR is the known-good one here:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew :navigatorkit-core:test              # core logic, pure JVM, no emulator (~38 tests)
./gradlew :navigatorkit-compose:testDebugUnitTest   # compose facade, pure JVM (no Robolectric)
./gradlew :navigatorkit-compose:assembleDebug  # the UI library AAR
./gradlew :sample-app:assembleDebug            # installable sample APK

# run one test class / method:
./gradlew :navigatorkit-core:test --tests "*IntentResolverTest"
./gradlew :navigatorkit-core:test --tests "*IntentResolverTest.canonical hard intent end state"
```

**Two test frameworks, on purpose:** `navigatorkit-core` uses JUnit 5 (`useJUnitPlatform()`);
`navigatorkit-compose` uses JUnit 4 (`junit:junit`). Both run on the plain JVM — the compose facade
tests need no Robolectric because `Navigator`, the resolver, and the snapshot-backed tree are pure
Kotlin.

The Gradle wrapper isn't checked in freshly for every clone; if `./gradlew` is missing, generate it
from a cached distribution (`~/.gradle/wrapper/dists/gradle-8.14.3-*/.../bin/gradle wrapper`). Plugin
versions are declared once in the root `build.gradle.kts` (`apply false`); modules apply them without
versions. `android.useAndroidX=true` in `gradle.properties` is required.

## Architecture

Three modules: **navigatorkit-core** (pure JVM logic), **navigatorkit-compose** (Android library, UI
binding), **sample-app** (demo application). The governing idea, same as the iOS sibling: **push all
navigation logic below the Compose seam so it's headless-testable**, and make the state tree the
single source of truth that Compose merely binds to.

### `navigatorkit-core` (`src/main/kotlin/io/github/memfrag/navigatorkit/`)

- **`Route.kt`** — `Route` is a marker interface. There is **no `AnyRoute`**: kotlinx polymorphic
  serialization handles type erasure *and* the decoding registry natively. `@SerialName` is the
  stable type id; `routeSerializersModule { }` builds the registry used for restoration.
- **`state/`** — the recursive tree of **snapshot-backed** classes (`mutableStateOf` /
  `SnapshotStateList` from compose-runtime, so Compose observes them). `NavigationContext` is the
  node: a `backStack: SnapshotStateList<Route>` plus optional `sheet`/`fullScreen`
  (`PresentedContext` whose content is *another* `NavigationContext` — the recursion) plus
  `alert`/`dialog`. Above it: `RootLayout` (`Stack`/`Tabs`/`Split`), `TabsLayout`, `SplitLayout`
  (sidebar = a *selection*, detail = a full context), `SceneNavigator` (one per window).
- **`intent/` + `resolver/`** — the crux; read before changing navigation behavior. A
  `NavigationIntent` is an **ordered list of `NavigationOperation`s** applied to a *moving cursor*
  (`selectTab` retargets it, `present` descends into the new child). `IntentResolver.resolve()`
  applies the whole intent in **one synchronous pass** — this is the key difference from the iOS
  package, which needs a staged executor because SwiftUI can't batch nested presentations. Compose
  recomposes from whatever tree you hand it, so no staging, no coordinator, no awaiting. But the
  *semantics* are preserved verbatim: the cursor walk, the dismissal rules, and the guardrail that
  `dismiss`/`dismissAll`/`activate` **may not follow `present`** in one intent (throws
  `NavigationException.InvalidOperation`). Canonical order: tear down → select/set → present →
  push/overlay.
- **`back/`** — `SceneNavigator.goBack()` / `canGoBack()`. System/predictive back is the one
  navigation concern Android has that iOS doesn't; it's resolved by one tree-owned rule,
  deepest-first (overlay → pop deepest stack → dismiss empty presentation → back-to-first-tab →
  hand off to the system). Wire it up once with `BackHandler(enabled = scene.canGoBack()) { scene.goBack() }`.
- **`deeplink/`** — `deepLinkMap { }` / `UrlPattern` DSL (`URI → NavigationIntent?`, pure and
  unit-testable; custom-scheme host folds into the path so one pattern serves schemes and App Links).
- **`restore/`** — `NavigationSnapshotCoder`: a `@Serializable` value mirror of the tree, decoded
  leniently (unknown/renamed route types truncate the path rather than failing the whole restore),
  versioned.

### `navigatorkit-compose` (`.../compose/`)

The SwiftUI-analogue binding. `RoutedStack` renders a `NavigationContext`'s `backStack` with
`AnimatedContent` — Navigation-3-*style* (the list is the source of truth) but with **no Navigation 3
dependency**. `Presentations.kt`'s `PresentationHost` installs `ModalBottomSheet` / full-screen /
`AlertDialog` **recursively** at every level (that's how nested sheets work). `RoutedRoot` handles
tabs (`NavigationBar`) and the responsive list-detail split. `RoutedScene` provides the composition
locals and wires `BackHandler` to `goBack()`. `Navigator` is the facade views consume via
`LocalNavigator` — a concrete class, not a protocol; nothing to mock (see `Navigator.testable(...)`
in its companion and the README's "Previewing and testing composables"). `DestinationRegistry` /
`RoutableFeature` give feature decoupling (a feature maps routes → composables knowing nothing about
the app shell or siblings).

### Dismissal contract (invariants to preserve)

`navigate(route, placement)` resolves placement as: explicit arg → the route type's registered
default → `Push`. `dismiss()` closes the topmost presentation, `dismissAll()` unwinds to the base,
`pop()`/`popToRoot()` walk the stack. Invariants the resolver enforces: sibling tabs' **back stacks**
are never mutated by navigation; switching tabs dismisses the outgoing tab's presentations; mutating
a stack under a presentation dismisses the presentation first.

## Conventions

- When adding a `NavigationOperation`, thread it through: the enum
  (`intent/NavigationIntent.kt`, `NavigationOperation`), the `navigationIntent { }` builder, the
  `IntentResolver`, the `Navigator` facade if it warrants a convenience — plus an `IntentResolverTest`
  case in core.
- The `maven-publish` plugin on the two library modules is **not** for publishing anywhere — it
  declares the module's `io.github.memfrag.navigatorkit` coordinates so consumers can depend on it
  straight from git via Gradle `sourceControl`. Don't remove it.
- Releases are bare-SemVer git tags (`1.0.0`); module `version` in the two library build files must
  match the tag.
- Git: don't self-credit in commits.
