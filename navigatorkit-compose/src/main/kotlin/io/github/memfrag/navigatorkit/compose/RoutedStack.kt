package io.github.memfrag.navigatorkit.compose

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.memfrag.navigatorkit.Route
import io.github.memfrag.navigatorkit.state.NavigationContext
import java.util.UUID

/**
 * Renders one [NavigationContext] as a back stack of screens, plus everything
 * presented over it (sheets, full-screen modals, alerts, dialogs) —
 * recursively, since presented content is itself a `RoutedStack`.
 *
 * The stack is drawn by Navigation 3's [NavDisplay] — the counterpart of the
 * Swift package binding its context to SwiftUI's `NavigationStack`. The
 * context's [NavigationContext.backStack] stays the source of truth; Nav3 only
 * renders it, giving each entry its own saved UI state and `ViewModelStore`
 * (both survive pushes over the entry, and are cleared when it's popped) and
 * predictive-back pop animations.
 *
 * System back is still resolved by the tree: [NavDisplay]'s back handler
 * delegates to [Navigator.goBack], so the deepest-first rule holds no matter
 * which handler receives the event.
 *
 * @param overrideRoot when non-null, replaces the context's own root screen —
 *   used by the split detail pane to show the current sidebar selection.
 */
@Composable
fun RoutedStack(
    context: NavigationContext,
    modifier: Modifier = Modifier,
    overrideRoot: Route? = null,
) {
    val registry = LocalDestinationRegistry.current
    val navigator = LocalNavigator.current

    // root (or override) + pushed routes; reading backStack (a SnapshotStateList)
    // in composable scope subscribes this call to stack changes.
    val stack: List<StackEntry> = buildList {
        (overrideRoot ?: context.root)?.let { add(it) }
        addAll(context.backStack)
    }.mapIndexed(::StackEntry)

    // Entry state is keyed by content key, and the ViewModel decorator keeps one
    // store map per ViewModelStoreOwner (the activity) shared by every
    // NavDisplay — so keys must be unique across simultaneously-shown stacks
    // (e.g. a sheet over its base), not just within this one.
    val scope = rememberSaveable(context) { UUID.randomUUID().toString() }

    Box(modifier.fillMaxSize()) {
        // NavDisplay requires a non-empty stack; a context with no routed root
        // and nothing pushed renders no screen (presentations still attach).
        if (stack.isNotEmpty()) {
            NavDisplay(
                backStack = stack,
                onBack = { navigator.goBack() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = { slide(forward = true) },
                popTransitionSpec = { slide(forward = false) },
                entryProvider = { entry ->
                    NavEntry(entry, contentKey = "$scope/${entry.index}/${entry.route}") {
                        registry.Screen(entry.route)
                    }
                },
            )
        }

        // Presentations + overlays attach at this level, then recurse.
        PresentationHost(context)
    }
}

/** A stack position — the same route may legitimately appear twice in one stack. */
private data class StackEntry(val index: Int, val route: Route)

private fun slide(forward: Boolean) =
    (slideInHorizontally(tween(280)) { w -> if (forward) w else -w } + fadeIn(tween(220)))
        .togetherWith(slideOutHorizontally(tween(280)) { w -> if (forward) -w else w } + fadeOut(tween(220)))
