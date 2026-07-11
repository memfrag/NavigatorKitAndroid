package io.github.memfrag.navigatorkit.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.memfrag.navigatorkit.Route
import io.github.memfrag.navigatorkit.state.NavigationContext

/**
 * Renders one [NavigationContext] as a back stack of screens, plus everything
 * presented over it (sheets, full-screen modals, alerts, dialogs) —
 * recursively, since presented content is itself a `RoutedStack`.
 *
 * This is the Navigation-3-style renderer: the context's [NavigationContext.backStack]
 * *is* the source of truth. Push/pop animate via [AnimatedContent] keyed on
 * stack depth; the transition direction follows whether the stack grew or shrank.
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

    // root (or override) + pushed routes; reading backStack (a SnapshotStateList)
    // in composable scope subscribes this call to stack changes.
    val stack: List<Route> = buildList {
        (overrideRoot ?: context.root)?.let { add(it) }
        addAll(context.backStack)
    }

    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stack.size,
            transitionSpec = {
                val forward = targetState >= initialState
                val enter = slideInHorizontally(tween(280)) { w -> if (forward) w else -w } + fadeIn(tween(220))
                val exit = slideOutHorizontally(tween(280)) { w -> if (forward) -w else w } + fadeOut(tween(220))
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            label = "stack",
        ) { depth ->
            val visible = stack.getOrNull(depth - 1) ?: stack.lastOrNull()
            if (visible != null) {
                registry.Screen(visible)
            }
        }

        // Presentations + overlays attach at this level, then recurse.
        PresentationHost(context)
    }
}
