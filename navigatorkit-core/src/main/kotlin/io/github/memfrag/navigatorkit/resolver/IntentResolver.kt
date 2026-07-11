package io.github.memfrag.navigatorkit.resolver

import io.github.memfrag.navigatorkit.Route
import io.github.memfrag.navigatorkit.TabId
import io.github.memfrag.navigatorkit.intent.ActivationFallback
import io.github.memfrag.navigatorkit.intent.NavigationIntent
import io.github.memfrag.navigatorkit.intent.NavigationOperation
import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.PresentationStyle
import io.github.memfrag.navigatorkit.state.PresentedContext
import io.github.memfrag.navigatorkit.state.RootLayout
import io.github.memfrag.navigatorkit.state.RouteLocation
import io.github.memfrag.navigatorkit.state.SceneNavigator
import io.github.memfrag.navigatorkit.state.SplitLayout
import io.github.memfrag.navigatorkit.state.allContexts
import io.github.memfrag.navigatorkit.state.primaryContext

/**
 * Applies a [NavigationIntent] to a scene's state tree.
 *
 * This is the Swift package's Planner + IntentExecutor collapsed into one
 * pass: Compose materializes arbitrary state in a single recomposition, so
 * Android needs no staged execution or transition awaiting. What *is*
 * preserved — verbatim — is the cross-platform contract:
 *
 * - Operations apply to a cursor that starts at the scene's active context;
 *   `selectTab` retargets it, `present` descends into the child.
 * - Switching tabs dismisses the outgoing lineage's presentations (parity
 *   with iOS, where presentations are window-global; kept on Android so the
 *   same intent yields the same end state on both platforms).
 * - Mutating a stack that has something presented over it dismisses the
 *   presentation first.
 * - Sibling tabs' stacks are never touched.
 * - `dismiss`/`dismissAll`/`activate` may not follow `present` within one
 *   intent (same restriction as iOS, keeping shared intent tables portable).
 */
object IntentResolver {

    fun resolve(intent: NavigationIntent, scene: SceneNavigator) {
        Resolution(scene).run {
            intent.operations.forEach(::apply)
        }
    }
}

sealed class NavigationException(message: String) : Exception(message) {
    class NoTabsLayout : NavigationException("Tab operation used but the scene root is not tabs")
    class UnknownTab(val tab: TabId) : NavigationException("Unknown tab: ${tab.value}")
    class NoSplitLayout : NavigationException("Sidebar operation used but no split layout is active")
    class RouteNotInBackStack(val route: Route) : NavigationException("Route not in back stack: $route")
    class InvalidOperation(reason: String) : NavigationException(reason)
}

private class Resolution(private val scene: SceneNavigator) {

    private var cursor: NavigationContext = scene.activeContext
    private var currentLayout: RootLayout = when (val root = scene.root) {
        is RootLayout.Tabs -> root.tabs.selectedLayout
        else -> root
    }
    private var visibleBase: NavigationContext = scene.baseContext
    private var presentedInIntent = false

    fun apply(op: NavigationOperation) {
        when (op) {
            is NavigationOperation.SelectTab -> selectTab(op.tab)
            is NavigationOperation.SelectSidebar -> selectSidebar(op.selection)
            is NavigationOperation.SetStack -> mutateStack { it.clear(); it.addAll(op.routes) }
            is NavigationOperation.Push -> mutateStack { it.add(op.route) }
            is NavigationOperation.Pop -> mutateStack { if (it.isNotEmpty()) it.removeAt(it.lastIndex) }
            is NavigationOperation.PopToRoot -> mutateStack { it.clear() }
            is NavigationOperation.PopTo -> {
                val index = cursor.backStack.lastIndexOf(op.route)
                if (index < 0) throw NavigationException.RouteNotInBackStack(op.route)
                mutateStack { stack -> while (stack.lastIndex > index) stack.removeAt(stack.lastIndex) }
            }
            is NavigationOperation.Present -> present(op.route, op.style)
            is NavigationOperation.Dismiss -> dismiss()
            is NavigationOperation.DismissAll -> dismissAll()
            is NavigationOperation.ShowAlert -> cursor.alert = op.alert
            is NavigationOperation.ShowDialog -> cursor.dialog = op.dialog
            is NavigationOperation.Activate -> activate(op.route, op.fallback)
        }
    }

    // ---- Tab / sidebar selection ----

    private fun selectTab(id: TabId) {
        val tabs = (scene.root as? RootLayout.Tabs)?.tabs
            ?: throw NavigationException.NoTabsLayout()
        val layout = tabs.layoutFor(id) ?: throw NavigationException.UnknownTab(id)
        if (tabs.selection != id) {
            // Outgoing lineage's presentations would cover the incoming tab
            // on iOS; dismissed on both platforms for contract parity.
            dismissVisibleLineagePresentations()
            tabs.selection = id
        }
        currentLayout = layout
        visibleBase = layout.primaryContext
        retarget(layout.primaryContext)
    }

    private fun selectSidebar(selection: Route?) {
        val split = findSplit(currentLayout) ?: throw NavigationException.NoSplitLayout()
        if (split.sidebarSelection != selection) {
            // The detail pane's content changes; a sheet presented from the
            // old detail would linger over the new one.
            split.detailContext.dismissPresented()
            split.sidebarSelection = selection
        }
        retarget(split.detailContext)
    }

    private fun findSplit(layout: RootLayout): SplitLayout? = when (layout) {
        is RootLayout.Split -> layout.split
        is RootLayout.Tabs -> findSplit(layout.tabs.selectedLayout)
        is RootLayout.Stack -> null
    }

    // ---- Stack mutation ----

    private fun mutateStack(mutation: (MutableList<Route>) -> Unit) {
        // No invisible changes behind a presentation.
        clearPresentationsAbove(cursor)
        mutation(cursor.backStack)
    }

    // ---- Present / dismiss ----

    private fun present(route: Route, style: PresentationStyle) {
        clearPresentationsAbove(cursor)
        val content = NavigationContext(root = route)
        val presented = PresentedContext(style, content)
        if (style is PresentationStyle.FullScreen) {
            cursor.fullScreen = presented
        } else {
            cursor.sheet = presented
        }
        cursor = content
        presentedInIntent = true
    }

    private fun dismiss() {
        requireNoPriorPresent("dismiss")
        scene.rootPresentation?.let { rootPresentation ->
            val presenter = rootPresentation.content.selfAndPresentedDescendants
                .lastOrNull { it.presented != null }
            if (presenter != null) {
                presenter.dismissPresented()
                retarget(presenter)
            } else {
                scene.rootPresentation = null
                retarget(visibleBase)
            }
            return
        }
        val presenter = visibleBase.selfAndPresentedDescendants.lastOrNull { it.presented != null }
        if (presenter != null) {
            presenter.dismissPresented()
            retarget(presenter)
        }
        // Nothing presented: no-op.
    }

    private fun dismissAll() {
        requireNoPriorPresent("dismissAll")
        dismissVisibleLineagePresentations()
        retarget(visibleBase)
    }

    private fun requireNoPriorPresent(operation: String) {
        if (presentedInIntent) {
            throw NavigationException.InvalidOperation(
                "$operation cannot follow present within a single intent"
            )
        }
    }

    // ---- Activate ----

    private fun activate(route: Route, fallback: ActivationFallback) {
        requireNoPriorPresent("activate")

        val location = scene.findRoute { it == route }
        if (location == null) {
            when (fallback) {
                ActivationFallback.Push -> mutateStack { it.add(route) }
                ActivationFallback.ReplaceStack -> mutateStack { it.clear(); it.add(route) }
                is ActivationFallback.Present -> present(route, fallback.style)
            }
            return
        }

        when (location) {
            is RouteLocation.InSidebar -> {
                owningTab(location.split.detailContext)?.let(::selectTab)
                retarget(location.split.detailContext)
            }

            is RouteLocation.InContext -> {
                val context = location.context
                val owner = owningTab(context)
                if (owner != null) {
                    selectTab(owner)
                } else if (!isInsideRootPresentation(context)) {
                    scene.rootPresentation = null
                }
                clearPresentationsAbove(context)
                when (val position = location.position) {
                    is RouteLocation.InContext.Position.Root ->
                        context.backStack.clear()
                    is RouteLocation.InContext.Position.BackStack ->
                        while (context.backStack.lastIndex > position.index) {
                            context.backStack.removeAt(context.backStack.lastIndex)
                        }
                }
                retarget(context)
            }
        }
    }

    private fun owningTab(context: NavigationContext): TabId? {
        val tabs = (scene.root as? RootLayout.Tabs)?.tabs ?: return null
        return tabs.tabs.firstOrNull { descriptor ->
            descriptor.content.allContexts.any { it === context }
        }?.id
    }

    private fun isInsideRootPresentation(context: NavigationContext): Boolean =
        scene.rootPresentation?.content?.selfAndPresentedDescendants
            ?.any { it === context } == true

    // ---- Dismissal helpers ----

    private fun dismissVisibleLineagePresentations() {
        scene.rootPresentation = null
        visibleBase.dismissPresented()
    }

    private fun clearPresentationsAbove(context: NavigationContext) {
        if (!isInsideRootPresentation(context)) {
            scene.rootPresentation = null
        }
        context.dismissPresented()
    }

    private fun retarget(context: NavigationContext) {
        cursor = context
    }
}
