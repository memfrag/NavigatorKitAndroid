package io.github.memfrag.navigatorkit.compose

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.memfrag.navigatorkit.Route
import io.github.memfrag.navigatorkit.TabId
import io.github.memfrag.navigatorkit.back.canGoBack
import io.github.memfrag.navigatorkit.back.goBack
import io.github.memfrag.navigatorkit.intent.NavigationIntent
import io.github.memfrag.navigatorkit.intent.RoutePlacement
import io.github.memfrag.navigatorkit.resolver.IntentResolver
import io.github.memfrag.navigatorkit.resolver.NavigationException
import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.PresentationStyle
import io.github.memfrag.navigatorkit.state.RootLayout
import io.github.memfrag.navigatorkit.state.SceneNavigator

/**
 * The facade views use to navigate — the only routing API feature screens
 * ever see, provided via [LocalNavigator]. Mirrors the Swift package's
 * `Navigator`.
 *
 * Because Compose materializes any tree in a single recomposition, these are
 * synchronous state mutations — no staged executor, no suspending calls.
 *
 * ```kotlin
 * val navigator = LocalNavigator.current
 * Button(onClick = { navigator.navigate(ReviewRoute.Compose(id)) }) { Text("Review") }
 * Button(onClick = { navigator.dismiss() }) { Text("Done") }
 * ```
 */
@Stable
class Navigator(
    val scene: SceneNavigator,
    val registry: DestinationRegistry,
    /** Invoked when an intent can't be applied (e.g. `popTo` a missing route). */
    var onError: (NavigationException) -> Unit = { throw it },
) {
    /** Applies a compound intent. */
    fun perform(intent: NavigationIntent) {
        try {
            IntentResolver.resolve(intent, scene)
        } catch (e: NavigationException) {
            onError(e)
        }
    }

    /**
     * Navigates to a single route. Placement resolution: explicit argument →
     * the route type's registered default → [RoutePlacement.Push].
     */
    fun navigate(route: Route, placement: RoutePlacement? = null) {
        perform(
            NavigationIntent.navigate(
                route,
                placement ?: registry.defaultPlacement(route) ?: RoutePlacement.Push,
            )
        )
    }

    fun push(route: Route) = perform(NavigationIntent(listOf(op { push(route) })))
    fun pop() = perform(NavigationIntent(listOf(op { pop() })))
    fun popToRoot() = perform(NavigationIntent(listOf(op { popToRoot() })))
    fun present(route: Route, style: PresentationStyle = PresentationStyle.BottomSheet()) =
        perform(NavigationIntent(listOf(op { present(route, style) })))
    fun dismiss() = perform(NavigationIntent(listOf(op { dismiss() })))
    fun dismissAll() = perform(NavigationIntent(listOf(op { dismissAll() })))
    fun selectTab(tab: TabId) = perform(NavigationIntent(listOf(op { selectTab(tab) })))
    fun <T : Enum<T>> selectTab(tab: T) = perform(NavigationIntent(listOf(op { selectTab(tab) })))
    fun selectSidebar(route: Route?) = perform(NavigationIntent(listOf(op { selectSidebar(route) })))
    fun alert(title: String, message: String? = null) =
        perform(NavigationIntent(listOf(op { alert(title, message) })))

    /** Resolves a system-back event against the tree. */
    fun goBack(): Boolean = scene.goBack()

    /** Whether [goBack] would consume a back event right now. */
    fun canGoBack(): Boolean = scene.canGoBack()

    companion object {
        /**
         * A headless navigator for `@Preview`s and tests. A *real* navigator
         * whose intents apply synchronously (the resolver is single-pass),
         * over a fresh scene. Nothing is mocked — assert on [scene] after
         * driving it.
         *
         * ```kotlin
         * val navigator = Navigator.testable(
         *     RootLayout.Stack(NavigationContext(root = ProductRoute.Detail(42)))
         * )
         * ```
         */
        fun testable(
            root: RootLayout,
            registry: DestinationRegistry = destinationRegistry {},
        ): Navigator = Navigator(SceneNavigator(root), registry)

        /** A headless navigator over a single back stack — the common case. */
        fun testableStack(
            root: Route? = null,
            path: List<Route> = emptyList(),
            registry: DestinationRegistry = destinationRegistry {},
        ): Navigator = testable(RootLayout.Stack(NavigationContext(root, path)), registry)
    }
}

/** Builds a one-operation intent with the core DSL (keeps call sites terse). */
private fun op(
    build: io.github.memfrag.navigatorkit.intent.NavigationIntentBuilder.() -> Unit,
): io.github.memfrag.navigatorkit.intent.NavigationOperation =
    io.github.memfrag.navigatorkit.intent.navigationIntent(build).operations.single()

/** The navigator for the enclosing [RoutedScene]. */
val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("No Navigator in scope. Wrap content in RoutedScene { }.")
}
