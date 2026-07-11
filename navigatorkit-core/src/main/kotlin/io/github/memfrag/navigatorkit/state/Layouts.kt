package io.github.memfrag.navigatorkit.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.memfrag.navigatorkit.Route
import io.github.memfrag.navigatorkit.TabId

/**
 * The container shape at (some level of) a scene's root: a single stack,
 * a tab bar, or a list-detail split. Tabs may host splits or stacks,
 * covering the "navigation suite + list-detail" tablet pattern.
 */
sealed interface RootLayout {
    data class Stack(val context: NavigationContext) : RootLayout
    data class Tabs(val tabs: TabsLayout) : RootLayout
    data class Split(val split: SplitLayout) : RootLayout
}

/**
 * The context that base navigation operations target for this layout:
 * the stack itself, the selected tab's primary context, or the split's
 * detail context.
 */
val RootLayout.primaryContext: NavigationContext
    get() = when (this) {
        is RootLayout.Stack -> context
        is RootLayout.Tabs -> tabs.selectedLayout.primaryContext
        is RootLayout.Split -> split.detailContext
    }

/** Base (unpresented) contexts across all tabs/panes. */
val RootLayout.baseContexts: List<NavigationContext>
    get() = when (this) {
        is RootLayout.Stack -> listOf(context)
        is RootLayout.Tabs -> tabs.tabs.flatMap { it.content.baseContexts }
        is RootLayout.Split -> listOf(split.detailContext)
    }

/** Every context reachable in this layout, including presented descendants. */
val RootLayout.allContexts: List<NavigationContext>
    get() = baseContexts.flatMap { it.selfAndPresentedDescendants }

/** State of a tab root: which tab is selected and what each tab hosts. */
class TabsLayout(
    selection: TabId,
    val tabs: List<TabDescriptor>,
) {
    init {
        require(tabs.isNotEmpty()) { "TabsLayout requires at least one tab" }
        require(tabs.any { it.id == selection }) {
            "Initial selection ${selection.value} is not among the declared tabs"
        }
    }

    var selection: TabId by mutableStateOf(selection)

    fun layoutFor(tab: TabId): RootLayout? = tabs.firstOrNull { it.id == tab }?.content

    val selectedLayout: RootLayout
        get() = checkNotNull(layoutFor(selection)) {
            "Selected tab ${selection.value} is not among the declared tabs"
        }
}

/** One tab: identity, chrome, and the layout it hosts. */
class TabDescriptor(
    val id: TabId,
    val title: String,
    val icon: String? = null,
    val content: RootLayout,
)

/**
 * State of a list-detail split (Material 3 adaptive
 * `NavigableListDetailPaneScaffold` on the UI side).
 *
 * The list pane is a *selection*, not a stack; the detail pane is a full
 * [NavigationContext], so intents address it exactly like a tab's stack.
 * Property names match the Swift package so both teams share vocabulary.
 */
class SplitLayout(
    /** The route rendered as the list pane's content (from the blueprint). */
    val sidebarRoot: Route? = null,
    sidebarSelection: Route? = null,
    val detailContext: NavigationContext = NavigationContext(),
) {
    var sidebarSelection: Route? by mutableStateOf(sidebarSelection)
}
