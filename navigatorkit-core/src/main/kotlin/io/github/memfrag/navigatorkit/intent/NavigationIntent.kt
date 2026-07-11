package io.github.memfrag.navigatorkit.intent

import io.github.memfrag.navigatorkit.Route
import io.github.memfrag.navigatorkit.TabId
import io.github.memfrag.navigatorkit.state.AlertButton
import io.github.memfrag.navigatorkit.state.PresentationStyle
import io.github.memfrag.navigatorkit.state.RoutedAlert
import io.github.memfrag.navigatorkit.state.RoutedDialog

/**
 * A complete, declarative navigation destination: an ordered list of
 * [NavigationOperation]s. Same DSL shape as the Swift package, so deep-link
 * tables and navigation contracts translate across platforms line-for-line.
 *
 * ```kotlin
 * navigationIntent {
 *     selectTab(AppTab.Shop)
 *     setStack(ProductRoute.List, ProductRoute.Detail(42))
 *     present(ReviewRoute.Compose(42), PresentationStyle.BottomSheet())
 *     push(ReviewRoute.PhotoPicker)
 *     alert("Arrived!")
 * }
 * ```
 */
data class NavigationIntent(val operations: List<NavigationOperation>) {

    /** The route the intent ultimately lands on — used by scene policies. */
    val primaryRoute: Route?
        get() {
            for (op in operations.asReversed()) {
                when (op) {
                    is NavigationOperation.Push -> return op.route
                    is NavigationOperation.PopTo -> return op.route
                    is NavigationOperation.Present -> return op.route
                    is NavigationOperation.Activate -> return op.route
                    is NavigationOperation.SetStack ->
                        op.routes.lastOrNull()?.let { return it }
                    is NavigationOperation.SelectSidebar ->
                        op.selection?.let { return it }
                    else -> continue
                }
            }
            return null
        }

    companion object {
        /** Single-route navigation with explicit placement semantics. */
        fun navigate(route: Route, placement: RoutePlacement = RoutePlacement.Push): NavigationIntent =
            NavigationIntent(
                listOf(
                    when (placement) {
                        RoutePlacement.Push -> NavigationOperation.Push(route)
                        RoutePlacement.ReplaceStack -> NavigationOperation.SetStack(listOf(route))
                        is RoutePlacement.Present -> NavigationOperation.Present(route, placement.style)
                        is RoutePlacement.ActivateExisting ->
                            NavigationOperation.Activate(route, placement.fallback)
                    }
                )
            )
    }
}

/** One declarative navigation step. Applied to a moving cursor — see the resolver. */
sealed interface NavigationOperation {
    data class SelectTab(val tab: TabId) : NavigationOperation
    data class SelectSidebar(val selection: Route?) : NavigationOperation
    data class SetStack(val routes: List<Route>) : NavigationOperation
    data class Push(val route: Route) : NavigationOperation
    data object Pop : NavigationOperation
    data object PopToRoot : NavigationOperation
    data class PopTo(val route: Route) : NavigationOperation
    data class Present(val route: Route, val style: PresentationStyle) : NavigationOperation
    data object Dismiss : NavigationOperation
    data object DismissAll : NavigationOperation
    data class ShowAlert(val alert: RoutedAlert) : NavigationOperation
    data class ShowDialog(val dialog: RoutedDialog) : NavigationOperation
    data class Activate(val route: Route, val fallback: ActivationFallback) : NavigationOperation
}

/** What [NavigationOperation.Activate] does when the route isn't in the tree. */
sealed interface ActivationFallback {
    data object Push : ActivationFallback
    data object ReplaceStack : ActivationFallback
    data class Present(val style: PresentationStyle) : ActivationFallback
}

/** Where `navigate(to:)` puts a single route. Mirrors the Swift contract. */
sealed interface RoutePlacement {
    data object Push : RoutePlacement
    data object ReplaceStack : RoutePlacement
    data class Present(val style: PresentationStyle) : RoutePlacement
    data class ActivateExisting(val fallback: ActivationFallback) : RoutePlacement

    companion object {
        fun bottomSheet(skipPartiallyExpanded: Boolean = false): RoutePlacement =
            Present(PresentationStyle.BottomSheet(skipPartiallyExpanded))
        val fullScreen: RoutePlacement = Present(PresentationStyle.FullScreen)
    }
}

// ---- Builder DSL ----

fun navigationIntent(build: NavigationIntentBuilder.() -> Unit): NavigationIntent =
    NavigationIntent(NavigationIntentBuilder().apply(build).operations)

class NavigationIntentBuilder internal constructor() {
    internal val operations = mutableListOf<NavigationOperation>()

    fun selectTab(tab: TabId) { operations += NavigationOperation.SelectTab(tab) }
    fun selectTab(tab: String) = selectTab(TabId(tab))
    fun <T : Enum<T>> selectTab(tab: T) = selectTab(TabId(tab.name.lowercase()))

    fun selectSidebar(selection: Route?) { operations += NavigationOperation.SelectSidebar(selection) }

    fun setStack(vararg routes: Route) { operations += NavigationOperation.SetStack(routes.toList()) }
    fun push(route: Route) { operations += NavigationOperation.Push(route) }
    fun pop() { operations += NavigationOperation.Pop }
    fun popToRoot() { operations += NavigationOperation.PopToRoot }
    fun popTo(route: Route) { operations += NavigationOperation.PopTo(route) }

    fun present(route: Route, style: PresentationStyle = PresentationStyle.BottomSheet()) {
        operations += NavigationOperation.Present(route, style)
    }

    fun dismiss() { operations += NavigationOperation.Dismiss }
    fun dismissAll() { operations += NavigationOperation.DismissAll }

    fun alert(title: String, message: String? = null, buttons: List<AlertButton> = emptyList()) {
        operations += NavigationOperation.ShowAlert(RoutedAlert(title, message, buttons))
    }

    fun dialog(title: String, message: String? = null, buttons: List<AlertButton> = emptyList()) {
        operations += NavigationOperation.ShowDialog(RoutedDialog(title, message, buttons))
    }

    fun activate(route: Route, fallback: ActivationFallback = ActivationFallback.Push) {
        operations += NavigationOperation.Activate(route, fallback)
    }
}
