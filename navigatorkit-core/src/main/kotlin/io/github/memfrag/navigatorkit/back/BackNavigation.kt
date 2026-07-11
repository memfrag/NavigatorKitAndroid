package io.github.memfrag.navigatorkit.back

import io.github.memfrag.navigatorkit.state.NavigationContext
import io.github.memfrag.navigatorkit.state.RootLayout
import io.github.memfrag.navigatorkit.state.SceneNavigator

/**
 * System-back resolution — the one navigation concern Android has that iOS
 * doesn't. One rule, owned by the state tree instead of scattered across
 * screens, so predictive back and `BackHandler` wire up in a single place:
 *
 * 1. An alert or dialog on the active leaf is dismissed.
 * 2. The active leaf's back stack pops (works the same inside a sheet).
 * 3. An empty presented leaf is dismissed (sheet, full-screen, or the root
 *    presentation).
 * 4. With a tab root and a non-first tab selected, selection returns to the
 *    first tab (Android convention; disable with [backToFirstTab]).
 * 5. Otherwise returns `false` — let the system finish the activity.
 *
 * ```kotlin
 * BackHandler(enabled = scene.canGoBack()) { scene.goBack() }
 * ```
 */
fun SceneNavigator.goBack(backToFirstTab: Boolean = true): Boolean {
    val leaf = activeContext

    // 1. Transient overlays first.
    if (leaf.alert != null) { leaf.alert = null; return true }
    if (leaf.dialog != null) { leaf.dialog = null; return true }

    // 2. Pop within the deepest visible stack.
    if (leaf.backStack.isNotEmpty()) {
        leaf.backStack.removeAt(leaf.backStack.lastIndex)
        return true
    }

    // 3. Dismiss the presentation the empty leaf belongs to.
    presenterOf(leaf)?.let { presenter ->
        presenter.dismissPresented()
        return true
    }
    if (rootPresentation?.content === leaf) {
        rootPresentation = null
        return true
    }

    // 4. Tab convention: back returns to the first tab.
    if (backToFirstTab) {
        val tabs = (root as? RootLayout.Tabs)?.tabs
        if (tabs != null && tabs.selection != tabs.tabs.first().id) {
            tabs.selection = tabs.tabs.first().id
            return true
        }
    }

    // 5. Nothing left — the system should handle it.
    return false
}

/** Whether [goBack] would consume a back event right now. */
fun SceneNavigator.canGoBack(backToFirstTab: Boolean = true): Boolean {
    val leaf = activeContext
    if (leaf.alert != null || leaf.dialog != null) return true
    if (leaf.backStack.isNotEmpty()) return true
    if (presenterOf(leaf) != null || rootPresentation?.content === leaf) return true
    if (backToFirstTab) {
        val tabs = (root as? RootLayout.Tabs)?.tabs
        if (tabs != null && tabs.selection != tabs.tabs.first().id) return true
    }
    return false
}

/** The context presenting `leaf`, or null if `leaf` is not presented content. */
private fun SceneNavigator.presenterOf(leaf: NavigationContext): NavigationContext? =
    allContexts.firstOrNull { it.presented?.content === leaf }
