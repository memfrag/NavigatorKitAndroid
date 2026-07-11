package io.github.memfrag.navigatorkit.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.memfrag.navigatorkit.Route

/**
 * Per-scene navigation state: one of these exists per activity/window,
 * owning the scene's complete navigation tree. Pure state plus tree
 * queries — mutation with correct semantics is [IntentResolver]'s job.
 */
class SceneNavigator(
    val root: RootLayout,
) {
    /** A presentation covering the entire root (e.g. onboarding over tabs). */
    var rootPresentation: PresentedContext? by mutableStateOf(null)

    /** The context new navigation operations target by default. */
    val activeContext: NavigationContext
        get() = rootPresentation?.content?.activeLeaf ?: root.primaryContext.activeLeaf

    /** The current base context (selected tab / detail pane), ignoring presentations. */
    val baseContext: NavigationContext
        get() = root.primaryContext

    /** Every context in the scene, including presented descendants. */
    val allContexts: List<NavigationContext>
        get() = root.allContexts +
            (rootPresentation?.content?.selfAndPresentedDescendants ?: emptyList())

    fun contains(route: Route): Boolean = findRoute { it == route } != null

    inline fun <reified R : Route> containsRouteOfType(): Boolean =
        findRoute { it is R } != null

    /**
     * Finds the first route matching the predicate, searching the root layout
     * (contexts, presented descendants, and split sidebars) in layout order,
     * then any root presentation chain.
     */
    fun findRoute(predicate: (Route) -> Boolean): RouteLocation? {
        root.findRoute(predicate)?.let { return it }
        rootPresentation?.content?.selfAndPresentedDescendants?.forEach { context ->
            context.findRouteHere(predicate)?.let { return it }
        }
        return null
    }
}

/** Where in a scene's tree a route was found. */
sealed interface RouteLocation {
    /** In a context's root screen or back stack. */
    data class InContext(
        val context: NavigationContext,
        val position: Position,
    ) : RouteLocation {
        sealed interface Position {
            data object Root : Position
            data class BackStack(val index: Int) : Position
        }
    }

    /** A split's sidebar root or sidebar selection. */
    data class InSidebar(val split: SplitLayout, val slot: Slot) : RouteLocation {
        enum class Slot { Root, Selection }
    }
}

internal fun NavigationContext.findRouteHere(predicate: (Route) -> Boolean): RouteLocation? {
    root?.takeIf(predicate)?.let {
        return RouteLocation.InContext(this, RouteLocation.InContext.Position.Root)
    }
    val index = backStack.indexOfFirst(predicate)
    if (index >= 0) {
        return RouteLocation.InContext(this, RouteLocation.InContext.Position.BackStack(index))
    }
    return null
}

internal fun RootLayout.findRoute(predicate: (Route) -> Boolean): RouteLocation? = when (this) {
    is RootLayout.Stack ->
        context.selfAndPresentedDescendants.firstNotNullOfOrNull { it.findRouteHere(predicate) }

    is RootLayout.Tabs ->
        tabs.tabs.firstNotNullOfOrNull { it.content.findRoute(predicate) }

    is RootLayout.Split -> {
        split.sidebarRoot?.takeIf(predicate)
            ?.let { RouteLocation.InSidebar(split, RouteLocation.InSidebar.Slot.Root) }
            ?: split.sidebarSelection?.takeIf(predicate)
                ?.let { RouteLocation.InSidebar(split, RouteLocation.InSidebar.Slot.Selection) }
            ?: split.detailContext.selfAndPresentedDescendants
                .firstNotNullOfOrNull { it.findRouteHere(predicate) }
    }
}
