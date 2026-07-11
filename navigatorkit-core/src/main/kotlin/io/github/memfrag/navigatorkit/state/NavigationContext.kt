package io.github.memfrag.navigatorkit.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.github.memfrag.navigatorkit.Route

/**
 * One presentation context: a back stack plus everything presented on top
 * of it. The recursive node of the navigation state tree — a bottom sheet's
 * content is itself a `NavigationContext`, so "a sheet containing a stack
 * that presents another sheet" is just a deeper tree.
 *
 * Snapshot-backed: Compose UIs (Navigation 3 `NavDisplay`, tab scaffolds,
 * `ModalBottomSheet`) bind straight to these properties and recompose on
 * change. Unlike SwiftUI, Compose materializes any state in one pass, so
 * there is no staged executor on Android — the tree *is* the truth.
 */
class NavigationContext(
    /**
     * The route rendered at the root of this context's stack, or null when
     * the root screen is supplied directly by the composition layer.
     */
    val root: Route? = null,
    initialBackStack: List<Route> = emptyList(),
) {
    /** Pushed routes; binds to Navigation 3's `NavDisplay(backStack)`. */
    val backStack: SnapshotStateList<Route> =
        mutableStateListOf<Route>().apply { addAll(initialBackStack) }

    /** The bottom sheet presented over this context, if any. */
    var sheet: PresentedContext? by mutableStateOf(null)

    /** The full-screen modal presented over this context, if any. */
    var fullScreen: PresentedContext? by mutableStateOf(null)

    /** The alert shown on this context, if any. Transient — never persisted. */
    var alert: RoutedAlert? by mutableStateOf(null)

    /** The choice dialog shown on this context, if any. Transient. */
    var dialog: RoutedDialog? by mutableStateOf(null)

    // ---- Tree queries ----

    /** The presentation currently on top (sheet takes precedence). */
    val presented: PresentedContext?
        get() = sheet ?: fullScreen

    /** The deepest currently-presented context — `this` if nothing is presented. */
    val activeLeaf: NavigationContext
        get() = presented?.content?.activeLeaf ?: this

    /** This context followed by presented descendants, outermost first. */
    val selfAndPresentedDescendants: List<NavigationContext>
        get() = buildList {
            var current: NavigationContext? = this@NavigationContext
            while (current != null) {
                add(current)
                current = current.presented?.content
            }
        }

    /** Root (if routed) plus the back stack. */
    val allRoutes: List<Route>
        get() = listOfNotNull(root) + backStack

    fun containsRoute(route: Route): Boolean = allRoutes.contains(route)

    /** State-level dismissal of whatever is presented (both slots). */
    fun dismissPresented() {
        sheet = null
        fullScreen = null
    }
}

/** A child context presented over a parent, together with how it is shown. */
class PresentedContext(
    val style: PresentationStyle,
    val content: NavigationContext,
)
